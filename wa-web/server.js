/*
 * UbiSenderPro - Service compagnon WhatsApp Web (Baileys).
 *
 * ⚠️  Canal NON officiel : pilote WhatsApp Web. Contraire aux CGU de WhatsApp,
 *     risque de bannissement du numéro. À réserver à des numéros assumés.
 *
 * Expose une API REST interne (protégée par un token partagé) consommée par
 * le backend Java d'UbiSenderPro :
 *   POST   /sessions/:id/start        -> démarre/relance la session, renvoie {status, qr}
 *   GET    /sessions/:id/status       -> {status, qr, me}
 *   POST   /sessions/:id/logout       -> déconnecte et oublie la session
 *   POST   /sessions/:id/send         -> {to, text}
 *   POST   /sessions/:id/send-media   -> {to, type, mediaUrl|mediaBase64, mimeType, fileName, caption}
 *   POST   /sessions/:id/check-numbers-> {numbers:[...]} -> [{number, exists, jid}]
 *
 * Statuts de session : DECONNECTE | CONNEXION | QR | CONNECTE
 * Santé de réception (indépendante du statut) : OK | DEGRADED
 *   DEGRADED = socket ouvert (l'envoi marche) mais des messages entrants
 *   arrivent illisibles (session de chiffrement désynchronisée après une
 *   coupure) → les réponses des clients se perdent, il faut reconnecter.
 *   Remontée via le callback /status ({status, health, reason}).
 */
'use strict';

import './quiet.js'; // doit précéder l'import de Baileys (surcharge console)
import express from 'express';
import pino from 'pino';
import QRCode from 'qrcode';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';
import * as baileys from '@whiskeysockets/baileys';

// Destructuration tolérante (l'API Baileys évolue selon les versions).
const makeWASocket = baileys.default || baileys.makeWASocket;
const useMultiFileAuthState = baileys.useMultiFileAuthState;
const fetchLatestBaileysVersion = baileys.fetchLatestBaileysVersion;
const makeInMemoryStore = baileys.makeInMemoryStore; // peut être absent
const makeCacheableSignalKeyStore = baileys.makeCacheableSignalKeyStore; // peut être absent
const DisconnectReason = baileys.DisconnectReason || {};

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

/**
 * Charge un fichier `.env` posé à côté de server.js (sans dépendance externe).
 * Évite le piège classique : sous Windows, un `set VAR=...` ne vaut que pour la
 * fenêtre courante — rouvrir une invite fait perdre la configuration et les
 * messages entrants ne sont alors plus transmis à l'application.
 * Une vraie variable d'environnement reste prioritaire sur le fichier.
 */
function chargerEnvFichier() {
  const f = path.join(__dirname, '.env');
  if (!fs.existsSync(f)) { return false; }
  for (const ligne of fs.readFileSync(f, 'utf8').split(/\r?\n/)) {
    const t = ligne.trim();
    if (!t || t.startsWith('#')) { continue; }
    const i = t.indexOf('=');
    if (i <= 0) { continue; }
    const cle = t.slice(0, i).trim();
    let val = t.slice(i + 1).trim();
    if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
      val = val.slice(1, -1);
    }
    if (!process.env[cle]) { process.env[cle] = val; }
  }
  return true;
}
const ENV_FICHIER = chargerEnvFichier();

const PORT = process.env.PORT || 3000;
const API_TOKEN = process.env.WA_WEB_TOKEN || '';
const DATA_DIR = process.env.WA_WEB_DATA || path.join(__dirname, 'data');
// Base UbiSenderPro pour renvoyer les messages entrants / l'état (ex. http://localhost:8080/ubisenderpro)
const CALLBACK = (process.env.UBISENDER_CALLBACK || '').replace(/\/+$/, '');
const logger = pino({ level: process.env.LOG_LEVEL || 'info' });

/** Notifie UbiSenderPro d'un événement (message entrant, statut). Best-effort. */
async function postCallback(chemin, body) {
  if (!CALLBACK) { logger.warn('UBISENDER_CALLBACK non défini : événement ' + chemin + ' ignoré'); return; }
  try {
    const resp = await fetch(CALLBACK + '/api/v1/webhooks/wa-web' + chemin, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Api-Token': API_TOKEN },
      body: JSON.stringify(body)
    });
    if (!resp.ok) {
      logger.warn('Callback ' + chemin + ' -> HTTP ' + resp.status + ' (' + (CALLBACK + '/api/v1/webhooks/wa-web' + chemin) + ')');
    } else {
      logger.info('Callback ' + chemin + ' OK');
    }
  } catch (e) { logger.warn('Callback ' + chemin + ' injoignable : ' + (e.message || e)); }
}

