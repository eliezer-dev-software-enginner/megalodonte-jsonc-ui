package megalodonte.jsonc;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import megalodonte.base.theme.ThemeManager;
import megalodonte.theme.DefaultTheme;
import org.junit.jupiter.api.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RendererTest {
    @Test void sessionBindingsSynchronizeAndDetach() {
        var model = megalodonte.base.state.State.of("first");
        var definition = UiDefinition.parse(DefinitionTest.document("""
                {"value":{"type":"string","initial":""}}
                """, """
                {"type":"Text","bind":{"text":"value"}}
                """));
        var session = definition.open(new Actions()).bind("value", model, String.class);
        assertEquals("first", session.states().string("value").get());
        session.states().string("value").set("second");
        assertEquals("second", model.get());
        model.set("third");
        assertEquals("third", session.states().string("value").get());
        var view = session.states().string("value");
        session.close();
        model.set("after close");
        assertEquals("third", view.get());
    }
    @BeforeAll static void startup() throws Exception {
        var started = new CountDownLatch(1);
        Platform.startup(() -> { Platform.setImplicitExit(false); started.countDown(); });
        assertTrue(started.await(15, TimeUnit.SECONDS));
    }
    @AfterAll static void shutdown() { Platform.exit(); }
    static void fx(Runnable runnable) throws Exception {
        var task = new FutureTask<Void>(() -> { runnable.run(); return null; });
        Platform.runLater(task); task.get(15, TimeUnit.SECONDS);
    }
    static TextField field(UiSession session, String id) {
        return (TextField) ((StackPane) session.component(id).getJavaFxNode()).getChildren().getFirst();
    }
    @Test void currencyInputKeepsCentsStateWhileFormattingVisibleText() throws Exception {
        fx(() -> {
            ThemeManager.setTheme(new DefaultTheme());
            var definition = UiDefinition.parse(DefinitionTest.document(
                    "{\"amount\":{\"type\":\"string\",\"initial\":\"1234\"}}",
                    "{\"type\":\"Input\",\"id\":\"amount\",\"bind\":{\"value\":\"amount\"},\"format\":{\"kind\":\"currency\",\"currency\":\"BRL\",\"locale\":\"pt-BR\"}}"));
            try (var session = definition.open(new Actions())) {
                session.render();
                var input = field(session, "amount");
                assertTrue(input.getText().contains("12,34"));
                input.setText("R$ 56,78");
                assertEquals("5678", session.states().string("amount").get());
            }
        });
    }
    @Test void nativeChartsFollowJsoncItems() throws Exception {
        fx(() -> {
            ThemeManager.setTheme(new DefaultTheme());
            var definition = UiDefinition.parse(DefinitionTest.document("""
                    {"bars":{"type":"string","initial":"[]"},"slices":{"type":"string","initial":"[]"}}
                    """, """
                    {"type":"Column","children":[
                      {"type":"BarChart","id":"bars","props":{"title":"Comparison"},"bind":{"items":"bars"}},
                      {"type":"PieChart","id":"slices","props":{"title":"Channels"},"bind":{"items":"slices"}}
                    ]}
                    """));
            try (var session = definition.open(new Actions())) {
                session.render();
                var bars = (javafx.scene.chart.BarChart<?, ?>) session.component("bars").getJavaFxNode();
                var slices = (javafx.scene.chart.PieChart) session.component("slices").getJavaFxNode();
                session.states().string("bars").set("[{\"label\":\"Revenue\",\"value\":12}]");
                session.states().string("slices").set("[{\"label\":\"Online\",\"value\":3}]");
                assertEquals(12.0, ((Number) bars.getData().getFirst().getData().getFirst().getYValue()).doubleValue());
                assertEquals(3.0, slices.getData().getFirst().getPieValue());
            }
        });
    }
    @Test void inputPasswordTextActionsAndDisposal() throws Exception {
        fx(() -> {
            ThemeManager.setTheme(new DefaultTheme());
            var calls = new AtomicInteger();
            var definition = UiDefinition.parse(DefinitionTest.document("""
                    {"email":{"type":"string","initial":"start@example.com"},
                     "password":{"type":"string","initial":"secret"},
                     "visible":{"type":"boolean","initial":true}}
                    """, """
                    {"type":"Column","children":[
                      {"type":"Input","id":"email","bind":{"value":"email"}},
                      {"type":"PasswordInput","id":"password","bind":{"value":"password"},"events":{"onEnter":"go"}},
                      {"type":"Text","id":"echo","bind":{"text":"email","visible":"visible"}},
                      {"type":"Button","id":"go","props":{"text":"Go","defaultButton":true},"events":{"onClick":"go"}}
                    ]}
                    """));
            var session = definition.open(new Actions().register("go", view -> calls.incrementAndGet()));
            session.render();
            var email = field(session, "email");
            var password = field(session, "password");
            var echo = (javafx.scene.text.Text) session.component("echo").getJavaFxNode();
            var button = (Button) session.component("go").getJavaFxNode();
            assertInstanceOf(PasswordField.class, password);
            assertEquals("secret", password.getText());
            email.setText("changed@example.com");
            assertEquals("changed@example.com", session.states().get("email"));
            assertEquals("changed@example.com", echo.getText());
            session.states().string("email").set("external@example.com");
            assertEquals("external@example.com", email.getText());
            session.states().set("visible", false); assertFalse(echo.isVisible()); assertFalse(echo.isManaged());
            button.fire(); password.fireEvent(new javafx.event.ActionEvent()); assertEquals(2, calls.get());
            var retained = session.states().string("email");
            assertSame(session.render(), session.render());
            session.close(); session.close();
            // Global theme changes must not update components after disposal.
            echo.setStyle("-fx-fill: magenta;");
            ThemeManager.setTheme(new DefaultTheme());
            assertEquals("-fx-fill: magenta;", echo.getStyle());
            assertEquals("", password.getText());
            assertNull(button.getOnAction());
            email.setText("after close"); assertEquals("external@example.com", retained.get());
        });
    }
    @Test void allLayoutsIncludeTheirChildrenAndThemeCanChange() throws Exception {
        fx(() -> {
            ThemeManager.setTheme(new DefaultTheme());
            var definition = UiDefinition.parse(DefinitionTest.document("{}", """
                    {"type":"Container","props":{"spacing":10,"padding":8},"children":[
                      {"type":"Row","children":[{"type":"Text","props":{"text":"inside row"}}]},
                      {"type":"Column","children":[{"type":"Text","props":{"text":"inside column"}}]}
                    ]}
                    """));
            try (var session = definition.open(new Actions())) {
                var root = (javafx.scene.layout.VBox) session.render().getJavaFxNode();
                assertEquals(2, root.getChildren().size());
                assertInstanceOf(javafx.scene.layout.HBox.class, root.getChildren().getFirst());
                assertEquals(1, ((javafx.scene.layout.HBox) root.getChildren().getFirst()).getChildren().size());
                ThemeManager.setTheme(new DefaultTheme());
                assertEquals(2, root.getChildren().size());
            }
        });
    }

    @Test void nativeTableUpdatesRowsAndSelection() throws Exception {
        fx(() -> {
            ThemeManager.setTheme(new DefaultTheme());
            var definition = UiDefinition.parse(DefinitionTest.document("""
                    {"rows":{"type":"string","initial":"[{\\"id\\":\\"1\\",\\"name\\":\\"Ada\\"}]"},
                     "selected":{"type":"string","initial":""},"checked":{"type":"string","initial":"[]"}}
                    """, """
                    {"type":"Table","id":"table","props":{"columns":[{"title":"Name","field":"name"},{"title":"Photo","field":"image","kind":"image","size":48}],"multipleSelection":true},
                     "bind":{"items":"rows","selected":"selected","selectedItems":"checked"}}
                    """));
            try (var session = definition.open(new Actions())) {
                session.render();
                var table = (TableView<?>) session.component("table").getJavaFxNode();
                assertEquals(1, table.getItems().size());
                assertFalse(table.getColumns().getLast().isSortable());
                table.getSelectionModel().selectFirst();
                assertTrue(session.states().string("selected").get().contains("Ada"));
                session.states().string("rows").set("[]");
                assertTrue(table.getItems().isEmpty());
            }
        });
    }

    @Test void nativeDatePickerSynchronizesIsoDate() throws Exception {
        fx(() -> {
            ThemeManager.setTheme(new DefaultTheme());
            var definition = UiDefinition.parse(DefinitionTest.document("""
                    {"date":{"type":"string","initial":"2026-10-14"}}
                    """, """
                    {"type":"DatePicker","id":"datePicker","bind":{"value":"date"}}
                    """));
            try (var session = definition.open(new Actions())) {
                session.render();
                var picker = (javafx.scene.control.DatePicker) session.component("datePicker").getJavaFxNode();
                assertEquals(java.time.LocalDate.of(2026, 10, 14), picker.getValue());
                picker.setValue(java.time.LocalDate.of(2026, 10, 15));
                assertEquals("2026-10-15", session.states().string("date").get());
            }
        });
    }

    @Test void slotMountsNativeChildBeforeRendering() throws Exception {
        fx(() -> {
            ThemeManager.setTheme(new DefaultTheme());
            var child = new megalodonte.components.Text("child");
            var definition = UiDefinition.parse(DefinitionTest.document("{}", """
                    {"type":"Column","children":[{"type":"Slot","props":{"name":"content"}}]}
                    """));
            try (var session = definition.open(new Actions()).mount("content", child)) {
                var root = (javafx.scene.layout.VBox) session.render().getJavaFxNode();
                assertSame(child.getJavaFxNode(), root.getChildren().getFirst());
            }
        });
    }
    @Test void imageCanBindSourceAndVisibility() throws Exception {
        fx(() -> {
            ThemeManager.setTheme(new DefaultTheme());
            var definition = UiDefinition.parse(DefinitionTest.document("""
                    {"logo":{"type":"string","initial":""},"visible":{"type":"boolean","initial":false}}
                    """, """
                    {"type":"Image","id":"logoImage","bind":{"source":"logo","visible":"visible"}}
                    """));
            try (var session = definition.open(new Actions())) {
                var image = (javafx.scene.image.ImageView) session.render().getJavaFxNode();
                assertNull(image.getImage());
                assertFalse(image.isVisible());
                session.states().set("visible", true);
                assertTrue(image.isVisible());
            }
        });
    }
    @Test void selectBindsOptionsAndSelectedValue() throws Exception {
        fx(() -> {
            ThemeManager.setTheme(new DefaultTheme());
            var definition = UiDefinition.parse(DefinitionTest.document("""
                    {"choices":{"type":"string","initial":"[\\\"Serial\\\",\\\"TCP\\\"]"},
                     "selected":{"type":"string","initial":"Serial"}}
                    """, """
                    {"type":"Select","id":"connection","bind":{"items":"choices","value":"selected"}}
                    """));
            try (var session = definition.open(new Actions())) {
                @SuppressWarnings("unchecked") var control = (ComboBox<String>) session.render().getJavaFxNode();
                assertEquals(java.util.List.of("Serial", "TCP"), control.getItems());
                assertEquals("Serial", control.getValue());
                control.setValue("TCP");
                assertEquals("TCP", session.states().string("selected").get());
                session.states().string("choices").set("[\"USB\",\"TCP\"]");
                assertEquals(java.util.List.of("USB", "TCP"), control.getItems());
                session.states().string("choices").set("[{\"label\":\"All clients\",\"value\":\"0\"},{\"label\":\"Acme\",\"value\":\"42\"}]");
                assertEquals(java.util.List.of("0", "42"), control.getItems());
                assertEquals("Acme", control.getConverter().toString("42"));
                control.setValue("42");
                assertEquals("42", session.states().string("selected").get());
                control.setValue(null);
                assertEquals("", session.states().string("selected").get());
                session.states().string("selected").set("0");
                assertEquals("0", control.getValue());
                session.states().string("selected").set("");
                assertNull(control.getValue());
            }
        });
    }
    @Test void inputSelectorFiltersWithoutASecondControlAndKeepsLabel() throws Exception {
        fx(() -> {
            ThemeManager.setTheme(new DefaultTheme());
            var definition = UiDefinition.parse(DefinitionTest.document("""
                    {"query":{"type":"string","initial":""},
                     "selected":{"type":"string","initial":""},
                     "choices":{"type":"string","initial":"[]"}}
                    """, """
                    {"type":"InputSelector","props":{"placeholder":"Nome ou código"},
                     "bind":{"text":"query","value":"selected","items":"choices"}}
                    """));
            try (var session = definition.open(new Actions())) {
                @SuppressWarnings("unchecked") var control = (ComboBox<String>) session.render().getJavaFxNode();
                assertTrue(control.isEditable());
                control.getEditor().setText("jaq");
                assertEquals("jaq", session.states().string("query").get());
                session.states().string("choices").set("[{\"value\":\"SKU003\",\"label\":\"Jaqueta — SKU003\"}]");
                assertEquals("jaq", control.getEditor().getText());
                assertEquals("Jaqueta — SKU003", control.getConverter().toString("SKU003"));
                var popupList = new javafx.scene.control.ListView<String>(control.getItems());
                var popupCell = control.getCellFactory().call(popupList);
                popupCell.updateListView(popupList);
                popupCell.updateIndex(0);
                assertEquals("Jaqueta — SKU003", popupCell.getText());
                assertTrue(control.getStyle().contains("-fx-faint-focus-color: transparent"));
                control.setValue("SKU003");
                assertEquals("SKU003", session.states().string("selected").get());
                assertEquals("Jaqueta — SKU003", session.states().string("query").get());
                control.getEditor().setText("vest");
                assertEquals("", session.states().string("selected").get());
                assertEquals("vest", session.states().string("query").get());
                assertEquals("vest", control.getEditor().getText());
            }
        });
    }
    @Test void maskEditingAndCurrencyDisplay() throws Exception {
        fx(() -> {
            ThemeManager.setTheme(new DefaultTheme());
            var definition = UiDefinition.parse(DefinitionTest.document("""
                    {"cpf":{"type":"string","initial":"12345678901"},
                     "total":{"type":"decimal","initial":"1234.56"}}
                    """, """
                    {"type":"Column","children":[
                      {"type":"Input","id":"cpf","bind":{"value":"cpf"},"format":{"kind":"mask","pattern":"###.###.###-##"}},
                      {"type":"Text","id":"total","bind":{"text":"total"},"format":{"kind":"currency","currency":"BRL","locale":"pt-BR"}}
                    ]}
                    """));
            try (var session = definition.open(new Actions())) {
                var scene = new Scene((Parent) session.render().getJavaFxNode());
                scene.getRoot().applyCss(); scene.getRoot().layout();
                var cpf = field(session, "cpf");
                assertEquals("123.456.789-01", cpf.getText());
                cpf.replaceText(0, cpf.getLength(), "98765432100");
                assertEquals("987.654.321-00", cpf.getText());
                assertEquals("98765432100", session.states().get("cpf"));
                // Backspace over punctuation also removes the preceding digit.
                cpf.deleteText(3, 4);
                assertEquals("9865432100", session.states().get("cpf"));
                session.states().string("cpf").set("12345678901");
                assertEquals("123.456.789-01", cpf.getText());
                assertTrue(((javafx.scene.text.Text) session.component("total").getJavaFxNode()).getText().contains("1.234,56"));
            }
        });
    }
    @Test void desktopDataBindingSelectionVirtualizationAndDisposal() throws Exception {
        fx(() -> {
            ThemeManager.setTheme(new DefaultTheme());
            var actions = new AtomicInteger();
            var definition = UiDefinition.parse(DefinitionTest.document("""
                {"items":{"type":"string","initial":"[]"},"selected":{"type":"integer","initial":-1},
                 "diff":{"type":"string","initial":""}}
                """, """
                {"type":"Row","children":[
                  {"type":"DataList","id":"files","props":{"width":300,"height":400,"fillHeight":true},
                   "bind":{"items":"items","selected":"selected"},"events":{"onSelect":"choose"}},
                  {"type":"DiffView","id":"diff","props":{"fillWidth":true,"fillHeight":true},"bind":{"text":"diff"}}]}
                """));
            var session = definition.open(new Actions().register("choose", view -> actions.incrementAndGet()));
            var scene = new Scene((Parent) session.render().getJavaFxNode(), 900, 400);
            var data = (megalodonte.components.DataList) session.component("files");
            @SuppressWarnings("unchecked") var list = (ListView<megalodonte.components.DataList.Item>) data.getJavaFxNode();
            var items = new com.google.gson.JsonArray();
            for (int i=0;i<10000;i++) { var item = new com.google.gson.JsonObject(); item.addProperty("label", "file-"+i); item.addProperty("badge", "M"); items.add(item); }
            session.states().set("items", items.toString());
            scene.getRoot().applyCss(); scene.getRoot().layout();
            assertEquals(10000, data.items().size());
            assertTrue(list.lookupAll(".list-cell").size() < 100, "List must create only viewport cells");
            session.states().set("selected", 15);
            assertEquals(15, data.selectedIndex()); assertEquals(0, actions.get());
            list.getSelectionModel().select(27);
            assertEquals(27, session.states().get("selected")); assertEquals(1, actions.get());
            session.states().set("items", "[]");
            assertEquals(-1, session.states().get("selected")); assertEquals(1, actions.get());
            var diff = (megalodonte.components.DiffView) session.component("diff");
            session.states().set("diff", "--- a/file\n+++ b/file\n@@ -1 +1 @@\n-old\n+new");
            @SuppressWarnings("unchecked") var lines = (ListView<String>) diff.getJavaFxNode();
            lines.getSelectionModel().selectIndices(3, 4);
            assertEquals("-old\n+new", diff.selectedText());
            assertEquals(megalodonte.components.DiffView.Kind.HEADER, megalodonte.components.DiffView.kind("+++ b/file"));
            assertEquals(megalodonte.components.DiffView.Kind.ADDED, megalodonte.components.DiffView.kind("+new"));
            assertEquals(megalodonte.components.DiffView.Kind.REMOVED, megalodonte.components.DiffView.kind("-old"));
            session.close();
            assertTrue(list.getItems().isEmpty()); assertTrue(lines.getItems().isEmpty());
            assertNull(lines.getContextMenu()); assertNull(lines.getOnKeyPressed());
            assertEquals("", diff.text());
        });
    }

}
