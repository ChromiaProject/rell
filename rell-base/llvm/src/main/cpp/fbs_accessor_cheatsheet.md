// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.

# FlatBuffer C++ accessor cheat-sheet (RR-tree)

Generated header: `app_generated.h` (root: `ir::GetApp(ptr)`, verify: `ir::VerifyAppBuffer(verifier)`).
Namespace alias used throughout the C++ runtime: `namespace ir = rell::ir;`.

## flatc naming rules (memorize — every entry below follows these)

- Union wrapper table `T` with field `f: U (required)`:
  - tag enum:  `ir::U_<Variant>`            e.g. `ir::ExprUnion_VarExpr`
  - discriminant getter: `t.<f>_type()`     e.g. `expr.expr_type()` → `ir::U` enum
  - typed getter: `t.<f>_as_<Variant>()`    e.g. `expr.expr_as_VarExpr()` → `const ir::VarExpr*` (nullptr if tag differs)
- Scalar field `x: int` → `t.x()` returns `int32_t`. `long`→`int64_t`, `uint`→`uint32_t`, `bool`→`bool`, `ubyte` enum→its enum type.
- Table/struct field `y: Foo` → `t.y()` returns `const ir::Foo*` (nullptr if absent; non-null guaranteed only if `(required)`).
- `string` field `s` → `t.s()` returns `const flatbuffers::String*`; use `->c_str()` / `->str()` / `->size()`.
- Vector field `v: [Foo]` → `t.v()` returns `const flatbuffers::Vector<flatbuffers::Offset<ir::Foo>>*`; `->size()`, `->Get(i)` → `const ir::Foo*`.
  Vector of scalars `[ubyte]`/`[int]` → `->Get(i)` returns the scalar. Vector of strings → `->Get(i)` is `const flatbuffers::String*`.
- `struct` (VarPtr) is inline/by-value: getters return scalars directly, never null when the containing field is present.
- Nullable-default scalar (`x: int = null`): presence test via the generated `t.x()` returning sentinel; flatc also emits no separate has-bit — these are documented per-field below where it matters (treat `= null` fields as "may be Kotlin-absent; check the documented sentinel").
- `(required)` fields are guaranteed non-null AFTER `VerifyAppBuffer` passed. Still defensively null-check in lowering (mirror jni_bridge.cpp), because soft-fail must never deref null.

## SOFT-FAIL CONTRACT (correctness floor)

Any variant/field a lowering pass cannot translate bit-exactly must make the WHOLE function soft-fail
(return 0 from `compileFunctionByIndex` → JVM `Rt_InterpreterImpl` runs it). Never emit approximate IR.
Determinism is non-negotiable. When in doubt → soft-fail or route to the universal JNI stdlib caller.

================================================================================
# UNION: Expr  (wrapper `ir::Expr`, field `expr: ExprUnion (required)`)
================================================================================
Discriminant: `expr.expr_type()` → `ir::ExprUnion`.  Typed: `expr.expr_as_<V>()`.
37 variants. Most carry a `type: Type (required)` → result type (read via `v.type()`).

