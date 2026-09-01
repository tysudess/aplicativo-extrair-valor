from pathlib import Path

helper_path = Path('app/src/main/java/br/com/extratorvalor/AutoPdfHelper.java')
auto_path = Path('app/src/main/java/br/com/extratorvalor/AutoRunService.java')
main_path = Path('app/src/main/java/br/com/extratorvalor/MainActivity.java')
helper = helper_path.read_text(encoding='utf-8')
auto = auto_path.read_text(encoding='utf-8')
main = main_path.read_text(encoding='utf-8')

def rep(text, old, new, label):
    if old not in text:
        raise SystemExit(f'Patch v0.7.3 não encontrou trecho: {label}')
    return text.replace(old, new, 1)

# AutoPdfHelper: rejeita miniaturas/tiles quadrados e promove URLs recortadas para página inteira.
helper = rep(helper,
    'private static final int MIN_ACCEPTABLE_WIDTH = 1800;\n',
    'private static final int MIN_ACCEPTABLE_WIDTH = 1800;\n    private static final double MIN_PAGE_ASPECT = 1.20;\n',
    'constante proporção')

helper = rep(helper,
    'if (bm == null) throw new IllegalStateException("imagem inválida");\n\n                long pixels = (long) bm.getWidth() * (long) bm.getHeight();',
    'if (bm == null) throw new IllegalStateException("imagem inválida");\n\n                if (bm.getHeight() < Math.round(bm.getWidth() * MIN_PAGE_ASPECT)) {\n                    String shape = bm.getWidth() + "x" + bm.getHeight();\n                    if (!bm.isRecycled()) bm.recycle();\n                    throw new IllegalStateException("miniatura/recorte rejeitado: " + shape);\n                }\n\n                long pixels = (long) bm.getWidth() * (long) bm.getHeight();',
    'rejeita recorte quadrado')

marker = '    private static List<String> candidates(String rawUrl) {'
insert = '''    static Bitmap downloadBestBitmap(List<String> rawUrls, String userAgent, String referer) throws Exception {\n        if (rawUrls == null || rawUrls.isEmpty()) {\n            throw new IllegalStateException("nenhuma URL de página disponível");\n        }\n        Exception last = null;\n        int tried = 0;\n        for (String raw : rawUrls) {\n            if (raw == null || raw.trim().isEmpty()) continue;\n            tried++;\n            try {\n                return downloadBitmap(raw, userAgent, referer);\n            } catch (Exception e) {\n                last = e;\n            }\n            if (tried >= 8) break;\n        }\n        if (last != null) throw last;\n        throw new IllegalStateException("nenhuma imagem HD válida encontrada");\n    }\n\n'''
if marker not in helper:
    raise SystemExit('Patch v0.7.3 não encontrou marker candidates')
helper = helper.replace(marker, insert + marker, 1)

helper = rep(helper,
    'if ("scale".equalsIgnoreCase(name) || "width".equalsIgnoreCase(name)) continue;',
    'String n = name.toLowerCase(java.util.Locale.ROOT);\n            if ("scale".equals(n) || "width".equals(n) || "height".equals(n)\n                    || "top".equals(n) || "left".equals(n) || "right".equals(n)\n                    || "bottom".equals(n) || "zoom".equals(n) || "preview".equals(n)\n                    || "scaletolandscape".equals(n)) continue;',
    'remove parâmetros de recorte')

# AutoRunService: guarda vários candidatos por página e tenta o melhor + alternativas.
auto = rep(auto,
    'import java.util.Date;\nimport java.util.HashMap;\nimport java.util.Locale;\nimport java.util.Map;\n',
    'import java.util.ArrayList;\nimport java.util.Date;\nimport java.util.HashMap;\nimport java.util.LinkedHashSet;\nimport java.util.List;\nimport java.util.Locale;\nimport java.util.Map;\n',
    'imports candidatos')

auto = rep(auto,
    'private static final int MIN_RENDER_WIDTH = 1440;\n    private static final int MIN_RENDER_HEIGHT = 2400;',
    'private static final int MIN_RENDER_WIDTH = 1800;\n    private static final int MIN_RENDER_HEIGHT = 3000;',
    'viewport automático maior')

auto = rep(auto,
    'private final Map<Integer, String> bestImageUrl = new HashMap<>();\n    private final Map<Integer, Integer> bestImageScore = new HashMap<>();',
    'private final Map<Integer, String> bestImageUrl = new HashMap<>();\n    private final Map<Integer, Integer> bestImageScore = new HashMap<>();\n    private final Map<Integer, LinkedHashSet<String>> imageCandidates = new HashMap<>();',
    'mapa múltiplos candidatos')

auto = rep(auto,
    'bestImageUrl.clear();\n        bestImageScore.clear();',
    'bestImageUrl.clear();\n        bestImageScore.clear();\n        imageCandidates.clear();',
    'limpa candidatos')

auto = rep(auto,
    'webView.setInitialScale(180);',
    'webView.setInitialScale(250);',
    'escala inicial 250')

