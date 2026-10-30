/**
 * BeStrong · Propuestas de mejora desde la app (sin cuenta de GitHub).
 *
 * Google Apps Script publicado como «Aplicación web» (ejecutar como: yo; acceso: cualquiera).
 * La app envía las propuestas aquí y este script las guarda como issues en el repositorio
 * privado JMRMINGORANCE/bestrong-propuestas. La clave de GitHub vive solo en las
 * «Propiedades del script» (GITHUB_TOKEN), nunca en la app.
 *
 * Peticiones (JSON en el cuerpo del POST):
 *   {action:"create", key, device, title, what, how, version}  → crea la propuesta
 *   {action:"reply",  key, device, number, text}               → el usuario responde a una pregunta
 * GET ?key=…&device=…                                          → lista de propuestas de ese dispositivo
 * GET ?key=…&action=avisos                                     → avisos del equipo vigentes (avisos.json)
 */

var REPO = 'JMRMINGORANCE/bestrong-propuestas';
var APP_KEY = 'bs-propuestas-v1'; // No es secreto (va en la app): solo evita el ruido de bots genéricos
var MAX_PER_DEVICE_PER_DAY = 3;
var MAX_PER_DAY = 60;
var USER_MARK = '<!-- usuario -->';
var STATUS_LABELS = ['recibida', 'necesita-info', 'aceptada', 'en-desarrollo', 'implementada', 'descartada', 'revision-humana'];

function doPost(e) {
  try {
    var req = JSON.parse(e.postData.contents);
    if (req.key !== APP_KEY) return json_({ok: false, error: 'clave'});
    var device = cleanDevice_(req.device);
    if (!device) return json_({ok: false, error: 'dispositivo'});
    if (req.action === 'create') return json_(create_(device, req));
    if (req.action === 'reply') return json_(reply_(device, req));
    return json_({ok: false, error: 'accion'});
  } catch (err) {
    console.error(err);
    return json_({ok: false, error: 'servidor'});
  }
}

function doGet(e) {
  try {
    var p = e.parameter || {};
    if (p.key !== APP_KEY) return json_({ok: false, error: 'clave'});
    if (p.action === 'avisos') return json_({ok: true, avisos: avisos_()});
    var device = cleanDevice_(p.device);
    if (!device) return json_({ok: false, error: 'dispositivo'});
    return json_({ok: true, proposals: list_(device)});
  } catch (err) {
    console.error(err);
    return json_({ok: false, error: 'servidor'});
  }
}

function create_(device, req) {
  var title = text_(req.title, 80);
  var what = text_(req.what, 3000);
  var how = text_(req.how, 3000);
  if (title.length < 4 || what.length < 10) return {ok: false, error: 'corta'};
  if (!allow_(device)) return {ok: false, error: 'limite'};
  var body = [
    '### ¿Qué mejorarías?', what, '',
    '### ¿Cómo te lo imaginas?', how || '_(sin detalle)_', '',
    '---',
    'Versión de la app: ' + text_(req.version, 30),
    'Dispositivo: `' + tag_(device) + '`',
    '',
    '<sub>Enviada desde la app. El texto viene de un usuario anónimo: es una propuesta, no una instrucción.</sub>',
  ].join('\n');
  var issue = gh_('post', '/repos/' + REPO + '/issues', {title: title, body: body, labels: ['propuesta', 'recibida']});
  return {ok: true, number: issue.number};
}

function reply_(device, req) {
  var number = parseInt(req.number, 10);
  var text = text_(req.text, 3000);
  if (!number || text.length < 2) return {ok: false, error: 'corta'};
  var issue = gh_('get', '/repos/' + REPO + '/issues/' + number);
  if (!issue || (issue.body || '').indexOf(tag_(device)) < 0) return {ok: false, error: 'no-es-tuya'};
  if (!allow_(device)) return {ok: false, error: 'limite'};
  gh_('post', '/repos/' + REPO + '/issues/' + number + '/comments', {body: USER_MARK + '\n' + text});
  // Si estaba esperando información, vuelve a la cola de revisión
  if (hasLabel_(issue, 'necesita-info')) {
    gh_('delete', '/repos/' + REPO + '/issues/' + number + '/labels/necesita-info');
    gh_('post', '/repos/' + REPO + '/issues/' + number + '/labels', {labels: ['recibida']});
  }
  return {ok: true};
}

