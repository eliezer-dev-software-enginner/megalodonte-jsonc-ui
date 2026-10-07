package megalodonte.jsonc;

import com.google.gson.*;
import megalodonte.base.state.State;
import java.math.BigDecimal;
import java.util.*;

/** Per-view state. No automatic persistence. Mutations must occur on the UI thread after rendering. */
public final class StateStore implements AutoCloseable {
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private boolean closed;
    private record Entry(Class<?> type, State<?> state) {}

    StateStore(JsonObject declarations) {
        declarations.asMap().forEach((name, value) -> {
            String path = "$.states." + name;
            Values.name(new JsonPrimitive(name), path);
            JsonObject spec = Values.object(value, path);
            Values.keys(spec, path, "type", "initial");
            String type = Values.string(spec.get("type"), path + ".type");
            JsonElement initial = spec.get("initial");
            switch (type) {
                case "string" -> add(name, String.class, Values.string(initial, path + ".initial"));
                case "boolean" -> add(name, Boolean.class, Values.bool(initial, path + ".initial"));
                case "integer" -> add(name, Integer.class, Values.integer(initial, path + ".initial"));
                case "decimal" -> {
                    try { add(name, BigDecimal.class, new BigDecimal(Values.string(initial, path + ".initial"))); }
                    catch (NumberFormatException e) { throw new UiException(path + ".initial", "Expected a decimal encoded as a string"); }
                }
                default -> throw new UiException(path + ".type", "Unsupported state type: " + type);
            }
        });
    }
    private <T> void add(String name, Class<T> type, T initial) {
        State<T> state = new State<>(initial) {
            @Override public void set(T value) {
                ensureOpen();
                if (!type.isInstance(value)) throw new UiException("$.states." + name, "Expected " + type.getSimpleName());
                super.set(value);
            }
        };
        entries.put(name, new Entry(type, state));
    }
    private void ensureOpen() { if (closed) throw new IllegalStateException("View state is closed"); }
    public Set<String> names() { ensureOpen(); return Collections.unmodifiableSet(entries.keySet()); }
    public Class<?> type(String name) { return entry(name).type(); }
    private Entry entry(String name) {
        ensureOpen();
        Entry entry = entries.get(name);
        if (entry == null) throw new UiException("$.states." + name, "Unknown state");
        return entry;
    }
    @SuppressWarnings("unchecked")
    public <T> State<T> state(String name, Class<T> type) {
        Entry entry = entry(name);
        if (entry.type() != type) throw new UiException("$.states." + name, "Expected " + type.getSimpleName() + ", found " + entry.type().getSimpleName());
        return (State<T>) entry.state();
    }
    public State<String> string(String name) { return state(name, String.class); }
    public Object get(String name) { return entry(name).state().get(); }
    @SuppressWarnings("unchecked")
    public void set(String name, Object value) { ((State<Object>) entry(name).state()).set(value); }
    /** Values are released; callers must also release any State references they retain. */
    @Override public void close() { closed = true; entries.clear(); }
}
