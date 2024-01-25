# General
The formatter module is used to enable formatting for *.rell* files. It does this by using a set of defined rules for how tokens should be formatted
in regard to its surrounding with a few options that can override the default values with a `.rellformat` file in the repository.


# Packages
## Formatter
The entrypoint for the formatter is these three companion objects

#### getFormattingChanges
```kotlin
fun getFormattingChanges(source: String, formatterRequest: FormatterOptions): List<TextReplacement>
```
This method retrieves the content of the source files as a string along with any specified options. The content of the file then undergoes tokenization and is passed into the formatter as a parse tree.
The formatter locates the root node for the document and invokes the function `format(node)` ([here](Text_modifications.md#format)) recursively on its children. 
This function tries to identify a matching formatting rule for the node with its corresponding overloads, and if none is found, it recursively attempts to find one for its children.
The result is a list of mutations applied to the original string representation of the file.

#### applyTextReplacements
```kotlin
fun applyTextReplacements(source: String, replacements: List<TextReplacement>): String
```

This method get the source files content as a string and a list of replacements that should be done of the files content to adhere to the set of rules. 
The method applies a series of text replacements to the original text in a way that accounts for potential changes in text length due to previous replacements. 
The replacements are applied in reverse order to minimize potential issues with changing offsets.
To see what are the replacements being done, go [here](Text_modifications.md).

#### formatString
```kotlin
fun formatString(source: String, formatterRequest: FormatterOptions): String
```
FormatString wrapps the two previously mentioned methods *getFormattingChanges* and *applyTextReplacements*, this is the intended method to call given that no 
extended modifications are intended to be applied.

