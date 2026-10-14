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
        boolean desktop = Set.of("DataList", "DiffView").contains(type);
        boolean input = Set.of("Input", "PasswordInput", "TextArea").contains(type);
        if (!layout && !scroll && !desktop && !input && !Set.of("Text", "Button", "Image", "ImagePreview", "ImageSequence", "SoundSequence", "Select", "Table").contains(type)) throw new UiException(path + ".type", "Unknown component: " + type);
        if (node.has("id") && !ids.add(Values.name(node.get("id"), path + ".id"))) throw new UiException(path + ".id", "Duplicate component id");
        JsonObject props = Values.optional(node, "props", path);
        String[] allowed = type.equals("Table") ? new String[]{"columns", "height", "horizontalScroll", "pageSize", "multipleSelection"}
                : (scroll || desktop) ? new String[]{"width", "height", "fillWidth", "fillHeight"}
                : type.equals("Container") ? new String[]{"spacing", "padding", "width", "maxWidth", "height", "fillWidth", "fillHeight", "alignment", "bgColor", "bgImage"}
                : layout ? new String[]{"spacing", "padding", "width", "maxWidth", "height", "fillWidth", "fillHeight", "alignment", "bgColor"}
                : type.equals("TextArea") ? new String[]{"placeholder", "height", "width", "readOnly"}
                : type.equals("Select") ? new String[]{"height", "width"}
                : input ? new String[]{"placeholder", "height", "width"}
                : type.equals("Image") ? new String[]{"source", "height", "width"}
                : Set.of("ImagePreview", "ImageSequence", "SoundSequence").contains(type) ? new String[]{"height", "width"}
                : type.equals("Button") ? new String[]{"text", "height", "fillWidth", "defaultButton"} : new String[]{"text", "wrapWidth"};
        Values.keys(props, path + ".props", allowed);
        props.asMap().forEach((key, value) -> {
            String p = path + ".props." + key;
            if (key.equals("columns")) {
                if (!value.isJsonArray() || value.getAsJsonArray().isEmpty()) throw new UiException(p, "Table requires columns");
                for (var column : value.getAsJsonArray()) {
                    var definition = Values.object(column, p);
                    Values.keys(definition, p, "title", "field");
                    Values.string(definition.get("title"), p + ".title");
                    Values.name(definition.get("field"), p + ".field");
                }
            }
            else if (Set.of("fillWidth", "fillHeight", "defaultButton", "readOnly", "horizontalScroll", "multipleSelection").contains(key)) Values.bool(value, p);
            else if (Set.of("text", "placeholder", "alignment", "bgColor", "source", "bgImage").contains(key)) Values.string(value, p);
            else if (Values.integer(value, p) < 0) throw new UiException(p, "Must be nonnegative");
        });
        if (props.has("alignment") && !Set.of("start", "center", "end").contains(props.get("alignment").getAsString()))
            throw new UiException(path + ".props.alignment", "Expected start, center or end");
        if (type.equals("Image") && !props.has("source") && !Values.optional(node, "bind", path).has("source"))
            throw new UiException(path + ".props.source", "Image source or binding is required");
        JsonObject style = Values.optional(node, "style", path);
        if ((layout || scroll || desktop || type.equals("Image") || type.equals("Table")) && !style.isEmpty()) throw new UiException(path + ".style", "Text style is only supported on Text, Button and inputs");
        Values.keys(style, path + ".style", type.equals("Button")
                ? new String[]{"fontSize", "bold", "textColor", "fontFamily", "bgColor"}
                : new String[]{"fontSize", "bold", "textColor", "fontFamily"});
        style.asMap().forEach((key, value) -> {
            String p = path + ".style." + key;
            if (key.equals("fontSize")) { if (Values.integer(value, p) <= 0) throw new UiException(p, "Must be positive"); }
            else if (key.equals("bold")) Values.bool(value, p);
            else Values.string(value, p);
        });
        JsonObject bind = Values.optional(node, "bind", path);
        Values.keys(bind, path + ".bind", "value", "text", "visible", "disabled", "source", "items", "selected", "selectedItems");
        bind.asMap().forEach((key, value) -> {
            String p = path + ".bind." + key, name = Values.name(value, p);
            Class<?> stateType;
            try { stateType = states.type(name); } catch (UiException e) { throw new UiException(p, "Unknown state: " + name); }
            if (key.equals("source") && (!Set.of("Image", "ImagePreview").contains(type) || stateType != String.class))
                throw new UiException(p, "source requires Image or ImagePreview and string state");
            if (key.equals("items") && (!Set.of("ImageSequence", "SoundSequence", "DataList", "Select", "Table").contains(type) || stateType != String.class)) throw new UiException(p, "items requires a list component and string JSON state");
            if (key.equals("selected") && (!Set.of("ImageSequence", "SoundSequence", "DataList", "Table").contains(type) || stateType != (type.equals("Table") ? String.class : Integer.class))) throw new UiException(p, "selected requires a matching state type");
            if (key.equals("selectedItems") && (!type.equals("Table") || stateType != String.class)) throw new UiException(p, "selectedItems requires Table and string state");
            if (key.equals("text") && type.equals("DiffView") && stateType != String.class) throw new UiException(p, "DiffView requires string state");
            if (key.equals("value") && (!(input || type.equals("Select")) || stateType != String.class)) throw new UiException(p, "value requires an input and string state");
            if (key.equals("text") && !Set.of("Text", "Button", "DiffView").contains(type)) throw new UiException(p, "text requires Text or Button");
            if (Set.of("visible", "disabled").contains(key) && stateType != Boolean.class) throw new UiException(p, "Requires boolean state");
        });
        if (Set.of("ImageSequence", "SoundSequence", "DataList").contains(type) && (!bind.has("items") || !bind.has("selected"))) throw new UiException(path + ".bind", "ImageSequence requires items and selected");
        if (type.equals("DiffView") && !bind.has("text")) throw new UiException(path + ".bind", "DiffView requires text");
        if (type.equals("DataList")) DesktopNodes.parseItems(states.string(bind.get("items").getAsString()).get());
        if (type.equals("Table")) {
            if (!props.has("columns") || !bind.has("items") || !bind.has("selected")) throw new UiException(path, "Table requires columns, items and selected");
            if (props.has("multipleSelection") && props.get("multipleSelection").getAsBoolean() && !bind.has("selectedItems")) throw new UiException(path + ".bind.selectedItems", "Multiple selection requires selectedItems");
            TableNodes.parseRows(states.string(bind.get("items").getAsString()).get());
            if (props.has("pageSize") && props.get("pageSize").getAsInt() <= 0) throw new UiException(path + ".props.pageSize", "Must be positive");
        }
        if (type.equals("Select") && (!bind.has("items") || !bind.has("value")))
            throw new UiException(path + ".bind", "Select requires items and value");
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
        Values.keys(events, path + ".events", type.equals("Button") ? new String[]{"onClick"} : type.equals("Table") ? new String[]{"onSelect", "onDoubleClick"} : Set.of("ImageSequence", "SoundSequence", "DataList").contains(type) ? new String[]{"onSelect"} : input && !type.equals("TextArea") ? new String[]{"onEnter"} : new String[]{});
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
