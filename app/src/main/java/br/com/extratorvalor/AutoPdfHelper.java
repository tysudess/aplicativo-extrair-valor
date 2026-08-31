package br.com.extratorvalor;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.CookieManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class AutoPdfHelper {
    private static final int TARGET_WIDTH = 2200;
    private static final int MIN_ACCEPTABLE_WIDTH = 1800;
    private static final int[] HIGH_SCALES = {416, 390, 364, 338, 312, 286, 260, 234, 208, 182, 156, 130, 104};
    private static final int[] HIGH_WIDTHS = {3200, 3000, 2800, 2600, 2400, 2200, 2000, 1800, 1600};

    static final class PdfResult {
        final byte[] bytes;
        final Uri uri;
        final String fileName;

        PdfResult(byte[] bytes, Uri uri, String fileName) {
            this.bytes = bytes;
            this.uri = uri;
            this.fileName = fileName;
        }
    }

    static Bitmap downloadBitmap(String rawUrl, String userAgent, String referer) throws Exception {
        Exception first = null;
        Bitmap best = null;
        long bestPixels = 0;

        for (String candidate : candidates(rawUrl)) {
            HttpURLConnection con = null;
            try {
                con = (HttpURLConnection) new URL(candidate).openConnection();
                con.setConnectTimeout(20000);
                con.setReadTimeout(45000);
                con.setInstanceFollowRedirects(true);
                con.setUseCaches(false);
                con.setRequestProperty("User-Agent", userAgent);
                con.setRequestProperty("Referer", referer);
                con.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
                con.setRequestProperty("Cache-Control", "no-cache");
                String cookies = CookieManager.getInstance().getCookie(candidate);
                if (cookies != null && !cookies.isEmpty()) con.setRequestProperty("Cookie", cookies);

                int code = con.getResponseCode();
                if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);

                Bitmap bm;
                try (InputStream in = con.getInputStream()) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    bm = BitmapFactory.decodeStream(in, null, options);
                }
                if (bm == null) throw new IllegalStateException("imagem inválida");

                long pixels = (long) bm.getWidth() * (long) bm.getHeight();
                if (pixels > bestPixels) {
                    if (best != null && best != bm && !best.isRecycled()) best.recycle();
                    best = bm;
                    bestPixels = pixels;
                } else if (bm != best && !bm.isRecycled()) {
                    bm.recycle();
                }

                if (best != null && best.getWidth() >= TARGET_WIDTH) return best;
            } catch (Exception e) {
                if (first == null) first = e;
            } finally {
                if (con != null) con.disconnect();
            }
        }

        if (best != null && best.getWidth() >= MIN_ACCEPTABLE_WIDTH) return best;

        String detail = best == null
                ? "nenhuma imagem válida"
                : (best.getWidth() + "x" + best.getHeight() + " px");
        if (best != null && !best.isRecycled()) best.recycle();
        throw new IllegalStateException(
                "qualidade insuficiente: " + detail + "; mínimo exigido " + MIN_ACCEPTABLE_WIDTH + " px de largura"
        );
    }

    private static List<String> candidates(String rawUrl) {
        Set<String> unique = new LinkedHashSet<>();
        try {
            Uri in = Uri.parse(rawUrl);
            String path = in.getPath();
            if (path != null && path.contains("/img")) {
                for (int scale : HIGH_SCALES) {
                    unique.add(replaceSizing(in, "scale", String.valueOf(scale)));
                }
                for (int width : HIGH_WIDTHS) {
                    unique.add(replaceSizing(in, "width", String.valueOf(width)));
                }
            }
        } catch (Exception ignored) {}
        unique.add(rawUrl);
        return new ArrayList<>(unique);
    }

    private static String replaceSizing(Uri in, String param, String value) {
        Uri.Builder b = in.buildUpon().clearQuery();
        for (String name : in.getQueryParameterNames()) {
            if ("scale".equalsIgnoreCase(name) || "width".equalsIgnoreCase(name)) continue;
            for (String v : in.getQueryParameters(name)) b.appendQueryParameter(name, v);
        }
        b.appendQueryParameter(param, value);
        return b.build().toString();
    }

    static PdfResult[] createAndSaveSeparatePdfs(ContentResolver resolver, Bitmap[] pages, String[] fileNames) throws Exception {
        if (pages == null || fileNames == null || pages.length != 3 || fileNames.length != 3) {
            throw new IllegalArgumentException("São necessárias exatamente 3 páginas e 3 nomes de arquivo");
        }

        PdfResult[] results = new PdfResult[3];
        try {
            for (int i = 0; i < 3; i++) {
                Bitmap bm = pages[i];
                if (bm == null) throw new IllegalStateException("Página " + (i + 1) + " ausente");
                if (bm.getWidth() < MIN_ACCEPTABLE_WIDTH) {
                    throw new IllegalStateException(
                            "Página " + (i + 1) + " abaixo da qualidade mínima: " + bm.getWidth() + "x" + bm.getHeight() + " px"
                    );
                }
                byte[] bytes = createSinglePagePdfBytes(bm);
                Uri uri = savePdf(resolver, bytes, fileNames[i]);
                results[i] = new PdfResult(bytes, uri, fileNames[i]);
            }
            return results;
        } finally {
            for (Bitmap bm : pages) {
                if (bm != null && !bm.isRecycled()) bm.recycle();
            }
        }
    }

    private static byte[] createSinglePagePdfBytes(Bitmap bm) throws Exception {
        PdfDocument doc = new PdfDocument();
        try {
            int w = Math.max(1, bm.getWidth());
            int h = Math.max(1, bm.getHeight());
            PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(w, h, 1).create();
            PdfDocument.Page page = doc.startPage(info);
            page.getCanvas().drawBitmap(bm, 0, 0, null);
            doc.finishPage(page);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.writeTo(bos);
            return bos.toByteArray();
        } finally {
            doc.close();
        }
    }

    private static Uri savePdf(ContentResolver resolver, byte[] bytes, String fileName) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ExtratorValor");
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IllegalStateException("não foi possível criar " + fileName);
        try (OutputStream os = resolver.openOutputStream(uri)) {
            if (os == null) throw new IllegalStateException("não foi possível abrir " + fileName);
            os.write(bytes);
        }
        return uri;
    }

    static String sendToAppsScript(String endpoint, String secret, PdfResult[] pdfs) throws Exception {
        if (pdfs == null || pdfs.length != 3) {
            throw new IllegalArgumentException("São necessários os 3 PDFs para o envio");
        }

        JSONObject body = new JSONObject();
        body.put("secret", secret);
        body.put("subject", "Valor Econômico - páginas 1, 2 e 3");
        body.put("message", "Seguem em anexo, em arquivos separados, as três primeiras páginas da edição autorizada do Valor Econômico.");

        JSONArray files = new JSONArray();
        for (PdfResult pdf : pdfs) {
            JSONObject item = new JSONObject();
            item.put("fileName", pdf.fileName);
            item.put("pdfBase64", Base64.encodeToString(pdf.bytes, Base64.NO_WRAP));
            files.put(item);
        }
        body.put("files", files);

        HttpURLConnection con = (HttpURLConnection) new URL(endpoint).openConnection();
        con.setConnectTimeout(20000);
        con.setReadTimeout(45000);
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = con.getOutputStream()) {
            os.write(payload);
        }

        int code = con.getResponseCode();
        InputStream stream = code >= 200 && code < 400 ? con.getInputStream() : con.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (stream != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
        }
        con.disconnect();

        if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code + " " + sb);
        JSONObject result = new JSONObject(sb.toString());
        if (!result.optBoolean("ok", false)) {
            throw new IllegalStateException(result.optString("error", "falha no envio"));
        }
        return "ok";
    }

    private AutoPdfHelper() {}
}
