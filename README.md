# Extrator Valor Android v0.2 — Calibração ampliada

Projeto Android para automatizar, em etapas, a obtenção das páginas 1, 2 e 3 do Valor Econômico no PressReader usando apenas uma sessão que o usuário já esteja autorizado a acessar.

## O que a v0.2 faz
- abre `https://valoreconomico.pressreader.com/valor-economico` dentro do app;
- mantém a sessão normal do WebView no aparelho;
- identifica a URL da edição/página atual;
- registra recursos do WebView via `shouldInterceptRequest` e `onLoadResource`;
- observa também requisições feitas por Service Worker;
- coleta o inventário de recursos que o JavaScript já carregou usando `performance.getEntriesByType('resource')`;
- inclui recursos dos domínios do PressReader e `prcdn.co`;
- preserva `issue`, `page`, `pageNumber(s)`, `file` e `scale` quando não são credenciais;
- não exporta cookies, senhas ou cabeçalhos de autenticação;
- oculta tokens, tickets, assinaturas e outros parâmetros sensíveis;
- exporta um TXT de diagnóstico para `Downloads/ExtratorValor/`.

## Resultado obtido na v0.1
A calibração anterior confirmou que a navegação usa URLs como `/20260828/page/1`, `/page/2` e `/page/3` e chamou o endpoint `ingress.pressreader.com/services/pagesMetadata/` com o identificador da edição. A v0.2 amplia a observação para localizar os recursos de imagem/CDN correspondentes às três páginas.

## Teste de calibração v0.2
1. Instale o APK da Release v0.2.0.
2. Abra o app e faça login no PressReader normalmente, se necessário.
3. Abra a edição do Valor Econômico.
4. Toque **INICIAR**.
5. Com a página 1 aberta, aguarde alguns segundos e toque **P1**.
6. Vá para a página 2, aguarde carregar e toque **P2**.
7. Vá para a página 3, aguarde carregar e toque **P3**.
8. Toque **EXPORTAR**.
9. Envie o TXT gerado no chat para análise.

## Próxima etapa
Com os recursos de imagem identificados, a próxima versão será preparada para obter automaticamente as três páginas autorizadas, gerar um PDF e encaminhá-lo por e-mail conforme a configuração do usuário.

## Segurança e acesso
O projeto não tenta remover paywall, burlar assinatura, contornar autenticação ou ignorar restrições de acesso. A automação deve operar somente sobre conteúdo que a sessão do usuário esteja autorizada a visualizar.

## Envio por e-mail
O arquivo `apps-script-Code.gs` contém a estrutura de relay para a etapa final de envio pelo Gmail. O destinatário e a chave privada ficam nas Script Properties, e não no APK.