| tag enum | as_ accessor | key fields (accessor : element-type) |
|---|---|---|
| `ir::ExprUnion_VarExpr` | `expr_as_VarExpr()` | `type()`:`const Type*` · `ptr()`:`const VarPtr*` (struct: `.block_uid()` u32, `.offset()` i32) · `name()`:`const String*` |
| `ir::ExprUnion_ConstantValueExpr` | `expr_as_ConstantValueExpr()` | `typed_value()`:`const TypedValue*` (req) |
| `ir::ExprUnion_BinaryExpr` | `expr_as_BinaryExpr()` | `type()` · `op()`:`ir::BinaryOp` · `left()`:`const Expr*`(req) · `right()`:`const Expr*`(req) · `err_pos()`:`const SourcePos*`(nullable) · `cmp()`:`const CmpInfo*` (NON-NULL ⇒ this is a comparison; `op()` is then unused — dispatch on `cmp()->op()`/`cmp()->cmp_type()`) |
| `ir::ExprUnion_UnaryExpr` | `expr_as_UnaryExpr()` | `type()` · `op()`:`ir::UnaryOp` · `expr()`:`const Expr*`(req) · `err_pos()`:`const SourcePos*`(req) |
| `ir::ExprUnion_IfExpr` | `expr_as_IfExpr()` | `type()` · `cond()`:`const Expr*`(req) · `true_expr()`:`const Expr*`(req) · `false_expr()`:`const Expr*`(req) |
| `ir::ExprUnion_WhenExpr` | `expr_as_WhenExpr()` | `type()` · `chooser()`:`const WhenChooser*`(req) · `exprs()`:`Vector<Expr>`(req) |
| `ir::ExprUnion_ElvisExpr` | `expr_as_ElvisExpr()` | `type()` · `left()`:`const Expr*`(req) · `right()`:`const Expr*`(req) |
| `ir::ExprUnion_NotNullExpr` | `expr_as_NotNullExpr()` | `type()` · `expr()`:`const Expr*`(req) · `err_pos()`:`const SourcePos*`(req) |
| `ir::ExprUnion_TupleExpr` | `expr_as_TupleExpr()` | `type()` (R_TupleType) · `exprs()`:`Vector<Expr>`(req) |
| `ir::ExprUnion_ListLiteralExpr` | `expr_as_ListLiteralExpr()` | `type()` (R_ListType) · `exprs()`:`Vector<Expr>`(req) |
| `ir::ExprUnion_MapLiteralExpr` | `expr_as_MapLiteralExpr()` | `type()` (R_MapType) · `keys()`:`Vector<Expr>`(req) · `values()`:`Vector<Expr>`(req) · `err_pos()`:`const SourcePos*`(req) |
| `ir::ExprUnion_StructExpr` | `expr_as_StructExpr()` | `struct_def_index()`:`uint32_t` · `attrs()`:`Vector<CreateExprAttr>`(req) |
| `ir::ExprUnion_RegularCreateExpr` | `expr_as_RegularCreateExpr()` | `entity_def_index()`:`uint32_t` · `err_pos()`:`const SourcePos*`(req) · `attrs()`:`Vector<CreateExprAttr>`(req) |
| `ir::ExprUnion_StructCreateExpr` | `expr_as_StructCreateExpr()` | `entity_def_index()`:`uint32_t` · `err_pos()`(req) · `struct_def_index()`:`uint32_t` · `struct_expr()`:`const Expr*`(req) |
| `ir::ExprUnion_StructListCreateExpr` | `expr_as_StructListCreateExpr()` | `entity_def_index()`:`uint32_t` · `err_pos()`(req) · `struct_def_index()`:`uint32_t` · `result_list_type()`:`const Type*`(req) · `list_expr()`:`const Expr*`(req) |
| `ir::ExprUnion_FunctionCallExpr` | `expr_as_FunctionCallExpr()` | `type()` · `base()`:`const Expr*`(NULLABLE) · `call()`:`const FunctionCall*`(req) · `safe()`:`bool`(def false) |
| `ir::ExprUnion_MemberExpr` | `expr_as_MemberExpr()` | `base()`:`const Expr*`(req) · `calculator()`:`const MemberCalculator*`(req) · `safe()`:`bool` |
| `ir::ExprUnion_AssignExpr` | `expr_as_AssignExpr()` | `type()` · `op()`:`ir::BinaryOp` · `dst_expr()`:`const Expr*`(req) · `src_expr()`:`const Expr*`(req) · `post()`:`bool`(def false) |
| `ir::ExprUnion_StatementExpr` | `expr_as_StatementExpr()` | `type()`:`const Type*`(NULLABLE) · `stmt()`:`const Stmt*`(req) |
| `ir::ExprUnion_GlobalConstantExpr` | `expr_as_GlobalConstantExpr()` | `type()` · `const_def_index()`:`uint32_t` |
| `ir::ExprUnion_ChainHeightExpr` | `expr_as_ChainHeightExpr()` | `chain()`:`const ExternalChainRef*`(req) (`.name()`, `.index()`) |
| `ir::ExprUnion_TypeAdapterExpr` | `expr_as_TypeAdapterExpr()` | `type()` · `expr()`:`const Expr*`(req) · `adapter()`:`const TypeAdapter*`(req) |
| `ir::ExprUnion_ParameterDefaultValueExpr` | `expr_as_ParameterDefaultValueExpr()` | `type()` · `call_file_pos()`:`const SourcePos*`(req) · `init_frame()`:`const CallFrame*`(req) · `inner_expr()`:`const Expr*`(req) · `def_id()`:`const DefinitionName*`(nullable) |
| `ir::ExprUnion_AttributeDefaultValueExpr` | `expr_as_AttributeDefaultValueExpr()` | `attr_index()`:`int32_t` · `attr_name()`:`const String*`(req) · `attr_def_index()`:`uint32_t` · `create_file_pos()`:`const SourcePos*`(nullable) · `init_frame()`:`const CallFrame*`(req) · `inner_expr()`:`const Expr*`(req) · `def_id()`:`const DefinitionName*` |
| `ir::ExprUnion_DbAtExpr` | `expr_as_DbAtExpr()` | SQL at-expr — see DbAtExpr table below. ⇒ route to JNI/SQL back-call. |
| `ir::ExprUnion_ColAtExpr` | `expr_as_ColAtExpr()` | collection at-expr — see ColAtExpr table below. |
| `ir::ExprUnion_ErrorExpr` | `expr_as_ErrorExpr()` | `type()` · `message()`:`const String*`(req). ⇒ should not appear; soft-fail. |
| `ir::ExprUnion_ListSubscriptExpr` | `expr_as_ListSubscriptExpr()` | `type()` · `base()`:`const Expr*`(req) · `index()`:`const Expr*`(req) · `err_pos()`:`const SourcePos*`(req) |
| `ir::ExprUnion_MapSubscriptExpr` | `expr_as_MapSubscriptExpr()` | `type()` · `base()`(req) · `key()`:`const Expr*`(req) · `err_pos()`(req) |
| `ir::ExprUnion_TextSubscriptExpr` | `expr_as_TextSubscriptExpr()` | `base()`(req) · `index()`(req) · `err_pos()`(req) (NO `type()` field) |
| `ir::ExprUnion_ByteArraySubscriptExpr` | `expr_as_ByteArraySubscriptExpr()` | `base()`(req) · `index()`(req) · `err_pos()`(req) (no `type()`) |
| `ir::ExprUnion_VirtualListSubscriptExpr` | `expr_as_VirtualListSubscriptExpr()` | `type()` · `base()`(req) · `index()`(req) |
| `ir::ExprUnion_VirtualMapSubscriptExpr` | `expr_as_VirtualMapSubscriptExpr()` | `type()` · `base()`(req) · `key()`(req) · `err_pos()`(req) |
| `ir::ExprUnion_JsonArraySubscriptExpr` | `expr_as_JsonArraySubscriptExpr()` | `base()`(req) · `index()`(req) · `err_pos()`(req) (no `type()`) |
| `ir::ExprUnion_JsonObjectSubscriptExpr` | `expr_as_JsonObjectSubscriptExpr()` | `base()`(req) · `key()`(req) · `err_pos()`(req) (no `type()`) |
| `ir::ExprUnion_StructMemberExpr` | `expr_as_StructMemberExpr()` | `type()` · `base()`(req) · `attr_name()`:`const String*`(req) · `attr_index()`:`int32_t` |
| `ir::ExprUnion_ObjectValueExpr` | `expr_as_ObjectValueExpr()` | `type()` · `object_def_index()`:`uint32_t` |
| `ir::ExprUnion_LazyExpr` | `expr_as_LazyExpr()` | `type()` · `inner_expr()`:`const Expr*`(req) |

CreateExprAttr (in StructExpr/RegularCreateExpr `attrs()`): `attr_index()`:`int32_t` · `attr_name()`:`const String*`(req) · `expr()`:`const Expr*`(req).

================================================================================
# UNION: Stmt  (wrapper `ir::Stmt`, field `stmt: StmtUnion (required)`)
================================================================================
Discriminant: `stmt.stmt_type()` → `ir::StmtUnion`.  Typed: `stmt.stmt_as_<V>()`.  17 variants.

