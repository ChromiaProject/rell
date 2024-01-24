# Text modifications
In this section all the types of text replacements are defined.

### TextReplacement Object
```kotlin
class TextReplacement(val startOffset: Int, val stopOffset: Int, val text: String)
```
These objects hold the start and stop offsets from the start of the string representation of the document being formatted.
Within the selected range from the startOffset to the stopOffset is where the text replacement will be applied.

### Changes
A changes object holds all the information on what should be applied to each node. From this information the *TextReplacement* are then derived and solved by merging conflicting changes. 
As there are many changes that can be conflicting to others, there is a priority set to each change and this determines the merging strategy.

### FormattableDocument
The formattableDocument keeps track of all rule violations that are detected within the source document,
this is done in the list of *Changes*, operations that create the changes are;

```kotlin
fun append(appendAfterNode, changeModifier: (Changes) -> Unit)

fun prepend(prependBeforeNode, changeModifier: (Changes) -> Unit)

fun surround(surroundNode, changeModifier: (Changes) -> Unit)

fun format(node)

fun interiorIndent(interiorNode)

fun interiorIndentRange(startNode, endNode)

fun interiorIndentRangeIncludeLast(startNode, endNode)
```
Give the string `foo(bar: text)` as a node and a change modifier to add a string `%`

- Append adds a change after a node will result in `foo(bar: text)%`
- Prepend adds a change before a node will result in `%foo(bar: text)`
- Surround adds a change before and after a node will result in `%foo(bar: text)%`
- Format will invoke the correct formatting rule for that nodes type
- InteriorIndent will add indent on the nodes children given that it is invoked on the functions parenthesis 
```
foo(
    bar: text
)
```
- InteriorIndentRange does the same as InteriorIndent, but the range to indent is specified in the call
- interiorIndentRangeIncludeLast will do the same as well but forcibly includes the last node in the range 