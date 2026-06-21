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
 */
'use strict';

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
const DisconnectReason = baileys.DisconnectReason || {};

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PORT = process.env.PORT || 3000;
const API_TOKEN = process.env.WA_WEB_TOKEN || '';
const DATA_DIR = process.env.WA_WEB_DATA || path.join(__dirname, 'data');
const logger = pino({ level: process.env.LOG_LEVEL || 'info' });

if (!fs.existsSync(DATA_DIR)) { fs.mkdirSync(DATA_DIR, { recursive: true }); }

/** sessions: id -> { sock, status, qr, me, starting } */
const sessions = new Map();

function sessionDir(id) { return path.join(DATA_DIR, 'session-' + id); }

function jidOf(numero) {
  const clean = String(numero).replace(/[^0-9]/g, '');
  return clean + '@s.whatsapp.net';
}

/**
 * Résout le JID réel d'un numéro via WhatsApp. Renvoie null si le numéro
 * n'est pas sur WhatsApp (ou format invalide) — évite les faux « envoyés ».
 */
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
  return s ? { status: s.status, qr: s.qr || null, me: s.me || null }
           : { status: 'DECONNECTE', qr: null, me: null };
}

/** Démarre (ou relance) une session Baileys et câble les événements. */
async function startSession(id) {
  let s = sessions.get(id);
  if (s && s.starting) { return s; }
  if (s && (s.status === 'CONNECTE' || s.status === 'QR')) { return s; }

  s = s || {};
  s.starting = true;
  s.status = 'CONNEXION';
  s.qr = null;
  sessions.set(id, s);

  const { state, saveCreds } = await useMultiFileAuthState(sessionDir(id));
  let version;
  try { ({ version } = await fetchLatestBaileysVersion()); } catch (e) { /* défaut interne */ }

  const sock = makeWASocket({
    version,
    auth: state,
    printQRInTerminal: false,
    logger: pino({ level: 'silent' }),
    browser: ['UbiSenderPro', 'Chrome', '1.0.0']
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
      s.me = sock.user ? { id: sock.user.id, name: sock.user.name } : null;
      logger.info({ id, me: s.me }, 'Session connectée');
    }
    if (connection === 'close') {
      const code = lastDisconnect && lastDisconnect.error
        && lastDisconnect.error.output && lastDisconnect.error.output.statusCode;
      const loggedOut = code === DisconnectReason.loggedOut;
      s.status = loggedOut ? 'DECONNECTE' : 'CONNEXION';
      s.qr = null;
      logger.warn({ id, code, loggedOut }, 'Connexion fermée');
      if (!loggedOut) {
        setTimeout(() => { startSession(id).catch((e) => logger.error(e)); }, 3000);
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
    if (!jid) { return res.json({ success: false, erreur: 'Numéro absent de WhatsApp ou format invalide (attendu : international, ex. 22501020304)' }); }
    const r = await s.sock.sendMessage(jid, { text: String(req.body.text || '') });
    res.json({ success: true, id: r && r.key ? r.key.id : null });
  } catch (e) { res.status(502).json({ success: false, erreur: String(e.message || e) }); }
});

app.post('/sessions/:id/send-media', async (req, res) => {
  const s = requireConnected(req, res); if (!s) { return; }
  try {
    const jid = await resolveJid(s.sock, req.body.to);
    if (!jid) { return res.json({ success: false, erreur: 'Numéro absent de WhatsApp ou format invalide (attendu : international, ex. 22501020304)' }); }
    const buffer = await bufferFromMedia(req.body);
    const r = await s.sock.sendMessage(jid, contenuMedia(req.body.type, buffer, req.body));
    res.json({ success: true, id: r && r.key ? r.key.id : null });
  } catch (e) { res.status(502).json({ success: false, erreur: String(e.message || e) }); }
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

app.listen(PORT, () => logger.info('UbiSenderPro WA-Web sur le port ' + PORT));
