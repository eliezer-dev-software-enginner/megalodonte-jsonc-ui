package megalodonte.jsonc.components;

import javafx.beans.value.ChangeListener;
import javafx.scene.control.*;
import megalodonte.base.components.Component;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

/** Virtualized, keyboard-accessible data list. Platform controls stay inside the framework. */
public final class DataList extends Component implements AutoCloseable {
    public record Item(String label, String detail, String badge) {
        public Item { Objects.requireNonNull(label); Objects.requireNonNull(detail); Objects.requireNonNull(badge); }
    }
    private final ListView<Item> list;
    private final ChangeListener<Number> selectionListener;
    private IntConsumer onSelect = index -> {};
    private boolean updating;
    public DataList() {
        super(new ListView<Item>());
        @SuppressWarnings("unchecked") var control = (ListView<Item>) node;
        list = control;
        list.setFixedCellSize(54);
        list.setPlaceholder(new Label("Nenhum item"));
        list.setStyle("-fx-background-color: #ffffff; -fx-control-inner-background: #ffffff; -fx-font-size: 13px;");
        list.setCellFactory(view -> new ListCell<>() {
            @Override protected void updateItem(Item item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : (item.badge().isBlank() ? "" : item.badge() + "  ") + item.label() + (item.detail().isBlank() ? "" : "\n" + item.detail()));
                setAccessibleText(getText());
                setGraphic(null);
            }
        });
        selectionListener = (observable, previous, next) -> { if (!updating) onSelect.accept(next.intValue()); };
        list.getSelectionModel().selectedIndexProperty().addListener(selectionListener);
    }
    /** Atomic replacement preserves the current index when possible and emits no business action. */
    public void setItems(List<Item> items) {
        var checked = List.copyOf(items);
        int selected = selectedIndex();
        updating = true;
        try { list.getItems().setAll(checked); list.getSelectionModel().clearSelection(); if (selected >= 0 && selected < checked.size()) list.getSelectionModel().select(selected); }
        finally { updating = false; }
    }
    public void select(int index) {
        updating = true;
        try { if (index < 0 || index >= list.getItems().size()) list.getSelectionModel().clearSelection(); else { list.getSelectionModel().select(index); list.scrollTo(index); } }
        finally { updating = false; }
    }
    public int selectedIndex() { return list.getSelectionModel().getSelectedIndex(); }
    public List<Item> items() { return List.copyOf(list.getItems()); }
    public void onSelect(IntConsumer callback) { onSelect = Objects.requireNonNull(callback); }
    @Override public void close() {
        list.getSelectionModel().selectedIndexProperty().removeListener(selectionListener);
        onSelect = index -> {};
        list.getItems().clear();
        list.setCellFactory(null);
    }
}
