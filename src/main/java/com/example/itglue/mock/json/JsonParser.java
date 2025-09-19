package com.example.itglue.mock.json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal JSON parser that produces standard Java collections.
 */
public final class JsonParser {
    private final String source;
    private int index;

    private JsonParser(String source) {
        this.source = source;
    }

    public static Object parse(String json) {
        JsonParser parser = new JsonParser(json);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.isEnd()) {
            throw new IllegalArgumentException("Unexpected trailing content at position " + parser.index);
        }
        return value;
    }

    private Object parseValue() {
        skipWhitespace();
        if (isEnd()) {
            throw new IllegalArgumentException("Unexpected end of input");
        }
        char c = source.charAt(index);
        return switch (c) {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"' -> parseString();
            case 't' -> parseTrue();
            case 'f' -> parseFalse();
            case 'n' -> parseNull();
            default -> {
                if (c == '-' || Character.isDigit(c)) {
                    yield parseNumber();
                }
                throw new IllegalArgumentException("Unexpected character '" + c + "' at position " + index);
            }
        };
    }

    private Map<String, Object> parseObject() {
        expect('{');
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        skipWhitespace();
        if (peek('}')) {
            index++;
            return result;
        }
        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expect(':');
            Object value = parseValue();
            result.put(key, value);
            skipWhitespace();
            if (peek('}')) {
                index++;
                break;
            }
            expect(',');
        }
        return result;
    }

    private List<Object> parseArray() {
        expect('[');
        List<Object> result = new ArrayList<>();
        skipWhitespace();
        if (peek(']')) {
            index++;
            return result;
        }
        while (true) {
            Object value = parseValue();
            result.add(value);
            skipWhitespace();
            if (peek(']')) {
                index++;
                break;
            }
            expect(',');
        }
        return result;
    }

    private String parseString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (!isEnd()) {
            char c = source.charAt(index++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                if (isEnd()) {
                    throw new IllegalArgumentException("Unexpected end of input in escape sequence");
                }
                char escape = source.charAt(index++);
                switch (escape) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (index + 4 > source.length()) {
                            throw new IllegalArgumentException("Invalid Unicode escape sequence at position " + index);
                        }
                        String hex = source.substring(index, index + 4);
                        sb.append((char) Integer.parseInt(hex, 16));
                        index += 4;
                    }
                    default -> throw new IllegalArgumentException("Invalid escape character '" + escape + "' at position " + (index - 1));
                }
            } else {
                sb.append(c);
            }
        }
        throw new IllegalArgumentException("Unterminated string literal");
    }

    private Number parseNumber() {
        int start = index;
        if (source.charAt(index) == '-') {
            index++;
        }
        if (isEnd()) {
            throw new IllegalArgumentException("Invalid number at end of input");
        }
        if (source.charAt(index) == '0') {
            index++;
        } else if (Character.isDigit(source.charAt(index))) {
            while (!isEnd() && Character.isDigit(source.charAt(index))) {
                index++;
            }
        } else {
            throw new IllegalArgumentException("Invalid number format at position " + index);
        }
        if (!isEnd() && source.charAt(index) == '.') {
            index++;
            if (isEnd() || !Character.isDigit(source.charAt(index))) {
                throw new IllegalArgumentException("Invalid fractional part in number at position " + index);
            }
            while (!isEnd() && Character.isDigit(source.charAt(index))) {
                index++;
            }
        }
        if (!isEnd() && (source.charAt(index) == 'e' || source.charAt(index) == 'E')) {
            index++;
            if (!isEnd() && (source.charAt(index) == '+' || source.charAt(index) == '-')) {
                index++;
            }
            if (isEnd() || !Character.isDigit(source.charAt(index))) {
                throw new IllegalArgumentException("Invalid exponent in number at position " + index);
            }
            while (!isEnd() && Character.isDigit(source.charAt(index))) {
                index++;
            }
        }
        String number = source.substring(start, index);
        return new BigDecimal(number);
    }

    private Boolean parseTrue() {
        expectSequence("true");
        return Boolean.TRUE;
    }

    private Boolean parseFalse() {
        expectSequence("false");
        return Boolean.FALSE;
    }

    private Object parseNull() {
        expectSequence("null");
        return null;
    }

    private void expect(char expected) {
        skipWhitespace();
        if (isEnd() || source.charAt(index) != expected) {
            throw new IllegalArgumentException("Expected '" + expected + "' at position " + index);
        }
        index++;
    }

    private void expectSequence(String expected) {
        for (int i = 0; i < expected.length(); i++) {
            if (isEnd() || source.charAt(index + i) != expected.charAt(i)) {
                throw new IllegalArgumentException("Expected '" + expected + "' at position " + index);
            }
        }
        index += expected.length();
    }

    private boolean peek(char c) {
        return !isEnd() && source.charAt(index) == c;
    }

    private void skipWhitespace() {
        while (!isEnd()) {
            char c = source.charAt(index);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                index++;
            } else {
                break;
            }
        }
    }

    private boolean isEnd() {
        return index >= source.length();
    }
}