function texteMessage(m) {
  var msg = m && m.message; if (!msg) { return null; }
  if (msg.conversation) { return { type: 'TEXTE', text: msg.conversation }; }
  if (msg.extendedTextMessage && msg.extendedTextMessage.text) { return { type: 'TEXTE', text: msg.extendedTextMessage.text }; }
  if (msg.imageMessage) { return { type: 'IMAGE', text: msg.imageMessage.caption || '[image]' }; }
  if (msg.videoMessage) { return { type: 'VIDEO', text: msg.videoMessage.caption || '[vidéo]' }; }
  if (msg.documentMessage) { return { type: 'DOCUMENT', text: msg.documentMessage.fileName || '[document]' }; }
  if (msg.audioMessage) { return { type: 'AUDIO', text: '[audio]' }; }
  return { type: 'TEXTE', text: '[message]' };
}

if (!fs.existsSync(DATA_DIR)) { fs.mkdirSync(DATA_DIR, { recursive: true }); }

/** sessions: id -> { sock, status, qr, me, starting } */
const sessions = new Map();

function sessionDir(id) { return path.join(DATA_DIR, 'session-' + id); }

function jidOf(numero) {
  const clean = String(numero).replace(/[^0-9]/g, '');
  return clean + '@s.whatsapp.net';
}

/**
 * Extrait le numéro de téléphone d'une clé de message. WhatsApp peut adresser
 * en @lid (numéro masqué) ; le vrai numéro (@s.whatsapp.net) est alors dans un
 * champ alternatif (remoteJidAlt, senderPn, participant…).
 */
function phoneFromKey(k) {
  if (!k) { return null; }
  const cands = [k.remoteJid, k.remoteJidAlt, k.senderPn, k.participantPn, k.participant, k.participantAlt];
  for (const c of cands) {
    if (c && typeof c === 'string' && c.endsWith('@s.whatsapp.net')) { return c.split('@')[0]; }
  }
  return null;
}

/**
 * Résout le JID réel d'un numéro via WhatsApp. Renvoie null si le numéro
 * n'est pas sur WhatsApp (ou format invalide) — évite les faux « envoyés ».
 */
/** Mémorise un message envoyé (pour répondre aux retry receipts). Borne la taille. */
function rememberSent(s, r) {
  if (!s || !s.sent || !r || !r.key || !r.key.id || !r.message) { return; }
  s.sent.set(r.key.id, r.message);
  if (s.sent.size > 1000) {
    const first = s.sent.keys().next().value;
    s.sent.delete(first);
  }
}

async function resolveJid(sock, numero) {
  const clean = String(numero).replace(/[^0-9]/g, '');
  if (clean.length < 6) { return null; }
  try {
    const r = await sock.onWhatsApp(clean);
    if (r && r[0] && r[0].exists) { return r[0].jid; }
  } catch (e) { /* ignore */ }
  return null;
}

function publicState(s) {
  return s ? { status: s.status, health: s.health || 'OK', reason: s.degradedReason || null,
               qr: s.qr || null, me: s.me || null,
               lastInboundAt: s.lastInboundAt || null, undecipherable: s.undecipherable || 0 }
           : { status: 'DECONNECTE', health: 'OK', reason: null, qr: null, me: null,
               lastInboundAt: null, undecipherable: 0 };
}

/** Échecs de déchiffrement consécutifs avant de déclarer la session dégradée. */
const SEUIL_ECHECS = 3;
/** Délai sans AUCUNE réception lisible avant de pouvoir déclarer la session dégradée. */
const DELAI_SANS_RECEPTION_MS = 60000;

