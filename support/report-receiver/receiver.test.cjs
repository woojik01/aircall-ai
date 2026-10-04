const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

function receiver() {
  const rows = [['receivedAt', 'id', 'category', 'reason', 'response', 'appVersion', 'androidApi']];
  const properties = {REPORT_SPREADSHEET_ID: 'private-sheet', RETENTION_DAYS: '30'};
  const triggers = [{getHandlerFunction: () => 'purgeExpiredReports'}];
  let held = false, failFlush = false, failWrite = false, failDelete = false;
  const sheet = {
    getLastRow: () => rows.length,
    appendRow: row => {
      if (failWrite) throw new Error('read-only sheet');
      rows.push(row.map(value => typeof value === 'string' && value.startsWith("'") ? value.slice(1) : value));
    },
    deleteRow: row => { if (failDelete) throw new Error('delete blocked'); rows.splice(row - 1, 1); },
    getRange: (row, col, count, width) => ({
      getValues: () => rows.slice(row - 1, row - 1 + count).map(r => r.slice(col - 1, col - 1 + width)),
      createTextFinder: text => ({matchEntireCell: () => ({findNext: () => {
        const index = rows.findIndex((r, i) => i >= row - 1 && i < row - 1 + count && r[col - 1] === text);
        return index < 0 ? null : {getRow: () => index + 1};
      }})}),
    }),
  };
  const context = {
    Date, JSON, Number, String, Object, Array, Error,
    PropertiesService: {getScriptProperties: () => ({getProperty: key => properties[key] || null,
      setProperty: (key, value) => { properties[key] = value; }})},
    SpreadsheetApp: {openById: () => ({getSheetByName: () => sheet, insertSheet: () => sheet}),
      flush: () => { if (failFlush) throw new Error('storage failed'); }},
    ContentService: {MimeType: {JSON: 'application/json'}, createTextOutput: body => ({setMimeType: () => JSON.parse(body)})},
    LockService: {getScriptLock: () => ({tryLock: () => {held = true; return true;}, hasLock: () => held, releaseLock: () => { held = false; }})},
    Utilities: {formatDate: date => date.toISOString().slice(0, 10)},
    ScriptApp: {getProjectTriggers: () => triggers,
      deleteTrigger: trigger => triggers.splice(triggers.indexOf(trigger), 1),
      newTrigger: handler => ({timeBased: () => ({everyDays: () => ({create: () => triggers.push({getHandlerFunction: () => handler})})})})},
  };
  vm.createContext(context);
  vm.runInContext(fs.readFileSync(path.join(__dirname, 'Code.gs'), 'utf8'), context);
  const report = () => ({id: '10000000-0000-4000-8000-000000000001', category: 'content', reason: '부적절한 응답',
    response: '', appVersion: '0.4.0', androidApi: 36});
  return {context, rows, properties, triggers, report,
    post: value => context.doPost({postData: {contents: JSON.stringify(value)}}),
    failFlush: () => {failFlush = true;},
    failWrite: () => {failWrite = true;}, failDelete: () => {failDelete = true;}};
}

test('acknowledges stored reports, makes retry idempotent, and rejects changed content for an ID', () => {
  const r = receiver();
  const report = r.report();
  assert.deepEqual(r.post(report), {ok: true, id: report.id});
  assert.equal(r.rows.length, 2);
  assert.deepEqual(r.post(report), {ok: true, id: report.id});
  assert.equal(r.rows.length, 2);
  assert.deepEqual(r.post({...report, reason: 'changed'}), {ok: false});
});

test('does not acknowledge a failed durable flush', () => {
  const r = receiver(); r.failFlush();
  assert.deepEqual(r.post(r.report()), {ok: false});
});

test('rejects unexpected credentials and limits untrusted fields', () => {
  for (const changes of [{apiKey: 'must-not-be-collected'}, {reason: ''}, {response: 'x'.repeat(4001)},
    {reason: 'x'.repeat(2001)}, {id: 'bad'}, {category: 'other'}, {androidApi: '36'}]) {
    const r = receiver();
    assert.deepEqual(r.post({...r.report(), ...changes}), {ok: false});
    assert.equal(r.rows.length, 1);
  }
});

test('writes user text as literal cells, including formulas', () => {
  const r = receiver();
  assert.equal(r.context.literal_('=IMPORTXML("secret")'), '\'=IMPORTXML("secret")');
  assert.equal(r.post({...r.report(), reason: '=SUM(1,2)'}).ok, true);
  assert.equal(r.rows[1][3], '=SUM(1,2)');
});

test('fails new requests when anonymous global quota is exhausted', () => {
  const r = receiver();
  r.properties.REPORT_DAILY_COUNT = JSON.stringify({day: new Date().toISOString().slice(0, 10), count: 500});
  assert.deepEqual(r.post(r.report()), {ok: false});
  assert.equal(r.rows.length, 1);
});

test('advertises readiness only with the cleanup trigger and removes expired reports', () => {
  const r = receiver();
  assert.equal(r.context.doGet().ready, true);
  r.triggers.length = 0;
  assert.equal(r.context.doGet().ready, false);
  r.context.setupReports();
  assert.equal(r.context.doGet().ready, true);
  r.rows.push([new Date(Date.now() - 31 * 86400000), 'old'], [new Date(), 'new']);
  r.context.purgeExpiredReports();
  assert.equal(r.rows.length, 2);
  assert.equal(r.rows[1][1], 'new');
});

test('release canary confirms the real write/read/delete path without altering reports or quota', () => {
  const r = receiver();
  r.post(r.report());
  const before = r.rows.map(row => row.slice());
  const quota = r.properties.REPORT_DAILY_COUNT;
  const canary = {operation: 'preflight', id: '20000000-0000-4000-8000-000000000002'};
  assert.deepEqual(r.post(canary), {ok: true, id: canary.id, cleared: true});
  assert.deepEqual(r.rows, before);
  assert.equal(r.properties.REPORT_DAILY_COUNT, quota);
  assert.deepEqual(r.post(canary), {ok: false}); // separately rate-limited
});

test('release canary rejects read-only storage, failed flushes, failed cleanup and existing IDs', () => {
  const canary = {operation: 'preflight', id: '20000000-0000-4000-8000-000000000002'};
  for (const failure of ['failWrite', 'failFlush', 'failDelete']) {
    const r = receiver(); r[failure]();
    assert.deepEqual(r.post(canary), {ok: false});
    assert.equal(r.properties.REPORT_DAILY_COUNT, undefined);
  }
  const r = receiver();
  r.post(r.report());
  assert.deepEqual(r.post({...canary, id: r.report().id}), {ok: false});
  assert.equal(r.rows.length, 2);
  const invalid = receiver();
  assert.deepEqual(invalid.post({...canary, token: 'unexpected'}), {ok: false});
  assert.equal(invalid.rows.length, 1);
});
