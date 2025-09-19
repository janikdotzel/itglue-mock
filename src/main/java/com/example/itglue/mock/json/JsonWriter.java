package com.example.itglue.mock.json;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Serializes Java collections produced by {@link JsonParser} back into JSON.
 */
public final class JsonWriter {
    private JsonWriter() {
    }

    public static String toJson(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(value, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String string) {
            writeString(string, sb);
        } else if (value instanceof Number number) {
            writeNumber(number, sb);
        } else if (value instanceof Boolean bool) {
            sb.append(bool ? "true" : "false");
        } else if (value instanceof Map<?, ?> map) {
            writeObject((Map<String, Object>) map, sb);
        } else if (value instanceof List<?> list) {
            writeArray((List<Object>) list, sb);
        } else {
            throw new IllegalArgumentException("Unsupported value type: " + value.getClass());
        }
    }

    private static void writeObject(Map<String, Object> map, StringBuilder sb) {
        sb.append('{');
        Iterator<Map.Entry<String, Object>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            writeString(entry.getKey(), sb);
            sb.append(':');
            writeValue(entry.getValue(), sb);
            if (iterator.hasNext()) {
                sb.append(',');
            }
        }
        sb.append('}');
    }

    private static void writeArray(List<Object> list, StringBuilder sb) {
        sb.append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            writeValue(list.get(i), sb);
        }
        sb.append(']');
    }

    private static void writeString(String string, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    private static void writeNumber(Number number, StringBuilder sb) {
        if (number instanceof BigDecimal bigDecimal) {
            sb.append(bigDecimal.toPlainString());
        } else {
            sb.append(number.toString());
        }
    }
}
