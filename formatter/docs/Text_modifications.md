# Text modifications
In this section all the types of text replacements are defined.

### TextReplacement Object
```kotlin
class TextReplacement(val startOffset: Int, val stopOffset: Int, val text: String)
```
These objects hold the start and stop offsets from the start of the string representation of the document being formatted.
Within the selected range from the startOffset to the stopOffset is where the text replacement will be applied.

### 