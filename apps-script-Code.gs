/**
 * Extrator Valor Android v0.5 — envio de 3 PDFs separados.
 *
 * Destinatário fixo:
 *   imprensa30.monitoramento@gmail.com
 *
 * Assunto fixo:
 *   Monitoramento: CAPAS DE JORNAIS
 *
 * Configure em Project Settings > Script properties apenas:
 *   APP_SECRET = uma_chave_longa_aleatoria
 *
 * Implante como Web App executando como você.
 */
function doPost(e) {
  try {
    const DEST_EMAIL = 'imprensa30.monitoramento@gmail.com';
    const SUBJECT = 'Monitoramento: CAPAS DE JORNAIS';
    const props = PropertiesService.getScriptProperties();
    const expectedSecret = props.getProperty('APP_SECRET');
    const body = JSON.parse((e && e.postData && e.postData.contents) || '{}');

    if (!expectedSecret) {
      return json_({ ok: false, error: 'APP_SECRET não configurado.' });
    }
    if (body.secret !== expectedSecret) {
      return json_({ ok: false, error: 'Não autorizado.' });
    }

    const attachments = [];

    if (Array.isArray(body.files) && body.files.length) {
      body.files.forEach(function(file) {
        if (!file || !file.pdfBase64 || !file.fileName) return;
        const bytes = Utilities.base64Decode(file.pdfBase64);
        attachments.push(Utilities.newBlob(bytes, 'application/pdf', file.fileName));
      });
    }

    if (!attachments.length && body.pdfBase64 && body.fileName) {
      const bytes = Utilities.base64Decode(body.pdfBase64);
      attachments.push(Utilities.newBlob(bytes, 'application/pdf', body.fileName));
    }

    if (!attachments.length) {
      return json_({ ok: false, error: 'Nenhum PDF recebido.' });
    }

    const message = body.message || 'Seguem em anexo, em arquivos separados, as três primeiras páginas da edição autorizada do Valor Econômico.';

    GmailApp.sendEmail(DEST_EMAIL, SUBJECT, message, {
      attachments: attachments
    });

    return json_({
      ok: true,
      email: DEST_EMAIL,
      subject: SUBJECT,
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
