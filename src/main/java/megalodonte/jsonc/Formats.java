package megalodonte.jsonc;

import com.google.gson.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;

final class Formats {
    static void validate(JsonObject spec, String path, String component, Class<?> type) {
        if (spec.isEmpty()) return;
        String kind = Values.string(spec.get("kind"), path + ".kind");
        switch (kind) {
            case "mask" -> {
                Values.keys(spec, path, "kind", "pattern", "store");
                String pattern = Values.string(spec.get("pattern"), path + ".pattern");
                if (!pattern.contains("#") || !pattern.matches("[# .()/+-]+")) throw new UiException(path, "Mask requires # digits and punctuation");
                if (!Values.string(spec, "store", "digits").equals("digits")) throw new UiException(path + ".store", "Only digits storage is supported");
                if (type != String.class) throw new UiException(path, "Mask requires a string state");
            }
            case "currency", "number" -> {
                if (!component.equals("Text")) throw new UiException(path, "Numeric formatting is supported on Text only");
                if (type != Integer.class && type != BigDecimal.class) throw new UiException(path, "Numeric formatting requires an integer or decimal state");
                if (kind.equals("currency")) {
                    Values.keys(spec, path, "kind", "currency", "locale");
                    try { Currency.getInstance(Values.string(spec.get("currency"), path + ".currency")); }
                    catch (IllegalArgumentException e) { throw new UiException(path + ".currency", "Invalid ISO currency"); }
                } else Values.keys(spec, path, "kind", "locale");
                String locale = Values.string(spec, "locale", "pt-BR");
                try { new Locale.Builder().setLanguageTag(locale).build(); }
                catch (IllformedLocaleException e) { throw new UiException(path + ".locale", "Invalid locale"); }
            }
            case "uppercase", "lowercase" -> {
                Values.keys(spec, path, "kind");
                if (type != String.class) throw new UiException(path, "Case formatting requires a string state");
            }
            default -> throw new UiException(path + ".kind", "Unsupported format: " + kind);
        }
        if (component.equals("PasswordInput")) throw new UiException(path, "Password fields do not accept formatting");
    }
    static String storage(String text, JsonObject format) {
        return switch (Values.string(format, "kind", "")) {
            case "mask" -> {
                String digits = text.replaceAll("[^0-9]", "");
                int limit = (int) format.get("pattern").getAsString().chars().filter(c -> c == '#').count();
                yield digits.substring(0, Math.min(limit, digits.length()));
            }
            case "uppercase" -> text.toUpperCase(Locale.ROOT);
            case "lowercase" -> text.toLowerCase(Locale.ROOT);
            default -> text;
        };
    }
    static String display(Object value, JsonObject format) {
        String kind = Values.string(format, "kind", "");
        if (kind.equals("currency") || kind.equals("number")) {
            Locale locale = Locale.forLanguageTag(Values.string(format, "locale", "pt-BR"));
            NumberFormat nf = kind.equals("currency") ? NumberFormat.getCurrencyInstance(locale) : NumberFormat.getNumberInstance(locale);
            if (kind.equals("currency")) nf.setCurrency(Currency.getInstance(format.get("currency").getAsString()));
            return nf.format(value);
        }
        String text = String.valueOf(value);
        if (!kind.equals("mask")) return storage(text, format);
        String digits = storage(text, format), pattern = format.get("pattern").getAsString();
        var out = new StringBuilder();
        int index = 0;
        for (char c : pattern.toCharArray()) {
            if (index >= digits.length()) break;
            out.append(c == '#' ? digits.charAt(index++) : c);
        }
        return out.toString();
    }
}
