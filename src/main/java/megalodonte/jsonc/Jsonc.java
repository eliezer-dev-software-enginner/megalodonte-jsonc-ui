package megalodonte.jsonc;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.math.BigDecimal;

/** JSON plus // and block comments. Trailing commas and duplicate keys are rejected. */
final class Jsonc {
    static JsonObject parse(String source) {
        try (var reader = new JsonReader(new StringReader(stripComments(source)))) {
            reader.setStrictness(Strictness.STRICT);
            JsonElement root = read(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new UiException("$", "Unexpected content after document");
            if (!root.isJsonObject()) throw new UiException("$", "Expected an object");
            return root.getAsJsonObject();
        } catch (IOException | NumberFormatException e) {
            throw new UiException("$", "Invalid JSONC: " + e.getMessage(), e);
        }
    }

    private static JsonElement read(JsonReader reader, int depth) throws IOException {
        if (depth > 128) throw new UiException(reader.getPath(), "Maximum nesting depth exceeded");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                var object = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (object.has(name)) throw new UiException(reader.getPath(), "Duplicate key: " + name);
                    object.add(name, read(reader, depth + 1));
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                var array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) array.add(read(reader, depth + 1));
                reader.endArray();
                yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw new UiException(reader.getPath(), "Expected a JSON value");
        };
    }

    private static String stripComments(String source) {
        char[] out = source.toCharArray();
        boolean string = false, escaped = false;
        for (int i = 0; i < out.length; i++) {
            char c = source.charAt(i);
            if (string) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') string = false;
            } else if (c == '"') string = true;
            else if (c == '/' && i + 1 < out.length) {
                char next = source.charAt(i + 1);
                if (next == '/') {
                    out[i++] = ' '; out[i] = ' ';
                    while (i + 1 < out.length && source.charAt(i + 1) != '\n' && source.charAt(i + 1) != '\r') out[++i] = ' ';
                } else if (next == '*') {
                    int start = i;
                    out[i++] = ' '; out[i++] = ' ';
                    while (i + 1 < out.length && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) {
                        if (out[i] != '\n' && out[i] != '\r') out[i] = ' ';
                        i++;
                    }
                    if (i + 1 >= out.length) throw new UiException("$", "Unterminated comment at offset " + start);
                    out[i++] = ' '; out[i] = ' ';
                }
            }
        }
        return new String(out);
    }
}