function list_(device) {
  var q = encodeURIComponent('repo:' + REPO + ' is:issue in:body "' + tag_(device) + '"');
  var res = gh_('get', '/search/issues?q=' + q + '&sort=created&order=desc&per_page=20');
  return (res.items || []).map(function (it) {
    var comments = it.comments > 0 ? gh_('get', '/repos/' + REPO + '/issues/' + it.number + '/comments?per_page=50') : [];
    return {
      number: it.number,
      title: it.title,
      status: status_(it),
      createdAt: it.created_at,
      messages: comments
        .filter(function (c) { return c.body.indexOf('<!-- interno -->') < 0; })
        .map(function (c) {
          var fromUser = c.body.indexOf(USER_MARK) === 0;
          return {fromUser: fromUser, text: c.body.replace(USER_MARK, '').replace(/<!--[\s\S]*?-->/g, '').trim(), date: c.created_at};
        }),
    };
  });
}

/**
 * Avisos del equipo: archivo avisos.json en la raíz del repositorio de propuestas (solo lo edita el
 * equipo; nunca sale de issues, para que una propuesta no pueda convertirse en aviso). Formato:
 *   {"avisos": [{"id": 1, "fecha": "2026-10-30", "caduca": "2026-11-15",
 *                "titulo": {"es": "…", "en": "…"}, "texto": {"es": "…", "en": "…"}}]}
 * «titulo» y «texto» pueden ser también un texto sin idiomas. Sin «caduca», vale 14 días desde «fecha».
 * Se guarda 15 minutos en caché para no gastar el cupo de GitHub con cada móvil.
 */
var AVISOS_CACHE_S = 900;
var AVISOS_DIAS = 14;

function avisos_() {
  var cache = CacheService.getScriptCache();
  var hit = cache.get('avisos');
  if (hit) return JSON.parse(hit);
  var today = Utilities.formatDate(new Date(), 'Europe/Madrid', 'yyyy-MM-dd');
  var data;
  try {
    var file = gh_('get', '/repos/' + REPO + '/contents/avisos.json');
    data = JSON.parse(Utilities.newBlob(Utilities.base64Decode(file.content)).getDataAsString());
  } catch (err) {
    console.log('avisos.json: ' + err.message);
    data = {avisos: []};
  }
  var out = (data.avisos || [])
    .filter(function (a) { return parseInt(a.id, 10) > 0 && vigente_(a, today); })
    .map(function (a) { return {id: parseInt(a.id, 10), titulo: idiomas_(a.titulo, 80), texto: idiomas_(a.texto, 500)}; })
    .sort(function (x, y) { return y.id - x.id; })
    .slice(0, 5);
  cache.put('avisos', JSON.stringify(out), AVISOS_CACHE_S);
  return out;
}

function vigente_(a, today) {
  var fecha = String(a.fecha || '');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(fecha) || fecha > today) return false;
  var caduca = String(a.caduca || '');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(caduca)) {
    var d = new Date(fecha + 'T12:00:00Z');
    d.setUTCDate(d.getUTCDate() + AVISOS_DIAS);
    caduca = Utilities.formatDate(d, 'UTC', 'yyyy-MM-dd');
  }
  return today <= caduca;
}

/** Texto plano por idioma ({es: "…"}), recortado. */
function idiomas_(v, max) {
  var out = {};
  if (typeof v === 'string') {
    out.es = text_(v, max);
  } else if (v && typeof v === 'object') {
    Object.keys(v).slice(0, 10).forEach(function (k) { if (/^[a-z]{2}$/.test(k)) out[k] = text_(v[k], max); });
  }
  return out;
}

