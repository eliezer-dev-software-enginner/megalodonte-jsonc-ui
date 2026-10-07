package megalodonte.jsonc;

import java.util.*;
import java.util.function.Consumer;

/** Explicit action registry; documents never execute Java/Lua or arbitrary expressions. */
public final class Actions {
    private final Map<String, Consumer<UiSession>> handlers = new LinkedHashMap<>();
    public Actions register(String name, Consumer<UiSession> handler) {
        Objects.requireNonNull(name); Objects.requireNonNull(handler);
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("Invalid action name: " + name);
        if (handlers.putIfAbsent(name, handler) != null) throw new IllegalArgumentException("Duplicate action: " + name);
        return this;
    }
    boolean contains(String name) { return handlers.containsKey(name); }
    Actions snapshot() { var copy = new Actions(); copy.handlers.putAll(handlers); return copy; }
    void invoke(String name, UiSession session) {
        Consumer<UiSession> handler = handlers.get(name);
        if (handler == null) throw new UiException("$.events", "Unregistered action: " + name);
        handler.accept(session);
    }
}
