package net.postchain.rell.toolbox.grammer;

import net.postchain.rell.base.utils.grammar.XtextGrammarKt;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

public class RellGrammarChangesTest {

    // Test is written in Java because calling main() from Kotlin is ambiguous for compiler,
    // as there are multiple main() methods in the rell project under the same package.
    @Test
    public void testRellGrammarChanges() throws Exception {
        var byteArrayOutputStream = new ByteArrayOutputStream();
        var oldOut = System.out;
        try (var customPrintStream = new PrintStream(byteArrayOutputStream)) {
            var ourXtextGrammarFile = "/grammar/Rell.xtext";
            System.setOut(customPrintStream);
            XtextGrammarKt.main();
            var capturedXtextGrammar = normalizeNewLines(removeComments(byteArrayOutputStream.toString()));
            var expectedXtextGrammar = normalizeNewLines(removeComments(readResourceFile(ourXtextGrammarFile)));
            // When assertion fails, we should
            // 1. Inspect the difference between the two grammars
            // 2. Adjust our ANTLR grammar to match the changes in the Rell grammar
            // 3. Copy content of new xtext grammar to /grammar/Rell.xtext so text comparison will pass next time
            Assertions.assertEquals(expectedXtextGrammar, capturedXtextGrammar);
        } finally {
            System.setOut(oldOut);
        }
    }

    private String removeComments(String text) {
        String regex = "^\\s*//.*$";
        Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(text);
        return matcher.replaceAll("").trim();
    }

    private String normalizeNewLines(String text) {
        return text.replace("\r\n", "\n");
    }

    private static String readResourceFile(String filePathInResources) throws IOException, URISyntaxException {
        var fileURI = requireNonNull(RellGrammarChangesTest.class.getResource(filePathInResources)).toURI();
        Path resourcePath = Paths.get(fileURI);
        byte[] fileBytes = Files.readAllBytes(resourcePath);
        return new String(fileBytes, StandardCharsets.UTF_8);
    }
}
