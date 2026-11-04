/**
 * Follow-Up Board — Google Sheet backend
 *
 * Paste this whole file into Extensions > Apps Script (replace what's there),
 * run "setup" once, then Deploy > New deployment > Web app
 * (Execute as: Me, Who has access: Anyone).
 *
 * The desktop and Android apps talk to this script. Every request must carry
 * the access key that setup() creates, so the link alone isn't enough to read
 * your board.
 */

var TABS = {
  Tasks:    ['id', 'title', 'notes', 'people', 'col', 'status', 'followUp', 'created', 'updated', 'completedAt', 'lastFollowUp', 'log'],
  People:   ['initials', 'name'],
  Groups:   ['id', 'name', 'members'],
  Settings: ['key', 'value']
};

/* ---------- menu & first-time setup ---------- */

function onOpen() {
  SpreadsheetApp.getUi()
    .createMenu('Follow-Up Board')
    .addItem('Show connection details', 'showConnection')
    .addItem('Run first-time setup', 'setup')
    .addSeparator()
    .addItem('Make a new access key', 'newKey')
    .addToUi();
}

function setup() {
  ensureTabs_();
  var props = PropertiesService.getScriptProperties();
  if (!props.getProperty('KEY')) props.setProperty('KEY', makeKey_());
  bump_();
  try { showConnection(); } catch (e) { Logger.log('Access key: ' + props.getProperty('KEY')); }
}

function newKey() {
  PropertiesService.getScriptProperties().setProperty('KEY', makeKey_());
  showConnection();
}

function showConnection() {
  var key = PropertiesService.getScriptProperties().getProperty('KEY') || '(run setup first)';
  var url = '';
  try { url = ScriptApp.getService().getUrl() || ''; } catch (e) {}
  var html = HtmlService.createHtmlOutput(
    '<div style="font:14px Segoe UI,Roboto,Arial,sans-serif;line-height:1.5">' +
    '<p><b>Access key</b> (paste into the app):</p>' +
    '<input style="width:100%;box-sizing:border-box;font:14px Consolas,monospace;padding:6px" readonly onclick="this.select()" value="' + key + '">' +
    '<p style="margin-top:14px"><b>Web app URL</b>: copy it from <i>Deploy &gt; Manage deployments</i> in the Apps Script editor. It ends in <code>/exec</code>.</p>' +
    (url ? '<p style="color:#666;font-size:12px">This script reports: ' + url + '</p>' : '') +
    '<p style="color:#666;font-size:12px">Anyone with both the URL and the key can read and change your board. Use "Make a new access key" if you ever need to lock old copies out.</p>' +
    '</div>'
  ).setWidth(460).setHeight(300);
  SpreadsheetApp.getUi().showModalDialog(html, 'Follow-Up Board connection');
}

function makeKey_() {
  return (Utilities.getUuid() + Utilities.getUuid()).replace(/-/g, '').slice(0, 40);
}

function ensureTabs_() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  Object.keys(TABS).forEach(function (name) {
    var sh = ss.getSheetByName(name) || ss.insertSheet(name);
    var head = TABS[name];
    sh.getRange(1, 1, 1, head.length).setValues([head]).setFontWeight('bold');
    sh.setFrozenRows(1);
    // keep everything as plain text so dates aren't converted
    sh.getRange(1, 1, sh.getMaxRows(), head.length).setNumberFormat('@');
  });
  var first = ss.getSheetByName('Sheet1');
  if (first && first.getLastRow() === 0 && ss.getSheets().length > 1) ss.deleteSheet(first);
}

/* ---------- web endpoints ---------- */

function doGet(e) {
  return handle_((e && e.parameter) || {});
}

function doPost(e) {
  var body = {};
  try { body = JSON.parse((e && e.postData && e.postData.contents) || '{}'); } catch (err) {}
  var p = {};
  var q = (e && e.parameter) || {};
  Object.keys(q).forEach(function (k) { p[k] = q[k]; });
  Object.keys(body).forEach(function (k) { p[k] = body[k]; });
  return handle_(p);
}

function handle_(p) {
  try {
    var key = PropertiesService.getScriptProperties().getProperty('KEY');
    if (!key || p.key !== key) return out_({ ok: false, error: 'bad_key' });
    switch (p.action) {
      case 'rev':     return out_({ ok: true, rev: rev_() });
      case 'data':    return out_({ ok: true, rev: rev_(), data: readAll_(), tz: Session.getScriptTimeZone() });
      case 'summary': return out_(summary_());
      case 'ops':     return out_(applyOps_(p.ops || []));
    }
    return out_({ ok: false, error: 'unknown_action' });
  } catch (err) {
    return out_({ ok: false, error: String(err && err.message || err) });
  }
}

function out_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}

/* ---------- storage ---------- */

function rev_() {
  return Number(PropertiesService.getScriptProperties().getProperty('REV') || 0);
}
function bump_() {
  var r = rev_() + 1;
  PropertiesService.getScriptProperties().setProperty('REV', String(r));
  return r;
}

function cell_(v) {
  if (v instanceof Date) return Utilities.formatDate(v, Session.getScriptTimeZone(), 'yyyy-MM-dd');
  return v === null || v === undefined ? '' : String(v);
}

