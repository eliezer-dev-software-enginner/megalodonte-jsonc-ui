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
        Component nativeComponent = null;
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
            var frame = new StackPane(image, placeholder);
            frame.setId("videoFrame"); frame.setManaged(false);
            var pane = new Pane(frame) {
                @Override protected void layoutChildren() {
                    double width = Math.min(getWidth(), getHeight() * 16.0 / 9);
                    double height = width * 9 / 16;
                    frame.resizeRelocate((getWidth()-width)/2, (getHeight()-height)/2, width, height);
                }
            };
            frame.setStyle("-fx-background-color: #000000;");
            pane.setStyle("-fx-background-color: #151b28; -fx-background-radius: 8;");
            image.fitWidthProperty().bind(frame.widthProperty());
            image.fitHeightProperty().bind(frame.heightProperty());
            session.observe(session.states().string(bind.get("source").getAsString()), source -> {
                var loaded = localImage(source, 1600, 900);
                image.setImage(loaded); placeholder.setVisible(loaded == null);
            });
            session.cleanup(() -> { image.fitWidthProperty().unbind(); image.fitHeightProperty().unbind(); image.setImage(null); });
            node = pane;
        } else if(type.equals("SoundSequence")) {
            var lane=new Pane();lane.setMinHeight(110);
            var scroll=new megalodonte.components.v2.HorizontalScroll(Component.CreateFromJavaFxNode(lane));
            var selected=session.states().state(bind.get("selected").getAsString(),Integer.class);
            Runnable highlight=()-> {
                for(var nodeItem:lane.getChildren()) if(nodeItem.getUserData() instanceof Integer index)
                    nodeItem.setStyle("-fx-background-color: "+(selected.get().equals(index) ? "#a6e5dc" : "#d7eee9")+"; -fx-border-color: #368c80; -fx-background-radius: 5; -fx-border-radius: 5;");
            };
            session.observe(session.states().string(bind.get("items").getAsString()),json->{
                lane.getChildren().clear();var items=JsonParser.parseString(json).getAsJsonArray();
                var ends=new java.util.ArrayList<Double>();double width=600;
                for(int i=0;i<items.size();i++) {
                    var item=items.get(i).getAsJsonObject();int index=i;
                    double start=item.get("start").getAsDouble(),duration=item.get("duration").getAsDouble();
                    double x=12+start*24,w=Math.max(100,duration*24);int row=0;
                    while(row<ends.size() && ends.get(row)>x) row++;
                    if(row==ends.size()) ends.add(0.0);ends.set(row,x+w+4);
                    var label=new Label(Values.string(item,"label","Som"));label.setMaxWidth(w-12);
                    var time=new Label(String.format(java.util.Locale.ROOT,"%.1fs → %.1fs · %.0f%%",start,start+duration,item.get("volume").getAsDouble()*100));
                    var card=new VBox(4,time,label);card.setPadding(new Insets(6));card.setManaged(false);card.resizeRelocate(x,26+row*66,w,60);card.setUserData(index);
                    card.setFocusTraversable(true);card.setAccessibleText(label.getText()+" "+time.getText());
                    Tooltip.install(card,new Tooltip(label.getText()+"\n"+time.getText()));
                    Runnable choose=()->{selected.set(index);if(events.has("onSelect"))session.invoke(events.get("onSelect").getAsString());};
                    card.setOnMouseClicked(event->choose.run());card.setOnKeyPressed(event->{if(event.getCode()==javafx.scene.input.KeyCode.ENTER || event.getCode()==javafx.scene.input.KeyCode.SPACE){choose.run();event.consume();}});
                    lane.getChildren().add(card);width=Math.max(width,x+w+12);
                }
                for(int second=0;second*24<width;second+=10) { var tick=new Label(second+"s");tick.setManaged(false);tick.resizeRelocate(12+second*24,2,60,20);lane.getChildren().add(tick); }
                if(items.isEmpty()) {var empty=new Label("Adicione sons para posicioná-los no vídeo");empty.relocate(12,32);lane.getChildren().add(empty);}
                double height=Math.max(props.has("height") ? props.get("height").getAsDouble() : 170,50+ends.size()*66);
                lane.setPrefSize(width,height-14);((Region)scroll.getJavaFxNode()).setMinHeight(height);((Region)scroll.getJavaFxNode()).setPrefHeight(height);scroll.refresh();highlight.run();
            });
            session.observe(selected,value->{highlight.run();for(var card:lane.getChildren()) if(Integer.valueOf(value).equals(card.getUserData())) scroll.reveal(card);});
            session.cleanup(()->lane.getChildren().clear());node=scroll.getJavaFxNode();nativeComponent=scroll;
        } else {
            var cards = new HBox(8); cards.setPadding(new Insets(12));
            var scroll = new megalodonte.components.v2.HorizontalScroll(Component.CreateFromJavaFxNode(cards));
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
                scroll.refresh(); highlight.run();
            });
            session.observe(selected, value -> { highlight.run(); if (value >= 0 && value < cards.getChildren().size()) scroll.reveal(cards.getChildren().get(value)); });
            session.cleanup(() -> cards.getChildren().clear());
            node = scroll.getJavaFxNode(); nativeComponent = scroll;
        }
        if (node instanceof Region region) {
            region.setMinWidth(0);
            if (props.has("height")) { double height=type.equals("SoundSequence") ? Math.max(region.getPrefHeight(),props.get("height").getAsDouble()) : props.get("height").getAsDouble();region.setPrefHeight(height);region.setMinHeight(height); }
            if (props.has("width")) region.setPrefWidth(props.get("width").getAsDouble());
            else { region.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(region, Priority.ALWAYS); }
        }
        return nativeComponent == null ? Component.CreateFromJavaFxNode(node) : nativeComponent;
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