| tag enum | as_ accessor | key fields |
|---|---|---|
| `ir::StmtUnion_EmptyStatement` | `stmt_as_EmptyStatement()` | (no fields) |
| `ir::StmtUnion_VarStatement` | `stmt_as_VarStatement()` | `declarator()`:`const VarDeclarator*`(req) · `expr()`:`const Expr*`(NULLABLE — no initializer) |
| `ir::StmtUnion_ReturnStatement` | `stmt_as_ReturnStatement()` | `expr()`:`const Expr*`(NULLABLE — `return;`) |
| `ir::StmtUnion_BlockStatement` | `stmt_as_BlockStatement()` | `stmts()`:`Vector<Stmt>`(req) · `frame_block()`:`const FrameBlock*`(req) |
| `ir::StmtUnion_ExprStatement` | `stmt_as_ExprStatement()` | `expr()`:`const Expr*`(req) |
| `ir::StmtUnion_ReplExprStatement` | `stmt_as_ReplExprStatement()` | `expr()`:`const Expr*`(req) |
| `ir::StmtUnion_AssignStatement` | `stmt_as_AssignStatement()` | `dst_expr()`:`const Expr*`(req) · `expr()`:`const Expr*`(req) · `op()`:`ir::BinaryOp` (NULL/absent ⇒ plain `=`; present ⇒ compound e.g. `+=`) |
| `ir::StmtUnion_IfStatement` | `stmt_as_IfStatement()` | `cond()`:`const Expr*`(req) · `true_stmt()`:`const Stmt*`(req) · `false_stmt()`:`const Stmt*`(req) |
| `ir::StmtUnion_WhenStatement` | `stmt_as_WhenStatement()` | `chooser()`:`const WhenChooser*`(req) · `stmts()`:`Vector<Stmt>`(req) |
| `ir::StmtUnion_WhileStatement` | `stmt_as_WhileStatement()` | `cond()`:`const Expr*`(req) · `body()`:`const Stmt*`(req) · `frame_block()`:`const FrameBlock*`(req) |
| `ir::StmtUnion_ForStatement` | `stmt_as_ForStatement()` | `var_declarator()`:`const VarDeclarator*`(req) · `expr()`:`const Expr*`(req) · `iterable_adapter()`:`ir::IterableAdapterKind` · `body()`:`const Stmt*`(req) · `frame_block()`:`const FrameBlock*`(req) |
| `ir::StmtUnion_BreakStatement` | `stmt_as_BreakStatement()` | (no fields) |
| `ir::StmtUnion_ContinueStatement` | `stmt_as_ContinueStatement()` | (no fields) |
| `ir::StmtUnion_GuardStatement` | `stmt_as_GuardStatement()` | `body()`:`const Stmt*`(req) |
| `ir::StmtUnion_LambdaStatement` | `stmt_as_LambdaStatement()` | `arg_exprs()`:`Vector<Expr>`(req) · `arg_ptrs()`:`Vector<VarPtr>`(req, struct-vec) · `block()`:`const FrameBlock*`(req) · `body()`:`const Stmt*`(req) |
| `ir::StmtUnion_UpdateStatement` | `stmt_as_UpdateStatement()` | SQL write — see below. ⇒ JNI/SQL back-call. |
| `ir::StmtUnion_DeleteStatement` | `stmt_as_DeleteStatement()` | SQL write — see below. ⇒ JNI/SQL back-call. |

UpdateStatement: `entity()`:`const DbAtEntity*`(req) · `extra_entities()`:`Vector<DbAtEntity>`(nullable) · `where()`:`const DbExpr*`(nullable) · `what()`:`Vector<UpdateStatementWhat>`(req) · `from_block()`:`const FrameBlock*`(req) · `err_pos()`(req) · `lambda_block()`:`const FrameBlock*` · `lambda_var_ptr()`:`const VarPtr*` · `lambda_expr()`:`const Expr*` · `target_kind()`:`ir::UpdateTargetKind` · `cardinality()`:`ir::AtCardinality`(=null) · `is_expr_set()`:`bool` · `expr_list_type()`:`const Type*`.
  UpdateStatementWhat: `attr_name()`:`const String*`(req) · `attr_index()`:`int32_t` · `expr()`:`const DbExpr*`(req).
DeleteStatement: same as UpdateStatement minus `what`/`is_expr_set`/`expr_list_type` (has `entity,extra_entities,where,from_block,err_pos,lambda_*,target_kind,cardinality`).

VarDeclarator (wrapper, field `declarator: VarDeclaratorUnion (required)`): disc `.declarator_type()`; `.declarator_as_<V>()`.
  - `ir::VarDeclaratorUnion_SimpleVarDeclarator` → `ptr()`:`const VarPtr*`(nullable struct) · `type()`:`const Type*`(req) · `adapter()`:`const TypeAdapter*`(nullable)
  - `ir::VarDeclaratorUnion_TupleVarDeclarator` → `sub_declarators()`:`Vector<VarDeclarator>`(req)
  - `ir::VarDeclaratorUnion_WildcardVarDeclarator` → (no fields)

================================================================================
# UNION: DbExpr  (wrapper `ir::DbExpr`, field `expr: DbExprUnion (required)`)
================================================================================
Discriminant: `dbexpr.expr_type()` → `ir::DbExprUnion`.  Typed: `dbexpr.expr_as_<V>()`.  16 variants.
ALL DbExpr lowering ⇒ SQL generation / JNI back-call into interpreter (no inline IR).

