package megalodonte.jsonc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.beans.value.ChangeListener;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;
import megalodonte.base.components.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Editable selection field whose suggestions follow a separately bound query. */
final class InputSelectorNode {
    private InputSelectorNode() { }

    static Component render(JsonObject props, JsonObject bind, UiSession session) {
        var selectProps = new ThemeProps.Select();
        if (props.has("width")) selectProps.minWidth(props.get("width").getAsInt());
        if (props.has("height")) selectProps.height(props.get("height").getAsInt());
        var select = new megalodonte.components.Select<String>();
        selectProps.applyTo(select.getJavaFxNode(), megalodonte.base.theme.ThemeManager.theme());
        @SuppressWarnings("unchecked") var control = (ComboBox<String>) select.getJavaFxNode();
        control.setEditable(true);
        control.setMaxWidth(Double.MAX_VALUE);
        control.getEditor().setPromptText(Values.string(props, "placeholder", ""));
        var query = session.states().string(bind.get("text").getAsString());
        var selected = session.states().string(bind.get("value").getAsString());
        var labels = new HashMap<String, String>();
        final boolean[] syncing = {false};
        final boolean[] disposed = {false};
        // ComboBox's converter formats the editor, but its popup cells otherwise
        // render the raw value (the product code). Use the themed Select cells.
        select.displayText(value -> value == null ? "" : labels.getOrDefault(value, value));
        control.setStyle(control.getStyle() + "; -fx-focus-color: transparent; -fx-faint-focus-color: transparent; -fx-background-insets: 0; -fx-border-insets: 0;");
        control.getEditor().setStyle(control.getEditor().getStyle() + "; -fx-focus-color: transparent; -fx-faint-focus-color: transparent; -fx-background-insets: 0; -fx-border-insets: 0; -fx-border-color: transparent; -fx-background-color: transparent;");
        control.setConverter(new StringConverter<>() {
            @Override public String toString(String value) { return value == null ? "" : labels.getOrDefault(value, value); }
            @Override public String fromString(String label) {
                return labels.entrySet().stream().filter(entry -> entry.getValue().equals(label))
                        .map(Map.Entry::getKey).findFirst().orElse(label);
            }
        });
        session.observe(query, text -> {
            if (Objects.equals(control.getEditor().getText(), text)) return;
            syncing[0] = true;
            try { control.getEditor().setText(text); }
            finally { syncing[0] = false; }
        });
        session.observe(selected, value -> {
            if (value.isEmpty() || control.getItems().contains(value)) {
                String choice = value.isEmpty() ? null : value;
                if (!Objects.equals(control.getValue(), choice)) {
                    syncing[0] = true;
                    try {
                        String typed = control.getEditor().getText();
                        control.setValue(choice);
                        if (choice == null) control.getEditor().setText(typed);
                    }
                    finally { syncing[0] = false; }
                }
            }
        });
        session.observe(session.states().string(bind.get("items").getAsString()), json -> {
            var items = new ArrayList<String>();
            labels.clear();
            try {
                for (JsonElement item : JsonParser.parseString(json).getAsJsonArray()) {
                    String value, label;
                    if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                        value = label = item.getAsString();
                    } else {
                        var option = item.getAsJsonObject();
                        value = option.get("value").getAsString();
                        label = option.get("label").getAsString();
                    }
                    if (labels.putIfAbsent(value, label) != null) throw new IllegalArgumentException("Duplicate value");
                    items.add(value);
                }
            } catch (RuntimeException error) {
                throw new UiException("view.bind.items", "InputSelector items must be unique strings or label/value objects");
            }
            syncing[0] = true;
            try {
                String typed = control.getEditor().getText();
                control.getItems().setAll(items);
                control.getEditor().setText(typed);
            } finally { syncing[0] = false; }
            if (!items.isEmpty() && control.getEditor().isFocused() && !query.get().isBlank()) control.show();
            else control.hide();
        });
        ChangeListener<String> textListener = (ignored, old, text) -> {
            if (syncing[0]) return;
            String currentValue = control.getValue();
            if ((currentValue != null && Objects.equals(text, labels.get(currentValue)))
                    || labels.containsValue(text)) {
                // The ComboBox skin also writes the chosen label into its editor
                // while the popup ListView is still processing the click.
                // The value listener commits that label after the event.
                return;
            }
            if (!selected.get().isEmpty()) selected.set("");
            if (!Objects.equals(query.get(), text)) query.set(text);
        };
        ChangeListener<String> valueListener = (ignored, old, value) -> {
            if (syncing[0] || value == null || !control.getItems().contains(value)) return;
            String label = labels.getOrDefault(value, value);
            // The popup ListView is still notifying its selection listeners here.
            // Touching the query/options or hiding the popup synchronously can
            // invalidate its added-items list before ListViewBehavior reads it.
            Platform.runLater(() -> {
                if (disposed[0] || !Objects.equals(control.getValue(), value)) return;
                control.hide();
                selected.set(value);
                query.set(label);
            });
        };
        control.getEditor().textProperty().addListener(textListener);
        control.valueProperty().addListener(valueListener);
        session.cleanup(() -> {
            disposed[0] = true;
            control.getEditor().textProperty().removeListener(textListener);
            control.valueProperty().removeListener(valueListener);
            control.getItems().clear();
        });
        return select;
    }
}
