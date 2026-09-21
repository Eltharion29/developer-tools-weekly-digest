package dev.digest.infrai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private final String source;
    private int offset;

    private Json(String source) { this.source = source; }

    static Object parse(String source) {
        Json parser = new Json(source);
        Object value = parser.value();
        parser.space();
        if (parser.offset != source.length()) throw parser.bad("trailing content");
        return value;
    }

    private Object value() {
        space();
        if (offset >= source.length()) throw bad("expected value");
        char c = source.charAt(offset);
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (source.startsWith("true", offset)) { offset += 4; return true; }
        if (source.startsWith("false", offset)) { offset += 5; return false; }
        if (source.startsWith("null", offset)) { offset += 4; return null; }
        return number();
    }

    private Map<String, Object> object() {
        Map<String, Object> result = new LinkedHashMap<>();
        offset++;
        space();
        if (take('}')) return result;
        do {
            space();
            String key = string();
            space();
            expect(':');
            result.put(key, value());
            space();
        } while (take(','));
        expect('}');
        return result;
    }

    private List<Object> array() {
        List<Object> result = new ArrayList<>();
        offset++;
        space();
        if (take(']')) return result;
        do { result.add(value()); space(); } while (take(','));
        expect(']');
        return result;
    }

    private String string() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (offset < source.length()) {
            char c = source.charAt(offset++);
            if (c == '"') return out.toString();
            if (c != '\\') { out.append(c); continue; }
            if (offset >= source.length()) throw bad("unfinished escape");
            char escaped = source.charAt(offset++);
            switch (escaped) {
                case '"', '\\', '/' -> out.append(escaped);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (offset + 4 > source.length()) throw bad("short unicode escape");
                    out.append((char) Integer.parseInt(source.substring(offset, offset + 4), 16));
                    offset += 4;
                }
                default -> throw bad("invalid escape");
            }
        }
        throw bad("unterminated string");
    }

    private Number number() {
        int start = offset;
        while (offset < source.length() && "-+0123456789.eE".indexOf(source.charAt(offset)) >= 0) offset++;
        String raw = source.substring(start, offset);
        try { return raw.contains(".") || raw.contains("e") || raw.contains("E")
                ? Double.parseDouble(raw) : Long.parseLong(raw); }
        catch (NumberFormatException e) { throw bad("invalid number"); }
    }

    private void space() { while (offset < source.length() && Character.isWhitespace(source.charAt(offset))) offset++; }
    private boolean take(char c) { if (offset < source.length() && source.charAt(offset) == c) { offset++; return true; } return false; }
    private void expect(char c) { if (!take(c)) throw bad("expected " + c); }
    private IllegalArgumentException bad(String message) { return new IllegalArgumentException(message + " at " + offset); }
}
