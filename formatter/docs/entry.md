# General
The formatter module is used to enable formatting for *.rell* files. It does this by using a set of defined rules for how tokens should be formatted
in regard to its surrounding with a few options that can override the default values.

//TODO should the rell_format file be introduced here? or in the client


# Packages
## Formatter
The entrypoint for the formatter is these three companion objects

#### getFormattingChanges
```kotlin
fun getFormattingChanges(source: String, formatterRequest: FormatterOptions): List<TextReplacement>
```
This method get the source files content as a string and any options that has been specified. The files content is then tokenized and sent in to the formatter as a parse tree, that then each node (token) in the tree matches against
its corresponding formatting rules and any override options. What is returned is a list of mutations to the original string representation of the file. 

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

