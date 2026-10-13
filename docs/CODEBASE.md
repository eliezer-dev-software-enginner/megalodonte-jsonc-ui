# JSONC UI codebase

The runtime separates parsing, validation, state, rendering and lifecycle. Public integration begins with `UiDefinition.parse`, `fromPath` or `fromResource`; call `open(Actions)` to obtain a `UiSession`. The session implements Megalodonte `ScreenComponent` and must be disposed with its view.

| Source | Responsibility |
| --- | --- |
| [`Jsonc`](../src/main/java/megalodonte/jsonc/Jsonc.java), [`Values`](../src/main/java/megalodonte/jsonc/Values.java) | Comment-aware parsing and typed field access. |
| [`UiDefinition`](../src/main/java/megalodonte/jsonc/UiDefinition.java), [`UiException`](../src/main/java/megalodonte/jsonc/UiException.java) | Reusable document, schema validation and path-specific errors. |
| [`StateStore`](../src/main/java/megalodonte/jsonc/StateStore.java) | Typed, session-local reactive state. |
| [`Actions`](../src/main/java/megalodonte/jsonc/Actions.java) | Explicit Java domain action registry; JSONC never executes code. |
| [`Renderer`](../src/main/java/megalodonte/jsonc/Renderer.java), [`ThemeProps`](../src/main/java/megalodonte/jsonc/ThemeProps.java) | Framework component construction and theme-aware presentation. |
| [`Formats`](../src/main/java/megalodonte/jsonc/Formats.java) | Input/text formatting rules. |
| [`DesktopNodes`](../src/main/java/megalodonte/jsonc/DesktopNodes.java), [`MediaNodes`](../src/main/java/megalodonte/jsonc/MediaNodes.java) | Data adapters for native framework list, diff, image and sound controls. |
| [`UiSession`](../src/main/java/megalodonte/jsonc/UiSession.java) | Render once, validate fields, dispatch actions and release subscriptions/handlers. |

The public [README](../README.md) defines the JSONC v1 data contract. [The schema](../schema/view.schema.json) provides editor diagnostics; the runtime performs additional reference and type checks. Tests under [`src/test`](../src/test) exercise parsing, validation, bindings and lifecycle. Add a missing visual control to `megalodonte-components` first, then add only validation and binding here.
