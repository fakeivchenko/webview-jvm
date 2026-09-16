package dev.ivchenko.webview.windows;

import dev.ivchenko.webview.windows.util.JsonStringUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class JsonStringUtilTest {
    @Test
    void decodesEscapes() {
        Assertions.assertEquals("a \"b\" \\ c\nпривет \u001f", JsonStringUtil.decode("\"a \\\"b\\\" \\\\ c\\n\\u043f\\u0440\\u0438\\u0432\\u0435\\u0442 \\u001f\""));
    }

    @Test
    void passesNonStringsThrough() {
        Assertions.assertEquals("null", JsonStringUtil.decode("null"));
        Assertions.assertEquals("42", JsonStringUtil.decode("42"));
        Assertions.assertNull(JsonStringUtil.decode(null));
    }
}