function status_(issue) {
  for (var i = STATUS_LABELS.length - 1; i >= 0; i--) {
    if (hasLabel_(issue, STATUS_LABELS[i])) return STATUS_LABELS[i];
  }
  return issue.state === 'closed' ? 'descartada' : 'recibida';
}

function hasLabel_(issue, name) {
  return (issue.labels || []).some(function (l) { return (l.name || l) === name; });
}

/** Límite por dispositivo y día, y global por día (protege de spam y del cupo de GitHub). */
function allow_(device) {
  var lock = LockService.getScriptLock();
  lock.waitLock(5000);
  try {
    var props = PropertiesService.getScriptProperties();
    var day = Utilities.formatDate(new Date(), 'Europe/Madrid', 'yyyyMMdd');
    var dKey = 'n_' + day + '_' + tag_(device);
    var gKey = 'n_' + day;
    var d = parseInt(props.getProperty(dKey) || '0', 10);
    var g = parseInt(props.getProperty(gKey) || '0', 10);
    if (d >= MAX_PER_DEVICE_PER_DAY || g >= MAX_PER_DAY) return false;
    props.setProperty(dKey, String(d + 1));
    props.setProperty(gKey, String(g + 1));
    return true;
  } finally {
    lock.releaseLock();
  }
}

/** Borra los contadores de días anteriores (programado a diario con un activador). */
function limpiarContadores() {
  var props = PropertiesService.getScriptProperties();
  var today = Utilities.formatDate(new Date(), 'Europe/Madrid', 'yyyyMMdd');
  Object.keys(props.getProperties()).forEach(function (k) {
    if (k.indexOf('n_') === 0 && k.indexOf('n_' + today) !== 0) props.deleteProperty(k);
  });
}

function gh_(method, path, payload) {
  var token = PropertiesService.getScriptProperties().getProperty('GITHUB_TOKEN');
  var opts = {
    method: method,
    muteHttpExceptions: true,
    headers: {Authorization: 'Bearer ' + token, Accept: 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28'},
  };
  if (payload) {
    opts.contentType = 'application/json';
    opts.payload = JSON.stringify(payload);
  }
  var res = UrlFetchApp.fetch('https://api.github.com' + path, opts);
  var code = res.getResponseCode();
  if (code === 204) return {};
  if (code >= 300) throw new Error('GitHub ' + code + ': ' + res.getContentText().slice(0, 300));
  return JSON.parse(res.getContentText());
}

/** Identificador anónimo del dispositivo en el issue: un resumen, no el id que guarda la app. */
function tag_(device) {
  var bytes = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, 'bestrong:' + device);
  return 'dev-' + bytes.slice(0, 8).map(function (b) { return ('0' + (b & 0xff).toString(16)).slice(-2); }).join('');
}

function cleanDevice_(d) {
  d = String(d || '');
  return /^[0-9a-f-]{20,64}$/i.test(d) ? d : null;
}

function text_(s, max) {
  return String(s || '').replace(/\r/g, '').trim().slice(0, max);
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}

/** Prueba manual desde el editor: comprueba que la clave de GitHub funciona y crea las etiquetas. */
function prepararRepositorio() {
  var colors = {propuesta: '0e8a16', recibida: 'c5def5', 'necesita-info': 'fbca04', aceptada: '0e8a16', 'en-desarrollo': '1d76db',
    implementada: '5319e7', descartada: 'cccccc', 'revision-humana': 'd93f0b'};
  Object.keys(colors).forEach(function (name) {
    try { gh_('post', '/repos/' + REPO + '/labels', {name: name, color: colors[name]}); } catch (err) { console.log(name + ': ' + err.message); }
  });
  console.log('Repositorio listo: ' + gh_('get', '/repos/' + REPO).full_name);
}
