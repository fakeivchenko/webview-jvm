package dev.ivchenko.webview.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** {@link ScriptUtil#quote} is the injection boundary of the bridge: every escape has to hold. */
class ScriptUtilTest {
    @Test
    void wrapsPlainTextInDoubleQuotes() {
        Assertions.assertEquals("\"hello\"", ScriptUtil.quote("hello"));
    }

    @Test
    void nullBecomesTheNullLiteral() {
        Assertions.assertEquals("null", ScriptUtil.quote(null));
    }

    @Test
    void escapesQuotesAndBackslashes() {
        Assertions.assertEquals("\"say \\\"hi\\\" \\\\ bye\"", ScriptUtil.quote("say \"hi\" \\ bye"));
    }

    @Test
    void escapesControlCharacters() {
        String input = "a\nb\rc\td" + (char) 0 + "e" + (char) 0x1f + "z";
        Assertions.assertEquals("\"a\\nb\\rc\\td\\u0000e\\u001fz\"", ScriptUtil.quote(input));
    }

    @Test
    void escapesLineAndParagraphSeparators() {
        // Legal in a Java string, a syntax error inside a JavaScript string literal before ES2019.
        String input = "x" + (char) 0x2028 + "y" + (char) 0x2029 + "z";
        Assertions.assertEquals("\"x\\u2028y\\u2029z\"", ScriptUtil.quote(input));
    }

    @Test
    void cannotBreakOutOfTheLiteral() {
        String quoted = ScriptUtil.quote("\"; alert(1); //");
        // Only the two delimiters are unescaped quotes.
        Assertions.assertEquals("\"\\\"; alert(1); //\"", quoted);
    }

    @Test
    void leavesUnicodeTextAlone() {
        Assertions.assertEquals("\"привет, 世界\"", ScriptUtil.quote("привет, 世界"));
    }
}
