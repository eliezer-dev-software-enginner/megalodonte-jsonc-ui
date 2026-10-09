package megalodonte.jsonc;

import com.google.gson.*;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import megalodonte.base.components.Component;
import megalodonte.base.scale.ScaleProvider;
import megalodonte.base.state.*;
import megalodonte.components.Text;
import megalodonte.components.inputs.InputBase;
import megalodonte.props.*;

final class Renderer {
    private final UiSession session;
    Renderer(UiSession session) { this.session = session; }

    Component render(JsonObject spec) {
        String type = spec.get("type").getAsString();
        JsonObject props = Values.optional(spec, "props", "view"), style = Values.optional(spec, "style", "view"),
                bind = Values.optional(spec, "bind", "view"), format = Values.optional(spec, "format", "view"),
                events = Values.optional(spec, "events", "view");
        Component component = switch (type) {
            case "Scroll" -> {
                var scroll = new megalodonte.components.v2.Scroll(render(spec.getAsJsonArray("children").get(0).getAsJsonObject()));
                var region = (Region) scroll.getJavaFxNode();
                region.setMinSize(0,0); region.setMaxSize(Double.MAX_VALUE,Double.MAX_VALUE);
                if (props.has("width")) region.setPrefWidth(props.get("width").getAsInt());
                if (props.has("height")) region.setPrefHeight(props.get("height").getAsInt());
                if (flag(props,"fillWidth")) HBox.setHgrow(region,Priority.ALWAYS);
                if (flag(props,"fillHeight")) VBox.setVgrow(region,Priority.ALWAYS);
                yield scroll;
            }
            case "Column" -> themed(Component.CreateFromJavaFxNode(new VBox()), layout(new ThemeProps.Column(), props));
            case "Row" -> themed(Component.CreateFromJavaFxNode(new HBox()), layout(new ThemeProps.Row(), props));
            case "Container" -> themed(Component.CreateFromJavaFxNode(new VBox()), layout(new ThemeProps.Container(), props));
            case "Text" -> {
                Text text = new Text(bind.has("text") ? "" : Formats.display(Values.string(props, "text", ""), format), null);
                themed(text, textStyle(new ThemeProps.Text(), style));
                var node = (javafx.scene.text.Text) text.getJavaFxNode();
                if (props.has("wrapWidth")) node.setWrappingWidth(ScaleProvider.scale(props.get("wrapWidth").getAsInt()));
                if (bind.has("text")) observeText(bind.get("text").getAsString(), format, node::setText);
                yield text;
            }
            case "Button" -> {
                ButtonProps bp = textStyle(new ThemeProps.Button(), style);
                if (props.has("height")) bp.height(props.get("height").getAsInt());
                if (flag(props, "fillWidth")) bp.fillWidth();
                var button = new megalodonte.components.Button(Values.string(props, "text", ""), null);
                themed(button, bp);
                var node = (javafx.scene.control.Button) button.getJavaFxNode();
                if (bind.has("text")) observeText(bind.get("text").getAsString(), format, node::setText);
                node.setDefaultButton(flag(props, "defaultButton"));
                if (events.has("onClick")) node.setOnAction(event -> session.invoke(events.get("onClick").getAsString()));
                session.cleanup(() -> node.setOnAction(null));
                yield button;
            }
            case "TextArea", "ImagePreview", "ImageSequence" -> MediaNodes.render(type, props, bind, events, session);
            case "Input", "PasswordInput" -> {
                var themedInput = new ThemeProps.Input();
                themedInput.automaticWidth = !props.has("width");
                InputProps ip = textStyle(themedInput, style);
                if (props.has("height")) ip.height(props.get("height").getAsInt());
                if (props.has("width")) ip.width(props.get("width").getAsInt());
                yield new BoundInput(type.equals("PasswordInput"), ip, Values.string(props, "placeholder", ""),
                        session.states().string(bind.get("value").getAsString()), format, events, session);
            }
            default -> throw new UiException("view.type", "Unknown component: " + type);
        };
        Node node = component.getJavaFxNode();
        if (spec.has("id")) {
            String id = spec.get("id").getAsString();
            node.setId(id); session.remember(id, component);
        }
        if (java.util.Set.of("Column", "Row", "Container").contains(type)) {
            if (spec.has("children")) for (JsonElement child : spec.getAsJsonArray("children")) ((Pane) node).getChildren().add(render(child.getAsJsonObject()).getJavaFxNode());
            if (props.has("alignment")) {
                Pos alignment = switch (props.get("alignment").getAsString()) { case "center" -> Pos.CENTER; case "end" -> Pos.BOTTOM_RIGHT; default -> Pos.TOP_LEFT; };
                if (node instanceof VBox box) box.setAlignment(alignment);
                if (node instanceof HBox box) box.setAlignment(alignment);
            }
            if (flag(props, "fillWidth") && node instanceof Region region) { region.setMinWidth(0); region.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(node, Priority.ALWAYS); }
            if (flag(props, "fillHeight") && node instanceof Region region) { region.setMaxHeight(Double.MAX_VALUE); VBox.setVgrow(node, Priority.ALWAYS); }
            if (props.has("bgColor")) megalodonte.styles.util.StyleUtils.applyBackgroundStyling(node, megalodonte.base.theme.ThemeManager.theme(), props.get("bgColor").getAsString());
        }
        if (bind.has("visible")) session.observe(session.states().state(bind.get("visible").getAsString(), Boolean.class), value -> { node.setVisible(value); node.setManaged(value); });
        if (bind.has("disabled")) session.observe(session.states().state(bind.get("disabled").getAsString(), Boolean.class), node::setDisable);
        return component;
    }
    private static boolean flag(JsonObject props, String name) { return props.has(name) && props.get(name).getAsBoolean(); }
    private <C extends Component> C themed(C component, Props props) {
        component.props = props;
        session.observe(megalodonte.base.theme.ThemeManager.state(), theme -> {
            if (theme != null) ((ThemeProps.Themed) props).applyTo(component.getJavaFxNode(), theme);
        });
        return component;
    }
    private <T extends LayoutProps<T>> T layout(T p, JsonObject spec) {
        if (spec.has("spacing")) p.spacingOf(spec.get("spacing").getAsInt());
        if (spec.has("padding")) p.paddingAll(spec.get("padding").getAsInt());
        if (spec.has("width")) p.width(spec.get("width").getAsInt());
        if (spec.has("height")) p.height(spec.get("height").getAsInt());
        if (spec.has("maxWidth")) p.maxWidth(spec.get("maxWidth").getAsInt());
        return p;
    }
    private <T extends TextComponentProps<T>> T textStyle(T p, JsonObject spec) {
        if (spec.has("fontSize")) p.fontSize(spec.get("fontSize").getAsInt());
        if (flag(spec, "bold")) p.bold();
        if (spec.has("textColor")) p.textColor(spec.get("textColor").getAsString());
        if (spec.has("fontFamily")) p.fontFamily(spec.get("fontFamily").getAsString());
        return p;
    }
    @SuppressWarnings("unchecked")
    private void observeText(String name, JsonObject format, java.util.function.Consumer<String> display) {
        State<Object> state = (State<Object>) session.states().state(name, session.states().type(name));
        session.observe(state, value -> display.accept(Formats.display(value, format)));
    }

