<!-- Copyright (C) 2026 ChromaWay AB. See LICENSE for license information. -->

# Gradle wiring for the LLVM backend

Ready-to-apply, copy-pasteable Kotlin-DSL edits to compile the new C++ runtime
sources into `librell-llvm` and exercise the backend from the test suites. Three
edits, in three build files:

1. `rell-base/llvm/build.gradle.kts` — add the new `.cpp` files to the `clang++`
   invocation in `buildNativeLibrary`.
2. `rell-base/build.gradle.kts` — a new `testLlvm` task mirroring `testTruffle`,
   wired into `check`.
3. `regression/...` — add an `LLVM` arm to the regression backend sweep.

All edits are surgical; nothing else in those files moves.

---

## 1. Compile the new `.cpp` files into `librell-llvm`

### Background

`buildNativeLibrary` (an `Exec` task) builds one shared library by handing a
single translation unit — `src/main/cpp/jni_bridge.cpp` — to `clang++` with
`-shared`. The command is assembled in the task's `doFirst` block (the
configuration-time `commandLine("true")` is a placeholder; the real command is
built in `doFirst` so the `llvm-config` providers can resolve). Today:

```kotlin
val cpp = layout.projectDirectory.file("src/main/cpp/jni_bridge.cpp")
...
inputs.file(cpp)
...
val cppAbs = cpp.asFile.absolutePath
...
cmd += "-o"
cmd += outFileAbs
cmd += cppAbs                 // <-- single source file
cmd += llvmLibArgs.get()
cmd += llvmSysLibs.get()
```

The new runtime is split across the ABI fileList (`value.cpp`,
`stdlib_bridge.cpp`, `sql_bridge.cpp`, `intrinsics_*.cpp`, `lower_*.cpp`)
alongside `jni_bridge.cpp`. All compile into the same `librell-llvm` shared
object. clang++ accepts multiple sources in one `-shared` invocation and links
them together, so we keep the single-invocation model — no separate object/link
steps — and just feed it the whole list.

### Edit A — replace the single-file `val cpp` with the source-file list

Find this block near the top of `buildNativeLibrary` (the `register` lambda):

```kotlin
val cpp = layout.projectDirectory.file("src/main/cpp/jni_bridge.cpp")
val outFile = sharedLibFile

inputs.file(cpp)
inputs.dir(generatedCppDir)
```

Replace it with:

```kotlin
val cppDir = layout.projectDirectory.dir("src/main/cpp")
// One shared object; clang++ compiles + links all TUs in a single -shared call.
// jni_bridge.cpp stays first by convention (it owns JNI_OnLoad / g_vm). The rest
// is the ABI fileList: tagged-value marshalling, the JNI stdlib/SQL back-call
// bridges, the pure-family intrinsics, and the RR-tree lowering passes.
val cppSources = listOf(
    "jni_bridge.cpp",
    "value.cpp",
    "stdlib_bridge.cpp",
    "sql_bridge.cpp",
    "intrinsics_integer.cpp",
    "intrinsics_decimal.cpp",
    "intrinsics_biginteger.cpp",
    "intrinsics_text.cpp",
    "intrinsics_bytearray.cpp",
    "intrinsics_math.cpp",
    "lower_expr.cpp",
    "lower_ops.cpp",
    "lower_stmt.cpp",
    "lower_call.cpp",
).map { cppDir.file(it) }
val outFile = sharedLibFile

cppSources.forEach { inputs.file(it) }
// rell_runtime.h and app_generated.h are shared headers; track the dir so an
// edit to either triggers a rebuild (generatedCppDir already covers the latter,
// but the cpp dir carries rell_runtime.h and the .md ABI notes are harmless).
inputs.dir(cppDir)
inputs.dir(generatedCppDir)
```

> Note: `inputs.dir(cppDir)` makes every source and the shared `rell_runtime.h`
> an up-to-date input. The individual `inputs.file(...)` calls are redundant with
> it but kept for clarity; drop them if you prefer the directory input alone.

### Edit B — snapshot absolute paths for the `doFirst`

Find:

```kotlin
val cppAbs = cpp.asFile.absolutePath
```

Replace with:

```kotlin
val cppSourcesAbs = cppSources.map { it.asFile.absolutePath }
```

### Edit C — feed the list to `clang++` in the `doFirst`

Find, in the command-assembly `doFirst`:

```kotlin
cmd += "-o"
cmd += outFileAbs
cmd += cppAbs
cmd += llvmLibArgs.get()
cmd += llvmSysLibs.get()
```

Replace with:

```kotlin
cmd += "-o"
cmd += outFileAbs
cmd += cppSourcesAbs
cmd += llvmLibArgs.get()
cmd += llvmSysLibs.get()
```

