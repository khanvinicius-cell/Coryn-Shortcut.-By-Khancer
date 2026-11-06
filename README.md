# Coryn Float (primeira versão)

Aplicativo Android em Kotlin para abrir o Coryn Club em uma janela sobre outros aplicativos.

## Compilação pelo GitHub
1. Crie um repositório vazio no GitHub.
2. Envie todo o conteúdo desta pasta para a raiz do repositório.
3. Abra **Actions** e aguarde o workflow **Build Coryn Float APK**.
4. Baixe o artefato `CorynFloat-debug` da execução concluída. Dentro dele estará `app-debug.apk`.

O workflow também é executado automaticamente em cada push para `main` ou `master`.

## Instalação e uso
1. Instale o APK.
2. Abra Coryn Float e toque em **Ativar botão flutuante**.
3. Conceda a permissão **Exibir sobre outros apps**.
4. O círculo tracejado aparecerá. Arraste-o para posicionar e toque para abrir o Coryn Club.
5. Arraste a barra superior para mover a janela.
6. Arraste a alça no canto inferior direito para redimensionar.
7. Toque fora da janela ou em `—` para ocultá-la.

## Estado desta versão / limitações conhecidas
- O tradutor integrado foi deixado para uma próxima etapa, conforme solicitado.
- O botão de recarregar ocupa o espaço que poderá receber o tradutor posteriormente.
- A navegação voltar/avançar está na barra inferior. O botão Voltar do Android pode não ser encaminhado ao WebView em todos os aparelhos, pois a janela sobreposta precisa manter foco para permitir digitação nos campos do site.
- A janela usa sobreposição Android (`SYSTEM_ALERT_WINDOW`). Alguns fabricantes podem impor restrições adicionais de bateria/execução em segundo plano.
- O redimensionamento é feito pela alça no canto inferior direito; o ícone superior informa como usar a alça.
- Esta é uma primeira base funcional, ainda não testada em seu Realme C71. A compilação do GitHub valida o build, não substitui teste no aparelho.

## Requisitos
- Android 8.0 (API 26) ou superior.
- Internet para carregar Coryn Club.
- Permissão de sobreposição.
