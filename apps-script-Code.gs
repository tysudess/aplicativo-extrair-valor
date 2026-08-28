/**
 * Relay opcional para a futura v0.2.
 * Configure em Project Settings > Script properties:
 *   DEST_EMAIL = destinatario@exemplo.com
 *   APP_SECRET = uma_chave_longa_aleatoria
 *
 * Depois implante como Web App executando como você.
 * O Android enviará o PDF somente depois que a etapa de extração for validada.
 */
function doPost(e) {
  try {
    const props = PropertiesService.getScriptProperties();
    const dest = props.getProperty('DEST_EMAIL');
    const expectedSecret = props.getProperty('APP_SECRET');
    const body = JSON.parse(e.postData.contents || '{}');

    if (!dest || !expectedSecret) {
      return json_({ ok: false, error: 'Script properties não configuradas.' });
    }
    if (body.secret !== expectedSecret) {
      return json_({ ok: false, error: 'Não autorizado.' });
    }
    if (!body.pdfBase64 || !body.fileName) {
      return json_({ ok: false, error: 'PDF ausente.' });
    }

    const bytes = Utilities.base64Decode(body.pdfBase64);
    const blob = Utilities.newBlob(bytes, 'application/pdf', body.fileName);
    const subject = body.subject || ('Valor Econômico - páginas 1 a 3 - ' + Utilities.formatDate(new Date(), Session.getScriptTimeZone(), 'dd/MM/yyyy'));
    const message = body.message || 'Segue em anexo o PDF automático com as três primeiras páginas da edição autorizada do Valor Econômico.';

    GmailApp.sendEmail(dest, subject, message, { attachments: [blob] });
    return json_({ ok: true });
  } catch (err) {
    return json_({ ok: false, error: String(err) });
  }
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
