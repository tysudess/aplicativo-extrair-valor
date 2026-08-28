# Extrator Valor Android v0.1 — Calibração

Primeira etapa do projeto para automatizar a obtenção das páginas 1, 2 e 3 do Valor Econômico no PressReader usando apenas uma sessão que o usuário já esteja autorizado a acessar.

## O que esta versão faz
- abre `https://valoreconomico.pressreader.com/valor-economico` dentro do app;
- mantém a sessão normal do WebView no aparelho;
- registra URLs de recursos/requisições candidatas enquanto a edição é visualizada;
- não exporta cookies, senhas ou cabeçalhos de autenticação;
- oculta parâmetros comuns de token/chave/sessão;
- permite marcar os momentos em que as páginas 1, 2 e 3 estão abertas;
- exporta um TXT de diagnóstico para `Downloads/ExtratorValor/`.

## Teste de calibração
1. Instale o APK/build de teste.
2. Abra o app e faça login no PressReader normalmente, se necessário.
3. Abra a edição do Valor Econômico.
4. Toque **INICIAR**.
5. Com a página 1 aberta, toque **P1**.
6. Vá para a página 2 e toque **P2**.
7. Vá para a página 3 e toque **P3**.
8. Toque **EXPORTAR**.
9. Envie o TXT gerado no chat para análise.

## Build e Release
O workflow `.github/workflows/android-build-release.yml` compila o APK no GitHub Actions e publica automaticamente o APK em **GitHub Releases** usando a versão definida em `app/build.gradle`.

## Próxima versão
A partir do diagnóstico, a v0.2 será fechada com o método específico de obtenção das três páginas. Só depois disso será ativado o fluxo diário em segundo plano + PDF + envio automático por e-mail.

## Segurança e acesso
O projeto não tenta remover paywall, burlar assinatura, contornar autenticação ou ignorar restrições territoriais. A automação deve operar somente sobre conteúdo que a sessão do usuário esteja autorizada a visualizar.

## Envio por e-mail (estrutura já preparada)
O arquivo `apps-script-Code.gs` contém o relay que será usado na etapa final para receber o PDF do Android e enviá-lo pelo Gmail. O destinatário e uma chave privada ficam nas **Script Properties**, e não são gravados no APK/projeto.
