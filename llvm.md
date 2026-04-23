# LLVM ORC JIT Backend for Rell

## Design Overview

Split the Rell toolchain into a JVM frontend (compiler) and a native backend (runtime), connected via Project Panama (Foreign Function & Memory API).

### JVM (Frontend) — unchanged

- **Parser / Tokenizer** (`S_` layer) — preserves parsing correctness, no migration risk
- **Compiler passes** (`C_` layer) — all 13 passes from DEFINITIONS through FINISH
- **Type system** (`M_` layer) — compile-time type resolution and checking
- **Library descriptors** (`L_` layer) — type/function metadata and signatures
- **Model construction** — produces `R_App`, the fully compiled application object graph

### LLVM / Native (Backend) — new

- **LLVM ORC JIT compiler** — translates R_App IR into LLVM IR, then JIT-compiles to native machine code at load time via ORC (On-Request Compilation)
- **Standard library function bodies** — all `R_SysFunction` implementations as precompiled C++ called from JIT'd code
- **SQL generation** — `Db_Expr` → `RedDb_Expr` → `ParameterizedSql` pipeline, compiled to native call sequences
- **SQL execution** — `libpq` replaces JDBC; implements the `SqlExecutor` interface natively
- **`Rt_Value` representation** — native value types (`mpdecimal` for decimals, ICU for strings, OpenSSL for crypto, etc.)

#### Why ORC JIT, not a native tree-walker

A native tree-walker just moves the same virtual-dispatch overhead from JVM to C++ — pointless. ORC JIT compiles Rell programs to actual machine code:

- `R_Expr` / `R_Statement` nodes become LLVM IR (SSA form) during IR lowering
- LLVM optimization passes (mem2reg, inlining, constant folding, loop unrolling) run on the generated IR
- ORC lazily compiles functions on first call — no ahead-of-time compilation delay
- Hot loops, arithmetic, collection operations, string processing all run as native instructions, not dispatched through vtables

#### IR Lowering: R_App → LLVM IR

The JIT compiler translates each `R_FunctionDefinition` body into an LLVM function:

- **Statements** (`R_IfStatement`, `R_WhileStatement`, `R_ForStatement`, etc.) map to LLVM basic blocks with `br`/`cond_br` instructions
- **Expressions** (`R_BinaryExpr`, `R_MemberExpr`, etc.) map to LLVM `Value` computations
- **Local variables** (`R_VarPtr` slots in the current frame) become LLVM `alloca` instructions, promoted to SSA registers by `mem2reg`
- **Function calls** become direct `call` instructions to other JIT'd functions or precompiled stdlib symbols
- **At-expressions** lower to native calls into the SQL generation/execution runtime (precompiled C++), passing computed parameter values as arguments
- **Control flow** (`break`, `continue`, `return`) maps directly to LLVM branches — no sealed-class result values needed

### Panama Bridge

**Compiler → Runtime (one-time, per program load):**

The JVM compiler serializes `R_App` into a binary IR format and passes it across Panama. This is the primary crossing point — a single transfer of the compiled program model.

The IR must encode:
- All `R_Expr` / `R_Statement` node types (dozens of subclasses each)
- `R_EntityDefinition`, `R_FunctionDefinition`, `R_Type` and their relationships
- Cyclic references (entities ↔ types ↔ functions)

**Runtime → JVM (infrequent callbacks):**

The native runtime calls back into JVM for Postchain integration:
- `chain_context` — blockchain configuration
- `op_context` — current operation/transaction context
- Block-building hooks, event emission
- GTV encode/decode for transaction arguments

These are per-operation, not per-expression, so Panama overhead is acceptable.

## Key Design Property

SQL stays on the same side as execution. At-expressions evaluate, generate SQL, and call `libpq` entirely within native code. No boundary-crossing during hot execution paths.

```
                    ┌─────────────┐
   Rell source ───►│ JVM Compiler │───► R_App (serialized IR)
                    └─────────────┘            │
                                               │ Panama (once)
                                               ▼
                                      ┌────────────────┐
                                      │  LLVM Runtime   │
                                      │                 │
                                      │  ORC JIT        │
                                      │  IR Lowering    │
                                      │  Stdlib (C++)   │
                                      │  SQL gen        │──── libpq ────► PostgreSQL
                                      │  Rt_Values      │
                                      └────────┬───────┘
                                               │ Panama (callbacks)
                                               ▼
                                      ┌────────────────┐
                                      │  Postchain JVM  │
                                      │  chain_context  │
                                      │  op_context     │
                                      │  GTV codec      │
                                      └────────────────┘
```

## Verification Strategy

Run the existing Rell test suite against both runtimes in parallel with the same PostgreSQL instance. Compare outputs, DB state, and side effects byte-for-byte.