| tag enum | as_ accessor | key fields |
|---|---|---|
| `ir::DbExprUnion_DbInterpretedExpr` | `expr_as_DbInterpretedExpr()` | `expr()`:`const Expr*`(req) — R_Expr injected as SQL param |
| `ir::DbExprUnion_DbBinaryExpr` | `expr_as_DbBinaryExpr()` | `type()`(req) · `op()`:`ir::DbBinaryOp` · `left()`:`const DbExpr*`(req) · `right()`:`const DbExpr*`(req) · `nullable_eq()`:`bool` |
| `ir::DbExprUnion_DbUnaryExpr` | `expr_as_DbUnaryExpr()` | `type()`(req) · `op()`:`ir::DbUnaryOp` · `expr()`:`const DbExpr*`(req) |
| `ir::DbExprUnion_DbEntityExpr` | `expr_as_DbEntityExpr()` | `entity_def_index()`:`uint32_t` · `entity_id()`:`uint32_t` (R_AtEntityId) |
| `ir::DbExprUnion_DbRelExpr` | `expr_as_DbRelExpr()` | `base()`:`const DbExpr*`(req) · `attr_name()`:`const String*`(req) · `target_entity_def_index()`:`uint32_t` |
| `ir::DbExprUnion_DbAttrExpr` | `expr_as_DbAttrExpr()` | `base()`:`const DbExpr*`(req) · `attr_name()`:`const String*`(req) · `type()`:`const Type*`(req) |
| `ir::DbExprUnion_DbRowidExpr` | `expr_as_DbRowidExpr()` | `base()`:`const DbExpr*`(req) |
| `ir::DbExprUnion_DbCollectionInterpretedExpr` | `expr_as_DbCollectionInterpretedExpr()` | `expr()`:`const Expr*`(req) |
| `ir::DbExprUnion_DbInExpr` | `expr_as_DbInExpr()` | `key_expr()`:`const DbExpr*`(req) · `exprs()`:`Vector<DbExpr>`(req) · `not()`:`bool`(def false) — NOTE getter is `not_()` (reserved word) |
| `ir::DbExprUnion_DbElvisExpr` | `expr_as_DbElvisExpr()` | `type()`(req) · `left()`:`const DbExpr*`(req) · `right()`:`const DbExpr*`(req) |
| `ir::DbExprUnion_DbCallExpr` | `expr_as_DbCallExpr()` | `type()`(req) · `fn_name()`:`const String*`(req; Db_SysFunction encoded: raw SQL or `text#{i}text` template) · `args()`:`Vector<DbExpr>`(req) |
| `ir::DbExprUnion_DbExistsExpr` | `expr_as_DbExistsExpr()` | `sub_expr()`:`const DbExpr*`(req) · `not()`→`not_()`:`bool` |
| `ir::DbExprUnion_DbInCollectionExpr` | `expr_as_DbInCollectionExpr()` | `left()`:`const DbExpr*`(req) · `right()`:`const Expr*`(req) · `not()`→`not_()`:`bool` |
| `ir::DbExprUnion_DbWhenExpr` | `expr_as_DbWhenExpr()` | `type()`(req) · `key_expr()`:`const DbExpr*`(NULLABLE) · `cases()`:`Vector<DbWhenCase>`(req) · `else_expr()`:`const DbExpr*`(NULLABLE) |
| `ir::DbExprUnion_DbNestedAtExpr` | `expr_as_DbNestedAtExpr()` | `type()`(req) · `inner()`:`const DbExpr*`(req) |
| `ir::DbExprUnion_DbSubQueryExpr` | `expr_as_DbSubQueryExpr()` | `from()`:`const DbAtExprFrom*`(req) · `what()`:`Vector<DbAtWhatField>`(req) · `where()`:`const DbExpr*`(nullable) · `extras()`:`const AtExprExtras*` · `is_many()`:`bool` · `internals()`:`const DbAtExprInternals*` |

DbWhenCase: `conds()`:`Vector<DbExpr>`(req) · `expr()`:`const DbExpr*`(req).

================================================================================
# UNION: FunctionCallTarget  (wrapper `ir::FunctionCallTarget`, field `target: FunctionCallTargetUnion (required)`)
================================================================================
Discriminant: `tgt.target_type()` → `ir::FunctionCallTargetUnion`.  Typed: `tgt.target_as_<V>()`.  10 variants.
SysGlobal / SysMember / NativeUser → universal JNI stdlib caller (back into JVM R_SysFunction).

| tag enum | as_ accessor | key fields |
|---|---|---|
| `ir::FunctionCallTargetUnion_FnTarget_RegularUser` | `target_as_FnTarget_RegularUser()` | `fn_def_index()`:`uint32_t` (→ App.functions) |
| `ir::FunctionCallTargetUnion_FnTarget_AbstractUser` | `target_as_FnTarget_AbstractUser()` | `fn_def_index()`:`uint32_t` |
| `ir::FunctionCallTargetUnion_FnTarget_NativeUser` | `target_as_FnTarget_NativeUser()` | `fn_name()`:`const String*`(req) ⇒ JNI stdlib caller |
| `ir::FunctionCallTargetUnion_FnTarget_Operation` | `target_as_FnTarget_Operation()` | `op_def_index()`:`uint32_t` (→ App.operations) |
| `ir::FunctionCallTargetUnion_FnTarget_FunctionValue` | `target_as_FnTarget_FunctionValue()` | (no fields — base Expr yields the fn value) |
| `ir::FunctionCallTargetUnion_FnTarget_SysGlobal` | `target_as_FnTarget_SysGlobal()` | `fn_name()`:`const String*`(req) ⇒ JNI stdlib caller |
| `ir::FunctionCallTargetUnion_FnTarget_SysMember` | `target_as_FnTarget_SysMember()` | `fn_name()`:`const String*`(req) ⇒ JNI stdlib caller |
| `ir::FunctionCallTargetUnion_FnTarget_AbstractOverride` | `target_as_FnTarget_AbstractOverride()` | `body()`:`const FunctionBody*`(req) |
| `ir::FunctionCallTargetUnion_FnTarget_Extendable` | `target_as_FnTarget_Extendable()` | `extendable_uid_id()`:`int32_t` · `combiner_kind()`:`ir::ExtendableCombinerKind` (UNIT/BOOLEAN/NULLABLE/LIST/MAP) · `return_type()`:`const Type*`(req) |
| `ir::FunctionCallTargetUnion_FnTarget_RegularQuery` | `target_as_FnTarget_RegularQuery()` | `query_def_index()`:`uint32_t` (→ App.queries) |

FunctionCall (wrapper `ir::FunctionCall`, field `call: FunctionCallUnion (required)`): disc `.call_type()`; `.call_as_<V>()`.
  - `ir::FunctionCallUnion_FullFunctionCall` → `return_type()`:`const Type*`(req) · `target()`:`const FunctionCallTarget*`(req) · `call_pos()`:`const SourcePos*`(req) · `args()`:`Vector<Expr>`(req) · `mapping()`:`Vector<int32_t>`(req)
  - `ir::FunctionCallUnion_PartialFunctionCall` → `return_type()`(req) · `target()`(req) · `wild_arg_count()`:`int32_t` · `mapping_values()`:`Vector<int32_t>`(req, -1 = wildcard) · `args()`:`Vector<Expr>`(req)

================================================================================
# UNION: MemberCalculator  (wrapper `ir::MemberCalculator`, field `calculator: MemberCalculatorUnion (required)`)
================================================================================
Discriminant: `mc.calculator_type()` → `ir::MemberCalculatorUnion`.  Typed: `mc.calculator_as_<V>()`.  9 variants.
All carry `type()`:`const Type*`(req) = member result type.

