/* Anonymous in-app reporting receiver. Deploy as yourself; access: Anyone.
 * User API keys/OAuth credentials must never be added to this project.
 * Script property: REPORT_SPREADSHEET_ID (private spreadsheet), RETENTION_DAYS (1..365).
 */
function reportSettings_() {
  const props = PropertiesService.getScriptProperties();
  const id = props.getProperty('REPORT_SPREADSHEET_ID');
  const days = Number(props.getProperty('RETENTION_DAYS') || '30');
  if (!id || !Number.isInteger(days) || days < 1 || days > 365) throw new Error('Configure report receiver');
  const spreadsheet = SpreadsheetApp.openById(id);
  const sheet = spreadsheet.getSheetByName('reports');
  if (!sheet) throw new Error('Run setupReports first');
  return {sheet: sheet, days: days, props: props};
}

function json_(data) {
  return ContentService.createTextOutput(JSON.stringify(data)).setMimeType(ContentService.MimeType.JSON);
}

function doGet() {
  try {
    const config = reportSettings_();
    const ready = ScriptApp.getProjectTriggers().some(t => t.getHandlerFunction() === 'purgeExpiredReports');
    return json_({protocol: 'aircall-report-v1', ready: ready, retentionDays: config.days});
  } catch (_) { return json_({protocol: 'aircall-report-v1', ready: false}); }
}

function validatedReport_(report) {
  if (!report || typeof report !== 'object' || Array.isArray(report)) throw new Error('Invalid report');
  const allowed = ['id', 'category', 'reason', 'response', 'appVersion', 'androidApi'];
  if (Object.keys(report).some(k => allowed.indexOf(k) === -1)) throw new Error('Unexpected fields');
  if (typeof report.id !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(report.id)) throw new Error('Invalid ID');
  if (['content', 'privacy', 'bug'].indexOf(report.category) === -1) throw new Error('Invalid category');
  if (typeof report.reason !== 'string' || !report.reason.trim() || report.reason.length > 2000) throw new Error('Invalid reason');
  if (typeof report.response !== 'string' || report.response.length > 4000) throw new Error('Invalid response');
  if (typeof report.appVersion !== 'string' || !report.appVersion.trim() || report.appVersion.length > 80) throw new Error('Invalid version');
  if (!Number.isInteger(report.androidApi) || report.androidApi < 26 || report.androidApi > 100) throw new Error('Invalid Android version');
  return report;
}

// Prefix every text cell so user input cannot become a spreadsheet formula.
function literal_(value) { return "'" + String(value); }

function doPost(e) {
  const lock = LockService.getScriptLock();
  try {
    if (!e || !e.postData || e.postData.contents.length > 30000) return json_({ok: false});
    const report = validatedReport_(JSON.parse(e.postData.contents));
    if (!lock.tryLock(10000)) return json_({ok: false});
    const config = reportSettings_();
    const sheet = config.sheet;
    // A lost HTTP response can be retried without creating a duplicate report.
    if (sheet.getLastRow() > 1) {
      const existing = sheet.getRange(2, 2, sheet.getLastRow() - 1, 1)
        .createTextFinder(report.id).matchEntireCell(true).findNext();
      if (existing) {
        const row = sheet.getRange(existing.getRow(), 2, 1, 6).getValues()[0];
        const same = row[0] === report.id && row[1] === report.category && row[2] === report.reason &&
          row[3] === report.response && row[4] === report.appVersion && row[5] === report.androidApi;
        return json_(same ? {ok: true, id: report.id} : {ok: false});
      }
    }
    // Global protection against anonymous abuse; no persistent user/IP identifier is stored.
    const day = Utilities.formatDate(new Date(), 'UTC', 'yyyy-MM-dd');
    const quota = JSON.parse(config.props.getProperty('REPORT_DAILY_COUNT') || '{}');
    const count = quota.day === day ? Number(quota.count || 0) : 0;
    if (count >= 500) return json_({ok: false});
    sheet.appendRow([new Date(), literal_(report.id), literal_(report.category), literal_(report.reason),
      literal_(report.response), literal_(report.appVersion), report.androidApi]);
    SpreadsheetApp.flush();
    config.props.setProperty('REPORT_DAILY_COUNT', JSON.stringify({day: day, count: count + 1}));
    return json_({ok: true, id: report.id});
  } catch (_) { return json_({ok: false}); }
  finally { if (lock.hasLock()) lock.releaseLock(); }
}

function setupReports() {
  const id = PropertiesService.getScriptProperties().getProperty('REPORT_SPREADSHEET_ID');
  if (!id) throw new Error('Set REPORT_SPREADSHEET_ID first');
  const spreadsheet = SpreadsheetApp.openById(id);
  const sheet = spreadsheet.getSheetByName('reports') || spreadsheet.insertSheet('reports');
  if (sheet.getLastRow() === 0) sheet.appendRow(['receivedAt', 'id', 'category', 'reason', 'response', 'appVersion', 'androidApi']);
  ScriptApp.getProjectTriggers().filter(t => t.getHandlerFunction() === 'purgeExpiredReports').forEach(t => ScriptApp.deleteTrigger(t));
  ScriptApp.newTrigger('purgeExpiredReports').timeBased().everyDays(1).create();
  reportSettings_();
}

function purgeExpiredReports() {
  const lock = LockService.getScriptLock();
  if (!lock.tryLock(10000)) return;
  try {
    const config = reportSettings_();
    const sheet = config.sheet;
    if (sheet.getLastRow() <= 1) return;
    const cutoff = Date.now() - config.days * 24 * 60 * 60 * 1000;
    const dates = sheet.getRange(2, 1, sheet.getLastRow() - 1, 1).getValues();
    for (let index = dates.length - 1; index >= 0; index--) {
      if (dates[index][0] instanceof Date && dates[index][0].getTime() < cutoff) sheet.deleteRow(index + 2);
    }
    SpreadsheetApp.flush();
  } finally { lock.releaseLock(); }
}
