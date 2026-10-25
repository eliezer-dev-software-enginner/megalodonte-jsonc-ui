# InputSelector

`InputSelector` is one editable selection field. Its `bind.text` string state holds the
search query, `bind.items` holds a JSON array of candidate strings or
`{"label":"Name — code","value":"code"}` objects, and `bind.value` holds the
selected value. An application can observe the query and replace the candidates
as the user types. The popup opens automatically when the focused field has
matching candidates; the user need not open a separate select control.

```jsonc
{"type":"InputSelector", "props":{"placeholder":"Name or code"},
 "bind":{"text":"query","items":"matches","value":"productCode"}}
```

Selecting an option writes its `value` to the selection state and its `label`
to the query state. Editing the text clears the prior selection. Consumers should
use the selected value, not the query text, when submitting a form.

When the user clicks a suggestion, JavaFX is still processing the popup list's
selection event. `InputSelector` commits the chosen value and label on the next
JavaFX event turn. This prevents a query observer from replacing the suggestions
while the popup list is finishing the click (which previously caused
`ReadOnlyUnbackedObservableList.subList` / `IndexOutOfBoundsException`). Code
handling a separate button click can read `bind.value` normally; code reacting
to the selection should observe that state rather than reading it inside the
popup's own selection callback.

The regression test `RendererTest.inputSelectorPopupSelectionDoesNotMutateListDuringClick`
opens a real JavaFX popup and selects an item while the query updates its
candidates. Run it with `./gradlew test --tests megalodonte.jsonc.RendererTest`.