/**
 * Passe la session en « dégradée » : le socket est ouvert (l'envoi marche) mais
 * des messages entrants arrivent illisibles → les réponses des clients sont
 * silencieusement perdues, la session doit être reconnectée (rescan du QR).
 *
 * Tolérance aux incidents passagers : WhatsApp re-livre souvent un message
 * illisible une seconde plus tard, et il arrive alors correctement. On ne
 * déclare donc « dégradé » qu'après plusieurs échecs CONSÉCUTIFS (compteur
 * remis à zéro à chaque réception lisible) ET en l'absence de toute réception
 * saine récente — le cas réel de la session « zombie », où plus rien n'arrive.
 * Ne notifie qu'au basculement OK -> DEGRADED (évite le flood).
 */
function marquerDegrade(id, s, raison) {
  if (!s) { return; }
  s.undecipherable = (s.undecipherable || 0) + 1;
  s.echecsConsecutifs = (s.echecsConsecutifs || 0) + 1;
  if (s.health === 'DEGRADED') { return; }

  const depuisReception = Date.now() - (s.lastInboundAt || 0);
  if (s.echecsConsecutifs < SEUIL_ECHECS || depuisReception < DELAI_SANS_RECEPTION_MS) {
    // Incident probablement passager : on trace sans alerter l'utilisateur.
    logger.info({ id, echecsConsecutifs: s.echecsConsecutifs },
      'Entrant illisible (incident passager, pas d\'alerte)');
    return;
  }
  s.health = 'DEGRADED';
  s.degradedReason = raison;
  logger.warn({ id, undecipherable: s.undecipherable, echecsConsecutifs: s.echecsConsecutifs },
    'Session dégradée : messages entrants illisibles');
  postCallback('/status', { sessionId: id, status: s.status, health: 'DEGRADED', reason: raison });
}

/** Réception saine : la session reçoit à nouveau des messages lisibles. */
function marquerSain(id, s) {
  if (!s) { return; }
  s.lastInboundAt = Date.now();
  s.echecsConsecutifs = 0; // la réception fonctionne : la série d'échecs est rompue
  if (s.health === 'DEGRADED') {
    s.health = 'OK';
    s.degradedReason = null;
    logger.info({ id }, 'Session rétablie : réception de nouveau lisible');
    postCallback('/status', { sessionId: id, status: s.status, health: 'OK', reason: null });
  }
}