```
                         ┌────────────────┤
                         ▼                ▼
                   ┌───────────┐   ┌────────────┐
                   │ JVM Runtime│   │LLVM Runtime │
                   │ (existing) │   │ (new)       │
                   └─────┬─────┘   └──────┬──────┘
                         │                │
                         ▼                ▼
                    Compare outputs, DB state,
                    side effects byte-for-byte
```

Any divergence is a bug in the native runtime.

## Critical Implementation Details

### Decimal Precision

`java.math.BigDecimal` with `MathContext.DECIMAL128` must be matched exactly by `mpdecimal` (or equivalent). IEEE 754-2008 decimal128 semantics should align if both implementations are compliant, but edge cases (subnormals, rounding ties, overflow) need exhaustive testing.

### Native Library Replacements

### R_App Serialization — `rell-serialization` Module

FlatBuffers zero-copy serialization transfers the compiled `R_App` model from the JVM compiler to native C++ code via Panama. The JVM side writes a flat buffer, passes the pointer across Panama, and C++ traverses it in-place without deserialization.

#### Module Structure

```
rell-serialization/
  build.gradle.kts                          # flatc provisioning + codegen tasks
  src/main/
    flatbuffers/rell/ir/
      app.fbs                               # App root table, Module
      def.fbs                               # Entity, Struct, Enum, Operation, Query, Function, Constant
      ir.fbs                                # ExprUnion, StmtUnion, DbExprUnion, WhenChooser, etc.
      common.fbs                            # Names, IDs, positions
      frame.fbs                             # CallFrame, FrameBlock, VarPtr
      op.fbs                                # BinaryOp, UnaryOp enums
      type.fbs                              # Type union (~20 variants)
      value.fbs                             # TypedValue for constants
    kotlin/net/postchain/rell/serialization/
      SerializerContext.kt                  # Index maps, FlatBufferBuilder, definition scanning
      TypeSerializer.kt                     # R_Type → FlatBuffer offset
      ExprSerializer.kt                     # R_Expr → FlatBuffer offset (35 expr types)
      StmtSerializer.kt                     # R_Statement → FlatBuffer offset (16 stmt types)
      OpSerializer.kt                       # R_BinaryOp/R_UnaryOp → FlatBuffer enums
      DefSerializer.kt                      # Definitions → FlatBuffer offsets
      DbExprSerializer.kt                   # Db_Expr → FlatBuffer offset (placeholder)
      ValueSerializer.kt                    # Rt_Value → TypedValue
      CommonSerializer.kt                   # Common helpers (names, positions, frames)
      RellAppSerializer.kt                  # Top-level: R_App → ByteArray
  build/generated/
    flatbuffers/kotlin/                     # flatc --kotlin output
    flatbuffers/cpp/                        # flatc --cpp output (consumed by rell-base/llvm)
```

#### Build Integration

- **flatc**: auto-downloaded from GitHub releases (v25.2.10), Mac ARM64/x86_64 + Linux clang++-18
- **`generateFlatBuffersKotlin`**: `flatc --kotlin` → `build/generated/flatbuffers/kotlin/`
- **`generateFlatBuffersCpp`**: `flatc --cpp` → `build/generated/flatbuffers/cpp/`
- **Dependencies**: `rell-base` (R_* types), `flatbuffers-java:25.2.10`

#### Schema Design

**Reference strategy**: Definitions (entity, struct, enum, function, operation, query, object, constant) are stored in flat arrays in the root `App` table. All cross-references use `uint32` indices. Types, expressions, and statements are serialized inline (no deduplication). Cyclic references (entity ↔ type) are broken by encoding `EntityType`/`StructType`/`EnumType` as indices into the flat arrays.

**Root table**:
```
table App {
  entities, objects, structs, enums,
  operations, queries, functions, constants,
  modules, external_chains
}
```

**Key unions**:
- **ExprUnion** (35 variants): VarExpr, ConstantValueExpr, BinaryExpr, UnaryExpr, IfExpr, WhenExpr, ElvisExpr, NotNullExpr, TupleExpr, ListLiteralExpr, MapLiteralExpr, StructExpr, RegularCreateExpr, StructCreateExpr, StructListCreateExpr, FunctionCallExpr, MemberExpr, AssignExpr, StatementExpr, GlobalConstantExpr, ChainHeightExpr, TypeAdapterExpr, ParameterDefaultValueExpr, AttributeDefaultValueExpr, DbAtExpr, ColAtExpr, ErrorExpr, 7 subscript exprs, StructMemberExpr
- **StmtUnion** (17 variants): EmptyStatement through LambdaStatement, UpdateStatement, DeleteStatement
- **DbExprUnion** (15 variants): DbInterpretedExpr through DbNestedAtExpr
- **FunctionCallTargetUnion** (7 variants): RegularUser, AbstractUser, NativeUser, Operation, FunctionValue, SysGlobal, SysMember
- **MemberCalculatorUnion** (4 variants): TupleAttr, VirtualTupleAttr, VirtualStructAttr, SysFunction

