# Megalodonte JSONC UI

A Java 25/JavaFX 25 library that builds native Megalodonte screens from `.view.jsonc` files. A view declares local state, a component tree, bindings, presentation, validation and events. Applications register Java domain actions; JSONC never executes arbitrary code. Every visual control is implemented in `megalodonte-components`. Add and test a missing control there before exposing it through this renderer.

The [codebase guide](docs/CODEBASE.md) maps every production class to its responsibility.

For applications organized as one script per view, use the sibling `megalodonte-script-app` launcher. This library also exposes a low-level API for custom hosts.

## Build and open a view

Building from this checkout requires JDK 25 and the included Gradle wrapper. The build uses JavaFX 25.0.1 and resolves Megalodonte `base` and `components` version `1.0.0-beta` through Gradle. If a sibling `../../libraries` directory exists, the build includes its `core`, `base`, `components` and `theme` source checkouts by default. Set `-PuseLocalSources=false` to resolve dependencies through the configured Maven repositories instead. The artifacts must be available in Maven Local or Maven Central; this repository does not contain them.

To use the library in another Gradle project, configure repositories and dependencies matching this build. The example also uses `DefaultTheme`, supplied by `megalodonte-theme`:

```kotlin
repositories { mavenLocal(); mavenCentral() }
dependencies {
    implementation("megalodonte:megalodonte-jsonc-ui:0.1.0")
    implementation("megalodonte:megalodonte-theme:1.0.0-beta")
}
```

For a JavaFX application with its toolkit initialized, the following loads the view distributed in this checkout. Run rendering and subsequent UI work on the JavaFX Application Thread:

```java
ThemeManager.setTheme(new DefaultTheme());
var screen = UiDefinition.fromPath(Path.of("examples/formatting.view.jsonc"))
        .open(new Actions());
var root = screen.render();
// Pass root to the host; call screen.close() when the view is removed.
```

This example runs from the repository root and requires imports for `Path`, `ThemeManager`, `DefaultTheme`, `UiDefinition` and `Actions`. `UiDefinition.fromResource(owner, path)` reads a classpath resource, while `UiDefinition.parse(jsonc)` reads a string. A definition may open multiple independent `UiSession` instances. `render()` builds each session's component tree once.

Application adapters can call `session.bind(name, modelState, String.class)` for
two-way form state or `session.display(name, readableState, Boolean.class)` for
one-way display state. The session detaches both bindings on `close()`. Keep
database work and navigation in registered Java actions; describe controls,
visibility and user-facing text in JSONC.

## Document contract v1

Each document contains `version: 1`, `id`, `states` and `view`. [The JSON Schema](schema/view.schema.json) supports editor validation and is packaged in the JAR at `megalodonte/jsonc/schema/view.schema.json`. Runtime validation additionally checks state types, references, registered actions, unique component IDs and property names before constructing nodes. Errors identify the field path; syntax errors retain original source line numbers. JSONC accepts `//` and block comments, preserves escaped strings/URLs, and rejects duplicate keys and trailing commas.

### State

| JSONC type | Initial value example | Java type |
| --- | --- | --- |
| `string` | `""` | `String` |
| `boolean` | `false` | `Boolean` |
| `integer` | `0` | 32-bit `Integer` |
| `decimal` | `"0.00"` | `BigDecimal` |

All states require a non-null `initial`. Decimals use strings to preserve precision. `screen.states().state("total", BigDecimal.class)` returns an existing state; `get`, `set` and `string` are shortcuts. States belong to one session and are not persisted automatically. Applications connect services and storage explicitly.

### Components and bindings

