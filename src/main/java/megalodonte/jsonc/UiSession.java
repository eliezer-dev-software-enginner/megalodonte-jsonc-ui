package megalodonte.jsonc;

import com.google.gson.*;
import javafx.application.Platform;
import megalodonte.base.components.*;
import megalodonte.base.state.*;
import java.util.*;
import java.util.function.Consumer;

/** One screen instance. close()/onDestroy() detach subscriptions and event handlers. */
public final class UiSession implements ScreenComponent, AutoCloseable {
    private final UiDefinition definition;
    private final Actions actions;
    private final StateStore states;
    private final List<Runnable> cleanup = new ArrayList<>();
    private final Map<String, Component> components = new LinkedHashMap<>();
    private Component root;
    private boolean closed;
    UiSession(UiDefinition definition, Actions actions) {
        this.definition = definition; this.actions = actions;
        states = new StateStore(Values.object(definition.document.get("states"), "$.states"));
        try { definition.validate(states, actions); }
        catch (RuntimeException e) { states.close(); throw e; }
    }
    private void ensureOpen() { if (closed) throw new IllegalStateException("View session is closed"); }
    public StateStore states() { ensureOpen(); return states; }
    public String id() { return definition.document.get("id").getAsString(); }
    public Component component(String id) {
        ensureOpen();
        if (root == null) throw new IllegalStateException("Render the view before accessing components");
        Component component = components.get(id);
        if (component == null) throw new IllegalArgumentException("Unknown component: " + id);
        return component;
    }
    public void invoke(String action) { ensureOpen(); actions.invoke(action, this); }
    void remember(String id, Component component) { components.put(id, component); }
    void cleanup(Runnable action) { cleanup.add(action); }
    <T> void observe(ReadableState<T> state, Consumer<T> listener) {
        Subscription subscription = state.observe(listener);
        cleanup(subscription::close);
    }
    @Override public Component render() {
        ensureOpen();
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Render must run on the JavaFX application thread");
        if (root == null) {
            try { root = new Renderer(this).render(definition.document.getAsJsonObject("view")); }
            catch (RuntimeException e) { close(); throw e; }
        }
        return root;
    }
    /** Returns first error per input, keyed by component id (or state name). */
    public Map<String, String> validate() {
        ensureOpen();
        var errors = new LinkedHashMap<String, String>();
        validate(definition.document.getAsJsonObject("view"), errors);
        return Collections.unmodifiableMap(errors);
    }
    private void validate(JsonObject node, Map<String, String> errors) {
        JsonObject rules = Values.optional(node, "validation", "view");
        if (!rules.isEmpty()) {
            String name = node.getAsJsonObject("bind").get("value").getAsString();
            String text = states.string(name).get();
            String label = Values.string(rules, "label", name);
            String error = null;
            if (rules.has("required") && rules.get("required").getAsBoolean() && text.isBlank()) error = label + " é obrigatório.";
            else if (!text.isEmpty()) {
                if (rules.has("email") && rules.get("email").getAsBoolean() && !text.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) error = label + " deve conter um email válido.";
                else if (rules.has("minLength") && text.length() < rules.get("minLength").getAsInt()) error = label + " deve ter pelo menos " + rules.get("minLength").getAsInt() + " caracteres.";
                else if (rules.has("maxLength") && text.length() > rules.get("maxLength").getAsInt()) error = label + " deve ter no máximo " + rules.get("maxLength").getAsInt() + " caracteres.";
            }
            if (error != null) errors.put(Values.string(node, "id", name), error);
        }
        if (node.has("children")) node.getAsJsonArray("children").forEach(child -> validate(child.getAsJsonObject(), errors));
    }
    @Override public void onDestroy() { close(); }
    @Override public void close() {
        if (closed) return;
        if (root != null && !Platform.isFxApplicationThread()) throw new IllegalStateException("Close a rendered view on the JavaFX application thread");
        closed = true;
        for (int i = cleanup.size() - 1; i >= 0; i--) cleanup.get(i).run();
        cleanup.clear(); components.clear(); states.close(); root = null;
    }
}