/** Démarre (ou relance) une session Baileys et câble les événements. */
async function startSession(id) {
  let s = sessions.get(id);
  // Évite d'ouvrir un 2e socket sur les mêmes clés (cause de « Bad MAC »).
  if (s && (s.starting || (s.sock && s.status !== 'DECONNECTE'))) { return s; }

  s = s || {};
  s.starting = true;
  s.status = 'CONNEXION';
  s.qr = null;
  sessions.set(id, s);

  const { state, saveCreds } = await useMultiFileAuthState(sessionDir(id));
  let version;
  try { ({ version } = await fetchLatestBaileysVersion()); } catch (e) { /* défaut interne */ }

  // Cache en mémoire des clés Signal : évite que la session de chiffrement soit
  // « manquée » puis reconstruite (pendingPreKey) à chaque envoi — cause du
  // « En attente de ce message… » et de la distribution silencieuse.
  const authKeys = (typeof makeCacheableSignalKeyStore === 'function')
    ? makeCacheableSignalKeyStore(state.keys, pino({ level: 'silent' }))
    : state.keys;
  const auth = { creds: state.creds, keys: authKeys };

  // Cache des messages envoyés : indispensable pour répondre aux « retry receipts »
  // (sinon le destinataire reste bloqué sur « En attente de ce message… »).
  if (!s.sent) { s.sent = new Map(); }
  if (!s.retryCache) {
    s.retryCache = {
      _m: new Map(),
      get(k) { return this._m.get(k); },
      set(k, v) { this._m.set(k, v); },
      del(k) { this._m.delete(k); },
      flushAll() { this._m.clear(); }
    };
  }

  const sock = makeWASocket({
    version,
    auth,
    printQRInTerminal: false,
    logger: pino({ level: 'silent' }),
    browser: ['UbiSenderPro', 'Chrome', '1.0.0'],
    msgRetryCounterCache: s.retryCache,
    // Permet à Baileys de ré-émettre un message qu'un destinataire n'a pas pu déchiffrer.
    getMessage: async (key) => {
      try {
        if (key && key.id && s.sent.has(key.id)) { return s.sent.get(key.id); }
        if (s.store && typeof s.store.loadMessage === 'function' && key) {
          const m = await s.store.loadMessage(key.remoteJid, key.id);
          if (m && m.message) { return m.message; }
        }
      } catch (e) { /* ignore */ }
      return undefined;
    }
  });
  s.sock = sock;
  s.starting = false;

  // Store en mémoire : capture contacts/chats/groupes pour l'extraction.
  if (!s.store && typeof makeInMemoryStore === 'function') {
    try { s.store = makeInMemoryStore({ logger: pino({ level: 'silent' }) }); }
    catch (e) { s.store = null; }
  }
  if (s.store) { try { s.store.bind(sock.ev); } catch (e) { /* ignore */ } }

  sock.ev.on('creds.update', saveCreds);

  // Messages entrants -> remontée vers UbiSenderPro (réponses des clients).
  sock.ev.on('messages.upsert', (ev) => {
    if (!ev || ev.type !== 'notify' || !Array.isArray(ev.messages)) { return; }
    for (const m of ev.messages) {
      if (!m || !m.key || m.key.fromMe) { continue; }
      const k = m.key;
      const jid = k.remoteJid || '';
      if (jid.endsWith('@g.us') || jid.endsWith('@broadcast')) { continue; } // ignore groupes/diffusions
      // Message entrant NON déchiffrable (m.message absent) : après une longue
      // coupure, la session de chiffrement peut être désynchronisée — le socket
      // reste « ouvert » mais les réponses arrivent illisibles et se perdent.
      // On ne les jette plus en silence : on bascule la session en « dégradée »
      // pour avertir l'utilisateur (bannière « à reconnecter »).
      if (!m.message) {
        marquerDegrade(id, sessions.get(id), 'Messages entrants illisibles (session de chiffrement désynchronisée) — reconnectez le compte.');
        logger.warn({ id, key: k, stub: m.messageStubType }, 'Entrant illisible (déchiffrement échoué)');
        continue;
      }
      const phone = phoneFromKey(k);
      if (!phone) {
        // Numéro introuvable (souvent @lid) : on logue la clé pour localiser le champ.
        logger.warn({ id, key: k }, 'Entrant sans numéro résolu');
        continue;
      }
      const contenu = texteMessage(m);
      if (!contenu) { continue; }
      marquerSain(id, sessions.get(id)); // réception lisible : la session va bien
      logger.info({ id, from: phone, type: contenu.type }, 'Message entrant');
      postCallback('/message', {
        sessionId: id, from: phone, name: m.pushName || null,
        type: contenu.type, text: contenu.text, id: k.id
      });
    }
  });

  sock.ev.on('connection.update', async (update) => {
    const { connection, lastDisconnect, qr } = update;
    if (qr) {
      s.status = 'QR';
      try { s.qr = await QRCode.toDataURL(qr); } catch (e) { s.qr = null; }
      logger.info({ id }, 'QR généré');
    }
    if (connection === 'open') {
      s.status = 'CONNECTE';
      s.qr = null;
      // Nouvelle connexion (ou rescan) : la santé repart de zéro.
      s.health = 'OK';
      s.degradedReason = null;
      s.undecipherable = 0;
      s.echecsConsecutifs = 0;
      s.me = sock.user ? { id: sock.user.id, name: sock.user.name } : null;
      logger.info({ id, me: s.me }, 'Session connectée');
      postCallback('/status', { sessionId: id, status: 'CONNECTE', health: 'OK', reason: null });
    }
    if (connection === 'close') {
      const code = lastDisconnect && lastDisconnect.error
        && lastDisconnect.error.output && lastDisconnect.error.output.statusCode;
      const loggedOut = code === DisconnectReason.loggedOut;
      s.sock = null; // libère le socket fermé (sinon la garde anti-doublon bloque la reconnexion)
      s.status = loggedOut ? 'DECONNECTE' : 'CONNEXION';
      s.qr = null;
      s.health = 'OK'; // hors connexion, la « santé de réception » n'a plus de sens
      s.degradedReason = null;
      logger.warn({ id, code, loggedOut }, 'Connexion fermée');
      postCallback('/status', { sessionId: id, status: s.status, health: 'OK', reason: null });
      if (!loggedOut) {
        setTimeout(() => { startSession(id).catch((e) => logger.error(e)); }, 2000);
      } else {
        try { fs.rmSync(sessionDir(id), { recursive: true, force: true }); } catch (e) { /* ignore */ }
        sessions.delete(id);
      }
    }
  });

  return s;
}