| tag enum | as_ accessor | key fields |
|---|---|---|
| `ir::MemberCalculatorUnion_MemberCalculator_StructAttr` | `calculator_as_MemberCalculator_StructAttr()` | `type()` · `attr_index()`:`int32_t` |
| `ir::MemberCalculatorUnion_MemberCalculator_TupleAttr` | `calculator_as_MemberCalculator_TupleAttr()` | `type()` · `attr_index()`:`int32_t` |
| `ir::MemberCalculatorUnion_MemberCalculator_VirtualTupleAttr` | `calculator_as_MemberCalculator_VirtualTupleAttr()` | `type()` · `field_index()`:`int32_t` |
| `ir::MemberCalculatorUnion_MemberCalculator_VirtualStructAttr` | `calculator_as_MemberCalculator_VirtualStructAttr()` | `type()` · `attr_def_index()`:`uint32_t` · `attr_name()`:`const String*`(req) |
| `ir::MemberCalculatorUnion_MemberCalculator_DataAttribute` | `calculator_as_MemberCalculator_DataAttribute()` | `type()` · `entity_def_index()`:`int32_t` · `attr_name()`:`const String*`(req). ⇒ DB read; JNI/SQL. |
| `ir::MemberCalculatorUnion_MemberCalculator_DataAttributeExpr` | `calculator_as_MemberCalculator_DataAttributeExpr()` | `type()` · `expr()`:`const Expr*`(req) · `lambda_block()`:`const FrameBlock*`(req) · `lambda_var_ptr()`:`const VarPtr*`(req) |
| `ir::MemberCalculatorUnion_MemberCalculator_SysFunction` | `calculator_as_MemberCalculator_SysFunction()` | `type()` · `fn_name()`:`const String*`(req) ⇒ JNI stdlib caller |
| `ir::MemberCalculatorUnion_MemberCalculator_FunctionCall` | `calculator_as_MemberCalculator_FunctionCall()` | `type()` · `call()`:`const FunctionCall*`(req) |
| `ir::MemberCalculatorUnion_MemberCalculator_ExprEval` | `calculator_as_MemberCalculator_ExprEval()` | `type()` · `expr()`:`const Expr*`(req) |

================================================================================
# UNION: Type  (wrapper `ir::Type`, field `type: TypeUnion (required)`)
================================================================================
Discriminant: `type.type_type()` → `ir::TypeUnion`.  Typed: `type.type_as_<V>()`.  20 variants.
Inline-primitive gate (mirror jni_bridge `isIntegerType`): `type->type_as_PrimitiveType()->kind() == ir::PrimitiveTypeKind_INTEGER`.

| tag enum | as_ accessor | key fields |
|---|---|---|
| `ir::TypeUnion_PrimitiveType` | `type_as_PrimitiveType()` | `kind()`:`ir::PrimitiveTypeKind` (see list below) |
| `ir::TypeUnion_NullType` | `type_as_NullType()` | (none) |
| `ir::TypeUnion_EntityType` | `type_as_EntityType()` | `def_index()`:`uint32_t` |
| `ir::TypeUnion_StructType` | `type_as_StructType()` | `def_index()`:`uint32_t` |
| `ir::TypeUnion_EnumType` | `type_as_EnumType()` | `def_index()`:`uint32_t` |
| `ir::TypeUnion_ObjectType` | `type_as_ObjectType()` | `def_index()`:`uint32_t` |
| `ir::TypeUnion_ListType` | `type_as_ListType()` | `element()`:`const Type*`(req) |
| `ir::TypeUnion_SetType` | `type_as_SetType()` | `element()`:`const Type*`(req) |
| `ir::TypeUnion_MapType` | `type_as_MapType()` | `key()`:`const Type*`(req) · `value()`:`const Type*`(req) |
| `ir::TypeUnion_TupleType` | `type_as_TupleType()` | `fields()`:`Vector<TupleField>`(req) — `TupleField.name()`:`const String*`(null=unnamed) · `.type()`:`const Type*`(req) |
| `ir::TypeUnion_NullableType` | `type_as_NullableType()` | `value()`:`const Type*`(req) |
| `ir::TypeUnion_FunctionType` | `type_as_FunctionType()` | `params()`:`Vector<Type>`(req) · `result()`:`const Type*`(req) |
| `ir::TypeUnion_VirtualListType` | `type_as_VirtualListType()` | `element()`:`const Type*`(req) |
| `ir::TypeUnion_VirtualSetType` | `type_as_VirtualSetType()` | `element()`:`const Type*`(req) |
| `ir::TypeUnion_VirtualMapType` | `type_as_VirtualMapType()` | `key()`(req) · `value()`(req) |
| `ir::TypeUnion_VirtualStructType` | `type_as_VirtualStructType()` | `def_index()`:`uint32_t` |
| `ir::TypeUnion_VirtualTupleType` | `type_as_VirtualTupleType()` | `fields()`:`Vector<TupleField>`(req) |
| `ir::TypeUnion_GenericType` | `type_as_GenericType()` | `name()`:`const String*`(req) · `args()`:`Vector<Type>`(nullable) — library-defined; ⇒ opaque handle / JNI. |
| `ir::TypeUnion_OperationType` | `type_as_OperationType()` | `def_index()`:`uint32_t` |
| `ir::TypeUnion_ErrorType` | `type_as_ErrorType()` | (none) ⇒ should not appear; soft-fail. |

PrimitiveTypeKind enum constants (`ir::PrimitiveTypeKind_*`):
  BOOLEAN, INTEGER, BIG_INTEGER, DECIMAL, TEXT, BYTE_ARRAY, ROWID, GUID, SIGNER, JSON, GTV, RANGE, UNIT.
  Inline-primitive fast path: BOOLEAN(i1/i64-tagged), INTEGER(i64), ROWID(i64). DECIMAL/BIG_INTEGER → inline ONLY when long-fitting else opaque handle. TEXT/BYTE_ARRAY/GUID/SIGNER/JSON/GTV/RANGE/UNIT → opaque handle.

================================================================================
# UNION: Value (TypedValue/ConstantValue)  (field `value: ValueUnion (required)`)
================================================================================
TypedValue wrapper `ir::TypedValue`: `type()`:`const Type*`(req) · `value_type()`→`ir::ValueUnion` · `value_as_<V>()`.
ConstantValue wrapper `ir::ConstantValue`: `value_type()`→`ir::ValueUnion` · `value_as_<V>()` (NO `type()` — type is contextual).
15 variants (same union for both wrappers).

