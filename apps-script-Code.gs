/**
 * Extrator Valor Android v0.5 — envio de 3 PDFs separados.
 *
 * Configure em Project Settings > Script properties:
 *   DEST_EMAIL = destinatario@exemplo.com
 *   APP_SECRET = uma_chave_longa_aleatoria
 *
 * Implante como Web App executando como você.
 */
function doPost(e) {
  try {
    const props = PropertiesService.getScriptProperties();
    const dest = props.getProperty('DEST_EMAIL');
    const expectedSecret = props.getProperty('APP_SECRET');
    const body = JSON.parse((e && e.postData && e.postData.contents) || '{}');

    if (!dest || !expectedSecret) {
      return json_({ ok: false, error: 'Script properties não configuradas.' });
    }
    if (body.secret !== expectedSecret) {
      return json_({ ok: false, error: 'Não autorizado.' });
    }

    const attachments = [];

    // Formato v0.5: três arquivos separados.
    if (Array.isArray(body.files) && body.files.length) {
      body.files.forEach(function(file) {
        if (!file || !file.pdfBase64 || !file.fileName) return;
        const bytes = Utilities.base64Decode(file.pdfBase64);
        attachments.push(Utilities.newBlob(bytes, 'application/pdf', file.fileName));
      });
    }

    // Compatibilidade com versões anteriores, caso necessário.
    if (!attachments.length && body.pdfBase64 && body.fileName) {
      const bytes = Utilities.base64Decode(body.pdfBase64);
      attachments.push(Utilities.newBlob(bytes, 'application/pdf', body.fileName));
    }

    if (!attachments.length) {
      return json_({ ok: false, error: 'Nenhum PDF recebido.' });
    }

    const date = Utilities.formatDate(new Date(), Session.getScriptTimeZone(), 'dd/MM/yyyy');
    const subject = body.subject || ('Valor Econômico - páginas 1, 2 e 3 - ' + date);
    const message = body.message || 'Seguem em anexo, em arquivos separados, as três primeiras páginas da edição autorizada do Valor Econômico.';

    GmailApp.sendEmail(dest, subject, message, {
      attachments: attachments
    });

    return json_({
      ok: true,
      attachments: attachments.length
    });
  } catch (err) {
    return json_({ ok: false, error: String(err) });
  }
}

function json_(obj) {
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
