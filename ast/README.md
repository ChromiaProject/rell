
## Updating Rell Version

### Aligning Grammar Changes

When updating the Rell version, you may need to synchronize grammar changes between the Rell compiler and the IDE plugin. This involves two main steps:

---

#### 1. Update Grammar File

**Check for grammar changes:**
1. Run the test `RellGrammarChangesTest` to detect any grammar changes in the latest Rell version.
2. If changes are detected, update the `Rell.g4` file accordingly.

**Apply the changes:**
- **For minor changes:** Update the `Rell.g4` file manually.
- **For major changes:** Auto-generate the file from the Rell project using Eclipse IDE:
    1. Open the Rell project in Eclipse IDE.
    2. Debug Xtext grammar to view ANTLR grammar.  Set debugGrammar to true in `.mwe2` file.
    ```
    parserGenerator {
      debugGrammar = true
    }
    ```
  And after build you you should find a `.g4` file in `src-gen` with the ANTLR grammar. 
For more context read this blog post:
https://blogs.itemis.com/en/debugging-xtext-grammars-what-to-do-when-your-language-is-ambiguous

---

#### 2. Update Transformation Logic

**Background:**

The Rell compiler uses the Xtext grammar language (`.xtext`), while the IDE plugin uses ANTLR (`.g4`). We use ANTLR because it provides an error-recoverable parser, which is essential for IDE functionality.

The transformation logic converts the ANTLR-generated AST to `S_RellFile`, which is the AST structure used by the Rell compiler. This transformation is implemented in the `net.postchain.rell.toolbox.transformer` package.

**Validate transformations:**

The test `ANTLR parsed AST is correctly transformed to Rell AST` in `RellParserTest` uses thousands of test cases from the Rell compiler to ensure the ASTs are equivalent.

**Update transformation logic:**

After grammar changes, you'll likely need to update the transformation code:

1. Run the `main` method in `AntlrActions` to see how transformations should be structured.
2. The output shows the expected transformations (defined in `AntlrToRell`).
3. **Important:** The generated output is a guide, not a complete solution. Review the changes carefully and consider which tokens were modified in the grammar file before applying changes.

---