| tag enum | as_ accessor | key fields (element-type) | inline class |
|---|---|---|---|
| `ir::ValueUnion_BoolValue` | `value_as_BoolValue()` | `value()`:`bool` | inline |
| `ir::ValueUnion_IntValue` | `value_as_IntValue()` | `value()`:`int64_t` | inline i64 |
| `ir::ValueUnion_TextValue` | `value_as_TextValue()` | `value()`:`const String*`(req) | opaque handle |
| `ir::ValueUnion_ByteArrayValue` | `value_as_ByteArrayValue()` | `value()`:`Vector<uint8_t>`(req) | opaque handle |
| `ir::ValueUnion_DecimalValue` | `value_as_DecimalValue()` | `value()`:`const String*`(req; decimal string repr) | inline if long-fitting else handle |
| `ir::ValueUnion_BigIntegerValue` | `value_as_BigIntegerValue()` | `value()`:`const String*`(req; bigint string repr) | inline if long-fitting else handle |
| `ir::ValueUnion_NullValue` | `value_as_NullValue()` | (none) | inline (null tag) |
| `ir::ValueUnion_EnumValue` | `value_as_EnumValue()` | `def_index()`:`uint32_t` · `attr_index()`:`uint32_t` | inline (i64 ordinal) |
| `ir::ValueUnion_GtvValue` | `value_as_GtvValue()` | `value()`:`Vector<uint8_t>`(req; ASN.1 DER) | opaque handle |
| `ir::ValueUnion_RowidValue` | `value_as_RowidValue()` | `value()`:`int64_t` | inline i64 |
| `ir::ValueUnion_UnitValue` | `value_as_UnitValue()` | (none) | inline (unit tag) |
| `ir::ValueUnion_StructConstantValue` | `value_as_StructConstantValue()` | `struct_def_index()`:`int32_t` · `field_values()`:`Vector<ConstantValue>`(req) | opaque handle |
| `ir::ValueUnion_CollectionConstantValue` | `value_as_CollectionConstantValue()` | `element_type()`:`const Type*`(req) · `element_values()`:`Vector<ConstantValue>`(req) | opaque handle |
| `ir::ValueUnion_MapConstantValue` | `value_as_MapConstantValue()` | `key_type()`(req) · `value_type()`(req) · `keys()`:`Vector<ConstantValue>`(req) · `values()`:`Vector<ConstantValue>`(req) | opaque handle |
| `ir::ValueUnion_TupleConstantValue` | `value_as_TupleConstantValue()` | `tuple_type()`:`const Type*`(req) · `field_values()`:`Vector<ConstantValue>`(req) | opaque handle |
| `ir::ValueUnion_MetaConstantValue` | `value_as_MetaConstantValue()` | `kind()`:`ir::MetaDefinitionKind` (ENTITY/MODULE/OBJECT/OPERATION/QUERY) · `module_name()`(req) · `full_name()`(req) · `simple_name()`(req) · `mount_name()`(req) all `const String*` | opaque handle |

NOTE: `MapConstantValue.value_type()` (the field) collides in name with the union discriminant getter `value_type()`. flatc disambiguates: the field accessor on `MapConstantValue` is the value-Type; the union discriminant is only on the `TypedValue`/`ConstantValue` wrapper. No conflict in practice — different tables.

================================================================================
# frame.fbs
================================================================================
VarPtr — `struct` (inline, by-value, never null when field present):
  `.block_uid()`:`uint32_t` · `.offset()`:`int32_t`.
  Idiom (from jni_bridge): key on `{vp->block_uid(), vp->offset()}`; struct getters return scalars directly.
FrameBlock — table:
  `parent_uid()`:`uint32_t` (=null ⇒ root block) · `uid()`:`uint32_t` · `offset()`:`int32_t` · `size()`:`int32_t`.
CallFrame — table:
  `size()`:`int32_t` · `root_block()`:`const FrameBlock*`(req) · `has_guard_block()`:`bool`(def false).

================================================================================
# op.fbs — ALL enum constants (grouped by operand type)
================================================================================
UnaryOp (`ir::UnaryOp_*`):
  MINUS_INTEGER, MINUS_BIG_INTEGER, MINUS_DECIMAL, NOT.

BinaryOp (`ir::BinaryOp_*`):
  equality/identity:  EQ, NE, EQ_REF, NE_REF
  logical:            AND, OR
  integer arith:      ADD_INTEGER, SUB_INTEGER, MUL_INTEGER, DIV_INTEGER, MOD_INTEGER
  big_integer arith:  ADD_BIG_INTEGER, SUB_BIG_INTEGER, MUL_BIG_INTEGER, DIV_BIG_INTEGER, MOD_BIG_INTEGER
  decimal arith:      ADD_DECIMAL, SUB_DECIMAL, MUL_DECIMAL, DIV_DECIMAL, MOD_DECIMAL
  concat:             CONCAT_TEXT, CONCAT_BYTE_ARRAY
  collection:         CONCAT_LIST
  membership:         IN_COLLECTION, IN_VIRTUAL_LIST, IN_VIRTUAL_SET, IN_MAP, IN_RANGE
  set/list/map:       SUB_LIST, SUB_SET, UNION_SET, INTERSECT_LIST, INTERSECT_SET, MERGE_MAP
  (jni_bridge inline-supported subset today: ADD_INTEGER, SUB_INTEGER, MUL_INTEGER. All others soft-fail or JNI.)

CmpOp (`ir::CmpOp_*`) — used in `CmpInfo.op()` on BinaryExpr:
  LT, GT, LE, GE.
CmpType (`ir::CmpType_*`) — used in `CmpInfo.cmp_type()`:
  BOOLEAN, INTEGER, BIG_INTEGER, DECIMAL, TEXT, BYTE_ARRAY, ROWID, ENTITY, ENUM.

DbUnaryOp (`ir::DbUnaryOp_*`):
  MINUS_INTEGER, MINUS_BIG_INTEGER, MINUS_DECIMAL, NOT.
DbBinaryOp (`ir::DbBinaryOp_*`):
  compare:   LT, GT, LE, GE
  logical:   AND, OR
  integer:   ADD_INTEGER, SUB_INTEGER, MUL_INTEGER, DIV_INTEGER, MOD_INTEGER
  bigint:    ADD_BIG_INTEGER, SUB_BIG_INTEGER, MUL_BIG_INTEGER, DIV_BIG_INTEGER, MOD_BIG_INTEGER
  decimal:   ADD_DECIMAL, SUB_DECIMAL, MUL_DECIMAL, DIV_DECIMAL, MOD_DECIMAL
  concat:    CONCAT
  membership:IN, NOT_IN
  equality:  EQ, NE, EQ_NULL, NE_NULL
  (note: schema order interleaves ADD_INTEGER/ADD_BIG_INTEGER/ADD_DECIMAL then SUB_*, MUL_*, DIV_*, MOD_* — see op.fbs for exact ordinal if needed.)

================================================================================
# CmpInfo (ir.fbs) — comparison sub-table on BinaryExpr
================================================================================
`CmpInfo.op()`:`ir::CmpOp` · `CmpInfo.cmp_type()`:`ir::CmpType`.
Dispatch rule: on a BinaryExpr, if `bin.cmp() != nullptr` it is a COMPARISON — ignore `bin.op()`, use `cmp()->op()`+`cmp()->cmp_type()`. If `bin.cmp() == nullptr`, it is arithmetic/logical/concat/membership/set — use `bin.op()`. (Equality EQ/NE/EQ_REF/NE_REF live in BinaryOp, not CmpInfo.)

