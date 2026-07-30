/*
 * UbiSenderPro - Filtre de bruit console pour libsignal/Baileys.
 *
 * Importé EN PREMIER (avant Baileys) afin que la surcharge de console soit
 * en place avant que libsignal ne capture éventuellement sa référence.
 *
 * libsignal écrit directement sur la console des messages non fatals liés au
 * ré-encliquetage des sessions de chiffrement (« Closing session », dumps
 * SessionEntry) et des échecs de déchiffrement intermittents (« Bad MAC »).
 * Ces lignes sont normales sur WhatsApp Web et polluent la console.
 */
'use strict';

const MARQUEURS = [
  'Bad MAC',
  'Failed to decrypt message',
  'Closing open session',
  'Closing session',
  'SessionEntry',
  'Removing old closed session',
  'Session error'
];

function bruit(args) {
  if (!args || !args.length) { return false; }
  for (let i = 0; i < args.length; i++) {
    const a = args[i];
    if (a == null) { continue; }
    // Argument texte : on teste les marqueurs connus.
    if (typeof a === 'string') {
      for (const m of MARQUEURS) { if (a.indexOf(m) !== -1) { return true; } }
    }
    // Argument objet de session libsignal (dump multi-lignes).
    if (typeof a === 'object' && a.constructor && a.constructor.name === 'SessionEntry') {
      return true;
    }
  }
  return false;
}

// Tous les canaux de la console sont couverts : libsignal n'utilise pas
// seulement console.log/error (les dumps SessionEntry passaient par info/warn).
for (const canal of ['log', 'error', 'info', 'warn', 'debug', 'trace']) {
  const origine = console[canal] && console[canal].bind(console);
  if (!origine) { continue; }
  console[canal] = function () { if (!bruit(arguments)) { origine.apply(null, arguments); } };
}