**Non-serializable types**:
- `R_SysFunction`: serialized as full name string; C++ side has a native function registry
- `Rt_Value` (constants): serialized as `TypedValue` union (Int, Bool, Text, ByteArray, Decimal, BigInteger, Null, Enum, Gtv)
- `C_LateGetter<T>`: resolved post-compilation; serializer calls `.get()`
- IDE/doc metadata (`C_IdeSymbolInfo`, `DocSymbol`): skipped

#### Visibility and Cross-Module Access

Most R_* model classes in `rell-base` have been made `public` to allow direct field access from the bridge module without reflection. The visibility lifts include:

- **Expression classes** (`r_expr.kt`, `r_expr_op_bin.kt`, `r_expr_op_un.kt`, `r_expr_member.kt`, `r_expr_fn.kt`, `r_expr_fn_target.kt`): ~80 classes/objects made public
- **Statement classes** (`r_stmt.kt`): all statement, declarator, and adapter classes made public
- **Definition classes** (`r_def.kt`, `r_def_fn.kt`, `r_def_attr.kt`): key fields like `fnBase`, `internals`, `bodyLate`, `constId`, `sqlMapping`, `canSetInCreate` made public
- **Frame types** (`r_frame.kt`, `r_app.kt`): `R_CallFrame`, `R_FrameBlock`, `R_VarPtr`, `R_AppUid`, `R_ContainerUid`, `R_FnUid`, `R_FrameBlockUid` made public
- **SQL mapping classes** (`r_sql.kt`): all `R_EntitySqlMapping` variants made public
- **Runtime types** (`rt_frame.kt`): `Rt_CallFrame`, `Rt_CallFrameState` made public

All `@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")` annotations have been removed. All required types are now public.

#### Serialization Flow

1. Pre-scan all modules → build flat arrays of all definitions, assign `uint32` indices
2. Serialize all definitions bottom-up (attributes → entities → functions → ...)
3. Serialize modules (with index references to definitions)
4. Build root `App` table
5. `builder.sizedByteArray()` → `ByteArray`

### Compilation Modes

#### Option A: Serialize R_App + ORC JIT (primary)

JVM serializes `R_App` via `rell-serialization` into a FlatBuffer. Native C++ traverses it in-place, lowers to LLVM IR via `IRBuilder`, and JIT-compiles with ORC at load time.

- JVM side: `RellAppSerializer.serialize(app)` → `ByteArray` (FlatBuffer)
- C++ side: traverse flat buffer → emit LLVM IR → ORC JIT → execute
- LLVM is a runtime dependency on every node

#### Option B: Serialize R_App + AOT to LLVM bitcode (future)

Compile Rell to target-independent LLVM bitcode at build time. Ship bitcode in GTX module metadata. Nodes do only final lowering (instruction selection + register allocation) — no full compilation at load time.

```
Developer machine                          Node
────────────────                          ────
Rell source
  → JVM compiler → R_App
  → serialize R_App
  → C++ lowers to LLVM IR
  → LLVM optimization passes
  → emit target-independent bitcode (.bc)
  → package .bc into GTX module metadata
                                          Load .bc from metadata
                                          → llc (target lowering only)
                                          → native object code
                                          → link against precompiled stdlib + libpq
                                          → execute
```

- LLVM is a build-time dependency only (developer toolchain), not a runtime dependency on nodes
- Nodes need only a thin `llc`-equivalent for their target architecture
- Determinism by construction: all nodes execute the same compiled code from the same bitcode
- Pin LLVM version in node runtime to prevent divergent codegen across nodes
- Makes Rell an actual compiled language

Option A is the primary target. Option B extends naturally from A once IR lowering is stable — the same C++ codegen pipeline produces LLVM IR in both cases, the difference is just ORC JIT vs. `llc` + emit bitcode.

## Work Breakdown

| Component | Size | Risk |
|---|---|---|
| R_App serialization format (IR) | Large | Medium |
| LLVM IR lowering (R_App → LLVM IR) | **Large** | **Medium–High** — core of the project |
| ORC JIT integration + lazy compilation | Medium | Medium |
| Stdlib — trivial functions | Medium | Low |
| Stdlib — decimal, json, crypto, GTV | Large | **High** (determinism) |
| libpq SQL executor | Small–Medium | Low |
| Panama bridge (R_App transfer) | Medium | Medium |
| Panama bridge (Postchain callbacks) | Small–Medium | Low |
| End-to-end test harness | Medium | Low |