| Component | Data contract |
| --- | --- |
| `Column`, `Row`, `Container` | `children`; spacing, padding, size, alignment and fill props. `Container` also accepts a classpath `bgImage` resource. |
| `Scroll` | Exactly one child; size and fill props |
| `Text`, `Button` | Text props and optional `bind.text`; text style; buttons can bind `onClick` |
| `Input`, `PasswordInput`, `TextArea` | String `bind.value`; placeholder/size props; validation. `TextArea` also accepts `props.readOnly` for display-only content. |
| `Image` | Static classpath `props.source` or live string `bind.source`, with optional width and height; rendered by `megalodonte-components`. |
| `Select` | String `bind.items` containing a JSON array of strings or `{label, value}` objects and string `bind.value` for two-way selection; uses the themed Megalodonte control. |
| `DatePicker` | Native themed Megalodonte date picker with an ISO `yyyy-MM-dd` string in `bind.value`; an empty string means no date. |
| `Slot` | Named position for a native child screen mounted with `UiSession.mount(name, component)` before rendering. |
| `ImagePreview` | Local image path from string `bind.source` |
| `ImageSequence`, `SoundSequence`, `DataList` | JSON-array string `bind.items`, integer `bind.selected`, optional `onSelect` |
| `DiffView` | Unified diff string from `bind.text` |
| `Table` | Native Megalodonte `SimpleTable` with declarative `props.columns`, JSON object-array `bind.items`, string `bind.selected`, and optional selection/double-click actions. |
| `BarChart`, `PieChart` | Native Megalodonte charts with `props.title` and string JSON-array `bind.items`. Bars use `{label, value, color?}`; slices use `{label, value}`. |

Layout measures are nonnegative integers resolved through Megalodonte scaling. `alignment` accepts `start`, `center` or `end`. Text style supports `fontSize`, `bold`, `textColor` and `fontFamily`; buttons also accept `bgColor`. `bind.visible` and `bind.disabled` require boolean states. `id` identifies a rendered component for `screen.component(id)`.

`Button` uses JavaFX `onAction` for mouse and keyboard operation. `Input` and `PasswordInput` accept `events.onEnter`; `TextArea` does not, because Enter inserts a line. Action names are registered through `Actions`, copied when a session opens, and invoked with that session.

### Format and validation

See [the formatting example](examples/formatting.view.jsonc). `mask` uses `#` for digits and punctuation, with `store: "digits"`; state values must use that digit-only representation. `uppercase` and `lowercase` normalize input or transform text display. `currency` and `number` format numeric `Text` states without changing their numeric type. A `currency` `Input` accepts a string state containing centavos as digits and displays a localized amount while preserving that storage format. Password inputs and text areas do not support formatters. Mask and currency input reformatting currently move the caret to the end.

Input validation supports `required`, `email`, `minLength`, `maxLength` and `label`. `screen.validate()` returns the first error per field; it neither submits a form nor changes UI automatically. Email validation checks basic shape, not address existence.

### Desktop data controls

`ImageSequence` accepts objects `{label, source, duration}`. Duration must be finite and positive; selection is a zero-based index, with `-1` meaning none. Thumbnail intervals are cumulative. `SoundSequence` accepts `{label, start, duration, volume}` and lays overlapping clips in separate lanes. Both use Megalodonte horizontal scrolling and update selection before invoking `onSelect`.

`DataList` accepts `{label, detail?, badge?}` rows in a JSON string. It virtualizes rows, exposes accessible text and clears an out-of-range selection after data changes. Programmatic item or selection updates do not invoke business actions. `DiffView` virtualizes unified-diff lines, colors headers/hunks/additions/removals, supports line selection, Ctrl+C and a copy context menu. Its content is literal text, never HTML. Both native controls live in `megalodonte-components`; this library only validates data and binds them.

`Table` accepts `props.columns` entries such as `{ "title": "Name", "field": "name" }` and a JSON array of objects in `bind.items`. To display a thumbnail, set `"kind": "image"` on a column and optionally set a positive `"size"` in pixels; the native Megalodonte `SimpleTable` renders the cell. It stores the selected row as JSON in the string state named by `bind.selected`; an empty string means no selection. `events.onSelect` and `events.onDoubleClick` run after that state changes. Optional `height`, `pageSize`, and `horizontalScroll` properties configure the native `SimpleTable`.
For checkbox selection, set `props.multipleSelection` to `true` and bind a string `selectedItems` state. That state contains a JSON array of checked rows.

Arrays are validated before visual replacement. Producers should bound large lists and diffs to avoid unbounded memory retention. These desktop controls currently use their own palette and do not dynamically restyle after a theme change.

## Lifecycle and limits

Create the session before showing a screen. Rendering, post-render state mutations and closing a rendered screen belong on the JavaFX thread; use `Platform.runLater` to deliver asynchronous results. `close()`/`onDestroy()` detach bindings and handlers, clear fields and dispose local state. Closing is idempotent, and a closed session cannot be reused.

The contract supports reactive local state, validation, formatting and data-driven list/media controls. It does not yet provide declarative computed states, global stores, templates or hot reload. Domain persistence and navigation policy belong to the host application.

```sh
./gradlew test publishToMavenLocal
```
