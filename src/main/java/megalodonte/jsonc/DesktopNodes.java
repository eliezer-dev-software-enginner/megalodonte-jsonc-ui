package megalodonte.jsonc;

import com.google.gson.*;
import javafx.scene.layout.*;
import megalodonte.base.components.Component;
import megalodonte.jsonc.components.DataList;
import megalodonte.jsonc.components.DiffView;
import java.util.*;

/** Data-oriented desktop bindings with session-owned subscriptions and cleanup. */
final class DesktopNodes {
    static Component render(String type, JsonObject props, JsonObject bind, JsonObject events, UiSession session) {
        Component component;
        if (type.equals("DataList")) {
            var list = new DataList();
            session.cleanup(list::close);
            var selected = session.states().state(bind.get("selected").getAsString(), Integer.class);
            session.observe(session.states().string(bind.get("items").getAsString()), json -> {
                var items = parseItems(json);
                list.setItems(items);
                list.select(selected.get());
                if (selected.get() < -1 || selected.get() >= items.size()) selected.set(-1);
            });
            session.observe(selected, list::select);
            list.onSelect(index -> { selected.set(index); if (events.has("onSelect")) session.invoke(events.get("onSelect").getAsString()); });
            component = list;
        } else {
            var diff = new DiffView();
            session.cleanup(diff::close);
            session.observe(session.states().string(bind.get("text").getAsString()), diff::setText);
            component = diff;
        }
        var region = (Region) component.getJavaFxNode();
        region.setMinSize(0, 0);
        region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        if (props.has("width")) region.setPrefWidth(props.get("width").getAsInt());
        if (props.has("height")) region.setPrefHeight(props.get("height").getAsInt());
        if (props.has("fillWidth") && props.get("fillWidth").getAsBoolean()) HBox.setHgrow(region, Priority.ALWAYS);
        if (props.has("fillHeight") && props.get("fillHeight").getAsBoolean()) VBox.setVgrow(region, Priority.ALWAYS);
        return component;
    }
    static List<DataList.Item> parseItems(String json) {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonArray()) throw new UiException("DataList.items", "Expected JSON array");
            var result = new ArrayList<DataList.Item>();
            int index = 0;
            for (var entry : parsed.getAsJsonArray()) {
                String path = "DataList.items[" + index++ + "]";
                var object = Values.object(entry, path);
                Values.keys(object, path, "label", "detail", "badge");
                String label = Values.string(object.get("label"), path + ".label");
                String detail = object.has("detail") ? Values.string(object.get("detail"), path + ".detail") : "";
                String badge = object.has("badge") ? Values.string(object.get("badge"), path + ".badge") : "";
                result.add(new DataList.Item(label, detail, badge));
            }
            return List.copyOf(result);
        } catch (JsonParseException e) { throw new UiException("DataList.items", "Invalid JSON array", e); }
    }
}
