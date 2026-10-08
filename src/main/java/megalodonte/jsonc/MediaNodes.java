package megalodonte.jsonc;

import com.google.gson.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.geometry.Insets;
import megalodonte.base.components.Component;
import java.nio.file.*;

/** Declarative local media controls; subscriptions and listeners belong to the session. */
final class MediaNodes {
    static Component render(String type, JsonObject props, JsonObject bind, JsonObject events, UiSession session) {
        javafx.scene.Node node;
        if (type.equals("TextArea")) {
            var area = new TextArea();
            area.setWrapText(true);
            area.setPromptText(Values.string(props, "placeholder", ""));
            var state = session.states().string(bind.get("value").getAsString());
            session.observe(state, value -> { if (!area.getText().equals(value)) area.setText(value); });
            javafx.beans.value.ChangeListener<String> listener = (obs, old, value) -> state.set(value);
            area.textProperty().addListener(listener);
            session.cleanup(() -> { area.textProperty().removeListener(listener); area.clear(); });
            node = area;
        } else if (type.equals("ImagePreview")) {
            var image = new ImageView(); image.setPreserveRatio(true);
            var placeholder = new Label("Selecione uma cena e importe uma imagem");
            placeholder.setStyle("-fx-text-fill: #c9d4e6;");
            var pane = new StackPane(image, placeholder);
            pane.setStyle("-fx-background-color: #151b28; -fx-background-radius: 8;");
            image.fitWidthProperty().bind(pane.widthProperty().subtract(24));
            image.fitHeightProperty().bind(pane.heightProperty().subtract(24));
            session.observe(session.states().string(bind.get("source").getAsString()), source -> {
                var loaded = localImage(source, 1600, 900);
                image.setImage(loaded); placeholder.setVisible(loaded == null);
            });
            session.cleanup(() -> { image.fitWidthProperty().unbind(); image.fitHeightProperty().unbind(); image.setImage(null); });
            node = pane;
        } else {
            var cards = new HBox(8); cards.setPadding(new Insets(12));
            var scroll = new ScrollPane(cards); scroll.setFitToHeight(true);
            var selected = session.states().state(bind.get("selected").getAsString(), Integer.class);
            Runnable highlight = () -> {
                for (int i = 0; i < cards.getChildren().size(); i++) cards.getChildren().get(i).setStyle(
                    "-fx-background-color: " + (selected.get() == i ? "#c7ddff" : "#e5eaf2") + "; -fx-border-color: " + (selected.get() == i ? "#367bef" : "#bcc6d5") + "; -fx-border-radius: 6; -fx-background-radius: 6;");
            };
            session.observe(session.states().string(bind.get("items").getAsString()), json -> {
                cards.getChildren().clear();
                var items = JsonParser.parseString(json).getAsJsonArray();
                double start = 0;
                for (int i = 0; i < items.size(); i++) {
                    var item = items.get(i).getAsJsonObject(); final int index = i;
                    double duration = item.get("duration").getAsDouble();
                    var thumb = new ImageView(localImage(Values.string(item, "source", ""), 200, 90));
                    thumb.setFitWidth(120); thumb.setFitHeight(65); thumb.setPreserveRatio(true);
                    var label = new Label((i + 1) + " · " + Values.string(item, "label", "Cena")); label.setWrapText(true); label.setMaxWidth(130);
                    var card = new VBox(6, new Label(String.format(java.util.Locale.ROOT, "%.1fs → %.1fs", start, start + duration)), thumb, label);
                    card.setPadding(new Insets(8)); card.setPrefWidth(Math.max(150, Math.min(280, duration * 25))); card.setMinWidth(150);
                    card.setFocusTraversable(true); card.setAccessibleText(label.getText());
                    Runnable choose = () -> { selected.set(index); if (events.has("onSelect")) session.invoke(events.get("onSelect").getAsString()); };
                    card.setOnMouseClicked(event -> choose.run());
                    card.setOnKeyPressed(event -> { if (event.getCode() == javafx.scene.input.KeyCode.ENTER || event.getCode() == javafx.scene.input.KeyCode.SPACE) { choose.run(); event.consume(); } });
                    cards.getChildren().add(card); start += duration;
                }
                highlight.run();
            });
            session.observe(selected, value -> highlight.run());
            session.cleanup(() -> cards.getChildren().clear());
            node = scroll;
        }
        if (node instanceof Region region) {
            region.setMinWidth(0);
            if (props.has("height")) { region.setPrefHeight(props.get("height").getAsDouble()); region.setMinHeight(props.get("height").getAsDouble()); }
            if (props.has("width")) region.setPrefWidth(props.get("width").getAsDouble());
            else { region.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(region, Priority.ALWAYS); }
        }
        return Component.CreateFromJavaFxNode(node);
    }
    private static Image localImage(String source, double width, double height) {
        if (source.isBlank()) return null;
        try {
            Path path = Path.of(source);
            if (!Files.isRegularFile(path)) return null;
            var image = new Image(path.toUri().toString(), width, height, true, true, false);
            return image.isError() ? null : image;
        } catch (RuntimeException exception) { return null; }
    }
}
