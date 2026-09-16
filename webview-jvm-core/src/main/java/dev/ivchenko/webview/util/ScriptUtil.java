package dev.ivchenko.webview.util;

import lombok.experimental.UtilityClass;

/** Builds JavaScript fragments that are safe to hand to an engine for evaluation. */
@UtilityClass
public class ScriptUtil {
    /**
     * Renders {@code value} as a JavaScript string literal, quotes included.
     *
     * <p>Everything crossing the bridge goes through here. A value produced by application code ends up inside a script
     * the engine will execute, so an unescaped quote is not a formatting bug but an injection.</p>
     */
    public String quote(String value) {
        if (value == null) return "null";

        StringBuilder quoted = new StringBuilder(value.length() + 16).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> {
                    if (c < 0x20 || c == ' ' || c == ' ') {
                        quoted.append(String.format("\\u%04x", (int) c));
                    } else {
                        quoted.append(c);
                    }
                }
            }
        }
        return quoted.append('"').toString();
    }
}
