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
