# Megalodonte JSONC UI

Biblioteca Java 25/JavaFX 25 que monta telas Megalodonte a partir de arquivos `.view.jsonc`.
O arquivo declara estados, árvore de componentes, bindings, apresentação e eventos.
As ações de negócio são funções Java registradas pelo aplicativo.
Para um cliente contendo apenas scripts por view, use a biblioteca vizinha
`megalodonte-script-app`, que automatiza o registro, validação, navegação e bootstrap.
O exemplo de registro abaixo é a API de baixo nível para integrações personalizadas.

## Usar em um projeto

Este checkout inclui as bibliotecas `core`, `base` e `components` do ecossistema via
Gradle composite builds quando elas existem ao lado. Não é preciso republicar essas
bibliotecas para desenvolver aqui. Fora desse workspace, use os artefatos Maven.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-25.0.4.1'
./gradlew.bat test
./gradlew.bat publishToMavenLocal
```

No projeto consumidor:

```kotlin
repositories { mavenLocal(); mavenCentral() }
dependencies { implementation("megalodonte:megalodonte-jsonc-ui:0.1.0") }
```

O consumidor também configura o JavaFX e o tema Megalodonte, como nos aplicativos
existentes. Para desenvolvimento entre pastas, `includeBuild("../megalodonte-jsonc-ui")`
substitui a dependência Maven pelo código local.

```java
ThemeManager.setTheme(new DefaultTheme());
var actions = new Actions().register("salvar", screen -> {
    var errors = screen.validate();
    if (errors.isEmpty()) salvarCliente(screen.states().string("nome").get());
});
var screen = UiDefinition.fromResource(Main.class, "/views/cadastro.view.jsonc")
    .open(actions);
context.useView(screen);
// Na destruição da tela: screen.close(), ou seu router chama onDestroy().
```

Também é possível carregar um arquivo externo com `UiDefinition.fromPath(path)` ou
uma string com `UiDefinition.parse(jsonc)`. Uma definição pode abrir várias sessões;
cada sessão tem estados independentes. `render()` cria a árvore uma única vez.

## Contrato v1

O documento contém `version: 1`, `id`, `states` e `view`. `schema/view.schema.json`
oferece sugestões e validação estrutural nos editores que suportam JSON Schema.
O schema também acompanha o JAR em `megalodonte/jsonc/schema/view.schema.json`.
O runtime também verifica referências, tipos, ações registradas e IDs únicos antes
de criar nós. Propriedades desconhecidas são rejeitadas. Erros indicam o caminho
do campo; erros de sintaxe preservam as linhas do arquivo original.

JSONC aceita comentários `//` e `/* ... */`, preserva URLs e strings escapadas e
rejeita chaves duplicadas, vírgulas finais e execução de código. Não há `eval`.

### Estados

| type | initial | Tipo Java |
| --- | --- | --- |
| string | `""` | String |
| boolean | `false` | Boolean |
| integer | `0` | Integer, 32 bits |
| decimal | `"0.00"` | BigDecimal |

Todos exigem `initial` não nulo. Decimais são strings para preservar precisão.
`screen.states().state("total", BigDecimal.class)` retorna o `State<T>` existente.
`get`, `set` e `string` são atalhos. Uma atribuição incompatível gera erro.
Estados são locais à sessão e não são gravados no arquivo ou em disco. O aplicativo
pode ligar serviços aos estados e implementar persistência explicitamente.

### Componentes, propriedades e bindings

| Componente | props |
| --- | --- |
| Column, Row, Container | spacing, padding, width, height, maxWidth, fillWidth, fillHeight, alignment, bgColor |
| Text | text, wrapWidth |
| Input, PasswordInput | placeholder, height, width |
| Button | text, height, fillWidth, defaultButton |

Layouts recebem `children`. Medidas são inteiros em unidades do Megalodonte e usam
o `ScaleProvider` existente. `alignment` aceita `start`, `center`, `end`.
`style` aceita `fontSize`, `bold`, `textColor`, `fontFamily` em textos, botões e inputs.

- `bind.value`: estado string, duas vias, em Input/PasswordInput.
- `bind.text`: qualquer estado escalar para Text/Button, uma via.
- `bind.visible`: estado booleano; também controla a participação no layout.
- `bind.disabled`: estado booleano para desabilitar o componente.
- `id`: identifica um componente para `screen.component(id)` após renderizar.

PasswordInput usa um PasswordField nativo com o tema Megalodonte e sem alternância
de visibilidade. Button usa `onAction` do JavaFX para funcionar com mouse e teclado.
`events.onClick` em Button e `events.onEnter` nos inputs referenciam ações registradas.
Ações recebem a sessão da tela, e o registro é copiado ao abrir a sessão.

### Formatação e validação

Veja `examples/formatting.view.jsonc`.

- `mask`: `pattern` com `#` para dígitos e pontuação, `store: "digits"`. A edição
  armazena apenas dígitos e limita a quantidade à máscara; valores programáticos
  devem usar essa representação. Máscaras são suportadas em Input e Text.
- `uppercase`/`lowercase`: Input normaliza a edição; Text transforma a apresentação.
- `currency`: em Text ligado a integer/decimal, `currency: "BRL"`, `locale: "pt-BR"`.
- `number`: em Text ligado a integer/decimal, `locale` opcional.

O estado numérico permanece numérico. Formatação de moeda é de exibição nesta versão.
PasswordInput não recebe formatadores. Máscaras que inserem pontuação reposicionam
o cursor ao fim quando reformulam o texto; edição avançada do cursor pode ser evoluída.

Nos inputs, `validation` aceita `required`, `email`, `minLength`, `maxLength`, `label`.
`screen.validate()` retorna o primeiro erro por campo, sem enviar o formulário ou
alterar a UI automaticamente. As ações escolhem como mostrar esses erros.
Email usa uma verificação básica de formato; não verifica existência do endereço.

### Ciclo de vida

Crie a sessão antes de montar a tela. Renderização, alterações de estado após montar
e fechamento da tela montada devem ocorrer na thread JavaFX. Use `Platform.runLater`
para aplicar resultados de serviços assíncronos.
`close()`/`onDestroy()` liberam bindings, assinaturas de tema e handlers, apagam o texto dos campos e
descartam o registro dos estados. Fechamento é idempotente; a sessão não é reutilizável.
Referências a estados que o aplicativo guardou também devem ser liberadas.

## Escopo inicial

Esta versão cobre telas estáticas com estados reativos, validação e formatação.
Listas dinâmicas, computed states declarativos, estados globais, templates, navegação
declarativa, novos componentes e hot reload são extensões futuras. O aplicativo cliente
vizinho é o exemplo executável e o ponto de partida para evoluir o contrato.