================================================================================
# Auxiliary tables referenced above (when/at/adapter)
================================================================================
WhenChooser (wrapper, field `chooser: WhenChooserUnion (required)`): disc `.chooser_type()`; `.chooser_as_<V>()`.
  - `ir::WhenChooserUnion_IterativeWhenChooser` → `key_expr()`:`const Expr*`(req) · `conditions()`:`Vector<WhenCondition>`(req) · `else_index()`:`int32_t`(=null ⇒ no else)
      WhenCondition: `index()`:`int32_t` (index into exprs) · `expr()`:`const Expr*`
  - `ir::WhenChooserUnion_LookupWhenChooser` → `key_expr()`(req) · `lookup_keys()`:`Vector<ConstantValue>`(req) · `lookup_values()`:`Vector<int32_t>`(req) · `else_index()`:`int32_t`(=null)

TypeAdapter (`ir.fbs`): `kind()`:`ir::TypeAdapterKind` (DIRECT, INTEGER_TO_BIG_INTEGER, INTEGER_TO_DECIMAL, BIG_INTEGER_TO_DECIMAL, NULLABLE) · `inner()`:`const TypeAdapter*` (only for NULLABLE).

DbAtEntity: `entity_def_index()`:`uint32_t` · `id()`:`uint32_t` · `join_where()`:`const DbExpr*`(nullable) · `is_outer()`:`bool` · `join_block()`:`const FrameBlock*`(nullable).
AtExprExtras: `limit()`:`const Expr*`(nullable) · `offset()`:`const Expr*`(nullable).
AtWhatFieldFlags: `omit()`:`bool` · `sort()`:`int32_t`(0=none,1=asc,-1=desc) · `group()`:`bool` · `aggregate()`:`bool`.
DbAtWhatField: `flags()`:`const AtWhatFieldFlags*`(req) · `expr()`:`const DbExpr*`(req) · `result_type()`:`const Type*`(nullable).
DbAtExprFrom: `entities()`:`Vector<DbAtEntity>`(req) · `block()`:`const FrameBlock*`(nullable).
DbAtExprInternals: `block()`:`const FrameBlock*`(nullable).
AtCardinality enum (`ir::AtCardinality_*`): ZERO_ONE, ONE, ZERO_MANY, ONE_MANY.

DbAtExpr (Expr variant): `type()`(req) · `from()`:`const DbAtExprFrom*`(req) · `what()`:`Vector<DbAtWhatField>`(req) · `where()`:`const DbExpr*`(nullable) · `cardinality()`:`ir::AtCardinality` · `extras()`:`const AtExprExtras*` · `internals()`:`const DbAtExprInternals*`(req) · `err_pos()`(req) · `what_field_groups()`:`Vector<DbAtWhatFieldGroup>`(nullable) · `object_name()`:`const String*` · `object_def_index()`:`int32_t`(=null). ⇒ SQL/JNI.

ColAtExpr (Expr variant): `type()`(req) · `block()`:`const FrameBlock*`(req) · `param()`:`const ColAtParam*`(req) · `from()`:`const ColAtFrom*`(req) · `what()`:`const ColAtWhat*`(req) · `where()`:`const Expr*`(req) · `summarization()`:`ir::ColAtSummarizationKind`(NONE/GROUP/ALL) · `err_pos()`(req) · `cardinality()`:`ir::AtCardinality` · `extras()`:`const AtExprExtras*` · `field_summarizations()`:`Vector<ColAtFieldSummarizationInfo>` · `sorting()`:`Vector<ColAtSortEntry>`.
  ColAtParam: `type()`(req) · `ptr()`:`const VarPtr*`(nullable struct).
  ColAtFrom: `expr()`:`const Expr*`(req) · `block()`:`const FrameBlock*` · `iterable_adapter()`:`ir::IterableAdapterKind`(DIRECT/LEGACY_MAP).
  ColAtWhat: `fields()`:`Vector<ColAtWhatField>`(req) · `field_count()`:`int32_t` · `selected_fields()`:`Vector<int32_t>`(req) · `group_fields()`:`Vector<int32_t>`(nullable).
    ColAtWhatField: `expr()`:`const Expr*`(req) · `flags()`:`const AtWhatFieldFlags*`(req).
  ColAtFieldSummarizationKind (`ir::ColAtFieldSummarizationKind_*`): NONE, GROUP, SUM, MIN, MAX, LIST, SET, MAP.
  ColAtFieldSummarizationInfo: `kind()` · `binary_op()`:`ir::BinaryOp`(=null; only for SUM) · `zero_value()`:`const ConstantValue*` · `is_min()`:`bool` · `collection_type()`:`const Type*` · `map_value_type()`:`const Type*`.
  ColAtSortEntry: `field_index()`:`int32_t` · `ascending()`:`bool`.

UpdateTargetKind (`ir::UpdateTargetKind_*`): SIMPLE, EXPR_ONE, EXPR_MANY, OBJECT.
IterableAdapterKind (`ir::IterableAdapterKind_*`): DIRECT, LEGACY_MAP.
ExtendableCombinerKind (`ir::ExtendableCombinerKind_*`): UNIT, BOOLEAN, NULLABLE, LIST, MAP.

================================================================================
# common.fbs
================================================================================
ModuleName: `parts()`:`Vector<String>`(req). (join idiom: jni_bridge `joinModuleName`.)
MountName:  `parts()`:`Vector<String>`(req).
DefinitionName: `module()`:`const String*`(req) · `qualified_name()`:`const String*`(req) · `simple_name()`:`const String*`(req).
DefinitionId: `module()`:`const String*`(req) · `definition()`:`const String*`(req).
SourcePos: `file()`:`const String*`(nullable) · `line()`:`int32_t` · `column()`:`int32_t`.
ExternalChainRef: `name()`:`const String*`(req) · `index()`:`uint32_t`.

================================================================================
# app.fbs / def.fbs — top-level navigation (mirror jni_bridge compileFunctionByIndex)
================================================================================
Root: `const ir::App* app = ir::GetApp(raw);`
App flat arrays (all `Vector<...>`):
  `schema_hash()`:`Vector<uint8_t>`(req) · `entities()` · `objects()` · `structs()` · `enums()` · `operations()` · `queries()` · `functions()` · `constants()` · `modules()`(req) · `external_chains()`(nullable) · `sys_queries()`(nullable) · `function_extensions()`(nullable) · `native_functions()`(nullable).
