package megalodonte.jsonc;

import com.google.gson.*;
import java.util.*;

final class Values {
    static JsonObject object(JsonElement value, String path) {
        if (value == null || !value.isJsonObject()) throw new UiException(path, "Expected an object");
        return value.getAsJsonObject();
    }
    static JsonObject optional(JsonObject parent, String key, String path) {
        return parent.has(key) ? object(parent.get(key), path + "." + key) : new JsonObject();
    }
    static String string(JsonElement value, String path) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new UiException(path, "Expected a string");
        return value.getAsString();
    }
    static String string(JsonObject parent, String key, String fallback) {
        return parent.has(key) ? string(parent.get(key), key) : fallback;
    }
    static boolean bool(JsonElement value, String path) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
            throw new UiException(path, "Expected a boolean");
        return value.getAsBoolean();
    }
    static int integer(JsonElement value, String path) {
        try {
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
                throw new ArithmeticException();
            return value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException e) { throw new UiException(path, "Expected a 32-bit integer"); }
    }
    static void keys(JsonObject object, String path, String... allowed) {
        Set<String> names = Set.of(allowed);
        for (String name : object.keySet()) if (!names.contains(name)) throw new UiException(path + "." + name, "Unknown property");
    }
    static String name(JsonElement value, String path) {
        String name = string(value, path);
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new UiException(path, "Expected an identifier");
        return name;
    }
}