    /** Uses the Megalodonte input theme and a native password field with disposable bindings. */
    private static final class BoundInput extends InputBase {
        private boolean syncing;
        BoundInput(boolean password, InputProps props, String placeholder, State<String> state,
                   JsonObject format, JsonObject events, UiSession session) {
            super(password ? new PasswordField() : new TextField(), null);
            this.props = props;
            session.observe(megalodonte.base.theme.ThemeManager.state(), theme -> {
                if (theme != null) ((ThemeProps.Themed) props).applyTo(node, theme);
            });
            field.setPromptText(placeholder);
            var stylesheet = getClass().getResource("/text-field.css");
            if (stylesheet != null) field.getStylesheets().add(stylesheet.toExternalForm());
            if (!format.isEmpty()) field.setTextFormatter(new TextFormatter<String>(change -> {
                if (syncing || !change.isContentChange()) return change;
                String candidate = change.getControlNewText();
                if (Values.string(format, "kind", "").equals("mask") && change.isDeleted()) {
                    String oldDigits = Formats.storage(change.getControlText(), format);
                    if (Formats.storage(candidate, format).equals(oldDigits) && !oldDigits.isEmpty()) {
                        int before = Formats.storage(change.getControlText().substring(0, change.getRangeStart()), format).length();
                        int digit = Math.max(0, before - 1);
                        candidate = oldDigits.substring(0, digit) + oldDigits.substring(digit + 1);
                    }
                }
                String display = Formats.display(candidate, format);
                if (!display.equals(change.getControlNewText())) {
                    change.setRange(0, change.getControlText().length()); change.setText(display);
                    change.setCaretPosition(display.length()); change.setAnchor(display.length());
                }
                return change;
            }));
            session.observe(state, value -> {
                if (syncing) return;
                syncing = true;
                try { field.setText(Formats.display(value, format)); } finally { syncing = false; }
            });
            javafx.beans.value.ChangeListener<String> listener = (obs, old, value) -> {
                if (syncing) return;
                syncing = true;
                try { state.set(Formats.storage(value, format)); } finally { syncing = false; }
            };
            field.textProperty().addListener(listener);
            if (events.has("onEnter")) ((TextField) field).setOnAction(event -> session.invoke(events.get("onEnter").getAsString()));
            session.cleanup(() -> {
                field.textProperty().removeListener(listener);
                ((TextField) field).setOnAction(null);
                field.setTextFormatter(null);
                field.clear();
            });
        }
    }
}