Index a function: `app->functions()->Get(functionIndex)` → `const ir::FunctionDefinition*` (bound-check vs `->size()`).

FunctionDefinition: `def_name()`:`const DefinitionName*`(req) · `body()`:`const FunctionBody*`(NULLABLE ⇒ abstract; soft-fail) · `is_test()`:`bool`(true ⇒ soft-fail today).
FunctionBody: `type()`:`const Type*`(req; return type) · `params()`:`Vector<FunctionParam>`(req) · `param_ptrs()`:`Vector<VarPtr>`(req; parallel to params) · `body()`:`const Stmt*`(req) · `frame()`:`const CallFrame*`(req) · `def_name()`:`const DefinitionName*`(nullable).
FunctionParam: `name()`:`const String*`(req) · `type()`:`const Type*`(req) · `init_frame()`:`const CallFrame*`(nullable) · `default_expr()`:`const Expr*`(NULLABLE; presence ⇒ jni_bridge soft-fails) · `size_constraint()`:`const SizeConstraint*`(nullable).

OperationDefinition: `def_name()`(req) · `mount_name()`:`const MountName*`(req) · `modifiers()`:`const OperationModifiers*`(req; `.is_compound()`,`.is_singular()`) · `body()`:`const FunctionBody*`(nullable) · `guard_body()`:`const Stmt*`(nullable).
QueryDefinition: `def_name()`(req) · `mount_name()`(req) · `body()`:`const QueryBody*`(nullable).
  QueryBody (wrapper, field `body: QueryBodyUnion (required)`): disc `.body_type()`; `.body_as_<V>()`.
    - `ir::QueryBodyUnion_UserQueryBody` → `ret_type()`(req) · `params()`:`Vector<FunctionParam>`(req) · `param_ptrs()`:`Vector<VarPtr>`(req) · `body()`:`const Stmt*`(req) · `frame()`:`const CallFrame*`(req)
    - `ir::QueryBodyUnion_SysQueryBody` → `ret_type()`(req) · `params()`(req) · `fn_name()`:`const String*`(req; R_SysFunction ⇒ JNI)
GlobalConstantDefinition: `def_name()`(req) · `const_index()`:`uint32_t` · `type()`:`const Type*`(req) · `value()`:`const Expr*`(nullable) · `frame()`:`const CallFrame*`(nullable) · `meta_gtv()`:`Vector<uint8_t>`(nullable).
EntityDefinition: `def_name()`(req) · `flags()`:`const EntityFlags*`(req) · `sql_mapping()`:`const EntitySqlMapping*`(req) · `attributes()`:`Vector<Attribute>`(req) · `keys()`/`indices()`:`Vector<KeyIndex>`(req) · `external()`:`const ExternalEntity*`(nullable) · `init_frame()`:`const CallFrame*`(nullable).
  Attribute: `index()`:`int32_t` · `name()`:`const String*`(req) · `type()`:`const Type*`(req) · `mutable()`→`mutable_()`:`bool` (reserved word) · `key_index_kind()`:`ir::KeyIndexKind`(=null) · `sql_mapping()`:`const String*`(req) · `can_set_in_create()`:`bool`(def true) · `default_expr()`:`const Expr*`(nullable) · `is_db_modification()`:`bool` · `size_constraint()`:`const SizeConstraint*`(nullable).
StructDefinition: `def_name()`(req) · `name()`:`const String*`(req) · `attributes()`:`Vector<Attribute>`(req) · `flags()`:`const StructFlags*`(nullable) · `mirror_info()`:`const MirrorStructInfo*`(nullable) · `has_default_constructor()`:`bool`(def true).
EnumDefinition: `def_name()`(req) · `attrs()`:`Vector<EnumAttr>`(req). EnumAttr: `name()`:`const String*`(req) · `value()`:`int32_t`.
ObjectDefinition: `def_name()`(req) · `entity_def_index()`:`uint32_t`.
NativeFunctionEntry (App.native_functions): `name()`:`const String*`(req) · `type()`:`const Type*`(req) · `params()`:`Vector<FunctionParam>`(req).
FunctionExtensions (App.function_extensions): `uid()`:`int32_t` · `extensions()`:`Vector<FunctionBody>`(req).

KeyIndexKind (`ir::KeyIndexKind_*`): KEY, INDEX.
SizeConstraintKind (`ir::SizeConstraintKind_*`): BYTE_ARRAY, TEXT.   SizeConstraint: `min()`/`max()`:`int64_t`(=null) · `kind()` · `code_prefix()`:`const String*`(req).
EntitySqlMappingKind (`ir::EntitySqlMappingKind_*`): REGULAR, EXTERNAL, TRANSACTION, BLOCK.
MirrorStructDefKind (`ir::MirrorStructDefKind_*`): ENTITY, OBJECT, OPERATION.
MetaDefinitionKind (`ir::MetaDefinitionKind_*`): ENTITY, MODULE, OBJECT, OPERATION, QUERY.

================================================================================
# RESERVED-WORD GETTER RENAMES (flatc appends `_`)
================================================================================
  `not`    → `not_()`       (DbInExpr, DbExistsExpr, DbInCollectionExpr)
  `mutable`→ `mutable_()`   (Attribute, MirrorStructInfo, StructFlags)
  (Any C++ keyword field name gets a trailing underscore in the generated getter.)

================================================================================
# ROUTING SUMMARY (which variants must leave inline IR)
================================================================================
INLINE-CAPABLE (pure, long-fitting envelope): VarExpr(int/bool/rowid param), ConstantValueExpr(Bool/Int/Rowid/Enum/Null/Unit + long-fit Decimal/BigInteger), BinaryExpr/UnaryExpr on integer (+ decimal/bigint/text/byte_array/math intrinsics with envelope check), IfExpr, comparison via CmpInfo on inline types, simple control flow (Block/Return/If/While/For/Assign/Var).
JNI STDLIB CALLER (back into JVM R_SysFunction): FnTarget_SysGlobal/SysMember/NativeUser, MemberCalculator_SysFunction, SysQueryBody, any stdlib reachable by name.
SQL / JNI INTERPRETER BACK-CALL: every DbExpr variant, DbAtExpr, ColAtExpr, UpdateStatement, DeleteStatement, MemberCalculator_DataAttribute.
SOFT-FAIL (whole function → JVM interpreter): ErrorExpr, ErrorType, abstract/test functions, params with default_expr, and ANYTHING not bit-exactly reproducible. Never emit incorrect IR.
