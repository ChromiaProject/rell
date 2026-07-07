# Lambdas

Anonymous functions that evaluate to first-class function values. Each is lowered by lifting its
body into a hidden function and emitting the lambda as a partial application of that function &mdash;
captures bound, parameters left as wildcards &mdash; so no runtime or serialization changes are needed.

## Syntax

The arrow is `->`, the same arrow `when` uses to map a condition to its result. A lambda maps its
parameters to a body &mdash; the same relation &mdash; so it reuses that arrow rather than introducing a
second one for the same idea.

    x -> e                     one parameter, bare
    (x, y) -> e                parenthesised parameters
    () -> e                    no parameters
    x -> { s1; s2; result }    value-block body

One parameter is written bare; zero parameters or more than one require parentheses. The bare
multi-parameter form `x, y -> e` is rejected as ambiguous inside comma-separated lists.

A body is either a single expression or a value block. In a value block the trailing expression &mdash;
the last one, without a semicolon &mdash; is the result; there is no `return` and no labels. When the
result type is `unit` the trailing expression may be omitted.

`->` reuses the one mapping arrow the language already has: a `when` arm writes
`condition -> result`, and a lambda therefore writes `params -> body`. The lambda is brace-less &mdash; the
parameters and arrow sit outside any braces, which only wrap a value-block body (`x -> { … }`),
following Java's `x -> {}`. The one cost is that `->` now does double duty: it also forms the function
type (`(integer) -> integer`), so the two arrows can meet on one line &mdash; `val f: (integer) ->
integer = x -> x * 2`. Consistency with `when` and with block statements was judged more important than
avoiding that overlap.

Other shapes were considered and rejected:

| Shape                     | Example       | Why not                                                                                                                                            |
|---------------------------|---------------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| Fat arrow                 | `x => e`      | A second mapping arrow. `when` writes `->`, so lambdas would be inconsistent.                                                                      |
| Brace lambda (Kotlin)     | `{ x -> e }`  | `{ … }` means block statement in Rell. Java hit the same wall &mdash; it has `{}`-blocks too and put the arrow (mandatory, unlike Kotlin) outside. |
| Marked brace              | `#{ x -> e }` | Resolves the block collision by tagging the brace, but is invented syntax with no relatives in any language, so there is no familiarity to borrow. |
| Closure bars (Rust, Ruby) | `\|x\| e`     | Delimiters instead of a maps-to arrow, again unlike `when`; and `\|…\|` reads like an absolute-value / cardinality notation.                       |

Relatedly, Rell does not take Kotlin's trailing-lambda call sugar either &mdash; writing a call's
last function argument as a block after the parentheses (`foo(x) { … }`). That sugar exists to make
receiver-lambda eDSLs read like built-in syntax, and Rell is not building those eDSLs. `try_call` is
about the only call that might read marginally nicer with it, which does not justify a second call
syntax and the parsing weight it carries. A lambda argument is passed like any other argument, inside
the parentheses.

## Typing

A parameter's type comes from its annotation or, when unannotated, from the expected function type
at the use site. A lambda whose parameters are neither annotated nor typed by an expected function
type is an error; a parameterless lambda has nothing to type and so needs neither.

Parameters may be annotated, but only in the parenthesised form:

    (x: integer) -> x * 2

Annotation is all-or-nothing &mdash; every parameter or none. When an annotation and an expected type are
both present, each annotated type must equal the corresponding expected one, structurally and
including nullability; the sound-but-useless contravariant direction and the unsound covariant
direction are both disallowed. An annotation makes a lambda self-typing, so it fits a use site that
supplies no expected function type &mdash; a generic or overloaded parameter, for instance.

The return type is always inferred from the body: the common type of its return points and trailing
expression. There is no return-type annotation. The only position that would need one is an
unannotated binding, and there `val f: (A) -> R = ...` is available and says more; return widening
otherwise rides the expected type through the normal type adapter, so `() -> 5` fits `() -> integer?`
without a cast. The form `(params): R -> body` is reserved for a later need.

## Capture

A lambda captures each enclosing local it names by reading the binding's **current value** when the
lambda value is created; rebinding that outer variable afterwards is not seen. This is a value capture
in the same sense as any Rell assignment, not a deep snapshot: a captured mutable object (a `list`,
`map`, `set`, entity, …) is captured by its reference, so in-place mutations made to it after the lambda
is created *are* visible inside the lambda &mdash; only reassigning the variable is not. A captured variable
is read-only inside the lambda. Because the capture is taken at a single well-defined point and the
captured binding cannot be reassigned, evaluation stays deterministic. The capture set is the
identifiers occurring in the body intersected with the definitely-initialised in-scope locals of the
enclosing scope.

## Example

`try_call` runs a function and traps any exception it throws; the lambda supplies the call to run. A
block body lets it do real work before returning:

    function checked_div(a: integer, b: integer): integer {
        require(b != 0, "division by zero");
        return a / b;
    }

    val ok = try_call(() -> {
        val h = checked_div(20, 4);
        h * h
    });                                                 // integer? - 25, or null if the call threw

    val safe = try_call(() -> checked_div(10, 0), -1);  // integer - -1, the fallback

`join_to_text` maps each element through a function before joining. The element type is resolved from
the receiver and flows into the expected `(integer) -> text`, so the lambda parameter needs no
annotation &mdash; and a named argument supplies just the transform, skipping the intervening optional
parameters:

    [1, 2, 3].join_to_text(transform = x -> "n" + x);   // "n1, n2, n3"

## What the shape gives for free

A lambda is an ordinary expression that evaluates to a function value, so it composes wherever a value
can go &mdash; no extra rules are needed for the following:

A `list<(integer) -> integer>` holds them like any other element:

    val fs: list<(integer) -> integer> = [x -> x + 1, x -> x * 2, x -> x * x];
    fs[2](10);   // 100

Currying falls out of right-associative nesting. A lambda body is itself an expression, so a lambda may
be the body of another: `x -> y -> e` reads as `x -> (y -> e)`, a function that returns a function. The
function type nests the same way and the same direction, so the type needs no extra parentheses:

    val add = (x: integer) -> (y: integer) -> x + y;   // (integer) -> (integer) -> integer
    add(3)(4);   // 7