That is the whole native-build change. The compile flags
(`-std=c++17 -fno-rtti -fPIC -shared -O0 -g`), the include paths (LLVM,
FlatBuffers headers, `generatedCppDir`, JNI), and the macOS link flags
(`-undefined dynamic_lookup`, `-Wl,-install_name,...`) are already correct for a
multi-TU build and need no change. Each `.cpp` includes `"rell_runtime.h"` and
`"app_generated.h"`, both already on the `-I` search path
(`src/main/cpp` is the compiler's implicit include dir for each source, and
`generatedCppDir` is passed via `-I`).

> If a future split wants incremental per-file compilation (separate `.o` +
> link), that needs two `Exec` tasks or a switch to the `cpp-library` plugin —
> deliberately out of scope here; the single-shot `clang++` call rebuilds all
> TUs on any input change, which is fine for a dev-loop O0/-g build.

---

## 2. `testLlvm` task in `rell-base/build.gradle.kts`

### Background

`testTruffle` (already present) is the template: a custom `Test` task that points
at the `test` source set, sets `rell.test.backend`, and is wired into `check`.
The LLVM analogue differs in three ways:

- backend value is `llvm` (read by `RellTestUtils.BACKEND`; the test-harness
  routing in `RellTestUtils.forCompilation` must learn an `"llvm" ->
  Llvm_Backend.forCompilation(...)` arm — that Kotlin edit is integrated
  separately and is a prerequisite for this task to do anything but fall back to
  the interpreter);
- it must depend on `:rell-base:llvm:buildNativeLibrary` and pass the built
  library's absolute path via `-Drell.llvm.libpath` (the property
  `RellLlvmNative` reads to `System.load` the `.dylib`/`.so`);
- it has no GraalVM gating and no JaCoCo disable — those exist only because
  Truffle needs libgraal on the worker JVM. LLVM has no such constraint, so the
  task always runs.

`rell-base` does not currently depend on `:rell-base:llvm`. The `testLlvm` task
needs the `Llvm_Backend` class on the **test** classpath so `RellTestUtils` can
route to it reflectively or directly. Add `:rell-base:llvm` as a
`testImplementation` dep (mirrors how `runtime-truffle` rides along on the test
classpath only).

### Edit A — add the test-only dependency

In the `dependencies { ... }` block, next to the Truffle test dep:

```kotlin
    testImplementation(projects.rellBase.runtimeTruffle)
```

add:

```kotlin
    // LLVM peer backend, referenced only by the testLlvm run (and Tf-style
    // activation tests). Test classpath only — never on the production classpath.
    testImplementation(projects.rellBase.llvm)
```

### Edit B — register the `testLlvm` task

Place this immediately after the `testTruffle` registration (before
`tasks.check { ... }`):

```kotlin
// Path to the JIT'd native library, produced by the llvm module's clang build.
// Threaded in as a Provider so configuration cache stays clean and the task only
// resolves the path when it actually runs.
val llvmNativeLib = project(":rell-base:llvm")
    .layout.buildDirectory
    .file(
        if (System.getProperty("os.name").startsWith("Mac")) "native/librell-llvm.dylib"
        else "native/librell-llvm.so"
    )

val testLlvm by tasks.registering(Test::class) {
    description = "Runs tests through the LLVM JIT peer backend (Llvm_Backend)"
    group = "verification"
    useJUnitPlatform()
    // Custom Test tasks don't auto-discover the test source set on Gradle 8+.
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    systemProperty("rell.test.roundtrip", "false")
    systemProperty("rell.test.backend", "llvm")

    // Build the .dylib/.so before the run and hand RellLlvmNative its load path.
    dependsOn(":rell-base:llvm:buildNativeLibrary")
    systemProperty("rell.llvm.libpath", llvmNativeLib.map { it.asFile.absolutePath }.get())

    shouldRunAfter(tasks.test)
}
```

### Edit C — wire into `check`

In the existing `tasks.check { ... }` block, alongside the Truffle line:

```kotlin
tasks.check {
    dependsOn(testRoundTrip)
    dependsOn(testTruffle)
    dependsOn(testLlvm)
}
```

That is the complete `rell-base` change. Running `./gradlew :rell-base:testLlvm`
builds the native lib, then re-runs the full `rell-base` test family with the
LLVM backend selected; any function the lowering passes can't handle soft-fails
to the interpreter inside the same JVM, so the suite must stay bit-identical to
the `interpreter` reference run — that equality is the correctness invariant
(exactly as for `testTruffle`).

> The `Test` task inherits `tasks.withType<Test>` config at the top of the file,
> which sets `rell.test.backend` from `findProperty("rellTestBackend")`. The
> explicit `systemProperty("rell.test.backend", "llvm")` here overrides it for
> this task (last-write-wins on `Test.systemProperties`), same as `testTruffle`.

---

## 3. Regression toolkit: add an LLVM backend pass

### Background

The regression sweep (`regression/`) fans one `DynamicTest` per
`(project, backend)`. The backend set lives in two places:

- the `ExecutionBackend` enum in `regression/src/regression/config.kt`
  (`INTERPRETER`, `TRUFFLE`);