auto = rep(auto,
    'userAgent = webView.getSettings().getUserAgentString() + " ExtratorValor/0.7.2";',
    'userAgent = webView.getSettings().getUserAgentString() + " ExtratorValor/0.7.3";',
    'userAgent 0.7.3')

auto = rep(auto,
    'handler.postDelayed(() -> visitPage(1), 7000);',
    'handler.postDelayed(() -> visitPage(1), 10000);',
    'aquecimento inicial')

auto = rep(auto,
    '            }, 3500);\n        }, 6500);',
    '            }, 5000);\n        }, 9000);',
    'tempo por página')

auto = rep(auto,
    'handler.postDelayed(this::finishCollection, 3500);',
    'handler.postDelayed(this::finishCollection, 5000);',
    'espera final página 3')

old_finish = '''            String[] urls = new String[3];\n            for (int page = 1; page <= 3; page++) {\n                urls[page - 1] = bestImageUrl.get(page);\n                if (urls[page - 1] == null) {\n                    failAttempt("Não encontrei a imagem da página " + page + ".", true);\n                    return;\n                }\n            }\n            updateNotification("Páginas localizadas. Validando e baixando em alta qualidade...");\n            new Thread(() -> buildAndSend(urls)).start();'''
new_finish = '''            @SuppressWarnings("unchecked")\n            List<String>[] urls = new List[3];\n            for (int page = 1; page <= 3; page++) {\n                ArrayList<String> ordered = new ArrayList<>();\n                String best = bestImageUrl.get(page);\n                if (best != null) ordered.add(best);\n                LinkedHashSet<String> all = imageCandidates.get(page);\n                if (all != null) {\n                    for (String candidate : all) {\n                        if (!ordered.contains(candidate)) ordered.add(candidate);\n                        if (ordered.size() >= 8) break;\n                    }\n                }\n                urls[page - 1] = ordered;\n                if (ordered.isEmpty()) {\n                    failAttempt("Não encontrei nenhum candidato de imagem da página " + page + ".", true);\n                    return;\n                }\n            }\n            updateNotification("Candidatos localizados. Procurando página inteira HD e descartando miniaturas...");\n            new Thread(() -> buildAndSend(urls)).start();'''
auto = rep(auto, old_finish, new_finish, 'finishCollection múltiplos candidatos')

auto = rep(auto,
    'private void buildAndSend(String[] urls) {',
    'private void buildAndSend(List<String>[] urls) {',
    'assinatura buildAndSend')

auto = rep(auto,
    'bitmaps[i] = AutoPdfHelper.downloadBitmap(urls[i], userAgent, referer);',
    'bitmaps[i] = AutoPdfHelper.downloadBestBitmap(urls[i], userAgent, referer);',
    'download múltiplos candidatos')

old_score = '''            int score = host.startsWith("i.") ? 1000000 : 10000;\n            score += safeInt(u.getQueryParameter("scale")) * 100;\n            score += safeInt(u.getQueryParameter("width"));\n            Integer old = bestImageScore.get(page);'''
new_score = '''            LinkedHashSet<String> all = imageCandidates.get(page);\n            if (all == null) {\n                all = new LinkedHashSet<>();\n                imageCandidates.put(page, all);\n            }\n            if (all.size() < 24) all.add(rawUrl);\n\n            int score = host.startsWith("i.") ? 1000000 : 10000;\n            if (u.getQueryParameter("ticket") != null) score += 400000;\n            if (u.getQueryParameter("top") == null && u.getQueryParameter("left") == null) score += 300000;\n            if (u.getQueryParameter("height") == null) score += 100000;\n            score += safeInt(u.getQueryParameter("scale")) * 100;\n            score += safeInt(u.getQueryParameter("width"));\n            Integer old = bestImageScore.get(page);'''
auto = rep(auto, old_score, new_score, 'score e armazenamento de candidatos')

# MainActivity: só atualiza identificação da versão exibida.
main = rep(main,
    'userAgent = webView.getSettings().getUserAgentString() + " ExtratorValor/0.7.2";',
    'userAgent = webView.getSettings().getUserAgentString() + " ExtratorValor/0.7.3";',
    'main userAgent 0.7.3')
main = rep(main,
    'TextView version = text("Extrator Valor • versão 0.7.2", 12, C_MUTED, false);',
    'TextView version = text("Extrator Valor • versão 0.7.3", 12, C_MUTED, false);',
    'versão settings 0.7.3')
main = rep(main,
    'Extrator Valor Android v0.7.2 - Disparo Direto + Diagnóstico + Auto HD',
    'Extrator Valor Android v0.7.3 - Automático Página Inteira HD + Diagnóstico',
    'diagnóstico versão 0.7.3')

helper_path.write_text(helper, encoding='utf-8')
auto_path.write_text(auto, encoding='utf-8')
main_path.write_text(main, encoding='utf-8')
print('Patch v0.7.3 aplicado com sucesso')