async function bufferFromMedia(body) {
  if (body.mediaBase64) { return Buffer.from(body.mediaBase64, 'base64'); }
  if (body.mediaUrl) {
    const resp = await fetch(body.mediaUrl);
    if (!resp.ok) { throw new Error('Téléchargement média HTTP ' + resp.status); }
    return Buffer.from(await resp.arrayBuffer());
  }
  throw new Error('Aucun média fourni (mediaUrl ou mediaBase64)');
}

function contenuMedia(type, buffer, body) {
  const caption = body.caption || undefined;
  switch ((type || 'image').toLowerCase()) {
    case 'video':    return { video: buffer, caption };
    case 'audio':    return { audio: buffer, mimetype: body.mimeType || 'audio/mpeg' };
    case 'document': return { document: buffer, mimetype: body.mimeType || 'application/octet-stream',
                              fileName: body.fileName || 'fichier', caption };
    default:         return { image: buffer, caption };
  }
}

/* ----------------------------- API REST ----------------------------- */
const app = express();
app.use(express.json({ limit: '60mb' }));

// Authentification par token partagé.
app.use((req, res, next) => {
  if (req.path === '/health') { return next(); }
  if (API_TOKEN && req.get('X-Api-Token') !== API_TOKEN) {
    return res.status(401).json({ erreur: 'Token invalide' });
  }
  next();
});

app.get('/health', (req, res) => res.json({ ok: true }));

app.post('/sessions/:id/start', async (req, res) => {
  try {
    const s = await startSession(req.params.id);
    res.json(publicState(s));
  } catch (e) { res.status(500).json({ erreur: String(e.message || e) }); }
});

app.get('/sessions/:id/status', (req, res) => {
  res.json(publicState(sessions.get(req.params.id)));
});

app.post('/sessions/:id/logout', async (req, res) => {
  const s = sessions.get(req.params.id);
  try { if (s && s.sock) { await s.sock.logout(); } } catch (e) { /* ignore */ }
  try { fs.rmSync(sessionDir(req.params.id), { recursive: true, force: true }); } catch (e) { /* ignore */ }
  sessions.delete(req.params.id);
  res.json({ status: 'DECONNECTE' });
});

function requireConnected(req, res) {
  const s = sessions.get(req.params.id);
  if (!s || s.status !== 'CONNECTE' || !s.sock) {
    res.status(409).json({ erreur: 'Session non connectée' });
    return null;
  }
  return s;
}

app.post('/sessions/:id/send', async (req, res) => {
  const s = requireConnected(req, res); if (!s) { return; }
  try {
    const jid = await resolveJid(s.sock, req.body.to);
    logger.info({ id: req.params.id, to: req.body.to, resolved: jid }, 'Envoi texte');
    if (!jid) { return res.json({ success: false, erreur: 'Numéro absent de WhatsApp ou format invalide (attendu : international, ex. 22501020304)' }); }
    const r = await s.sock.sendMessage(jid, { text: String(req.body.text || '') });
    rememberSent(s, r);
    res.json({ success: true, id: r && r.key ? r.key.id : null, waNumber: jid.split('@')[0] });
  } catch (e) { logger.warn('Envoi texte échec : ' + (e.message || e)); res.status(502).json({ success: false, erreur: String(e.message || e) }); }
});

app.post('/sessions/:id/send-media', async (req, res) => {
  const s = requireConnected(req, res); if (!s) { return; }
  try {
    const jid = await resolveJid(s.sock, req.body.to);
    logger.info({ id: req.params.id, to: req.body.to, resolved: jid }, 'Envoi média');
    if (!jid) { return res.json({ success: false, erreur: 'Numéro absent de WhatsApp ou format invalide (attendu : international, ex. 22501020304)' }); }
    const buffer = await bufferFromMedia(req.body);
    const r = await s.sock.sendMessage(jid, contenuMedia(req.body.type, buffer, req.body));
    rememberSent(s, r);
    res.json({ success: true, id: r && r.key ? r.key.id : null, waNumber: jid.split('@')[0] });
  } catch (e) { logger.warn('Envoi média échec : ' + (e.message || e)); res.status(502).json({ success: false, erreur: String(e.message || e) }); }
});