- the `backends` list in `regression/test/regression/RegressionTest.kt`
  (`listOf(ExecutionBackend.INTERPRETER, ExecutionBackend.TRUFFLE)`).

Backend selection at the chr boundary is a pure JVM-arg toggle: `chr` reads
`$JAVA_ARGS` and `RellApiInterpreterBackend` honours
`-Drell.execution.backend=<name>` reflectively (`interpreter` / `truffle`
today). Adding `llvm` to that production switch (an `"llvm" -> Llvm_Backend...`
arm in `RellApiInterpreterBackend.create`, plus `rell-api-base` carrying
`:rell-base:llvm` as a `runtimeOnly` dep so the class rides chr's classpath) is a
**Kotlin/build prerequisite handled separately**; this section covers only the
regression-toolkit wiring that drives it.

There is one LLVM-specific wrinkle: the JIT needs the native `.dylib`/`.so`, and
`RellLlvmNative` loads it from `-Drell.llvm.libpath`. So the LLVM regression arm's
`JAVA_ARGS` must carry **both** the backend selector and the libpath, and the
native lib must be built before the sweep runs.

### Edit A — add `LLVM` to the enum

In `regression/src/regression/config.kt`:

```kotlin
enum class ExecutionBackend {
    INTERPRETER,
    TRUFFLE,
    LLVM;

    fun label(): String = name.lowercase().replaceFirstChar(Char::uppercase)
}
```

### Edit B — emit the `-Drell.llvm.libpath` for the LLVM arm

In `regression/src/regression/compile.kt`, extend `ExecutionBackend.extraEnv()`.
The libpath is read from a system property the Gradle task injects (see Edit D):

```kotlin
private fun ExecutionBackend.extraEnv(): Map<String, String> = when (this) {
    ExecutionBackend.INTERPRETER -> emptyMap()
    ExecutionBackend.TRUFFLE -> mapOf("JAVA_ARGS" to "-Drell.execution.backend=truffle")
    ExecutionBackend.LLVM -> {
        val libPath = System.getProperty("rell.llvm.libpath")
            ?: error("LLVM regression arm needs -Drell.llvm.libpath (set by the Gradle task)")
        mapOf("JAVA_ARGS" to "-Drell.execution.backend=llvm -Drell.llvm.libpath=$libPath")
    }
}
```

### Edit C — include `LLVM` in the fan-out

In `regression/test/regression/RegressionTest.kt`:

```kotlin
val backends = listOf(
    ExecutionBackend.INTERPRETER,
    ExecutionBackend.TRUFFLE,
    ExecutionBackend.LLVM,
)
```

(Compilation — `chr install`/`chr build` — is backend-agnostic, so adding a third
backend triples only the `chr test` phase, matching the existing per-backend
isolation in `compile.kt`: each `(project, LLVM)` unit gets its own
`workdir/<project>-llvm/` tree and its own throw-away Postgres.)

### Edit D — Gradle: build the native lib and pass its path

In `regression/build.gradle.kts`, inside `fun Test.configureRegressionRun(...)`,
add the native-lib dependency and the libpath system property. The `chr`
subprocesses inherit `JAVA_ARGS` via `extraEnv()`, but the test JVM itself needs
`rell.llvm.libpath` set so `extraEnv()` (Edit B) can read it:

```kotlin
fun Test.configureRegressionRun(includePrivate: Boolean) {
    // ... existing body ...

    // LLVM arm: ensure the JIT shared lib exists and tell the harness where it is.
    val llvmLib = project(":rell-base:llvm").layout.buildDirectory
        .file(
            if (System.getProperty("os.name").startsWith("Mac")) "native/librell-llvm.dylib"
            else "native/librell-llvm.so"
        )
    dependsOn(":rell-base:llvm:buildNativeLibrary")
    systemProperty("rell.llvm.libpath", llvmLib.map { it.asFile.absolutePath }.get())

    // ... existing dependsOn(":performance:buildLocalChr", regressionClone) etc. ...
}
```

> The `chr` binary itself must also carry `:rell-base:llvm` on its runtime
> classpath for `-Drell.execution.backend=llvm` to resolve `Llvm_Backend`. That
> comes from the `rell-api-base` `runtimeOnly` dep prerequisite noted above; the
> `:performance:buildLocalChr` task (already a `dependsOn`) republishes Rell to
> `~/.m2`, so once that dep lands no extra regression wiring is needed.

---

## Verification checklist

```bash
# Native lib builds with all TUs linked in
./gradlew :rell-base:llvm:buildNativeLibrary

# LLVM backend re-runs the rell-base suite; must match the interpreter reference
./gradlew :rell-base:testLlvm

# Full check now includes testLlvm
./gradlew :rell-base:check

# Regression LLVM arm (long-running; reads the HTML report for the verdict)
./gradlew :regression:regressionPublic
```

A green `:rell-base:testLlvm` with results bit-identical to `:rell-base:test` is
the bar. Any divergence is an LLVM-lowering bug, not a flaky backend — the
interpreter run is canonical.
