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
import java.util.List;

final class AutoPdfHelper {
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
        for (String candidate : candidates(rawUrl)) {
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(candidate).openConnection();
                con.setConnectTimeout(20000);
                con.setReadTimeout(30000);
                con.setInstanceFollowRedirects(true);
                con.setRequestProperty("User-Agent", userAgent);
                con.setRequestProperty("Referer", referer);
                String cookies = CookieManager.getInstance().getCookie(candidate);
                if (cookies != null && !cookies.isEmpty()) con.setRequestProperty("Cookie", cookies);
                int code = con.getResponseCode();
                if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
                try (InputStream in = con.getInputStream()) {
                    Bitmap bm = BitmapFactory.decodeStream(in);
                    if (bm == null) throw new IllegalStateException("imagem inválida");
                    return bm;
                } finally {
                    con.disconnect();
                }
            } catch (Exception e) {
                first = e;
            }
        }
        throw first != null ? first : new IllegalStateException("URL de imagem inválida");
    }

    private static List<String> candidates(String rawUrl) {
        ArrayList<String> out = new ArrayList<>();
        try {
            Uri in = Uri.parse(rawUrl);
            String scale = in.getQueryParameter("scale");
            if (scale != null) {
                Uri.Builder b = in.buildUpon().clearQuery();
                for (String name : in.getQueryParameterNames()) {
                    if ("scale".equalsIgnoreCase(name)) b.appendQueryParameter(name, "104");
                    else for (String v : in.getQueryParameters(name)) b.appendQueryParameter(name, v);
                }
                out.add(b.build().toString());
            }
        } catch (Exception ignored) {}
        if (!out.contains(rawUrl)) out.add(rawUrl);
        return out;
    }

    static PdfResult createAndSavePdf(ContentResolver resolver, Bitmap[] pages, String fileName) throws Exception {
        PdfDocument doc = new PdfDocument();
        try {
            for (int i = 0; i < pages.length; i++) {
                Bitmap bm = pages[i];
                if (bm == null) throw new IllegalStateException("Página " + (i + 1) + " ausente");
                int w = Math.max(1, bm.getWidth());
                int h = Math.max(1, bm.getHeight());
                PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(w, h, i + 1).create();
                PdfDocument.Page page = doc.startPage(info);
                page.getCanvas().drawBitmap(bm, 0, 0, null);
                doc.finishPage(page);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.writeTo(bos);
            byte[] bytes = bos.toByteArray();

            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ExtratorValor");
            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("não foi possível criar o PDF");
            try (OutputStream os = resolver.openOutputStream(uri)) {
                if (os == null) throw new IllegalStateException("não foi possível abrir o PDF");
                os.write(bytes);
            }
            return new PdfResult(bytes, uri, fileName);
        } finally {
            doc.close();
            for (Bitmap bm : pages) if (bm != null && !bm.isRecycled()) bm.recycle();
        }
    }

    static String sendToAppsScript(String endpoint, String secret, PdfResult pdf) throws Exception {
        JSONObject body = new JSONObject();
        body.put("secret", secret);
        body.put("pdfBase64", Base64.encodeToString(pdf.bytes, Base64.NO_WRAP));
        body.put("fileName", pdf.fileName);
        body.put("subject", "Valor Econômico - páginas 1 a 3");
        body.put("message", "Segue em anexo o PDF automático com as três primeiras páginas da edição autorizada do Valor Econômico.");

        HttpURLConnection con = (HttpURLConnection) new URL(endpoint).openConnection();
        con.setConnectTimeout(20000);
        con.setReadTimeout(30000);
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = con.getOutputStream()) { os.write(payload); }
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
        if (!result.optBoolean("ok", false)) throw new IllegalStateException(result.optString("error", "falha no envio"));
        return "ok";
    }

    private AutoPdfHelper() {}
}
