package megalodonte.jsonc;

import javafx.scene.Node;
import javafx.scene.layout.*;
import megalodonte.base.theme.ThemeInterface;
import megalodonte.props.*;

/** Applies existing Megalodonte props without their permanent global theme subscription. */
final class ThemeProps {
    interface Themed { void applyTo(Node node, ThemeInterface theme); }
    static final class Column extends ColumnProps implements Themed {
        public void applyTo(Node node, ThemeInterface theme) { super.applyTheme(node, this, theme); }
    }
    static final class Row extends RowProps implements Themed {
        public void applyTo(Node node, ThemeInterface theme) { super.applyTheme(node, this, theme); }
    }
    static final class Container extends ContainerProps implements Themed {
        public void applyTo(Node node, ThemeInterface theme) { super.applyTheme(node, this, theme); }
    }
    static final class Text extends TextProps implements Themed {
        public void applyTo(Node node, ThemeInterface theme) { super.applyTheme(node, this, theme); }
    }
    static final class Button extends ButtonProps implements Themed {
        public void applyTo(Node node, ThemeInterface theme) { super.applyTheme(node, this, theme); applyFontStyling(node); }
    }
    static final class Input extends InputProps implements Themed {
        boolean automaticWidth = true;
        public void applyTo(Node node, ThemeInterface theme) {
            super.applyTheme(node, this, theme);
            var container = (StackPane) node;
            applyFontStyling(container.getChildren().getFirst());
            if (automaticWidth) container.setMaxWidth(Double.MAX_VALUE);
            container.setMaxHeight(Region.USE_PREF_SIZE);
        }
    }
}
