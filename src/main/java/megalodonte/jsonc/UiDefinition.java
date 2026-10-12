package megalodonte.jsonc;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Parsed reusable definition. Each open() call creates independent state and lifecycle. */
public final class UiDefinition {
    final JsonObject document;
    private UiDefinition(JsonObject document) { this.document = document; }
    public static UiDefinition parse(String jsonc) { return new UiDefinition(Jsonc.parse(jsonc)); }
    public static UiDefinition fromPath(Path path) throws IOException { return parse(Files.readString(path, StandardCharsets.UTF_8)); }
    public static UiDefinition fromResource(Class<?> owner, String path) {
        try (InputStream stream = owner.getResourceAsStream(path)) {
            if (stream == null) throw new UiException(path, "Resource not found");
            return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) { throw new UiException(path, "Cannot read resource", e); }
    }
    public UiSession open(Actions actions) { return new UiSession(this, actions.snapshot()); }
    public String id() { return Values.name(document.get("id"), "$.id"); }
    /** Event names referenced by this document, for convention-based hosts. */
    public Set<String> actionNames() {
        var names = new LinkedHashSet<String>();
        collectActions(Values.object(document.get("view"), "$.view"), names);
        return Collections.unmodifiableSet(names);
    }
    private void collectActions(JsonObject node, Set<String> names) {
        Values.optional(node, "events", "view").asMap().forEach((key, value) -> names.add(Values.name(value, "events." + key)));
        if (node.has("children")) {
            if (!node.get("children").isJsonArray()) throw new UiException("view.children", "Expected an array");
            node.getAsJsonArray("children").forEach(child -> collectActions(Values.object(child, "view.children"), names));
        }
    }

