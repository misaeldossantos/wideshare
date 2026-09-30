# WideShare

## Regras de código

- **Nenhum arquivo pode ter mais de 150 linhas** (vale para `src/main` e `src/test`). Passou disso, divida em arquivos com uma responsabilidade cada, no mesmo pacote, em vez de deixar tudo num arquivo só.
- Um arquivo por componente ou classe principal; funções auxiliares pequenas podem ficar junto de quem as usa.
- Ao dividir, mantenha só os imports usados e prefira `internal` a `private` para o que outros arquivos do módulo precisam.
- Rode `./gradlew test` depois de mexer em `core` ou `platform`.

## Interface (Compose Desktop)

- Cores só via `Palette` (`ui/Palette.kt`), que troca entre tema escuro e claro. Não use `Color(...)` solto nos componentes.
- Cards e botões não levam borda: o contraste vem do fundo. Sem gradientes e sem ícones dentro de quadrados arredondados.
- Cor de destaque única (azul); vermelho só para ações destrutivas (`PrimaryButton(danger = true)`).