function loadTable_(name) {
  var sh = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(name);
  if (!sh) { ensureTabs_(); sh = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(name); }
  var head = TABS[name];
  var n = sh.getLastRow() - 1;
  var rows = n > 0 ? sh.getRange(2, 1, n, head.length).getValues() : [];
  rows = rows.filter(function (r) { return String(r[0]) !== ''; }).map(function (r) {
    var o = {};
    head.forEach(function (h, i) { o[h] = cell_(r[i]); });
    return o;
  });
  return { name: name, sheet: sh, head: head, rows: rows, dirty: false };
}

function saveTable_(t) {
  if (!t.dirty) return;
  var sh = t.sheet, head = t.head;
  var last = sh.getLastRow();
  if (last > 1) sh.getRange(2, 1, last - 1, head.length).clearContent();
  if (t.rows.length) {
    var vals = t.rows.map(function (o) { return head.map(function (h) { return cell_(o[h]); }); });
    var rng = sh.getRange(2, 1, vals.length, head.length);
    rng.setNumberFormat('@');
    rng.setValues(vals);
  }
}

function taskOut_(o) {
  var log = [];
  try { log = o.log ? JSON.parse(o.log) : []; } catch (e) {}
  return {
    id: o.id, title: o.title, notes: o.notes,
    people: o.people ? o.people.split(',').map(function (s) { return s.trim(); }).filter(String) : [],
    col: o.col || 'immediate', status: o.status || 'queued',
    followUp: o.followUp || null, created: o.created, updated: o.updated,
    completedAt: o.completedAt || null, lastFollowUp: o.lastFollowUp || null, log: log
  };
}

function taskIn_(t) {
  return {
    id: String(t.id), title: t.title || '', notes: t.notes || '',
    people: (t.people || []).join(','), col: t.col || 'immediate', status: t.status || 'queued',
    followUp: t.followUp || '', created: t.created || '', updated: t.updated || '',
    completedAt: t.completedAt || '', lastFollowUp: t.lastFollowUp || '',
    log: t.log && t.log.length ? JSON.stringify(t.log.slice(-50)) : ''
  };
}

function readAll_() {
  var settings = {};
  loadTable_('Settings').rows.forEach(function (r) { settings[r.key] = r.value; });
  return {
    tasks: loadTable_('Tasks').rows.map(taskOut_),
    people: loadTable_('People').rows.map(function (r) { return { initials: r.initials, name: r.name }; }),
    groups: loadTable_('Groups').rows.map(function (r) {
      return { id: r.id, name: r.name, members: r.members ? r.members.split(',').filter(String) : [] };
    }),
    settings: settings
  };
}

function upsert_(table, keyField, obj) {
  for (var i = 0; i < table.rows.length; i++) {
    if (table.rows[i][keyField] === obj[keyField]) { table.rows[i] = obj; table.dirty = true; return; }
  }
  table.rows.push(obj); table.dirty = true;
}
function remove_(table, keyField, id) {
  var before = table.rows.length;
  table.rows = table.rows.filter(function (r) { return r[keyField] !== String(id); });
  if (table.rows.length !== before) table.dirty = true;
}

function applyOps_(ops) {
  var lock = LockService.getScriptLock();
  lock.waitLock(25000);
  try {
    var t = {};
    var tab = function (n) { return t[n] || (t[n] = loadTable_(n)); };
    ops.forEach(function (op) {
      switch (op.op) {
        case 'setTask':     upsert_(tab('Tasks'), 'id', taskIn_(op.task)); break;
        case 'deleteTask':  remove_(tab('Tasks'), 'id', op.id); break;
        case 'setPerson':   upsert_(tab('People'), 'initials', { initials: String(op.person.initials), name: op.person.name || '' }); break;
        case 'deletePerson':remove_(tab('People'), 'initials', op.initials); break;
        case 'setGroup':    upsert_(tab('Groups'), 'id', { id: String(op.group.id), name: op.group.name || '', members: (op.group.members || []).join(',') }); break;
        case 'deleteGroup': remove_(tab('Groups'), 'id', op.id); break;
        case 'setSetting':  upsert_(tab('Settings'), 'key', { key: String(op.key), value: typeof op.value === 'string' ? op.value : JSON.stringify(op.value) }); break;
      }
    });
    Object.keys(t).forEach(function (n) { saveTable_(t[n]); });
    SpreadsheetApp.flush();
    return { ok: true, rev: bump_() };
  } finally {
    lock.releaseLock();
  }
}

/* ---------- notification summary (used by the phone) ---------- */

function summary_() {
  var tz = Session.getScriptTimeZone();
  var now = new Date();
  var today = Utilities.formatDate(now, tz, 'yyyy-MM-dd');
  var open = loadTable_('Tasks').rows.map(taskOut_).filter(function (x) { return x.status !== 'done'; });
  var due = open.filter(function (x) { return x.followUp && x.followUp <= today; })
    .sort(function (a, b) { return a.followUp < b.followUp ? -1 : a.followUp > b.followUp ? 1 : 0; });
  return {
    ok: true,
    today: today,
    dueCount: due.length,
    overdueCount: due.filter(function (x) { return x.followUp < today; }).length,
    noDateCount: open.filter(function (x) { return !x.followUp; }).length,
    due: due.slice(0, 8).map(function (x) {
      var days = Math.round((Date.parse(today) - Date.parse(x.followUp)) / 864e5);
      return { title: x.title, people: x.people, daysOverdue: days };
    })
  };
}
