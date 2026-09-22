// Pega este código en Extensiones > Apps Script del Google Sheet donde quieres
// recibir los logs de Comfy, y despliégalo como Web App (Implementar > Nueva implementación
// > Aplicación web > Ejecutar como: yo > Quién tiene acceso: Cualquier usuario).
// Copia la URL /exec resultante y ponla en local.properties como SHEETS_LOG_URL.

function doPost(e) {
  if (!e || !e.postData) {
    return ContentService
      .createTextOutput("No postData")
      .setMimeType(ContentService.MimeType.TEXT);
  }

  const sheet = SpreadsheetApp
    .getActiveSpreadsheet()
    .getSheets()[0];

  const data = JSON.parse(e.postData.contents);

  sheet.appendRow([
    data.timestamp || new Date().toISOString(),
    data.level || "",
    data.tag || "",
    data.message || ""
  ]);

  return ContentService
    .createTextOutput(JSON.stringify({ status: "ok" }))
    .setMimeType(ContentService.MimeType.JSON);
}