    void validate(StateStore states, Actions actions) {
        Values.keys(document, "$", "$schema", "version", "id", "states", "view");
        if (document.has("$schema")) Values.string(document.get("$schema"), "$.$schema");
        if (Values.integer(document.get("version"), "$.version") != 1) throw new UiException("$.version", "Only version 1 is supported");
        Values.name(document.get("id"), "$.id");
        validateNode(Values.object(document.get("view"), "$.view"), "$.view", states, actions, new HashSet<>(), 0);
    }
    private void validateNode(JsonObject node, String path, StateStore states, Actions actions, Set<String> ids, int depth) {
        if (depth > 64) throw new UiException(path, "Maximum view depth exceeded");
        Values.keys(node, path, "type", "id", "props", "style", "bind", "format", "events", "validation", "children");
        String type = Values.string(node.get("type"), path + ".type");
        boolean layout = Set.of("Column", "Row", "Container").contains(type);
        boolean scroll = type.equals("Scroll");
        boolean input = Set.of("Input", "PasswordInput", "TextArea").contains(type);
        if (!layout && !scroll && !input && !Set.of("Text", "Button", "ImagePreview", "ImageSequence", "SoundSequence").contains(type)) throw new UiException(path + ".type", "Unknown component: " + type);
        if (node.has("id") && !ids.add(Values.name(node.get("id"), path + ".id"))) throw new UiException(path + ".id", "Duplicate component id");
        JsonObject props = Values.optional(node, "props", path);
        String[] allowed = scroll ? new String[]{"width", "height", "fillWidth", "fillHeight"} : layout ? new String[]{"spacing", "padding", "width", "maxWidth", "height", "fillWidth", "fillHeight", "alignment", "bgColor"}
                : input ? new String[]{"placeholder", "height", "width"}
                : Set.of("ImagePreview", "ImageSequence", "SoundSequence").contains(type) ? new String[]{"height", "width"}
                : type.equals("Button") ? new String[]{"text", "height", "fillWidth", "defaultButton"} : new String[]{"text", "wrapWidth"};
        Values.keys(props, path + ".props", allowed);
        props.asMap().forEach((key, value) -> {
            String p = path + ".props." + key;
            if (Set.of("fillWidth", "fillHeight", "defaultButton").contains(key)) Values.bool(value, p);
            else if (Set.of("text", "placeholder", "alignment", "bgColor").contains(key)) Values.string(value, p);
            else if (Values.integer(value, p) < 0) throw new UiException(p, "Must be nonnegative");
        });
        if (props.has("alignment") && !Set.of("start", "center", "end").contains(props.get("alignment").getAsString()))
            throw new UiException(path + ".props.alignment", "Expected start, center or end");
        JsonObject style = Values.optional(node, "style", path);
        if ((layout || scroll) && !style.isEmpty()) throw new UiException(path + ".style", "Text style is only supported on Text, Button and inputs");
        Values.keys(style, path + ".style", "fontSize", "bold", "textColor", "fontFamily");
        style.asMap().forEach((key, value) -> {
            String p = path + ".style." + key;
            if (key.equals("fontSize")) { if (Values.integer(value, p) <= 0) throw new UiException(p, "Must be positive"); }
            else if (key.equals("bold")) Values.bool(value, p);
            else Values.string(value, p);
        });
        JsonObject bind = Values.optional(node, "bind", path);
        Values.keys(bind, path + ".bind", "value", "text", "visible", "disabled", "source", "items", "selected");
        bind.asMap().forEach((key, value) -> {
            String p = path + ".bind." + key, name = Values.name(value, p);
            Class<?> stateType;
            try { stateType = states.type(name); } catch (UiException e) { throw new UiException(p, "Unknown state: " + name); }
            if (key.equals("source") && (!type.equals("ImagePreview") || stateType != String.class)) throw new UiException(p, "source requires ImagePreview and string state");
            if (key.equals("items") && (!Set.of("ImageSequence", "SoundSequence").contains(type) || stateType != String.class)) throw new UiException(p, "items requires ImageSequence and string JSON state");
            if (key.equals("selected") && (!Set.of("ImageSequence", "SoundSequence").contains(type) || stateType != Integer.class)) throw new UiException(p, "selected requires ImageSequence and integer state");
            if (key.equals("value") && (!input || stateType != String.class)) throw new UiException(p, "value requires an input and string state");
            if (key.equals("text") && !Set.of("Text", "Button").contains(type)) throw new UiException(p, "text requires Text or Button");
            if (Set.of("visible", "disabled").contains(key) && stateType != Boolean.class) throw new UiException(p, "Requires boolean state");
        });
        if (Set.of("ImageSequence", "SoundSequence").contains(type) && (!bind.has("items") || !bind.has("selected"))) throw new UiException(path + ".bind", "ImageSequence requires items and selected");
        if (type.equals("ImagePreview") && !bind.has("source")) throw new UiException(path + ".bind", "ImagePreview requires source");
        if (input && !bind.has("value")) throw new UiException(path + ".bind.value", "Input requires a state binding");
        if (bind.has("text") && props.has("text")) throw new UiException(path, "Declare either props.text or bind.text");
        if (type.equals("Button") && !bind.has("text") && !props.has("text")) throw new UiException(path + ".props.text", "Button requires text");
        JsonObject format = Values.optional(node, "format", path);
        if (node.has("format") && format.isEmpty()) throw new UiException(path + ".format", "Expected a format kind");
        if (!format.isEmpty() && !input && !type.equals("Text")) throw new UiException(path + ".format", "Formatting requires Input or Text");
        Class<?> formattedType = input ? String.class : bind.has("text") ? states.type(bind.get("text").getAsString()) : String.class;
        if (type.equals("TextArea") && !format.isEmpty()) throw new UiException(path + ".format", "TextArea does not support formatting");
        Formats.validate(format, path + ".format", type, formattedType);
        JsonObject events = Values.optional(node, "events", path);
        Values.keys(events, path + ".events", type.equals("Button") ? new String[]{"onClick"} : Set.of("ImageSequence", "SoundSequence").contains(type) ? new String[]{"onSelect"} : input && !type.equals("TextArea") ? new String[]{"onEnter"} : new String[]{});
        events.asMap().forEach((key, value) -> {
            String name = Values.name(value, path + ".events." + key);
            if (!actions.contains(name)) throw new UiException(path + ".events." + key, "Unregistered action: " + name);
        });
        JsonObject rules = Values.optional(node, "validation", path);
        if (!input && !rules.isEmpty()) throw new UiException(path + ".validation", "Validation requires an input");
        Values.keys(rules, path + ".validation", "required", "email", "minLength", "maxLength", "label");
        rules.asMap().forEach((key, value) -> {
            String p = path + ".validation." + key;
            if (key.equals("label")) Values.string(value, p);
            else if (key.equals("required") || key.equals("email")) Values.bool(value, p);
            else if (Values.integer(value, p) < 0) throw new UiException(p, "Must be nonnegative");
        });
        if (rules.has("minLength") && rules.has("maxLength") && rules.get("minLength").getAsInt() > rules.get("maxLength").getAsInt()) throw new UiException(path + ".validation", "minLength exceeds maxLength");
        if (scroll && !node.has("children")) throw new UiException(path + ".children", "Scroll requires exactly one child");
        if (node.has("children")) {
            if ((!layout && !scroll) || !node.get("children").isJsonArray()) throw new UiException(path + ".children", "Only layouts accept an array of children");
            JsonArray children = node.getAsJsonArray("children");
            if (scroll && children.size() != 1) throw new UiException(path + ".children", "Scroll requires exactly one child");
            for (int i = 0; i < children.size(); i++) validateNode(Values.object(children.get(i), path + ".children[" + i + "]"), path + ".children[" + i + "]", states, actions, ids, depth + 1);
        }
    }
}
