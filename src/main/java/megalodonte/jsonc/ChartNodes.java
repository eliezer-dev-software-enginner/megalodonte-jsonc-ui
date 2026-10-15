package megalodonte.jsonc;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import javafx.scene.layout.Region;
import megalodonte.base.components.Component;
import megalodonte.components.BarChartView;
import megalodonte.components.PieChartView;

import java.util.ArrayList;
import java.util.List;

/** JSONC bindings for charts implemented by megalodonte-components. */
final class ChartNodes {
    private ChartNodes() {}

    static Component render(String type, JsonObject props, JsonObject bind, UiSession session) {
        String title = Values.string(props, "title", "");
        String state = bind.get("items").getAsString();
        Component component;
        if (type.equals("BarChart")) {
            var chart = new BarChartView(title);
            session.observe(session.states().string(state), json -> chart.setBars(bars(json)));
            component = chart;
        } else {
            var chart = new PieChartView(title);
            session.observe(session.states().string(state), json -> chart.setSlices(slices(json)));
            component = chart;
        }
        var region = (Region) component.getJavaFxNode();
        if (props.has("width")) region.setPrefWidth(props.get("width").getAsInt());
        if (props.has("height")) region.setPrefHeight(props.get("height").getAsInt());
        return component;
    }

    static List<BarChartView.Bar> bars(String json) {
        try {
            var array = JsonParser.parseString(json).getAsJsonArray();
            var result = new ArrayList<BarChartView.Bar>(array.size());
            for (var item : array) {
                var object = item.getAsJsonObject();
                Values.keys(object, "BarChart.items", "label", "value", "color");
                result.add(new BarChartView.Bar(Values.string(object.get("label"), "BarChart.items.label"),
                        object.get("value").getAsDouble(), Values.string(object, "color", "")));
            }
            return List.copyOf(result);
        } catch (RuntimeException error) {
            throw new UiException("BarChart.items", "Expected an array of {label, value, color?}", error);
        }
    }

    static List<PieChartView.Slice> slices(String json) {
        try {
            var array = JsonParser.parseString(json).getAsJsonArray();
            var result = new ArrayList<PieChartView.Slice>(array.size());
            for (var item : array) {
                var object = item.getAsJsonObject();
                Values.keys(object, "PieChart.items", "label", "value");
                result.add(new PieChartView.Slice(Values.string(object.get("label"), "PieChart.items.label"),
                        object.get("value").getAsDouble()));
            }
            return List.copyOf(result);
        } catch (RuntimeException error) {
            throw new UiException("PieChart.items", "Expected an array of {label, value}", error);
        }
    }
}
