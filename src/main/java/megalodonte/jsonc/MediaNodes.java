package megalodonte.jsonc;

import com.google.gson.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import megalodonte.base.components.Component;
import megalodonte.components.ImagePreview;
import megalodonte.components.ImageSequence;
import megalodonte.components.SoundSequence;
import megalodonte.components.inputs.TextAreaInput;
import java.util.ArrayList;

/** Maps declarative state to controls supplied by megalodonte-components. */
final class MediaNodes {
    static Component render(String type, JsonObject props, JsonObject bind, JsonObject events, UiSession session) {
        Component component = switch (type) {
            case "TextArea" -> {
                var area = new TextAreaInput((megalodonte.props.InputProps) null);
                var control = area.control();
                var textAreaProps = new ThemeProps.TextArea();
                if (props.has("height")) textAreaProps.height(props.get("height").getAsInt());
                if (props.has("width")) textAreaProps.width(props.get("width").getAsInt());
                session.observe(megalodonte.base.theme.ThemeManager.state(), theme -> {
                    if (theme != null) textAreaProps.applyTo(control, theme);
                });
                control.setWrapText(true);
                control.setPromptText(Values.string(props, "placeholder", ""));
                var state = session.states().string(bind.get("value").getAsString());
                session.observe(state, value -> { if (!control.getText().equals(value)) control.setText(value); });
                javafx.beans.value.ChangeListener<String> listener = (obs, old, value) -> state.set(value);
                control.textProperty().addListener(listener);
                session.cleanup(() -> { control.textProperty().removeListener(listener); control.clear(); });
                yield area;
            }
            case "ImagePreview" -> {
                var preview = new ImagePreview();
                session.observe(session.states().string(bind.get("source").getAsString()), preview::setSource);
                session.cleanup(preview::close);
                yield preview;
            }
            case "ImageSequence" -> {
                var sequence = new ImageSequence();
                var selected = session.states().state(bind.get("selected").getAsString(), Integer.class);
                session.observe(session.states().string(bind.get("items").getAsString()), json -> sequence.setItems(images(json)));
                session.observe(selected, sequence::select);
                sequence.onSelect(index -> { selected.set(index); if (events.has("onSelect")) session.invoke(events.get("onSelect").getAsString()); });
                session.cleanup(sequence::close);
                yield sequence;
            }
            case "SoundSequence" -> {
                var sequence = new SoundSequence();
                if (props.has("height")) sequence.setMinimumHeight(props.get("height").getAsDouble());
                var selected = session.states().state(bind.get("selected").getAsString(), Integer.class);
                session.observe(session.states().string(bind.get("items").getAsString()), json -> sequence.setItems(sounds(json)));
                session.observe(selected, sequence::select);
                sequence.onSelect(index -> { selected.set(index); if (events.has("onSelect")) session.invoke(events.get("onSelect").getAsString()); });
                session.cleanup(sequence::close);
                yield sequence;
            }
            default -> throw new UiException("view.type", "Unknown media component: " + type);
        };
        if (component.getJavaFxNode() instanceof Region region) {
            region.setMinWidth(0);
            if (props.has("height")) { double height = props.get("height").getAsDouble(); region.setPrefHeight(height); region.setMinHeight(height); }
            if (props.has("width")) region.setPrefWidth(props.get("width").getAsDouble());
            else { region.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(region, Priority.ALWAYS); }
        }
        return component;
    }
    private static java.util.List<ImageSequence.Item> images(String json) {
        var result = new ArrayList<ImageSequence.Item>();
        for (var element : JsonParser.parseString(json).getAsJsonArray()) {
            var object = element.getAsJsonObject();
            result.add(new ImageSequence.Item(object.get("duration").getAsDouble(), Values.string(object, "source", ""), Values.string(object, "label", "Cena")));
        }
        return result;
    }
    private static java.util.List<SoundSequence.Item> sounds(String json) {
        var result = new ArrayList<SoundSequence.Item>();
        for (var element : JsonParser.parseString(json).getAsJsonArray()) {
            var object = element.getAsJsonObject();
            result.add(new SoundSequence.Item(object.get("start").getAsDouble(), object.get("duration").getAsDouble(), object.get("volume").getAsDouble(), Values.string(object, "label", "Som")));
        }
        return result;
    }
}
