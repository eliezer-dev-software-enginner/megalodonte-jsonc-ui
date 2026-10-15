package megalodonte.jsonc;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class DefinitionTest {
    @Test void shippedExampleAndSchemaAreValidDocuments() throws Exception {
        try (var session = UiDefinition.fromPath(java.nio.file.Path.of("examples/formatting.view.jsonc")).open(new Actions())) {
            assertEquals(new BigDecimal("1234.56"), session.states().get("total"));
        }
        var schema = Jsonc.parse(java.nio.file.Files.readString(java.nio.file.Path.of("schema/view.schema.json")));
        assertEquals("Megalodonte JSONC UI v1", schema.get("title").getAsString());
    }
    static String document(String states, String view) {
        return "{\"version\":1,\"id\":\"test\",\"states\":" + states + ",\"view\":" + view + "}";
    }
    static UiSession open(String states, String view) { return UiDefinition.parse(document(states, view)).open(new Actions()); }

    @Test void commentsPreserveStringsAndSourceCoordinates() {
        String jsonc = """
                {
                  // a line comment
                  "version":1, /* a block\n comment */ "id":"test",
                  "states":{"url":{"type":"string","initial":"https://example.com/a/*literal*/"}},
                  "view":{"type":"Text","bind":{"text":"url"}}
                }
                """;
        try (var session = UiDefinition.parse(jsonc).open(new Actions())) {
            assertEquals("https://example.com/a/*literal*/", session.states().get("url"));
        }
        assertThrows(UiException.class, () -> Jsonc.parse("{/*"));
        assertThrows(UiException.class, () -> Jsonc.parse("{\n // comment\n \"x\": }"));
        String literal = "quoted \"value\" // not a comment \\ end";
        String encoded = new com.google.gson.JsonPrimitive(literal).toString();
        assertEquals(literal, Jsonc.parse("{\"x\":" + encoded + "}").get("x").getAsString());
    }
    @Test void rejectsNonJsonSyntaxDuplicatesAndTrailingContent() {
        for (String source : new String[]{"{\"x\":1,}", "{\"x\":[1,]}", "{\"x\":1,\"x\":2}", "{} {}", "{x:1}", "{\"x\":NaN}"})
            assertThrows(UiException.class, () -> Jsonc.parse(source), source);
    }
    @Test void statesAreTypedIndependentAndNotMutableAfterClose() {
        String states = "{\"amount\":{\"type\":\"decimal\",\"initial\":\"0.10\"},\"count\":{\"type\":\"integer\",\"initial\":0}}";
        var definition = UiDefinition.parse(document(states, "{\"type\":\"Text\"}"));
        try (var a = definition.open(new Actions()); var b = definition.open(new Actions())) {
            a.states().set("amount", new BigDecimal("0.20"));
            assertEquals(new BigDecimal("0.10"), b.states().get("amount"));
            assertThrows(UiException.class, () -> a.states().set("count", "wrong"));
            assertThrows(UiException.class, () -> a.states().string("count"));
            var retained = a.states().state("count", Integer.class);
            a.close();
            assertThrows(IllegalStateException.class, () -> retained.set(1));
        }
        assertThrows(UiException.class, () -> open("{\"n\":{\"type\":\"integer\",\"initial\":1.5}}", "{\"type\":\"Text\"}"));
    }
    @Test void rejectsInvalidReferencesAndIgnoredConfigurationBeforeRendering() {
        String states = "{\"email\":{\"type\":\"string\",\"initial\":\"\"}}";
        for (String view : new String[]{
                "{\"type\":\"Input\",\"bind\":{\"value\":\"missing\"}}",
                "{\"type\":\"Input\"}",
                "{\"type\":\"Button\",\"props\":{\"text\":\"go\"},\"events\":{\"onClick\":\"missing\"}}",
                "{\"type\":\"Text\",\"props\":{\"typo\":1}}",
                "{\"type\":\"Text\",\"bind\":{\"visible\":\"email\"}}",
                "{\"type\":\"Text\",\"props\":{\"text\":\"x\"},\"bind\":{\"text\":\"email\"}}",
                "{\"type\":\"Input\",\"bind\":{\"value\":\"email\"},\"format\":{\"kind\":\"currency\",\"currency\":\"BRL\"}}",
                "{\"type\":\"Column\",\"children\":[{\"type\":\"Text\",\"id\":\"same\"},{\"type\":\"Text\",\"id\":\"same\"}]}"})
            assertThrows(UiException.class, () -> open(states, view), view);
    }
    @Test void validatesRequiredEmailAndLength() {
        String states = "{\"email\":{\"type\":\"string\",\"initial\":\"\"}}";
        try (var session = open(states, "{\"type\":\"Input\",\"bind\":{\"value\":\"email\"},\"validation\":{\"required\":true,\"email\":true,\"maxLength\":30}}")) {
            assertFalse(session.validate().isEmpty());
            session.states().string("email").set("bad"); assertFalse(session.validate().isEmpty());
            session.states().string("email").set("a@b.com"); assertTrue(session.validate().isEmpty());
            session.states().string("email").set("a".repeat(30) + "@b.com"); assertFalse(session.validate().isEmpty());
        }
    }
    @Test void formatsMasksAndCurrencyWithoutChangingNumericState() {
        var mask = Jsonc.parse("{\"kind\":\"mask\",\"pattern\":\"###.###.###-##\",\"store\":\"digits\"}");
        assertEquals("123.456.789-01", Formats.display("12345678901", mask));
        assertEquals("12345678901", Formats.storage("123.456.789-01999", mask));
        assertEquals("123", Formats.display("123", mask));
        var currency = Jsonc.parse("{\"kind\":\"currency\",\"currency\":\"BRL\",\"locale\":\"pt-BR\"}");
        assertTrue(Formats.display(new BigDecimal("1234.56"), currency).contains("1.234,56"));
    }
    @Test void desktopDefinitionsRejectWrongBindingsAndInvalidDataBeforeRendering() {
        String states = """
            {"items":{"type":"string","initial":"[]"},"index":{"type":"integer","initial":-1}}
            """;
        for (String view : new String[]{
            "{\"type\":\"DataList\",\"bind\":{\"items\":\"items\"}}",
            "{\"type\":\"DataList\",\"bind\":{\"items\":\"index\",\"selected\":\"index\"}}",
            "{\"type\":\"DiffView\",\"bind\":{\"text\":\"index\"}}",
            "{\"type\":\"DiffView\"}"}) assertThrows(UiException.class, () -> open(states, view));
        for (String items : new String[]{"{}", "[null]", "[{}]", "[{\"label\":5}]", "[{\"label\":\"a\",\"detail\":null}]", "[{\"label\":\"a\",\"extra\":true}]"})
            assertThrows(UiException.class, () -> DesktopNodes.parseItems(items));
        assertEquals("path/with spaces", DesktopNodes.parseItems("[{\"label\":\"path/with spaces\"}]").getFirst().label());
    }

    @Test void tableRequiresColumnsAndJsonObjectRows() {
        String states = "{\"rows\":{\"type\":\"string\",\"initial\":\"[]\"},\"selected\":{\"type\":\"string\",\"initial\":\"\"}}";
        String table = "{\"type\":\"Table\",\"props\":{\"columns\":[{\"title\":\"Name\",\"field\":\"name\"}]},\"bind\":{\"items\":\"rows\",\"selected\":\"selected\"}}";
        try (var session = open(states, table)) { assertEquals("[]", session.states().string("rows").get()); }
        assertThrows(UiException.class, () -> TableNodes.parseRows("[1]"));
        assertThrows(UiException.class, () -> TableNodes.parseRows("{}"));
        assertEquals("Ada", TableNodes.parseRows("[{\"name\":\"Ada\"}]").getFirst().get("name").getAsString());
        String multi = table.replace("\"columns\":", "\"multipleSelection\":true,\"columns\":");
        assertThrows(UiException.class, () -> open(states, multi));
    }
    @Test void chartDefinitionsRequireTypedJsonItems() {
        String states = "{\"bars\":{\"type\":\"string\",\"initial\":\"[{\\\"label\\\":\\\"Revenue\\\",\\\"value\\\":12,\\\"color\\\":\\\"#10b981\\\"}]\"}}";
        try (var session = open(states, "{\"type\":\"BarChart\",\"props\":{\"title\":\"Comparison\"},\"bind\":{\"items\":\"bars\"}}")) {
            assertEquals(12, ChartNodes.bars(session.states().string("bars").get()).getFirst().value());
        }
        assertEquals(2, ChartNodes.slices("[{\"label\":\"Online\",\"value\":2}]").getFirst().value());
        assertThrows(UiException.class, () -> ChartNodes.bars("[{\"label\":\"Bad\",\"value\":\"NaN\"}]"));
        assertThrows(UiException.class, () -> open(states, "{\"type\":\"PieChart\",\"props\":{\"title\":\"Channels\"}}"));
    }
    @Test void datePickerRequiresIsoStringState() {
        try (var session = open("{\"date\":{\"type\":\"string\",\"initial\":\"2026-10-14\"}}",
                "{\"type\":\"DatePicker\",\"bind\":{\"value\":\"date\"}}")) {
            assertEquals("2026-10-14", session.states().string("date").get());
        }
        assertThrows(UiException.class, () -> open("{\"date\":{\"type\":\"string\",\"initial\":\"14/10/2026\"}}",
                "{\"type\":\"DatePicker\",\"bind\":{\"value\":\"date\"}}"));
    }

}
