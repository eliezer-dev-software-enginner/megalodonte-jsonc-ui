package megalodonte.jsonc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import megalodonte.base.components.Component;
import megalodonte.base.state.State;
import megalodonte.components.SimpleTable;
import megalodonte.props.SimpleTableProps;

import java.util.ArrayList;
import java.util.List;

/** Renders tabular JSONC data with the native Megalodonte table component. */
final class TableNodes {
    private TableNodes() {}

    static Component render(JsonObject props, JsonObject bind, JsonObject events, UiSession session) {
        var tableProps = new SimpleTableProps();
        if (props.has("height")) tableProps.maxHeight(props.get("height").getAsInt());
        var table = new SimpleTable<JsonObject>(tableProps);
        if (props.has("rowHeight")) {
            int rowHeight = props.get("rowHeight").getAsInt();
            if (rowHeight <= 0) throw new UiException("view.props.rowHeight", "Table rowHeight must be positive");
            table.getTableView().setFixedCellSize(rowHeight);
        }
        var rows = State.<List<JsonObject>>of(List.of());
        table.fromData(rows);
        var columns = table.header().columns();
        for (JsonElement spec : props.getAsJsonArray("columns")) {
            var column = spec.getAsJsonObject();
            var field = column.get("field").getAsString();
            if (column.has("kind")) columns.imageColumn(column.get("title").getAsString(), row -> cellValue(row, field),
                    column.has("size") ? column.get("size").getAsInt() : 40);
            else columns.column(column.get("title").getAsString(), row -> cellValue(row, field));
        }
        columns.build();
        if (props.has("horizontalScroll") && props.get("horizontalScroll").getAsBoolean()) table.horizontalScroll();
        if (props.has("pageSize")) table.paginate(props.get("pageSize").getAsInt());
        session.observe(session.states().string(bind.get("items").getAsString()), json -> rows.set(parseRows(json)));
        var selected = session.states().string(bind.get("selected").getAsString());
        if (props.has("multipleSelection") && props.get("multipleSelection").getAsBoolean()) {
            var selectedItems = session.states().string(bind.get("selectedItems").getAsString());
            table.enableMultipleSelection(checked -> {
                var values = new com.google.gson.JsonArray();
                checked.forEach(values::add);
                selectedItems.set(values.toString());
                if (events.has("onSelect")) session.invoke(events.get("onSelect").getAsString());
            });
        }
        table.onItemSelectChange(row -> {
            selected.set(row == null ? "" : row.toString());
            if (events.has("onSelect")) session.invoke(events.get("onSelect").getAsString());
        });
        if (events.has("onDoubleClick")) table.onItemDoubleClick(row -> {
            selected.set(row.toString());
            session.invoke(events.get("onDoubleClick").getAsString());
        });
        return table;
    }

    private static String cellValue(JsonObject row, String field) {
        var cell = row.get(field);
        return cell == null || cell.isJsonNull() ? "" : cell.isJsonPrimitive() ? cell.getAsString() : cell.toString();
    }

    static List<JsonObject> parseRows(String json) {
        try {
            var parsed = JsonParser.parseString(json).getAsJsonArray();
            var rows = new ArrayList<JsonObject>(parsed.size());
            for (var row : parsed) rows.add(row.getAsJsonObject());
            return List.copyOf(rows);
        } catch (RuntimeException error) {
            throw new UiException("view.bind.items", "Table items must be a JSON object array");
        }
    }
}
