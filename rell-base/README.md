# Rell Base

Core of the Rell language: compiler, runtime, and the resolved model shared between them.
This module is an umbrella that re-exports `frontend` and `runtime` as its public API;
downstream modules depend on `rell-base` rather than on individual sub-modules.

## Sub-modules

| Sub-module         | Purpose                                                    |
|--------------------|------------------------------------------------------------|
| `utils`            | Shared utilities                                           |
| `rr-tree`          | Serializable IR classes: types, definitions, frames        |
| `rr-serialization` | FlatBuffers (de)serialization for RR model                 |
| `frontend`         | Parser, compiler passes and model, library framework       |
| `runtime`          | Interpreter, values, SQL generation, stdlib implementation |
| `test-utils`       | Fixtures shared by tests                                   |

## Layer prefixes

| Prefix | Layer             | Notes                                                       |
|--------|-------------------|-------------------------------------------------------------|
| `S_`   | AST               | Parsed source                                               |
| `C_`   | Compilation       | AST to runtime transformation                               |
| `R_`   | Compiler model    | Working model across compiler passes                        |
| `RR_`  | Resolved runtime  | Immutable, serializable IR — the model the runtime consumes |
| `Rt_`  | Runtime execution | Interpreter, values, type capabilities                      |
| `M_`   | Type system       | Compile-time generic types                                  |
| `L_`   | Library framework | Stdlib declarations and implementations                     |