app.post('/sessions/:id/check-numbers', async (req, res) => {
  const s = requireConnected(req, res); if (!s) { return; }
  const numbers = Array.isArray(req.body.numbers) ? req.body.numbers : [];
  const out = [];
  for (const n of numbers) {
    try {
      const clean = String(n).replace(/[^0-9]/g, '');
      const r = await s.sock.onWhatsApp(clean);
      const hit = r && r[0];
      out.push({ number: n, exists: !!(hit && hit.exists), jid: hit ? hit.jid : null });
    } catch (e) { out.push({ number: n, exists: false, jid: null, erreur: String(e.message || e) }); }
  }
  res.json({ results: out });
});

// ----- Extraction (Phase 4) -----

app.get('/sessions/:id/contacts', (req, res) => {
  const s = requireConnected(req, res); if (!s) { return; }
  const contacts = (s.store && s.store.contacts) || {};
  const out = [];
  for (const jid of Object.keys(contacts)) {
    if (!jid.endsWith('@s.whatsapp.net')) { continue; }
    const c = contacts[jid] || {};
    out.push({
      numero: jid.split('@')[0],
      nom: c.name || c.notify || c.verifiedName || null
    });
  }
  res.json({ total: out.length, contacts: out });
});

app.get('/sessions/:id/groups', async (req, res) => {
  const s = requireConnected(req, res); if (!s) { return; }
  try {
    const map = await s.sock.groupFetchAllParticipating();
    const out = Object.values(map || {}).map((g) => ({
      jid: g.id, nom: g.subject || null,
      taille: (g.participants || []).length
    }));
    res.json({ total: out.length, groups: out });
  } catch (e) { res.status(502).json({ erreur: String(e.message || e) }); }
});

app.get('/sessions/:id/groups/:jid/participants', async (req, res) => {
  const s = requireConnected(req, res); if (!s) { return; }
  try {
    const meta = await s.sock.groupMetadata(req.params.jid);
    const out = (meta.participants || []).map((p) => ({
      numero: String(p.id).split('@')[0],
      admin: p.admin || null
    }));
    res.json({ jid: req.params.jid, nom: meta.subject || null, total: out.length, participants: out });
  } catch (e) { res.status(502).json({ erreur: String(e.message || e) }); }
});

/** Reconnecte automatiquement les sessions persistées au démarrage. */
function restoreSessions() {
  try {
    for (const d of fs.readdirSync(DATA_DIR)) {
      if (d.startsWith('session-')) {
        const id = d.substring('session-'.length);
        logger.info({ id }, 'Restauration de session');
        startSession(id).catch((e) => logger.error(e));
      }
    }
  } catch (e) { logger.warn(String(e.message || e)); }
}

/**
 * Contrôle de configuration au démarrage, affiché en clair : sans
 * UBISENDER_CALLBACK, les messages entrants sont reçus mais JAMAIS transmis à
 * l'application (les réponses n'apparaissent pas dans les Discussions).
 */
function verifierConfiguration() {
  logger.info('Configuration : ' + (ENV_FICHIER ? 'fichier .env chargé' : 'aucun fichier .env (variables d\'environnement seules)'));
  if (!CALLBACK) {
    console.error('\n' + '='.repeat(72));
    console.error('  ERREUR DE CONFIGURATION : UBISENDER_CALLBACK n\'est pas defini.');
    console.error('  Les reponses des clients seront RECUES mais NON transmises a');
    console.error('  l\'application : elles n\'apparaitront PAS dans les Discussions.');
    console.error('');
    console.error('  Corrigez en creant un fichier .env a cote de server.js :');
    console.error('      UBISENDER_CALLBACK=http://localhost:8080/ubisenderpro');
    console.error('      WA_WEB_TOKEN=un-secret-partage');
    console.error('  (ou lancez le service avec demarrer.bat)');
    console.error('='.repeat(72) + '\n');
  } else {
    logger.info('Callback UbiSmartCRM Pro : ' + CALLBACK);
  }
  if (!API_TOKEN) {
    logger.warn('WA_WEB_TOKEN non defini : l\'API interne est OUVERTE et les callbacks '
      + 'partent sans jeton (l\'application peut les refuser).');
  }
}

app.listen(PORT, () => {
  logger.info('UbiSenderPro WA-Web sur le port ' + PORT);
  verifierConfiguration();
  restoreSessions();
});
