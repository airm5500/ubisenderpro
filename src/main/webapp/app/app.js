/*
 * UbiSenderPro - coquille ExtJS 4.2 (Phase 1)
 * Login, menu principal, grille des comptes clients et assistant d'import.
 * Cible REST : /ubisenderpro/api/v1
 */
Ext.Loader.setConfig({ enabled: true });

var Usp = {
    apiBase: 'api/v1',
    token: null,
    user: null,
    mode: 'API',   // mode d'envoi par défaut (API officielle | WEB) — chargé à la connexion
    prefixe: '225' // préfixe pays par défaut — chargé à la connexion
};

/* Logo WhatsApp (SVG en data-URI) pour l'entrée de menu « WhatsApp Web ». */
Usp.ICON_WA = '<img src="data:image/svg+xml,' +
    "%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 32 32'%3E" +
    "%3Cpath fill='%2325d366' d='M16 0a16 16 0 0 0-13.7 24.2L0 32l8-2.1A16 16 0 1 0 16 0z'/%3E" +
    "%3Cpath fill='%23fff' d='M12 8c-.3-.7-.6-.7-.9-.7h-.8c-.3 0-.7.1-1 .5-.4.4-1.3 1.3-1.3 " +
    "3.1s1.4 3.6 1.5 3.9c.2.3 2.6 4.1 6.4 5.6 3.2 1.2 3.8 1 4.5.9.7-.1 2.2-.9 2.5-1.7.3-.9.3-1.6.2-1.7" +
    "-.1-.2-.3-.2-.7-.4-.4-.2-2.2-1.1-2.5-1.2-.3-.1-.6-.2-.8.2-.2.3-.9 1.2-1.1 1.4-.2.2-.4.2-.7.1" +
    "-.4-.2-1.6-.6-3-1.8-1.1-1-1.8-2.2-2.1-2.6-.2-.4 0-.6.2-.7.2-.2.4-.4.5-.6.2-.2.2-.3.4-.6.1-.2.1-.4 0-.6" +
    "-.1-.2-.8-1.9-1-2.6z'/%3E%3C/svg%3E" +
    '" style="width:15px;height:15px;vertical-align:middle"/>';

/* Normalise un numéro : chiffres seuls ; préfixe pays ajouté si saisie locale. */
Usp.normNumero = function (n) {
    var d = String(n || '').replace(/[^0-9]/g, '');
    if (!d) { return ''; }
    var p = Usp.prefixe || '';
    if (p && d.indexOf(p) !== 0 && d.length <= 10) {
        d = p + d.replace(/^0+/, '');
    }
    return d;
};

/* Applique une icône d'application personnalisée (favicon). Vide = garde l'icône par défaut. */
Usp.appliquerFavicon = function (url) {
    if (!url) { return; }
    var lien = document.querySelector('link[rel="icon"]');
    if (!lien) {
        lien = document.createElement('link');
        lien.rel = 'icon';
        document.head.appendChild(lien);
    }
    lien.href = url;
};

/* Logo UbiSenderPro (avion en papier, SVG blanc) pour l'entête. */
Usp.LOGO = '<img src="data:image/svg+xml,' +
    "%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='white'%3E" +
    "%3Cpath d='M2 21l21-9L2 3v7l15 2-15 2z'/%3E%3C/svg%3E" +
    '" style="width:20px;height:20px;vertical-align:middle"/>';

/* Déclenche le téléchargement d'un contenu base64 (ex. .docx exporté). */
Usp.telechargerBase64 = function (nomFichier, base64, mime) {
    var bin = atob(base64), len = bin.length, arr = new Uint8Array(len);
    for (var i = 0; i < len; i++) { arr[i] = bin.charCodeAt(i); }
    var blob = new Blob([arr], { type: mime || 'application/octet-stream' });
    var url = URL.createObjectURL(blob);
    var a = document.createElement('a');
    a.href = url; a.download = nomFichier || 'fichier';
    document.body.appendChild(a); a.click(); document.body.removeChild(a);
    setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
};

/* Bip d'alerte (Web Audio) — utilisé à l'arrivée d'une escalade du bot. */
Usp.beep = function () {
    try {
        var AC = window.AudioContext || window.webkitAudioContext;
        if (!AC) { return; }
        if (!Usp._audioCtx) { Usp._audioCtx = new AC(); }
        var ctx = Usp._audioCtx;
        var jouer = function (freq, debut, duree) {
            var o = ctx.createOscillator(), g = ctx.createGain();
            o.type = 'sine'; o.frequency.value = freq;
            o.connect(g); g.connect(ctx.destination);
            g.gain.setValueAtTime(0.001, ctx.currentTime + debut);
            g.gain.exponentialRampToValueAtTime(0.25, ctx.currentTime + debut + 0.02);
            g.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + debut + duree);
            o.start(ctx.currentTime + debut); o.stop(ctx.currentTime + debut + duree);
        };
        jouer(880, 0, 0.18);
        jouer(1175, 0.2, 0.22); // deux notes : « ding-dong »
    } catch (e) { /* audio indisponible : on ignore */ }
};

/* Notification éphémère (toast) en bas à droite — confirmation d'action (#8).
   type : 'success' (vert, défaut) | 'error' (rouge) | 'info' (bleu). */
Usp.toast = function (message, type) {
    var couleurs = { success: '#2e7d32', error: '#c62828', info: '#1976d2' };
    var icones = { success: '✓', error: '⚠', info: 'ℹ' };
    var bg = couleurs[type] || couleurs.success;
    var ic = icones[type] || icones.success;
    var el = Ext.DomHelper.append(Ext.getBody(), {
        tag: 'div', cls: 'usp-toast',
        html: '<span style="font-weight:bold;margin-right:8px">' + ic + '</span>' + Ext.String.htmlEncode(message || ''),
        style: 'position:fixed;z-index:99999;right:18px;bottom:18px;max-width:340px;background:' + bg +
            ';color:#fff;padding:12px 18px;border-radius:8px;box-shadow:0 4px 16px rgba(0,0,0,.28);' +
            'font-family:sans-serif;font-size:13px;opacity:0;transition:opacity .25s ease,transform .25s ease;' +
            'transform:translateY(12px)'
    }, true);
    el.dom.offsetWidth; // force le reflow avant la transition
    el.setStyle({ opacity: 1, transform: 'translateY(0)' });
    Ext.defer(function () {
        el.setStyle({ opacity: 0, transform: 'translateY(12px)' });
        Ext.defer(function () { el.remove(); }, 300);
    }, 2800);
};

/* Message standard « X créé/modifié avec succès » selon le contexte (#8). */
Usp.toastEnregistre = function (libelle, modification) {
    Usp.toast(libelle + (modification ? ' modifié avec succès.' : ' créé avec succès.'));
};

/* Avatar rond du header (#3) : photo de l'utilisateur, ou cadre rond vide. */
Usp.avatarRond = function (photo) {
    if (photo) {
        return '<span class="usp-avatar"><img src="' + photo + '" alt=""/></span>';
    }
    return '<span class="usp-avatar usp-avatar--empty" title="Aucune photo"></span>';
};

/* Animation « lettre par lettre » (vague pl-letter-wave) — partagée entre le
   login plein écran et le branding du header. Reprise fidèlement de prestige. */
Usp.hasClass = function (t, c) { return (' ' + t.className + ' ').indexOf(' ' + c + ' ') > -1; };
Usp.renderAnimatedLetters = function (target, text, modifier) {
    var safeText = text || '', tokens = safeText.split(/(\s+)/), letterIndex = 0;
    target.innerHTML = '';
    if (modifier && !Usp.hasClass(target, modifier)) {
        target.className = (target.className + ' ' + modifier).replace(/\s+/g, ' ');
    }
    for (var i = 0; i < tokens.length; i += 1) {
        if (/^\s+$/.test(tokens[i])) { target.appendChild(document.createTextNode(' ')); continue; }
        var word = document.createElement('span');
        word.className = 'pl-animated-word';
        for (var j = 0; j < tokens[i].length; j += 1) {
            var letter = document.createElement('span');
            letter.className = 'pl-animated-letter';
            letter.style.cssText = '--letter-index:' + letterIndex + ';';
            letter.textContent = tokens[i].charAt(j);
            word.appendChild(letter);
            letterIndex += 1;
        }
        target.appendChild(word);
    }
};

/* ---------- Appels REST avec jeton de session ---------- */
Usp.ajax = function (options) {
    options.url = Usp.apiBase + options.url;
    options.headers = options.headers || {};
    if (Usp.token) {
        options.headers['Authorization'] = 'Bearer ' + Usp.token;
    }
    if (options.jsonData && typeof options.jsonData === 'object') {
        options.headers['Content-Type'] = 'application/json';
    }
    Ext.Ajax.request(options);
};

/* ---------- Personnalisations globales UI (exécutées au chargement) ---------- */
(function () {
    // Pagination en français (toutes les grilles paginées).
    if (Ext.toolbar && Ext.toolbar.Paging) {
        Ext.override(Ext.toolbar.Paging, {
            displayMsg: 'Affichage {0} - {1} sur {2}',
            emptyMsg: 'Aucune donnée à afficher',
            beforePageText: 'Page',
            afterPageText: 'sur {0}',
            firstText: 'Première page', prevText: 'Page précédente',
            nextText: 'Page suivante', lastText: 'Dernière page', refreshText: 'Actualiser'
        });
    }
    // Pastille active animée (effet « pace-maker ») + styles utilitaires.
    var css = '@keyframes uspPace{0%{transform:scale(1);opacity:1}50%{transform:scale(1.7);opacity:.45}100%{transform:scale(1);opacity:1}}'
        + '.usp-pace{color:#25d366;display:inline-block;animation:uspPace 1.2s ease-in-out infinite}'
        + '.usp-pace-tab{color:#d97757;display:inline-block;font-weight:bold;animation:uspPace 1.1s ease-in-out infinite}'
        // Boutons icône du header : animation au survol.
        + '.usp-icbtn .x-btn-inner,.usp-icbtn img{transition:transform .15s ease}'
        + '.usp-icbtn:hover .x-btn-inner,.usp-icbtn:hover img{transform:scale(1.3)}'
        + '.usp-logout-round .x-btn-button{border-radius:50%}'
        // Cloche de notifications avec activité (pace-maker) + pastille de comptage.
        + '.usp-notif-actif .x-btn-inner{animation:uspPace 1.1s ease-in-out infinite}'
        + '.usp-notif-badge{background:#c62828;color:#fff;border-radius:9px;padding:0 5px;font-size:10px;'
        + 'font-weight:bold;margin-left:3px;vertical-align:top}'
        // Icône « rafraîchir » animée (rotation) pendant un chargement.
        + '@keyframes uspSpin{from{transform:rotate(0)}to{transform:rotate(360deg)}}'
        + '.usp-spin{display:inline-block;animation:uspSpin .8s linear infinite}';
    var style = document.createElement('style');
    style.type = 'text/css';
    style.appendChild(document.createTextNode(css));
    document.getElementsByTagName('head')[0].appendChild(style);
})();

/* ---------- Export CSV / PDF (réutilisable, sans dépendance) ---------- */
Usp.export = {};

/* Colonnes exportables d'une grille (avec dataIndex et libellé, hors colonnes d'action). */
Usp.export.colonnes = function (grid) {
    return grid.columns.filter(function (c) {
        return c.dataIndex && c.text && !c.hidden;
    }).map(function (c) {
        return { d: c.dataIndex, t: Ext.String.trim(Ext.util.Format.stripTags(String(c.text))) || c.dataIndex };
    });
};

Usp.export.valeur = function (rec, d) {
    var v = rec.get(d);
    if (v === null || v === undefined) { return ''; }
    if (v === true) { return 'Oui'; }
    if (v === false) { return 'Non'; }
    if (Ext.isArray(v)) {
        return v.map(function (x) {
            return (x && (x.nom || x.code || x.libelle)) || x;
        }).join(' / ');
    }
    v = String(v);
    if (v.length >= 16 && v.charAt(10) === 'T') { v = v.replace('T', ' ').substring(0, 16); } // dates ISO
    return v;
};

Usp.export.lignes = function (cols, records) {
    return records.map(function (r) {
        return cols.map(function (c) { return Usp.export.valeur(r, c.d); });
    });
};

Usp.export.csv = function (titre, cols, records) {
    var esc = function (s) { s = String(s == null ? '' : s); return /[";\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s; };
    var out = [cols.map(function (c) { return esc(c.t); }).join(';')];
    Usp.export.lignes(cols, records).forEach(function (row) { out.push(row.map(esc).join(';')); });
    var blob = new Blob(['﻿' + out.join('\r\n')], { type: 'text/csv;charset=utf-8' });
    var a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = (titre || 'export') + '.csv';
    document.body.appendChild(a); a.click(); document.body.removeChild(a);
    URL.revokeObjectURL(a.href);
};

/* "PDF" via la boîte d'impression du navigateur (Enregistrer au format PDF). */
Usp.export.pdf = function (titre, cols, records) {
    var th = cols.map(function (c) { return '<th>' + Ext.String.htmlEncode(c.t) + '</th>'; }).join('');
    var trs = Usp.export.lignes(cols, records).map(function (row) {
        return '<tr>' + row.map(function (v) { return '<td>' + Ext.String.htmlEncode(v) + '</td>'; }).join('') + '</tr>';
    }).join('');
    var html = '<html><head><meta charset="utf-8"><title>' + Ext.String.htmlEncode(titre) + '</title>' +
        '<style>body{font-family:Arial,sans-serif;font-size:12px;margin:18px}h2{color:#1976d2;margin:0 0 4px}' +
        'table{border-collapse:collapse;width:100%}th,td{border:1px solid #ccc;padding:4px 6px;text-align:left}' +
        'th{background:#1976d2;color:#fff}tr:nth-child(even){background:#f4f6f8}</style></head><body>' +
        '<h2>' + Ext.String.htmlEncode(titre) + '</h2>' +
        '<div style="color:#666;margin-bottom:8px">' + records.length + ' ligne(s) — ' + new Date().toLocaleString() + '</div>' +
        '<table><thead><tr>' + th + '</tr></thead><tbody>' + trs + '</tbody></table>' +
        '<script>window.onload=function(){window.focus();window.print();};<\/script></body></html>';
    var w = window.open('', '_blank');
    if (!w) { Ext.Msg.alert('Export', 'Autorisez les fenêtres pop-up pour générer le PDF.'); return; }
    w.document.write(html); w.document.close();
};

/* Récupère toutes les lignes (va chercher le jeu complet si la grille est paginée). */
Usp.export.recuperer = function (grid, cb) {
    var store = grid.getStore();
    var total = store.getTotalCount ? store.getTotalCount() : store.getCount();
    if (!total || store.getCount() >= total) { cb(store.getRange()); return; }
    var proxy = store.getProxy();
    var params = Ext.apply({ start: 0, limit: 100000 }, proxy.extraParams);
    Usp.ajax({
        url: String(proxy.url).replace(Usp.apiBase, ''), method: 'GET', params: params,
        success: function (resp) {
            var data = Ext.decode(resp.responseText);
            var rows = Ext.isArray(data) ? data : (data.data || []);
            cb(rows.map(function (o) { return Ext.create(store.model, o); }));
        },
        failure: function () { cb(store.getRange()); }
    });
};

/* Bouton unique « Exporter » (menu déroulant : CSV ou PDF) pour la tbar d'une grille. */
Usp.export.boutons = function (titre) {
    var btn;
    var faire = function (format) {
        var grid = btn ? btn.up('grid') : null;
        if (!grid) { return; }
        Usp.export.recuperer(grid, function (recs) {
            var cols = Usp.export.colonnes(grid);
            if (format === 'csv') { Usp.export.csv(titre, cols, recs); } else { Usp.export.pdf(titre, cols, recs); }
        });
    };
    return [{
        text: '⬇️ Exporter', tooltip: 'Exporter « ' + titre + ' » (CSV ou PDF)',
        listeners: { afterrender: function (b) { btn = b; } },
        menu: [
            { text: '📊 CSV (Excel)', handler: function () { faire('csv'); } },
            { text: '🖨️ PDF', handler: function () { faire('pdf'); } }
        ]
    }];
};

/* ---------- Fenêtre de connexion ---------- */
Usp.showLogin = function () {
    // Réplique du login « prestige » (design plein écran, classes .pl-*),
    // branchée sur le REST /auth/login de la SPA UbiSenderPro.
    var html = [
        '<main class="pl-center">',
        '  <section class="pl-card" role="dialog" aria-labelledby="pl-title">',
        '    <span class="pl-glow pl-glow--primary" aria-hidden="true"></span>',
        '    <span class="pl-glow pl-glow--secondary" aria-hidden="true"></span>',
        '    <div class="pl-grid">',
        '      <aside class="pl-hero" aria-labelledby="pl-title">',
        '        <div class="pl-brand-row">',
        '          <div class="pl-brand">',
        '            <span class="pl-brand__mark" aria-hidden="true">✈</span>',
        '            <span class="pl-brand__text pl-animated-text--brand">UbiSenderPro</span>',
        '          </div>',
        '        </div>',
        '        <div class="pl-pharmacy-stack">',
        '          <div class="pl-pharmacy-card" id="pl-title">',
        '            <span class="pl-pharmacy-card__header">',
        '              <span class="pl-pharmacy-card__icon" aria-hidden="true">📤</span>',
        '              <span class="pl-pharmacy-card__label">Marketing Digital</span>',
        '            </span>',
        '            <strong id="pl-pharma-name" class="pl-animated-text--pharmacy">Smart CRM</strong>',
        '          </div>',
        '          <div class="pl-version-badge">UbiSenderPro · v1.0</div>',
        '        </div>',
        '      </aside>',
        '      <div class="pl-login-panel">',
        '        <div class="pl-panel-header">',
        '          <h2>Connexion</h2>',
        '          <p>Renseignez vos identifiants pour continuer.</p>',
        '        </div>',
        '        <div class="pl-col pl-col--form">',
        '          <label class="pl-label" for="str_login">Identifiant</label>',
        '          <div class="pl-input">',
        '            <span class="pl-input__icon" aria-hidden="true">👤</span>',
        '            <input id="str_login" name="str_login" type="text" autocomplete="username" autofocus placeholder="Votre identifiant"/>',
        '          </div>',
        '          <label class="pl-label" for="str_password">Mot de passe</label>',
        '          <div class="pl-input">',
        '            <span class="pl-input__icon" aria-hidden="true">🔒</span>',
        '            <input id="str_password" name="str_password" type="password" autocomplete="current-password" placeholder="Votre mot de passe"/>',
        '          </div>',
        '          <button type="button" id="login" name="login" class="pl-btn">',
        '            <span>Se connecter</span>',
        '            <span class="pl-btn__arrow" aria-hidden="true">→</span>',
        '          </button>',
        '          <div id="pl-error" style="display:none;margin-top:16px;color:#b91c1c;font-weight:800;text-align:center"></div>',
        '          <span class="pl-loader" id="loader" role="status" aria-live="polite" aria-label="Connexion en cours" style="display:none;">',
        '            <span class="pl-loader__card">',
        '              <span class="pl-loader__ring" aria-hidden="true"><span class="pl-loader__ring-core"></span></span>',
        '              <span class="pl-loader__text">Connexion en cours…</span>',
        '            </span>',
        '          </span>',
        '        </div>',
        '      </div>',
        '    </div>',
        '  </section>',
        '</main>'
    ].join('');

    var wrap = document.createElement('div');
    wrap.id = 'pl-login-root';
    wrap.className = 'pl-body';
    wrap.innerHTML = html;
    document.body.appendChild(wrap);

    // Animation lettre par lettre (repris fidèlement de prestige, helper partagé Usp.renderAnimatedLetters).
    var brand = wrap.querySelector('.pl-brand__text');
    if (brand) { Usp.renderAnimatedLetters(brand, brand.textContent, 'pl-animated-text--brand'); }
    var pharma = wrap.querySelector('#pl-pharma-name');
    if (pharma) { Usp.renderAnimatedLetters(pharma, pharma.textContent, 'pl-animated-text--pharmacy'); }

    var loader = wrap.querySelector('#loader');
    var errBox = wrap.querySelector('#pl-error');
    var loginInput = wrap.querySelector('#str_login');
    var pwdInput = wrap.querySelector('#str_password');

    function soumettre() {
        var login = (loginInput.value || '').trim();
        var motDePasse = pwdInput.value || '';
        if (!login || !motDePasse) {
            errBox.textContent = 'Veuillez saisir votre identifiant et votre mot de passe.';
            errBox.style.display = 'block';
            return;
        }
        errBox.style.display = 'none';
        loader.style.display = '';   // réaffiche le loader (CSS : grid)
        Usp.ajax({
            url: '/auth/login',
            method: 'POST',
            jsonData: { login: login, motDePasse: motDePasse },
            success: function (resp) {
                var data = Ext.decode(resp.responseText);
                Usp.token = data.token;
                Usp.user = data.user;
                var ouvrir = function () {
                    if (wrap.parentNode) { wrap.parentNode.removeChild(wrap); }
                    Usp.showMain();
                };
                // Charge les paramètres globaux (mode + préfixe + favicon) puis ouvre l'application.
                var chargerParams = function () {
                Usp.ajax({ url: '/parametres/whatsapp.mode_envoi', method: 'GET',
                    success: function (r) {
                        Usp.mode = (Ext.decode(r.responseText) || {}).valeur || 'API';
                        Usp.ajax({ url: '/parametres/whatsapp.prefixe_pays', method: 'GET',
                            success: function (r2) {
                                Usp.prefixe = (Ext.decode(r2.responseText) || {}).valeur || '225';
                                Usp.ajax({ url: '/parametres/app.favicon', method: 'GET',
                                    success: function (r3) {
                                        Usp.appliquerFavicon((Ext.decode(r3.responseText) || {}).valeur);
                                        // Société émettrice (Paramètres) : sert de valeur par défaut
                                        // pré-remplie et modifiable dans les écrans de création.
                                        Usp.ajax({ url: '/parametres/app.societe', method: 'GET',
                                            success: function (r4) {
                                                Usp.societeParDefaut = (Ext.decode(r4.responseText) || {}).valeur || '';
                                                ouvrir();
                                            }, failure: ouvrir });
                                    }, failure: ouvrir });
                            }, failure: ouvrir });
                    }, failure: ouvrir });
                };
                // Permissions effectives (menus + actions) -> pilotent menus et boutons.
                Usp.ajax({ url: '/permissions/me', method: 'GET',
                    success: function (rp) {
                        Usp.perms = Ext.decode(rp.responseText) || {};
                        chargerParams();
                    }, failure: function () { Usp.perms = null; chargerParams(); } });
            },
            failure: function () {
                loader.style.display = 'none';
                errBox.textContent = 'Identifiants invalides.';
                errBox.style.display = 'block';
            }
        });
    }

    wrap.querySelector('#login').addEventListener('click', soumettre);
    [loginInput, pwdInput].forEach(function (inp) {
        inp.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' || e.keyCode === 13) { soumettre(); }
        });
    });
    loginInput.focus();
};

/* ---------- Store des comptes clients ---------- */
Usp._clientStores = [];

Usp.createClientStore = function (actif) {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'numeroClient', 'nomCompte', 'entreprise', 'agence', 'region', 'tournee', 'commune',
                 'telephonePrincipal', 'emailPrincipal', 'statut', 'segmentationId', 'actif'],
        pageSize: 25,
        proxy: {
            type: 'ajax',
            url: Usp.apiBase + '/clients',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') },
            reader: { type: 'json', root: 'data', totalProperty: 'total' },
            extraParams: { actif: actif }
        },
        autoLoad: true
    });
    Usp._clientStores.push(store);
    return store;
};

/* Recharge les deux grilles (actifs + désactivés) après activation/désactivation. */
Usp.reloadClients = function () {
    Usp._clientStores.forEach(function (s) { s.loadPage(1); });
};

/* Combos de filtre alimentés par /clients/facettes (agences, communes). */
Usp.clientFacettes = function (combos) {
    Usp.ajax({ url: '/clients/facettes', method: 'GET', success: function (resp) {
        var d = Ext.decode(resp.responseText) || {};
        var remplir = function (cb, arr) {
            if (cb) { cb.getStore().loadData((arr || []).map(function (v) { return { v: v }; })); }
        };
        remplir(combos.agence, d.agences);
        remplir(combos.commune, d.communes);
    } });
};

/* ---------- Comptes clients : 2 onglets (liste + vérification de numéros) ---------- */
Usp.clientsPanel = function () {
    Usp._clientStores = [];
    return {
        xtype: 'tabpanel',
        title: 'Comptes clients',
        listeners: Usp.tabListeners,
        items: [
            Usp.clientsGrid(true),
            // Onglet des comptes désactivés (#10) : réactivation possible d'ici.
            Usp.clientsGrid(false),
            // Gestion des segmentations désormais en onglet (plus un bouton).
            Usp.segmentationsGrid(),
            // Gestion des listes de diffusion (création + membres).
            Usp.listesGrid(),
            // Onglet déplacé depuis « WhatsApp Web » (#4) : la vérification de
            // numéros vit désormais à côté de la liste des comptes clients.
            Usp.waweb.filterPanel()
        ]
    };
};

/* Grille des comptes clients (#10). actif=true : onglet principal (actifs) ;
   actif=false : onglet des comptes désactivés. Tri par segmentation/agence/commune. */
Usp.clientsGrid = function (actif) {
    var store = Usp.createClientStore(actif);

    // Store des segmentations partagé : sert au filtre ET au rendu de la colonne.
    var segStore = Ext.create('Ext.data.Store', { fields: ['id', 'libelle'], autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/segmentations',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } } });
    var segLib = function (id) {
        if (id === null || id === undefined || id === '') { return ''; }
        var i = segStore.findExact('id', id); return i >= 0 ? segStore.getAt(i).get('libelle') : '';
    };
    // Un filtre appliqué automatiquement à la sélection (et combinable avec les autres).
    var autoFiltre = { change: function (f) { appliquer(f.up('toolbar')); } };
    var comboSeg = { xtype: 'combobox', itemId: 'fSeg', emptyText: 'Segmentation', width: 150,
        queryMode: 'local', editable: false, valueField: 'id', displayField: 'libelle',
        store: segStore, listeners: autoFiltre };
    // Filtres Agence / Région alimentés par les référentiels (valeur = libellé).
    var refFilterStore = function (type) {
        return Ext.create('Ext.data.Store', { fields: ['id', 'code', 'libelle'], autoLoad: true,
            proxy: { type: 'ajax', url: Usp.apiBase + '/referentiels/' + type,
                headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } } });
    };
    var comboAgence = { xtype: 'combobox', itemId: 'fAgence', emptyText: 'Agence', width: 130,
        queryMode: 'local', editable: false, valueField: 'libelle', displayField: 'libelle',
        store: refFilterStore('AGENCE'), listeners: autoFiltre };
    var comboRegion = { xtype: 'combobox', itemId: 'fRegion', emptyText: 'Région', width: 130,
        queryMode: 'local', editable: false, valueField: 'libelle', displayField: 'libelle',
        store: refFilterStore('REGION'), listeners: autoFiltre };

    var appliquer = function (tb) {
        var p = { actif: actif };
        var q = tb.down('#fQ').getValue();
        var seg = tb.down('#fSeg').getValue();
        var ag = tb.down('#fAgence').getValue();
        var reg = tb.down('#fRegion').getValue();
        if (q) { p.q = q; }
        if (seg) { p.segmentationId = seg; }
        if (ag) { p.agence = ag; }
        if (reg) { p.region = reg; }
        store.getProxy().extraParams = p;
        store.loadPage(1);
    };

    var detailSpan = '<span class="cli-detail" title="Voir le détail" style="cursor:pointer;margin:0 4px">🔍</span>';
    var contactsSpan = '<span class="cli-contacts" title="Voir / ajouter les contacts" style="cursor:pointer;margin:0 4px;color:#1976d2">👥</span>';
    var actionCol = actif
        ? { text: 'Actions', width: 150, align: 'center', sortable: false, menuDisabled: true, dataIndex: 'id',
            renderer: function () {
                return detailSpan + contactsSpan +
                    '<span class="cli-edit" title="Modifier ce compte" style="cursor:pointer;margin:0 4px">✏️</span>' +
                    '<span class="cli-off" title="Désactiver ce compte" style="cursor:pointer;margin:0 4px;color:#c62828">⛔</span>';
            } }
        : { text: 'Actions', width: 150, align: 'center', sortable: false, menuDisabled: true, dataIndex: 'id',
            renderer: function () {
                return detailSpan + contactsSpan +
                    '<span class="cli-on" title="Réactiver ce compte" style="cursor:pointer;color:#2e7d32">✅ Activer</span>';
            } };

    var tbar = [];
    if (actif) {
        // « Nouveau client » placé avant la zone de recherche (demande client).
        tbar.push(
            Usp.permBtn('clients', 'CREER', { text: '➕ Nouveau client',
              tooltip: 'Créer un nouveau compte client', handler: function () { Usp.clientForm(store, null); } }),
            Usp.permBtn('clients', 'CREER', { text: '📥 Importer Excel/CSV',
              tooltip: 'Importer des comptes clients depuis un fichier', handler: Usp.showImport }),
            '-');
    }
    tbar.push(
        { xtype: 'textfield', itemId: 'fQ', emptyText: 'Rechercher...', width: 180,
          listeners: {
              // Recherche pendant la saisie (anti-rebond) + Entrée conservée.
              change: { buffer: 400, fn: function (f) { appliquer(f.up('toolbar')); } },
              specialkey: function (f, e) { if (e.getKey() === e.ENTER) { appliquer(f.up('toolbar')); } } } },
        comboSeg, comboAgence, comboRegion,
        { text: '♻️ Réinitialiser', tooltip: 'Effacer tous les filtres', handler: function (b) {
            var tb = b.up('toolbar');
            tb.down('#fQ').setValue(''); tb.down('#fSeg').setValue(null);
            tb.down('#fAgence').setValue(null); tb.down('#fRegion').setValue(null);
            store.getProxy().extraParams = { actif: actif }; store.loadPage(1);
        } });

    // Info-bulle (survol) sur code / nom / entreprise : segmentation + e-mail.
    var tip = function (v, meta, rec) {
        var seg = segLib(rec.get('segmentationId')) || '—';
        var email = rec.get('emailPrincipal') || '—';
        meta.tdAttr = 'data-qtip="' + Ext.String.htmlEncode('Segmentation : ' + seg + ' &#10; E-mail : ' + email) + '"';
        return Ext.String.htmlEncode(v || '');
    };
    return {
        xtype: 'grid',
        title: actif ? '👥 Liste des Clients' : '🚫 Clients désactivés',
        store: store,
        columns: [
            { text: 'Code client', dataIndex: 'numeroClient', width: 100, renderer: tip },
            { text: 'Nom client', dataIndex: 'nomCompte', flex: 1, renderer: tip },
            { text: 'Entreprise', dataIndex: 'entreprise', width: 160, renderer: tip },
            { text: 'Téléphone', dataIndex: 'telephonePrincipal', width: 130 },
            { text: 'Segmentation', dataIndex: 'segmentationId', width: 120,
              renderer: function (v) { return Ext.String.htmlEncode(segLib(v)); } },
            { text: 'Agence', dataIndex: 'agence', width: 120 },
            { text: 'Région', dataIndex: 'region', width: 140 },
            { text: 'Statut', dataIndex: 'statut', width: 90,
              renderer: function (v) {
                  return '<span style="color:' + (actif ? '#2e7d32' : '#c62828') + ';font-weight:bold">'
                      + Ext.String.htmlEncode(v || '') + '</span>';
              } },
            actionCol
        ],
        tbar: tbar.concat(Usp.export.boutons(actif ? 'Comptes clients' : 'Clients désactivés')),
        bbar: { xtype: 'pagingtoolbar', store: store, displayInfo: true },
        listeners: {
            // Filtres Agence/Région désormais alimentés par les référentiels (autoLoad).
            itemdblclick: function (g, rec) { if (actif) { Usp.clientForm(store, rec); } },
            cellclick: function (g, td, ci, rec, tr, ri, e) {
                if (e.getTarget('.cli-detail')) { Usp.clientDetail(rec.get('id'), segLib); return; }
                if (e.getTarget('.cli-contacts')) { Usp.contactsWindow(rec.get('id'), rec.get('nomCompte')); return; }
                if (e.getTarget('.cli-edit')) { Usp.clientForm(store, rec); return; }
                if (e.getTarget('.cli-off')) { Usp.clientActif(rec, false); return; }
                if (e.getTarget('.cli-on')) { Usp.clientActif(rec, true); return; }
            }
        }
    };
};

/* Active/désactive un compte client puis rafraîchit les deux onglets (#10). */
Usp.clientActif = function (rec, actif) {
    var nom = rec.get('nomCompte');
    var faire = function () {
        Usp.ajax({ url: '/clients/' + rec.get('id') + (actif ? '/activate' : '/deactivate'), method: 'POST',
            success: function () {
                Usp.reloadClients();
                Usp.toast('Compte « ' + nom + ' » ' + (actif ? 'réactivé' : 'désactivé') + ' avec succès.');
            },
            failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
    };
    if (actif) { faire(); return; }
    Ext.Msg.confirm('Désactiver', 'Désactiver le compte « ' + Ext.String.htmlEncode(nom) +
        ' » ? Il passera dans l\'onglet « Clients désactivés ».', function (b) { if (b === 'yes') { faire(); } });
};

/* Fiche client en lecture seule (bouton « Détail »). */
Usp.clientDetail = function (id, segLib) {
    Usp.ajax({ url: '/clients/' + id, method: 'GET', success: function (resp) {
        var c = {}; try { c = Ext.decode(resp.responseText) || {}; } catch (e) {}
        var seg = (segLib ? segLib(c.segmentationId) : '') || '—';
        var l = function (lib, val) {
            return '<tr><td style="color:#888;padding:2px 12px 2px 0;white-space:nowrap">' + lib
                + '</td><td style="font-weight:bold">' + Ext.String.htmlEncode(val || '—') + '</td></tr>';
        };
        var html = '<table style="width:100%;border-collapse:collapse">'
            + l('Code client', c.numeroClient) + l('Nom client', c.nomCompte) + l('Entreprise', c.entreprise)
            + l('Segmentation', seg) + l('E-mail', c.emailPrincipal)
            + l('Agence', c.agence) + l('Région', c.region) + l('Ville', c.ville)
            + l('Commune', c.commune) + l('Pays', c.pays) + l('Tournée', c.tournee) + l('Statut', c.statut)
            + '</table>'
            + (c.notes ? '<div style="margin-top:8px;color:#555"><b>Notes :</b><br>' + Ext.String.htmlEncode(c.notes) + '</div>' : '');
        Ext.create('Ext.window.Window', {
            title: '🔍 Détail — ' + Ext.String.htmlEncode(c.nomCompte || ''), width: 480, modal: true,
            bodyPadding: 14, autoScroll: true, items: [{ xtype: 'component', html: html }],
            buttons: [{ text: 'Fermer', handler: function (b) { b.up('window').close(); } }]
        }).show();
    } });
};

/* ---------- Gestion des segmentations (CRUD) ---------- */
/* Grille de gestion des segmentations (réutilisée en fenêtre ET en onglet des Comptes clients). */
Usp.segmentationsGrid = function () {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'code', 'libelle', 'description', 'ordreAffichage', 'actif'], autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/segmentations',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } }
    });
    var form = function (rec) {
        var win = Ext.create('Ext.window.Window', {
            title: rec ? 'Modifier la segmentation' : 'Nouvelle segmentation',
            width: 420, modal: true, bodyPadding: 12,
            items: [{ xtype: 'form', border: false, defaults: { anchor: '100%' }, items: [
                { xtype: 'textfield', name: 'code', fieldLabel: 'Code', allowBlank: false },
                { xtype: 'textfield', name: 'libelle', fieldLabel: 'Libellé', allowBlank: false },
                { xtype: 'textfield', name: 'description', fieldLabel: 'Description' },
                { xtype: 'numberfield', name: 'ordreAffichage', fieldLabel: 'Ordre d\'affichage', value: 0, minValue: 0 },
                { xtype: 'checkbox', name: 'actif', fieldLabel: 'Active', checked: true }
            ] }],
            buttons: [{ text: 'Enregistrer', handler: function (b) {
                var f = b.up('window').down('form').getForm();
                if (!f.isValid()) {
                    var m = f.getFields().findBy(function (x) { return !x.isValid(); });
                    if (m) { m.focus(true, 50); Ext.Msg.alert('Champ obligatoire', 'Renseignez : <b>' + (m.fieldLabel || m.getName()) + '</b>.'); }
                    return;
                }
                var data = f.getValues();
                data.actif = f.findField('actif').getValue();
                Usp.ajax({ url: rec ? '/segmentations/' + rec.get('id') : '/segmentations',
                    method: rec ? 'PUT' : 'POST', jsonData: data,
                    success: function () { win.close(); store.load(); },
                    failure: function (resp) {
                        var msg = 'Enregistrement impossible.';
                        try { msg = Ext.decode(resp.responseText).erreur || msg; } catch (e) {}
                        Ext.Msg.alert('Erreur', msg);
                    } });
            } }]
        });
        win.show();
        if (rec) {
            var frais = store.getById(rec.get('id')) || rec;
            win.down('form').getForm().setValues(frais.getData());
        }
    };
    return {
            xtype: 'grid', title: '🏷️ Segmentations', store: store,
            columns: [
                { text: 'Code', dataIndex: 'code', width: 120 },
                { text: 'Libellé', dataIndex: 'libelle', flex: 1 },
                { text: 'Description', dataIndex: 'description', flex: 1 },
                { text: 'Ordre', dataIndex: 'ordreAffichage', width: 70 },
                { text: 'Active', dataIndex: 'actif', width: 70, renderer: function (v) { return v ? 'Oui' : 'Non'; } },
                { text: 'Actions', width: 170, align: 'center', sortable: false, menuDisabled: true, dataIndex: 'id',
                  renderer: function () {
                      return '<span class="seg-edit" title="Modifier" style="cursor:pointer;margin:0 6px">✏️ Modifier</span>' +
                          '<span class="seg-del" title="Supprimer" style="cursor:pointer;color:#c62828;margin:0 6px">🗑️</span>';
                  } }
            ],
            tbar: [
                Usp.permBtn('clients', 'CREER', { text: 'Nouvelle segmentation', handler: function () { form(null); } }),
                { text: 'Rafraîchir', handler: function () { store.load(); } }
            ],
            listeners: {
                cellclick: function (g, td, ci, rec, tr, ri, e) {
                    if (e.getTarget('.seg-edit')) {
                        if (!Usp.can('clients', 'MODIFIER')) { Usp.refusPermission(); return; }
                        form(rec); return;
                    }
                    if (e.getTarget('.seg-del')) {
                        if (!Usp.can('clients', 'SUPPRIMER')) { Usp.refusPermission(); return; }
                        // Désactive une segmentation utilisée par des clients (au lieu de la supprimer).
                        var desactiver = function () {
                            var data = rec.getData();
                            data.actif = false;
                            Usp.ajax({ url: '/segmentations/' + rec.get('id'), method: 'PUT', jsonData: data,
                                success: function () { store.load(); },
                                failure: function (resp) {
                                    var m = 'Désactivation impossible.';
                                    try { m = Ext.decode(resp.responseText).erreur || m; } catch (ex) {}
                                    Ext.Msg.alert('Erreur', m);
                                } });
                        };
                        Ext.Msg.confirm('Supprimer', 'Supprimer la segmentation « ' + Ext.String.htmlEncode(rec.get('libelle')) + ' » ?',
                            function (btn) { if (btn === 'yes') {
                                Usp.ajax({ url: '/segmentations/' + rec.get('id'), method: 'DELETE',
                                    success: function () { store.load(); },
                                    failure: function (resp) {
                                        // Suppression refusée (clients rattachés) -> proposer la désactivation.
                                        var msg = 'Suppression impossible.';
                                        try { msg = Ext.decode(resp.responseText).erreur || msg; } catch (ex) {}
                                        Ext.Msg.show({
                                            title: 'Suppression impossible',
                                            msg: msg + '<br/><br/>Voulez-vous plutôt la <b>désactiver</b> ? '
                                                + 'Elle ne sera plus proposée pour de nouveaux clients, sans toucher aux clients existants.',
                                            buttons: Ext.Msg.YESNO,
                                            buttonText: { yes: 'Désactiver', no: 'Annuler' },
                                            icon: Ext.Msg.QUESTION,
                                            fn: function (b) { if (b === 'yes') { desactiver(); } }
                                        });
                                    } });
                            } });
                        return;
                    }
                },
                itemdblclick: function (g, rec) { form(rec); }
            }
    };
};

/* Gestion des listes de diffusion (onglet des Comptes clients) : CRUD + membres. */
Usp.listesGrid = function () {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'nom', 'description', 'actif'], autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/lists',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } }
    });
    var form = function (rec) {
        var win = Ext.create('Ext.window.Window', {
            title: rec ? 'Modifier la liste' : 'Nouvelle liste de diffusion', width: 460, modal: true, bodyPadding: 12,
            items: [{ xtype: 'form', border: false, defaults: { anchor: '100%' }, items: [
                { xtype: 'textfield', name: 'nom', fieldLabel: 'Nom', allowBlank: false },
                { xtype: 'textfield', name: 'description', fieldLabel: 'Description' },
                { xtype: 'checkbox', name: 'actif', fieldLabel: 'Active', checked: true }
            ] }],
            buttons: [{ text: 'Enregistrer', formBind: true, handler: function (b) {
                var f = b.up('window').down('form').getForm();
                if (!f.isValid()) { return; }
                var v = f.getValues();
                v.actif = f.findField('actif').getValue();
                Usp.ajax({ url: rec ? '/lists/' + rec.get('id') : '/lists', method: rec ? 'PUT' : 'POST', jsonData: v,
                    success: function () { win.close(); store.load(); Usp.toastEnregistre('Liste', !!rec); },
                    failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
            } }]
        });
        win.show();
        if (rec) { var frais = store.getById(rec.get('id')) || rec; win.down('form').getForm().setValues(frais.getData()); }
    };
    return {
        xtype: 'grid', title: '📇 Listes de diffusion', store: store,
        columns: [
            { text: 'Nom', dataIndex: 'nom', flex: 1 },
            { text: 'Description', dataIndex: 'description', flex: 1 },
            { text: 'Active', dataIndex: 'actif', width: 70, align: 'center', renderer: function (v) { return v ? '✅' : '—'; } },
            { text: 'Actions', width: 220, sortable: false, menuDisabled: true, dataIndex: 'id',
              renderer: function () {
                  return '<span class="ld-edit" title="Modifier" style="cursor:pointer;margin-right:10px">✏️ Modifier</span>' +
                      '<span class="ld-membres" title="Gérer les membres" style="cursor:pointer;color:#1976d2">👥 Membres</span>';
              } }
        ],
        tbar: [
            Usp.permBtn('clients', 'CREER', { text: '➕ Nouvelle liste', handler: function () { form(null); } }),
            { text: '🔄', tooltip: 'Rafraîchir', handler: function () { store.load(); } }
        ],
        listeners: {
            cellclick: function (g, td, ci, rec, tr, ri, e) {
                if (e.getTarget('.ld-edit')) { form(rec); }
                else if (e.getTarget('.ld-membres')) { Usp.listeMembresWindow(rec); }
            }
        }
    };
};

/* Fenêtre des membres d'une liste de diffusion : ajout (recherche) + retrait. */
Usp.listeMembresWindow = function (rec) {
    var listeId = rec.get('id');
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'nomComplet', 'numeroWhatsapp', 'telephonePrincipal'], autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/lists/' + listeId + '/contacts',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } }
    });
    // Ajout de membres via le SCL (Sélecteur de Clients) : on ajoute le contact
    // principal de chaque client coché. Les clients sans contact sont ignorés.
    var ajouterClients = function (rows) {
        var contactIds = [], sansContact = 0;
        rows.forEach(function (r) { if (r.contactId) { contactIds.push(r.contactId); } else { sansContact++; } });
        if (!contactIds.length) { Ext.Msg.alert('Info', 'Aucun contact rattaché aux clients choisis.'); return; }
        var reste = contactIds.length, erreurs = 0;
        contactIds.forEach(function (cid) {
            Usp.ajax({ url: '/lists/' + listeId + '/contacts', method: 'POST', jsonData: { contactId: cid, source: 'MANUEL' },
                success: function () { if (--reste === 0) { store.load(); Usp.toast(contactIds.length + ' membre(s) ajouté(s)' + (sansContact ? ' — ' + sansContact + ' sans contact ignoré(s).' : '.')); } },
                failure: function () { erreurs++; if (--reste === 0) { store.load(); } } });
        });
    };
    Ext.create('Ext.window.Window', {
        title: 'Membres — ' + Ext.String.htmlEncode(rec.get('nom')), width: 640, height: 460, modal: true, layout: 'fit',
        items: [{ xtype: 'grid', store: store,
            columns: [
                { text: 'Nom', dataIndex: 'nomComplet', flex: 1 },
                { text: 'WhatsApp', dataIndex: 'numeroWhatsapp', width: 150 },
                { text: '', width: 50, align: 'center', sortable: false, menuDisabled: true, dataIndex: 'id',
                  renderer: function () { return '<span class="ldm-del" title="Retirer" style="cursor:pointer;color:#c62828">🗑️</span>'; } }
            ],
            tbar: [
                { text: '➕ Choisir des clients', handler: function () {
                    Usp.clientPicker({ title: 'Ajouter des clients à la liste', boutonValider: 'Ajouter à la liste', onValider: ajouterClients }); } },
                { text: '📥 Importer des clients', tooltip: 'Importer des codes clients (un par ligne / CSV)', handler: function () {
                    Usp.importerClientsListe(listeId, function () { store.load(); }); } }
            ],
            listeners: { cellclick: function (g, td, ci, r, tr, ri, e) {
                if (e.getTarget('.ldm-del')) {
                    Usp.ajax({ url: '/lists/' + listeId + '/contacts/' + r.get('id'), method: 'DELETE',
                        success: function () { store.load(); },
                        failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
                }
            } }
        }]
    }).show();
};

/* Import de clients dans une liste de diffusion (codes clients, un par ligne ou .csv). */
Usp.importerClientsListe = function (listeId, onDone) {
    var win = Ext.create('Ext.window.Window', {
        title: 'Importer des clients dans la liste', width: 520, modal: true, bodyPadding: 12,
        items: [{ xtype: 'form', border: false, defaults: { anchor: '100%' }, items: [
            { xtype: 'displayfield', value: '<span style="color:#888">Un <b>code client</b> par ligne ' +
                '(le contact principal de chaque client est ajouté). Fichier .csv accepté.</span>' },
            { xtype: 'textareafield', name: 'contenu', height: 180, emptyText: 'C001\nC002\nC003' },
            { xtype: 'filefield', fieldLabel: 'ou fichier .csv', msgTarget: 'side',
              listeners: { change: function (f) {
                  var file = f.fileInputEl.dom.files[0]; if (!file) { return; }
                  var reader = new FileReader();
                  reader.onload = function (e) { f.up('form').down('[name=contenu]').setValue(e.target.result); };
                  reader.readAsText(file);
              } } }
        ] }],
        buttons: [{ text: 'Importer', handler: function (b) {
            var contenu = b.up('window').down('[name=contenu]').getValue();
            if (!contenu || !contenu.trim()) { Ext.Msg.alert('Info', 'Aucun code client.'); return; }
            Usp.ajax({ url: '/lists/' + listeId + '/import-clients', method: 'POST', jsonData: { contenu: contenu },
                success: function (resp) {
                    var r = Ext.decode(resp.responseText) || {};
                    win.close(); if (onDone) { onDone(); }
                    Usp.toast((r.ajoutes || 0) + ' ajouté(s), ' + (r.introuvables || 0) + ' introuvable(s), '
                        + (r.sansContact || 0) + ' sans contact.');
                },
                failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
        } }, { text: 'Annuler', handler: function (b) { b.up('window').close(); } }]
    });
    win.show();
};

/* Fenêtre de gestion des segmentations (conservée pour compatibilité). */
Usp.segmentationsManager = function () {
    Ext.create('Ext.window.Window', {
        title: 'Segmentations clients', width: 640, height: 440, modal: true, layout: 'fit',
        items: [Usp.segmentationsGrid()]
    }).show();
};

/* ---------- Formulaire client ---------- */
Usp.segmentationCombo = function (cfg) {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'libelle'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/segmentations',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
        autoLoad: true
    });
    return Ext.apply({ xtype: 'combobox', store: store, valueField: 'id', displayField: 'libelle',
        queryMode: 'local', editable: false, anchor: '100%' }, cfg || {});
};

/* Combo d'un référentiel géographique (PAYS/REGION/VILLE/COMMUNE/AGENCE).
 * La valeur stockée est le libellé ; saisie libre autorisée (forceSelection:false) :
 * une valeur nouvelle est enregistrée automatiquement dans le référentiel au save. */
Usp.referentielCombo = function (type, cfg) {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'code', 'libelle'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/referentiels/' + type,
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
        autoLoad: true
    });
    return Ext.apply({ xtype: 'combobox', store: store, valueField: 'libelle', displayField: 'libelle',
        queryMode: 'local', forceSelection: false, anchor: '100%',
        emptyText: 'Sélectionner ou saisir…' }, cfg || {});
};

/* Grille éditable des numéros d'un client (numéro + WhatsApp + principal unique). */
Usp.numerosGrid = function (store) {
    return {
        xtype: 'grid', columnWidth: 1, margin: '8 0 0 0', height: 160, store: store,
        title: '📞 Numéros du client (le 1er = principal ; on peut en ajouter plusieurs)',
        plugins: [Ext.create('Ext.grid.plugin.CellEditing', { clicksToEdit: 1 })],
        columns: [
            { text: 'Numéro (format international, ex. 2250700000000)', dataIndex: 'numero', flex: 1, editor: { xtype: 'textfield' } },
            { xtype: 'checkcolumn', text: 'WhatsApp', dataIndex: 'whatsapp', width: 90 },
            { xtype: 'checkcolumn', text: 'Principal', dataIndex: 'principal', width: 90,
              listeners: { checkchange: function (col, idx, checked) {
                  if (checked) { store.each(function (r, i) { if (i !== idx) { r.set('principal', false); } }); }
              } } }
        ],
        tbar: [
            { text: '➕ Ajouter un numéro', handler: function () {
                store.add({ numero: '', whatsapp: true, principal: store.getCount() === 0 }); } },
            { text: '🗑️ Retirer', handler: function (b) {
                var s = b.up('grid').getSelectionModel().getSelection()[0]; if (s) { store.remove(s); } } }
        ]
    };
};

Usp.clientForm = function (store, rec) {
    var numStore = Ext.create('Ext.data.Store', { fields: ['numero', 'whatsapp', 'principal'] });
    if (!rec) { numStore.add({ numero: '', whatsapp: true, principal: true }); }
    var win = Ext.create('Ext.window.Window', {
        title: rec ? 'Modifier le client' : 'Nouveau client',
        width: 780, modal: true, bodyPadding: 12, autoScroll: true,
        maxHeight: Ext.getBody().getViewSize().height - 40,
        items: [{
            xtype: 'form', border: false, layout: 'column',
            defaults: { columnWidth: 0.5, xtype: 'container', layout: 'anchor', border: false,
                        defaults: { anchor: '96%', labelWidth: 110 } },
            items: [
                { items: [
                    { xtype: 'textfield', name: 'numeroClient', fieldLabel: 'Code client *', allowBlank: false },
                    { xtype: 'textfield', name: 'nomCompte', fieldLabel: 'Nom client *', allowBlank: false,
                      listeners: Usp.majListeners },
                    { xtype: 'textfield', name: 'entreprise', fieldLabel: 'Entreprise',
                      emptyText: 'Nom de l\'officine (ex. PHCIE POLAP)', listeners: Usp.majListeners },
                    { xtype: 'textfield', name: 'emailPrincipal', fieldLabel: 'E-mail', vtype: 'email' },
                    Usp.segmentationCombo({ name: 'segmentationId', fieldLabel: 'Segmentation' }),
                    { xtype: 'datefield', name: 'dateNaissance', fieldLabel: 'Date de naissance',
                      format: 'd/m/Y', submitFormat: 'Y-m-d', editable: false, maxValue: new Date() }
                ] },
                { items: [
                    Usp.referentielCombo('AGENCE', { name: 'agence', fieldLabel: 'Agence' }),
                    Usp.referentielCombo('REGION', { name: 'region', fieldLabel: 'Région' }),
                    Usp.referentielCombo('TOURNEE', { name: 'tournee', fieldLabel: 'Tournée' }),
                    Usp.referentielCombo('VILLE', { name: 'ville', fieldLabel: 'Ville', value: rec ? undefined : 'Abidjan' }),
                    Usp.referentielCombo('COMMUNE', { name: 'commune', fieldLabel: 'Commune' }),
                    Usp.referentielCombo('PAYS', { name: 'pays', fieldLabel: 'Pays', value: rec ? undefined : 'Côte d\'Ivoire' })
                ] },
                { columnWidth: 1, items: [
                    { xtype: 'combobox', name: 'statut', fieldLabel: 'Statut', value: 'ACTIF', anchor: '48%',
                      store: ['PROSPECT', 'ACTIF', 'INACTIF', 'SUSPENDU', 'ARCHIVE'], queryMode: 'local' },
                    { xtype: 'textfield', name: 'notes', fieldLabel: 'Notes', anchor: '98%' }
                ] },
                Usp.numerosGrid(numStore)
            ]
        }],
        buttons: [{
            text: 'Enregistrer', formBind: true,
            handler: function (b) {
                var form = b.up('window').down('form').getForm();
                if (!form.isValid()) { return; }
                var vals = form.getValues();
                var numeros = [];
                numStore.each(function (r) {
                    var n = (r.get('numero') || '').trim();
                    if (n) { numeros.push({ numero: n, whatsapp: !!r.get('whatsapp'), principal: !!r.get('principal') }); }
                });
                vals.numeros = numeros;
                Usp.ajax({
                    url: rec ? '/clients/' + rec.get('id') : '/clients',
                    method: rec ? 'PUT' : 'POST', jsonData: vals,
                    success: function () {
                        win.close(); store.load();
                        Usp.toastEnregistre('Client « ' + (vals.nomCompte || vals.numeroClient || '') + ' »', !!rec);
                    },
                    failure: function (resp) {
                        var msg = 'Enregistrement impossible.', champ = null;
                        try { var r = Ext.decode(resp.responseText); msg = r.erreur || msg; champ = r.champ || null; } catch (e) {}
                        if (champ) { var f = form.findField(champ); if (f) { f.markInvalid(msg); f.focus(true, 50); } }
                        Ext.Msg.alert('Saisie à corriger', msg);
                    }
                });
            }
        }]
    });
    win.show();
    if (rec) {
        Usp.ajax({ url: '/clients/' + rec.get('id'), method: 'GET', success: function (resp) {
            win.down('form').getForm().setValues(Ext.decode(resp.responseText));
        } });
        // Numéros + date de naissance depuis les contacts existants.
        Usp.ajax({ url: '/clients/' + rec.get('id') + '/contacts', method: 'GET', success: function (resp) {
            var d = {}; try { d = Ext.decode(resp.responseText) || {}; } catch (e) {}
            var rows = d.data || (Ext.isArray(d) ? d : []);
            numStore.removeAll();
            rows.forEach(function (c) {
                var num = c.numeroWhatsapp || c.telephonePrincipal;
                if (num) { numStore.add({ numero: num, whatsapp: !!c.numeroWhatsapp, principal: !!c.contactPrincipal }); }
            });
            if (numStore.getCount() === 0) { numStore.add({ numero: '', whatsapp: true, principal: true }); }
            var princ = rows.filter(function (c) { return c.contactPrincipal; })[0] || rows[0];
            if (princ && princ.anneeNaissance && princ.moisNaissance && princ.jourNaissance) {
                var f = win.down('[name=dateNaissance]');
                if (f) { f.setValue(Ext.Date.parse(princ.anneeNaissance + '-'
                    + ('0' + princ.moisNaissance).slice(-2) + '-' + ('0' + princ.jourNaissance).slice(-2), 'Y-m-d')); }
            }
        } });
    }
};

/* ---------- Numéros d'un client (écran « Contacts » = numéros seuls) ---------- */
Usp.contactsWindow = function (clientId, nomCompte) {
    var numStore = Ext.create('Ext.data.Store', { fields: ['numero', 'whatsapp', 'principal'] });
    var win = Ext.create('Ext.window.Window', {
        title: 'Numéros — ' + nomCompte, width: 620, modal: true, bodyPadding: 12, layout: 'anchor',
        items: [
            { xtype: 'component', anchor: '100%', style: 'margin-bottom:8px;color:#555',
              html: 'Ajoutez un ou plusieurs numéros. Le 1er est le <b>principal</b> ; ' +
                    'les messages ne partent que sur les numéros cochés <b>WhatsApp</b>.' },
            Ext.apply(Usp.numerosGrid(numStore), { anchor: '100%' })
        ],
        buttons: [{
            text: 'Enregistrer', handler: function () {
                var numeros = [];
                numStore.each(function (r) {
                    var n = (r.get('numero') || '').trim();
                    if (n) { numeros.push({ numero: n, whatsapp: !!r.get('whatsapp'), principal: !!r.get('principal') }); }
                });
                Usp.ajax({
                    url: '/clients/' + clientId + '/numeros', method: 'POST', jsonData: numeros,
                    success: function () {
                        win.close();
                        Usp.toast('Numéros enregistrés.');
                    },
                    failure: function () { Ext.Msg.alert('Erreur', 'Enregistrement impossible.'); }
                });
            }
        }]
    });
    win.show();
    // Pré-remplissage depuis les contacts existants (un numéro par contact).
    Usp.ajax({ url: '/clients/' + clientId + '/contacts', method: 'GET', success: function (resp) {
        var d = {}; try { d = Ext.decode(resp.responseText) || {}; } catch (e) {}
        var rows = d.data || (Ext.isArray(d) ? d : []);
        numStore.removeAll();
        rows.forEach(function (c) {
            var num = c.numeroWhatsapp || c.telephonePrincipal;
            if (num) { numStore.add({ numero: num, whatsapp: !!c.numeroWhatsapp, principal: !!c.contactPrincipal }); }
        });
        if (numStore.getCount() === 0) { numStore.add({ numero: '', whatsapp: true, principal: true }); }
    } });
};

/* ---------- Assistant d'import (simplifié) ---------- */
Usp.showImport = function () {
    // Délègue à l'assistant d'import générique (mapping, modèles, rejets).
    Usp.importer.show('CLIENTS', '/imports/clients', function () {
        var grid = Ext.ComponentQuery.query('#center grid')[0];
        if (grid && grid.getStore()) { grid.getStore().load(); }
    });
};

/* Colonnes d'export des séries du graphe. */
Usp.dashboardChart = function () { return Usp.dashboardChart._build(); };
Usp.dashboardChart.COLS = [
    { d: 'date', t: 'Jour' }, { d: 'campagnes', t: 'Campagnes' },
    { d: 'waweb', t: 'WhatsApp Web (masse)' }, { d: 'api', t: 'Messages API' },
    { d: 'discussions', t: 'Discussions (Web)' }
];

/* Graphe d'évolution (30 j) : campagnes, WhatsApp Web (masse), API, discussions. */
Usp.dashboardChart._build = function () {
    if (!(Ext.chart && Ext.chart.Chart)) { return null; } // graphiques absents du build Ext
    var serieStore = Ext.create('Ext.data.Store', {
        fields: ['date', 'campagnes', 'waweb', 'api', 'discussions'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/dashboard/series',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' },
            extraParams: { jours: 30 } },
        autoLoad: false
    });
    var ligne = function (champ, titre, couleur) {
        return { type: 'line', axis: 'left', xField: 'date', yField: champ, title: titre, smooth: true,
            style: { stroke: couleur, 'stroke-width': 2 },
            markerConfig: { type: 'circle', size: 3, fill: couleur, stroke: couleur } };
    };
    return {
        xtype: 'panel', title: 'Évolution', anchor: '100%', height: 360,
        margin: '6 0 0 0', layout: 'fit', bodyPadding: 4,
        serieStore: serieStore,
        tbar: ['Période :',
            { xtype: 'combobox', width: 130, editable: false, queryMode: 'local',
              value: 30, valueField: 'v', displayField: 'l',
              store: Ext.create('Ext.data.Store', { fields: ['v', 'l'], data: [
                  { v: 7, l: '7 jours' }, { v: 30, l: '30 jours' }, { v: 90, l: '90 jours' }] }),
              listeners: { select: function (c) {
                  serieStore.getProxy().extraParams = { jours: c.getValue() };
                  serieStore.load();
              } } },
            '->',
            { text: 'Export CSV', handler: function () {
                Usp.export.csv('evolution', Usp.dashboardChart.COLS, serieStore.getRange()); } },
            { text: 'Export PDF', handler: function () {
                Usp.export.pdf('Évolution des envois', Usp.dashboardChart.COLS, serieStore.getRange()); } }
        ],
        items: [{
            xtype: 'chart', store: serieStore, animate: true, shadow: false, insetPadding: 24,
            legend: { position: 'top' },
            axes: [
                { type: 'Numeric', position: 'left', minimum: 0, title: 'Volume', grid: true,
                  fields: ['campagnes', 'waweb', 'api', 'discussions'] },
                { type: 'Category', position: 'bottom', fields: ['date'], title: 'Jour',
                  label: { rotate: { degrees: 315 } } }
            ],
            series: [
                ligne('campagnes', 'Campagnes', '#1976d2'),
                ligne('waweb', 'WhatsApp Web (masse)', '#25d366'),
                ligne('api', 'Messages API', '#6a1b9a'),
                ligne('discussions', 'Discussions (Web)', '#ef6c00')
            ]
        }]
    };
};

/* ---------- Tableau de bord ---------- */
Usp.dashboardPanel = function () {
    var cartes = Ext.create('Ext.Component', { anchor: '100%',
        html: '<div style="color:#999">Chargement des indicateurs…</div>' });
    var graphe = Usp.dashboardChart();
    var items = [cartes];
    if (graphe) { items.push(graphe); }
    var panel = Ext.create('Ext.panel.Panel', {
        title: 'Tableau de bord', autoScroll: true, bodyPadding: 18, layout: 'anchor',
        bodyStyle: 'background:#eef1f5', items: items,
        tools: [{ type: 'refresh', tooltip: 'Rafraîchir les indicateurs', handler: function () { charger(); } }]
    });
    var SECTIONS = [
        { titre: 'Clients & contacts', items: [
            { k: 'comptesClients', lib: 'Comptes clients', icon: '🏢', couleur: '#1976d2', vue: 'clients' },
            { k: 'contacts', lib: 'Contacts', icon: '👥', couleur: '#1976d2', vue: 'clients' },
            { k: 'contactsWhatsapp', lib: 'Contacts WhatsApp', icon: '💬', couleur: '#25d366' },
            { k: 'contactsSansWhatsapp', lib: 'Sans WhatsApp', icon: '🚫', couleur: '#9e9e9e' },
            { k: 'contactsConsentement', lib: 'Consentement', icon: '✅', couleur: '#2e7d32' },
            { k: 'contactsDesabonnes', lib: 'Désabonnés', icon: '⛔', couleur: '#c62828' }
        ] },
        { titre: 'Catalogue', items: [
            { k: 'articles', lib: 'Articles', icon: '📦', couleur: '#6a1b9a', vue: 'catalogue' },
            { k: 'articlesActifs', lib: 'Articles actifs', icon: '🟢', couleur: '#2e7d32' },
            { k: 'articlesRupture', lib: 'En rupture', icon: '⚠️', couleur: '#ef6c00' }
        ] },
        { titre: 'Messagerie', items: [
            { k: 'conversationsOuvertes', lib: 'Conversations ouvertes', icon: '💬', couleur: '#1976d2', vue: 'inbox' },
            { k: 'conversationsNonLues', lib: 'Non lues', icon: '🔔', couleur: '#ef6c00', vue: 'inbox' },
            { k: 'messagesEnvoyes', lib: 'Messages envoyés', icon: '📤', couleur: '#1976d2', vue: 'historique' },
            { k: 'sessionsWebConnectees', lib: 'Sessions Web connectées', icon: '🔗', couleur: '#25d366', vue: 'waweb' }
        ] },
        { titre: 'Campagnes & envois', items: [
            { k: 'campagnesEnCours', lib: 'Campagnes en cours', icon: '🚀', couleur: '#1976d2', vue: 'campaigns' },
            { k: 'campagnesTerminees', lib: 'Campagnes terminées', icon: '🏁', couleur: '#2e7d32', vue: 'campaigns' },
            { k: 'envoisMasse', lib: 'Envois de masse', icon: '📨', couleur: '#6a1b9a', vue: 'historique' },
            { k: 'modeles', lib: 'Modèles actifs', icon: '📝', couleur: '#455a64', vue: 'settings' }
        ] },
        { titre: 'Commercial', items: [
            { k: 'commandes', lib: 'Commandes', icon: '🛒', couleur: '#6a1b9a' },
            { k: 'opportunitesOuvertes', lib: 'Opportunités ouvertes', icon: '🎯', couleur: '#ef6c00', vue: 'crm' },
            { k: 'imports', lib: 'Imports', icon: '📥', couleur: '#455a64' }
        ] },
        { titre: 'Activité & usage', items: [
            { k: 'connexionsAujourdhui', lib: "Connexions aujourd'hui", icon: '🔑', couleur: '#1976d2', vue: 'users' },
            { k: 'utilisateursActifs7j', lib: 'Utilisateurs actifs (7 j)', icon: '👥', couleur: '#2e7d32', vue: 'users' },
            { k: 'sessionsEnCours', lib: 'Sessions en cours', icon: '🟢', couleur: '#00897b', vue: 'users' },
            { k: 'messagesEnvoyesAujourdhui', lib: "Messages envoyés aujourd'hui", icon: '📤', couleur: '#1976d2', vue: 'historique' }
        ] }
    ];
    function carte(it, val) {
        var nav = it.vue ? ' data-vue="' + it.vue + '" style="cursor:pointer;' : ' style="';
        return '<div class="usp-card"' + nav +
            'position:relative;display:inline-block;vertical-align:top;width:200px;min-height:92px;margin:8px;' +
            'padding:14px 16px;background:#fff;border-radius:10px;border-left:5px solid ' + it.couleur + ';' +
            'box-shadow:0 1px 4px rgba(0,0,0,.08)">' +
            '<div style="position:absolute;top:12px;right:14px;font-size:22px;opacity:.85">' + it.icon + '</div>' +
            '<div style="font-size:30px;font-weight:bold;color:' + it.couleur + ';line-height:1.1">' +
            (val == null ? '–' : val) + '</div>' +
            '<div style="font-size:12px;color:#5a6573;margin-top:6px">' + it.lib + '</div></div>';
    }
    function rendre(d) {
        var html = '';
        SECTIONS.forEach(function (sec) {
            html += '<div style="margin:6px 8px 2px;font-size:13px;font-weight:bold;color:#33404f;' +
                'text-transform:uppercase;letter-spacing:.5px">' + sec.titre + '</div>';
            html += '<div style="margin-bottom:14px">';
            sec.items.forEach(function (it) { html += carte(it, d[it.k]); });
            html += '</div>';
        });
        cartes.update(html);
    }
    function charger() {
        Usp.ajax({
            url: '/dashboard', method: 'GET',
            success: function (resp) { rendre(Ext.decode(resp.responseText) || {}); },
            failure: function () { cartes.update('<div style="color:#a00">Indicateurs indisponibles.</div>'); }
        });
        if (graphe && graphe.serieStore) { graphe.serieStore.load(); }
    }
    panel.on('afterrender', charger);
    // Clic sur une carte « navigable » : ouvre la vue correspondante.
    panel.on('render', function () {
        panel.getEl().on('click', function (e, t) {
            var card = Ext.fly(t).up('.usp-card');
            if (!card) { return; }
            var vue = card.getAttribute('data-vue');
            if (vue && Usp.ouvrirVue) { Usp.ouvrirVue(vue); }
        });
    });
    return panel;
};

/* ---------- Menu filtré par rôle ---------- */
Usp.MENU = [
    { text: 'Tableau de bord',     view: 'dashboard',  icon: '📊', roles: null },
    { text: 'Discussions',         view: 'inbox',      icon: '💬', roles: ['ADMIN', 'SUPERVISEUR', 'AGENT', 'MARKETING'] },
    { text: 'Comptes clients',     view: 'clients',    icon: '🏢', roles: ['ADMIN', 'MARKETING', 'SUPERVISEUR', 'AGENT', 'LECTURE'] },
    { text: 'Catalogue',           view: 'catalogue',  icon: '📦', roles: ['ADMIN', 'CATALOGUE', 'LECTURE'] },
    { text: 'Promotions',          view: 'promotions', icon: '🏷️', roles: ['ADMIN', 'MARKETING', 'CATALOGUE'] },
    { text: 'Marketing',           view: 'marketing',  icon: '📣', roles: ['ADMIN', 'MARKETING', 'CATALOGUE'] },
    { text: 'Disponibilités & Ruptures', view: 'dispo', icon: '📦', roles: ['ADMIN', 'MARKETING', 'CATALOGUE'] },
    { text: 'Informations Clients', view: 'infos', icon: '📨', roles: ['ADMIN', 'MARKETING', 'SUPERVISEUR'] },
    { text: 'Campagnes',           view: 'campaigns',  icon: '🚀', roles: ['ADMIN', 'MARKETING'] },
    { text: 'WhatsApp Web',        view: 'waweb',      iconHtml: Usp.ICON_WA, roles: ['ADMIN', 'MARKETING'] },
    { text: 'Historique des envois', view: 'historique', icon: '🗂️', roles: ['ADMIN', 'MARKETING'] },
    { text: 'CRM / Opportunités',  view: 'crm',        icon: '🎯', roles: ['ADMIN', 'SUPERVISEUR', 'AGENT', 'MARKETING'] },
    { text: 'Suivi Relance et Recouvrements', view: 'recouvrement', icon: '💰', roles: ['ADMIN'] },
    { text: 'Paramètres',          view: 'settings',   icon: '⚙️', roles: ['ADMIN'] },
    { text: 'Utilisateurs',        view: 'users',      icon: '👤', roles: ['ADMIN'] }
];

Usp.canSee = function (roles) {
    if (!roles || roles.length === 0) { return true; }
    var mine = (Usp.user && Usp.user.roles) || [];
    if (mine.indexOf('ADMIN') !== -1) { return true; }
    return roles.some(function (r) { return mine.indexOf(r) !== -1; });
};

/* Droit fin : l'utilisateur peut-il l'action sur le menu ? ADMIN = tout.
 * Repli permissif si les permissions n'ont pas pu être chargées (ne rien bloquer). */
Usp.can = function (menu, action) {
    var mine = (Usp.user && Usp.user.roles) || [];
    if (mine.indexOf('ADMIN') !== -1) { return true; }
    if (!Usp.perms) { return true; }
    var acts = Usp.perms[menu];
    return !!acts && acts.indexOf(action) !== -1;
};

/* Pastille « droit non accordé » (HTML) à accoler au libellé d'un bouton.
 * Vide si l'action est autorisée. */
Usp.permBadge = function (menu, action) {
    if (Usp.can(menu, action)) { return ''; }
    return ' <span style="color:#e74c3c;font-size:11px;vertical-align:middle"' +
           ' title="Droit non accordé : ' + action + ' sur ' + menu + '">●</span>';
};

/* Prépare une config de bouton : si l'action n'est pas autorisée, ajoute une
 * pastille au libellé et un libellé d'aide, SANS toucher au handler (le clic
 * conserve son comportement habituel, qui aboutira au refus serveur clair). */
Usp.permBtn = function (menu, action, cfg) {
    cfg = cfg || {};
    if (!Usp.can(menu, action)) {
        cfg.text = (cfg.text || '') + Usp.permBadge(menu, action);
        cfg.tooltip = (cfg.tooltip ? cfg.tooltip + ' — ' : '') + 'Droit non accordé (' + action + ')';
        // Bloque l'action au clic et informe l'utilisateur (il ne peut pas poursuivre).
        cfg.handler = function () { Usp.refusPermission(); };
    }
    return cfg;
};

/* Extrait le message d'erreur explicite renvoyé par le serveur ({erreur:...}),
 * avec repli si la réponse n'est pas exploitable. */
Usp.erreurServeur = function (resp, repli) {
    try {
        var r = Ext.decode(resp.responseText);
        if (r && r.erreur) { return r.erreur; }
    } catch (e) { /* réponse non JSON */ }
    return repli || 'Opération impossible.';
};

/* Message standard de refus de permission (clic sur une action non autorisée). */
Usp.refusPermission = function () {
    Ext.Msg.show({
        title: 'Action non autorisée',
        msg: 'Vous n\'avez pas la permission pour cette action.<br>' +
             'Merci de contacter l\'administrateur de votre système.',
        buttons: Ext.Msg.OK, icon: Ext.Msg.WARNING
    });
};

/* Lie deux champs date « début » / « fin » d'un formulaire pour contrôler la période
 * dès la sélection : la fin ne peut être avant le début, le début pas après la fin.
 * - contraint dynamiquement les bornes des deux champs (feedback immédiat) ;
 * - marque le champ fautif invalide avec un message clair.
 * fieldDebut / fieldFin : instances de datefield (ou datetimefield). */
Usp.lierPeriode = function (fieldDebut, fieldFin) {
    if (!fieldDebut || !fieldFin) { return; }
    var MSG = 'La date de fin ne peut pas être antérieure à la date de début.';
    var verifier = function () {
        var d = fieldDebut.getValue(), f = fieldFin.getValue();
        // Bornes dynamiques pour empêcher une sélection incohérente.
        fieldFin.setMinValue(d || null);
        fieldDebut.setMaxValue(f || null);
        if (d && f && f < d) {
            fieldFin.markInvalid(MSG);
            return false;
        }
        fieldFin.clearInvalid();
        return true;
    };
    fieldDebut.on('change', verifier);
    fieldFin.on('change', verifier);
    fieldDebut.on('select', verifier);
    fieldFin.on('select', verifier);
    return verifier;
};

/* Contrôle ponctuel d'une période à l'enregistrement (renvoie true si valide,
 * sinon affiche un message). Utilisé quand les champs ne sont pas liés en amont. */
Usp.periodeValide = function (debut, fin) {
    if (debut && fin && fin < debut) {
        Ext.Msg.alert('Période invalide', 'La date de fin ne peut pas être antérieure à la date de début.');
        return false;
    }
    return true;
};

/* Fenêtre « À propos » : version + développeur (lecture seule, non modifiable). */
Usp.apropos = function () {
    Usp.ajax({ url: '/about', method: 'GET', success: function (resp) {
        var a = {};
        try { a = Ext.decode(resp.responseText) || {}; } catch (e) {}
        Ext.Msg.show({
            title: 'À propos',
            msg: '<div style="padding:6px 2px;line-height:1.7">' +
                 '<b>' + Ext.String.htmlEncode(a.application || 'UbiSenderPro') + '</b><br>' +
                 'Version : <b>' + Ext.String.htmlEncode(a.version || '—') + '</b><br>' +
                 'Développeur : <b>' + Ext.String.htmlEncode(a.developpeur || '—') + '</b><br>' +
                 'E-mail : <b>' + Ext.String.htmlEncode(a.email || '—') + '</b>' +
                 '</div>',
            buttons: Ext.Msg.OK, icon: Ext.Msg.INFO, width: 360
        });
    }, failure: function () { Ext.Msg.alert('À propos', 'Informations indisponibles.'); } });
};

/* Listeners de mise en MAJUSCULES automatique d'un champ (noms clients / produits). */
Usp.majListeners = { blur: function (f) { var v = f.getValue(); if (v) { f.setValue(String(v).toUpperCase()); } } };

/* Renseigne un champ CIP7 avec un code à 7 chiffres garanti libre (serveur). */
Usp.genererCip7 = function (field) {
    if (!field) { return; }
    Usp.ajax({ url: '/articles/cip7-libre', method: 'GET', success: function (resp) {
        var r = {}; try { r = Ext.decode(resp.responseText) || {}; } catch (e) {}
        if (r.cip7) { field.setValue(r.cip7); }
    } });
};

/* Champ CIP7 + bouton « générer » (7 chiffres uniques). name = nom du champ. */
Usp.cip7Field = function (valeur) {
    return { xtype: 'fieldcontainer', fieldLabel: 'CIP7', layout: 'hbox', items: [
        { xtype: 'textfield', name: 'cip7', flex: 1, value: valeur || '',
          emptyText: 'Saisir ou générer' },
        { xtype: 'button', text: '🎲', width: 34, margin: '0 0 0 6',
          tooltip: 'Générer un CIP7 unique (7 chiffres)',
          handler: function (b) { Usp.genererCip7(b.up('fieldcontainer').down('textfield')); } }
    ] };
};

/* Sélecteur multiple (segments / agences / régions / tournées) : champ résumé +
 * bouton « ☑ Choisir » ouvrant une fenêtre à cocher (tout cocher / plusieurs).
 * La valeur est stockée en CSV dans un hiddenfield (name = cfg.name).
 * cfg : { name, fieldLabel, itemId, hidden, url (référentiel), valueField, displayField, value (CSV) } */
Usp.multiPicker = function (cfg) {
    cfg = cfg || {};
    var current = cfg.value ? String(cfg.value).split(',').map(function (s) { return s.trim(); }).filter(function (x) { return x; }) : [];
    var store = Ext.create('Ext.data.Store', {
        fields: [{ name: 'v', mapping: cfg.valueField }, { name: 't', mapping: cfg.displayField }],
        autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + cfg.url,
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } }
    });
    var container;
    var resumeHtml = function () {
        if (!current.length) { return '<span style="color:#999">Aucun sélectionné</span>'; }
        var labels = current.map(function (v) {
            // Comparaison souple (l'id peut être numérique côté store et texte côté CSV).
            var i = store.findBy(function (rec) { return String(rec.get('v')) === String(v); });
            return i >= 0 ? store.getAt(i).get('t') : v;
        });
        return Ext.String.htmlEncode(labels.join(', '));
    };
    var majResume = function () { if (container) { container.down('#resume').update(resumeHtml()); } };
    store.on('load', majResume);
    var ouvrir = function () {
        var win = Ext.create('Ext.window.Window', {
            title: cfg.fieldLabel || 'Sélection', width: 380, height: 430, modal: true, layout: 'fit',
            items: [{ xtype: 'grid', itemId: 'g', store: store, hideHeaders: true,
                selModel: Ext.create('Ext.selection.CheckboxModel', { checkOnly: true, mode: 'SIMPLE' }),
                columns: [{ text: '', dataIndex: 't', flex: 1 }] }],
            tbar: [
                { text: 'Tout cocher', handler: function (b) { b.up('window').down('#g').getSelectionModel().selectAll(); } },
                { text: 'Tout décocher', handler: function (b) { b.up('window').down('#g').getSelectionModel().deselectAll(); } }
            ],
            buttons: [
                { text: 'Valider', handler: function (b) {
                    var sel = b.up('window').down('#g').getSelectionModel().getSelection();
                    current = sel.map(function (r) { return String(r.get('v')); });
                    container.down('[name=' + cfg.name + ']').setValue(current.join(','));
                    majResume(); win.close();
                } },
                { text: 'Annuler', handler: function (b) { b.up('window').close(); } }
            ]
        });
        win.show();
        var g = win.down('#g');
        var appliquerSel = function () {
            var sm = g.getSelectionModel(); sm.deselectAll();
            store.each(function (r) { if (current.indexOf(String(r.get('v'))) !== -1) { sm.select(r, true); } });
        };
        if (store.getCount() > 0) { appliquerSel(); }
        else { store.on('load', appliquerSel, null, { single: true }); if (!store.isLoading()) { store.load(); } }
    };
    container = Ext.create('Ext.form.FieldContainer', {
        fieldLabel: cfg.fieldLabel, layout: 'hbox', hidden: cfg.hidden, itemId: cfg.itemId,
        items: [
            { xtype: 'hiddenfield', name: cfg.name, value: current.join(',') },
            { xtype: 'component', itemId: 'resume', flex: 1, style: 'padding-top:5px', html: resumeHtml() },
            { xtype: 'button', text: '☑ Choisir', width: 95, margin: '0 0 0 8', handler: ouvrir }
        ]
    });
    return container;
};

/* SCL — Sélecteur de Clients : fenêtre listant tous les clients actifs
 * (code / nom / entreprise / agence / région / téléphone) avec filtres
 * segmentation + agence + région + recherche live (combinables) et cases à
 * cocher (un / tout le résultat). cfg.onValider(rows) reçoit les clients cochés
 * ({clientId, code, nom, entreprise, agence, region, contactId, numero}). */
Usp.clientPicker = function (cfg) {
    cfg = cfg || {};
    var store = Ext.create('Ext.data.Store', {
        fields: ['clientId', 'code', 'nom', 'entreprise', 'agence', 'region', 'contactId', 'numero'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/clients/selection',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } } });
    var refStore = function (type) {
        return Ext.create('Ext.data.Store', { fields: ['id', 'code', 'libelle'], autoLoad: true,
            proxy: { type: 'ajax', url: Usp.apiBase + '/referentiels/' + type,
                headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } } });
    };
    var segStore = Ext.create('Ext.data.Store', { fields: ['id', 'libelle'], autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/segmentations',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } } });
    var sm = Ext.create('Ext.selection.CheckboxModel', { checkOnly: true });
    var etat = { q: '', agence: '', region: '', seg: '' };
    var charger = function () {
        store.getProxy().extraParams = { q: etat.q, agence: etat.agence, region: etat.region, segmentationId: etat.seg };
        store.load();
    };
    var win = Ext.create('Ext.window.Window', {
        title: cfg.title || 'Choisir des clients', width: 800,
        height: Math.min(560, Ext.getBody().getViewSize().height - 40), modal: true, layout: 'fit',
        items: [{ xtype: 'grid', store: store, selModel: sm,
            columns: [
                { text: 'Code', dataIndex: 'code', width: 90 },
                { text: 'Nom client', dataIndex: 'nom', flex: 1 },
                { text: 'Entreprise', dataIndex: 'entreprise', flex: 1 },
                { text: 'Agence', dataIndex: 'agence', width: 110 },
                { text: 'Région', dataIndex: 'region', width: 110 },
                { text: 'Téléphone', dataIndex: 'numero', width: 120 }
            ],
            tbar: [
                { xtype: 'textfield', emptyText: '🔎 Rechercher…', width: 170,
                  listeners: { change: { buffer: 350, fn: function (f, v) { etat.q = v || ''; charger(); } },
                      specialkey: function (f, e) { if (e.getKey() === e.ENTER) { etat.q = f.getValue() || ''; charger(); } } } },
                { xtype: 'combobox', emptyText: 'Segmentation', width: 150, store: segStore, valueField: 'id',
                  displayField: 'libelle', queryMode: 'local', editable: false,
                  listeners: { change: function (f, v) { etat.seg = v || ''; charger(); } } },
                { xtype: 'combobox', emptyText: 'Agence', width: 130, store: refStore('AGENCE'), valueField: 'libelle',
                  displayField: 'libelle', queryMode: 'local', editable: false,
                  listeners: { change: function (f, v) { etat.agence = v || ''; charger(); } } },
                { xtype: 'combobox', emptyText: 'Région', width: 130, store: refStore('REGION'), valueField: 'libelle',
                  displayField: 'libelle', queryMode: 'local', editable: false,
                  listeners: { change: function (f, v) { etat.region = v || ''; charger(); } } }
            ],
            bbar: ['->',
                { text: 'Tout sélectionner (résultat)', handler: function () { sm.selectAll(); } },
                { text: 'Tout désélectionner', handler: function () { sm.deselectAll(); } }
            ]
        }],
        buttons: (cfg.avecListe ? [
            { text: '📇 Ajouter à une liste de diffusion', handler: function () {
                var recs = sm.getSelection();
                if (!recs.length) { Ext.Msg.alert('Info', 'Cochez au moins un client.'); return; }
                Usp.ajouterAListe(recs.map(function (r) { return r.getData(); }));
            } }, '->'
        ] : []).concat([
            { text: cfg.boutonValider || 'Ajouter la sélection', handler: function () {
                var recs = sm.getSelection();
                if (!recs.length) { Ext.Msg.alert('Info', 'Cochez au moins un client.'); return; }
                if (cfg.onValider) { cfg.onValider(recs.map(function (r) { return r.getData(); })); }
                win.close();
            } },
            { text: 'Annuler', handler: function () { win.close(); } }
        ])
    });
    win.show();
    charger();
};

/* Ajoute une sélection de clients (leur contact principal) à une liste de diffusion choisie. */
Usp.ajouterAListe = function (rows) {
    var ids = rows.map(function (r) { return r.contactId; }).filter(function (x) { return x; });
    var sansContact = rows.length - ids.length;
    if (!ids.length) { Ext.Msg.alert('Info', 'Aucun contact rattaché aux clients choisis.'); return; }
    var store = Ext.create('Ext.data.Store', { fields: ['id', 'nom'], autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/lists',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } } });
    var win = Ext.create('Ext.window.Window', {
        title: 'Ajouter à une liste de diffusion', width: 420, modal: true, bodyPadding: 12,
        items: [{ xtype: 'form', border: false, defaults: { anchor: '100%' }, items: [
            { xtype: 'combobox', name: 'listeId', fieldLabel: 'Liste', store: store, valueField: 'id',
              displayField: 'nom', queryMode: 'local', editable: false, allowBlank: false, emptyText: 'Choisir une liste…' }
        ] }],
        buttons: [{ text: 'Ajouter', formBind: true, handler: function (b) {
            var lid = b.up('window').down('[name=listeId]').getValue();
            if (!lid) { return; }
            var reste = ids.length;
            ids.forEach(function (cid) {
                Usp.ajax({ url: '/lists/' + lid + '/contacts', method: 'POST', jsonData: { contactId: cid, source: 'MANUEL' },
                    success: function () { if (--reste === 0) { win.close(); Usp.toast(ids.length + ' contact(s) ajouté(s)' + (sansContact ? ' — ' + sansContact + ' sans contact ignoré(s).' : '.')); } },
                    failure: function () { if (--reste === 0) { win.close(); } } });
            });
        } }, { text: 'Annuler', handler: function (b) { b.up('window').close(); } }]
    });
    win.show();
};

/* ---------- Composant AUDIENCE réutilisable (Informations, Disponibilités…) ---------- */
/* Une audience pilote l'affichage d'UN seul champ complémentaire ; sélections
 * multiples via le SMC (segments/agences/régions/tournées) et le SCL (clients). */
Usp.AUDIENCES = [
    ['TOUS_LES_SEGMENTS', 'Tous les segments'],
    ['SEGMENTS_SELECTIONNES', 'Un ou plusieurs segments'],
    ['AGENCE', 'Une ou plusieurs agences'],
    ['REGION', 'Une ou plusieurs régions'],
    ['TOURNEE', 'Une ou plusieurs tournées'],
    ['LISTE_DE_DIFFUSION', 'Liste de diffusion'],
    ['CONTACTS_MANUELS', 'Sélection manuelle de clients']
];

/* Champ « sélection manuelle de clients » (via le SCL) : stocke contactIds en CSV. */
Usp.audienceContactsField = function (valeur) {
    var current = valeur ? String(valeur).split(',').filter(function (x) { return x; }) : [];
    var resume = current.length ? current.length + ' client(s) sélectionné(s)' : '<span style="color:#999">Aucun</span>';
    return { xtype: 'fieldcontainer', itemId: 'aud_contacts', fieldLabel: 'Clients sélectionnés', hidden: true, layout: 'hbox', items: [
        { xtype: 'hiddenfield', name: 'contactIds', value: current.join(',') },
        { xtype: 'component', itemId: 'resume', flex: 1, style: 'padding-top:5px', html: resume },
        { xtype: 'button', text: '☑ Choisir des clients', width: 160, margin: '0 0 0 8', handler: function (b) {
            var wrap = b.up('fieldcontainer');
            Usp.clientPicker({ title: 'Sélection manuelle de clients', boutonValider: 'Valider la sélection',
                onValider: function (rows) {
                    var ids = rows.map(function (r) { return r.contactId; }).filter(function (x) { return x; });
                    wrap.down('[name=contactIds]').setValue(ids.join(','));
                    wrap.down('#resume').update(ids.length + ' client(s) sélectionné(s)');
                } });
        } }
    ] };
};

/* Items du bloc audience (combo + champs pilotés). g = getter de valeurs existantes. */
Usp.audienceFields = function (g) {
    g = g || function () { return null; };
    var seg = g('segmentationIds') || (g('segmentationId') ? String(g('segmentationId')) : '');
    return [
        { xtype: 'combobox', name: 'audience', fieldLabel: 'Audience', editable: false, queryMode: 'local',
          store: Usp.AUDIENCES, value: g('audience') || 'TOUS_LES_SEGMENTS',
          listeners: { change: function (c) { Usp.majAudienceFields(c.up('form')); } } },
        Usp.multiPicker({ itemId: 'aud_segment', name: 'segmentationIds', fieldLabel: 'Segments ciblés', hidden: true,
            url: '/segmentations', valueField: 'id', displayField: 'libelle', value: seg }),
        Usp.multiPicker({ itemId: 'aud_agence', name: 'agence', fieldLabel: 'Agences ciblées', hidden: true,
            url: '/referentiels/AGENCE', valueField: 'libelle', displayField: 'libelle', value: g('agence') || '' }),
        Usp.multiPicker({ itemId: 'aud_region', name: 'region', fieldLabel: 'Régions ciblées', hidden: true,
            url: '/referentiels/REGION', valueField: 'libelle', displayField: 'libelle', value: g('region') || '' }),
        Usp.multiPicker({ itemId: 'aud_tournee', name: 'tournee', fieldLabel: 'Tournées ciblées', hidden: true,
            url: '/referentiels/TOURNEE', valueField: 'libelle', displayField: 'libelle', value: g('tournee') || '' }),
        { xtype: 'fieldcontainer', itemId: 'aud_liste', fieldLabel: 'Liste de diffusion', hidden: true, layout: 'hbox', items: [
            { xtype: 'combobox', name: 'listeId', flex: 1, valueField: 'id', displayField: 'nom', queryMode: 'local',
              editable: false, value: g('listeId'), emptyText: 'Choisir une liste…',
              store: Ext.create('Ext.data.Store', { fields: ['id', 'nom'], autoLoad: true,
                  proxy: { type: 'ajax', url: Usp.apiBase + '/lists',
                      headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } } }) },
            { xtype: 'button', text: '👁 Voir le contenu', width: 140, margin: '0 0 0 8', handler: function (b) {
                var lid = b.up('fieldcontainer').down('[name=listeId]').getValue();
                if (!lid) { Ext.Msg.alert('Info', 'Choisissez d\'abord une liste.'); return; }
                Usp.voirListeContenu(lid);
            } }
        ] },
        Usp.audienceContactsField(g('contactIds'))
    ];
};

/* Affiche le seul champ complémentaire correspondant à l'audience choisie. */
Usp.majAudienceFields = function (formPanel) {
    if (!formPanel) { return; }
    var aud = formPanel.down('[name=audience]');
    var v = aud ? aud.getValue() : null;
    var map = { SEGMENTS_SELECTIONNES: 'aud_segment', AGENCE: 'aud_agence', REGION: 'aud_region',
        TOURNEE: 'aud_tournee', LISTE_DE_DIFFUSION: 'aud_liste', CONTACTS_MANUELS: 'aud_contacts' };
    ['aud_segment', 'aud_agence', 'aud_region', 'aud_tournee', 'aud_liste', 'aud_contacts'].forEach(function (id) {
        var f = formPanel.down('#' + id); if (f) { f.setVisible(map[v] === id); }
    });
};

/* Contenu (lecture seule) d'une liste de diffusion. */
Usp.voirListeContenu = function (listeId) {
    var store = Ext.create('Ext.data.Store', { fields: ['id', 'nomComplet', 'numeroWhatsapp'], autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/lists/' + listeId + '/contacts',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } } });
    Ext.create('Ext.window.Window', { title: 'Contenu de la liste', width: 520, height: 420, modal: true, layout: 'fit',
        items: [{ xtype: 'grid', store: store, columns: [
            { text: 'Nom', dataIndex: 'nomComplet', flex: 1 },
            { text: 'WhatsApp', dataIndex: 'numeroWhatsapp', width: 160 }
        ] }] }).show();
};

/* Durée lisible entre deux dates (ex. « 29 jours », « 1 mois 10 jours », « 4 mois »). */
Usp.dureeLisible = function (debut, fin) {
    var d = debut ? new Date(String(debut).substring(0, 10)) : null;
    var f = fin ? new Date(String(fin).substring(0, 10)) : null;
    if (!d || !f || isNaN(d) || isNaN(f) || f < d) { return ''; }
    var mois = (f.getFullYear() - d.getFullYear()) * 12 + (f.getMonth() - d.getMonth());
    var jours = f.getDate() - d.getDate();
    if (jours < 0) { mois -= 1; var tmp = new Date(f.getFullYear(), f.getMonth(), 0); jours += tmp.getDate(); }
    var totalJours = Math.round((f - d) / 86400000);
    if (mois <= 0) { return totalJours + ' jour' + (totalJours > 1 ? 's' : ''); }
    var out = mois + ' mois';
    if (jours > 0) { out += ' ' + jours + ' jour' + (jours > 1 ? 's' : ''); }
    return out;
};

/* Colonne « Période » calculée à partir de dateDebut/dateFin. */
Usp.periodeColonne = function () {
    return { text: 'Période', width: 130, sortable: false, menuDisabled: true, dataIndex: 'dateFin',
        renderer: function (v, m, rec) { return Usp.dureeLisible(rec.get('dateDebut'), rec.get('dateFin')); } };
};

/* Colonne « Par » (créateur), dataIndex configurable (défaut creePar). */
Usp.parColonne = function (dataIndex) {
    return { text: 'Par', dataIndex: dataIndex || 'creePar', width: 110 };
};

/* Filtre client-side réutilisable pour une grille (recherche live + période + champs).
 * cfg : { champs:['code','nom'], periode:true }. Renvoie un tbar (array) + pose store.filterBy.
 * Recherche déclenchée pendant la saisie (anti-rebond) ET on garde Entrée. */
Usp.grilleFiltre = function (store, cfg) {
    cfg = cfg || {};
    var etat = { q: '', du: null, au: null, selects: {}, dateChamp: cfg.dateChamp || null };
    var appliquer = function () {
        store.clearFilter(true);
        store.filterBy(function (rec) {
            if (etat.q) {
                var ok = false, ql = etat.q.toLowerCase();
                (cfg.champs || []).forEach(function (c) {
                    var val = rec.get(c); if (val && String(val).toLowerCase().indexOf(ql) !== -1) { ok = true; }
                });
                if (!ok) { return false; }
            }
            var champsSelect = Object.keys(etat.selects);
            for (var i = 0; i < champsSelect.length; i++) {
                var k = champsSelect[i], val = etat.selects[k];
                if (val && String(rec.get(k)) !== String(val)) { return false; }
            }
            if (cfg.periode && (etat.du || etat.au)) {
                var deb, fin;
                if (etat.dateChamp) {
                    var dv = rec.get(etat.dateChamp);
                    deb = fin = dv ? new Date(String(dv).substring(0, 10)) : null;
                } else {
                    var dd = rec.get('dateDebut'), df = rec.get('dateFin');
                    deb = dd ? new Date(String(dd).substring(0, 10)) : null;
                    fin = df ? new Date(String(df).substring(0, 10)) : deb;
                }
                if (etat.du && fin && fin < etat.du) { return false; }
                if (etat.au && deb && deb > etat.au) { return false; }
            }
            return true;
        });
    };
    var items = [];
    if (cfg.champs && cfg.champs.length) {
        items.push({ xtype: 'textfield', emptyText: '🔎 Rechercher…', width: 190,
            listeners: {
                change: { buffer: 350, fn: function (f, v) { etat.q = v || ''; appliquer(); } },
                specialkey: function (f, e) { if (e.getKey() === e.ENTER) { etat.q = f.getValue() || ''; appliquer(); } }
            } });
    }
    (cfg.selects || []).forEach(function (s) {
        items.push({ xtype: 'combobox', emptyText: s.label, width: s.width || 130, queryMode: 'local', editable: false,
            store: s.options, valueField: 'v', displayField: 't',
            listeners: { change: function (f, v) { etat.selects[s.field] = v; appliquer(); } } });
    });
    if (cfg.periode) {
        items.push({ xtype: 'datefield', emptyText: 'Du', width: 105, format: 'd/m/Y', editable: false,
            listeners: { change: function (f, v) { etat.du = v || null; appliquer(); } } });
        items.push({ xtype: 'datefield', emptyText: 'Au', width: 105, format: 'd/m/Y', editable: false,
            listeners: { change: function (f, v) { etat.au = v || null; appliquer(); } } });
    }
    return items;
};

/* Code par défaut : 4 chiffres aléatoires (modifiable). Préfixe optionnel. */
Usp.codeAuto = function (prefixe) {
    var n = Math.floor(1000 + Math.random() * 9000);
    return prefixe ? (prefixe + '-' + n) : String(n);
};

/* Pastille sur l'onglet actif d'un tabpanel. */
Usp.tabPastille = function (tp, active) {
    if (!tp || !tp.items) { return; }
    tp.items.each(function (t) {
        if (t.baseTitle === undefined) { t.baseTitle = t.title; }
        // Onglet actif : sunburst orange animé (distinct du point vert des menus).
        t.setTitle(t.baseTitle + (t === active ? ' <span class="usp-pace-tab">✳</span>' : ''));
    });
};
Usp.tabListeners = {
    afterrender: function (tp) { Usp.tabPastille(tp, tp.getActiveTab()); },
    tabchange: function (tp, nc) { Usp.tabPastille(tp, nc); }
};

Usp.menuChildren = function () {
    return Usp.MENU.filter(function (m) {
            // Permissions chargées : visibilité par droit « VOIR » ; sinon repli par rôle.
            return Usp.perms ? Usp.can(m.view, 'VOIR') : Usp.canSee(m.roles);
        })
        .map(function (m) {
            var pre = m.iconHtml ? m.iconHtml + ' ' : (m.icon ? m.icon + '  ' : '');
            var t = pre + m.text;
            return { text: t, baseText: t, leaf: true, view: m.view };
        });
};

/* Charge la vue correspondante dans la zone centrale (menu + cartes du tableau de bord). */
/* Marque le menu actif (pastille + sélection) dans l'arbre de gauche, quelle que
 * soit la façon d'ouvrir la vue (clic direct ou navigation programmatique). */
Usp.activerMenu = function (vue) {
    var tree = Ext.ComponentQuery.query('#menuTree')[0];
    if (!tree || !vue) { return; }
    var root = tree.getStore().getRootNode();
    var cible = null;
    root.eachChild(function (n) {
        if (!n.data.baseText) { n.data.baseText = n.get('text'); }
        n.set('text', n.data.baseText);
        if (n.get('view') === vue) { cible = n; }
    });
    if (cible) {
        cible.set('text', cible.data.baseText + ' <span class="usp-pace">●</span>');
        tree.getSelectionModel().select(cible, false, true);
    }
};

Usp.ouvrirVue = function (vue) {
    Usp.activerMenu(vue);
    // Journalise le menu parcouru (best-effort) pour retracer l'activité de session.
    try {
        var m = Usp.MENU.filter(function (x) { return x.view === vue; })[0];
        if (Usp.token && vue) {
            Usp.ajax({ url: '/auth/navigation', method: 'POST',
                jsonData: { vue: vue, libelle: m ? m.text : vue } });
        }
    } catch (e) { /* non bloquant */ }
    switch (vue) {
        case 'inbox': Usp.loadCenter(Usp.inbox.panel()); break;
        case 'catalogue': Usp.loadCenter(Usp.catalogue.panel()); break;
        case 'promotions': Usp.loadCenter(Usp.marketing.promotionsPanel()); break;
        case 'marketing': Usp.loadCenter(Usp.marketing.panel()); break;
        case 'dispo': Usp.loadCenter(Usp.dispo.panel()); break;
        case 'infos': Usp.loadCenter(Usp.info.panel()); break;
        case 'campaigns': Usp.loadCenter(Usp.campaign.listPanel()); break;
        case 'waweb': Usp.loadCenter(Usp.waweb.tabs()); break;
        case 'historique': Usp.loadCenter(Usp.history.panel()); break;
        case 'crm': Usp.loadCenter(Usp.crm.tabs()); break;
        case 'settings': Usp.loadCenter(Usp.settings.tabs()); break;
        case 'clients': Usp.loadCenter(Usp.clientsPanel()); break;
        case 'users': Usp.loadCenter(Usp.users.panel()); break;
        case 'recouvrement': Usp.loadCenter(Usp.recouvrement.panel()); break;
        case 'dashboard': Usp.loadCenter(Usp.dashboardPanel()); break;
        case 'import': Usp.showImport(); break;
        default: Usp.loadCenter(Usp.dashboardPanel());
    }
};

/* ---------- Centre de notifications ---------- */
Usp.notifications = { _data: null, _win: null, _timer: null };

/* Démarre le rafraîchissement périodique (~30 s) du centre de notifications. */
Usp.notifications.demarrer = function () {
    Usp.notifications.rafraichir();
    if (!Usp.notifications._timer) {
        Usp.notifications._timer = Ext.TaskManager.start({ interval: 30000, run: function () {
            if (Usp.token) { Usp.notifications.rafraichir(); }
        } });
    }
};

Usp.notifications.rafraichir = function () {
    Usp.ajax({ url: '/notifications', method: 'GET',
        success: function (resp) {
            var d = {}; try { d = Ext.decode(resp.responseText) || {}; } catch (e) {}
            Usp.notifications._data = d;
            Usp.notifications.majBadge(d.total || 0);
        } });
};

/* Met à jour la cloche : pastille de comptage + animation « pace-maker » si activité. */
Usp.notifications.majBadge = function (total) {
    var btn = Ext.ComponentQuery.query('#uspNotif')[0];
    if (!btn) { return; }
    btn.setText('🔔' + (total ? ' <span class="usp-notif-badge">' + total + '</span>' : ''));
    if (total) { btn.addCls('usp-notif-actif'); } else { btn.removeCls('usp-notif-actif'); }
};

/* Clic sur une notification → va au menu/onglet concerné et ferme le volet. */
Usp.notifications.aller = function (vue) {
    if (Usp.notifications._win) { Usp.notifications._win.close(); }
    Usp.ouvrirVue(vue);
};

Usp.notifications.ouvrir = function () {
    var d = Usp.notifications._data || { groupes: [] };
    var fdate = function (v) { return v ? String(v).replace('T', ' ').substring(0, 16) : ''; };
    var items = (d.groupes || []).map(function (g) {
        var lignes = (g.items || []).map(function (it) {
            return '<div class="usp-notif-item" onclick="Usp.notifications.aller(\'' + g.vue + '\')" ' +
                'style="cursor:pointer;padding:3px 6px;border-bottom:1px solid #f0f0f0;font-size:12px">' +
                Ext.String.htmlEncode(it.libelle || '') +
                (it.date ? ' <span style="color:#999">' + fdate(it.date) + '</span>' : '') + '</div>';
        }).join('') || '<div style="padding:6px;color:#999;font-size:12px">Aucun élément récent.</div>';
        return {
            xtype: 'panel', collapsible: true, collapsed: true, titleCollapse: true, margin: '0 0 4 0',
            title: g.titre + ' (' + (g.count || 0) + ')',
            tools: [{ type: 'right', tooltip: 'Ouvrir le menu', handler: function () { Usp.notifications.aller(g.vue); } }],
            items: [{ xtype: 'component', html: lignes }]
        };
    });
    Usp.notifications._win = Ext.create('Ext.window.Window', {
        title: '🔔 Centre de notifications (' + (d.total || 0) + ')', width: 460,
        height: Math.min(560, Ext.getBody().getViewSize().height - 60), modal: false, autoScroll: true,
        bodyPadding: 8, layout: 'anchor', defaults: { anchor: '100%' }, items: items,
        tbar: [{ text: '🔄 Rafraîchir', handler: function () { Usp.notifications.rafraichir(); Usp.notifications._win.close(); Usp.notifications.ouvrir(); } }]
    });
    Usp.notifications._win.show();
};

/* ---------- Viewport principal ---------- */
Usp.showMain = function () {
    // Info-bulles au survol (data-qtip) — nécessaire pour les tooltips des grilles.
    if (Ext.tip && Ext.tip.QuickTipManager && !Ext.tip.QuickTipManager.isEnabled()) {
        Ext.tip.QuickTipManager.init();
    }
    // Boutons des boîtes de dialogue en français (Oui / Non).
    Ext.MessageBox.buttonText.yes = 'Oui';
    Ext.MessageBox.buttonText.no = 'Non';
    // Battement de cœur : maintient la session « active » tant que la page est ouverte.
    if (!Usp._heartbeat) {
        Usp._heartbeat = Ext.TaskManager.start({ interval: 60000, run: function () {
            if (Usp.token) { Usp.ajax({ url: '/auth/me', method: 'GET' }); }
        } });
    }
    Ext.create('Ext.container.Viewport', {
        layout: 'border',
        items: [
            {
                region: 'north',
                xtype: 'toolbar',
                cls: 'usp-header',
                height: 44,
                items: [
                    { xtype: 'tbtext', cls: 'usp-brand', text:
                        '<span class="usp-logo">' + Usp.LOGO + '</span>' +
                        '<span class="usp-brand-text pl-animated-text--brand">UbiSenderPro</span>',
                      listeners: { afterrender: function (c) {
                          // Même animation « vague » que l'accroche du login, appliquée au branding du header.
                          var el = c.getEl().dom.querySelector('.usp-brand-text');
                          if (el) { Usp.renderAnimatedLetters(el, el.textContent, 'pl-animated-text--brand'); }
                      } } },
                    '->',
                    { xtype: 'button', itemId: 'uspEscaladeBadge', hidden: true, cls: 'usp-escalade-badge',
                      margin: '0 10 0 0',
                      tooltip: 'Discussions où le bot a passé la main — cliquez pour les afficher',
                      handler: function () { Usp.escalades.ouvrir(); } },
                    // Centre de notifications : cloche + pastille cumulée (animée s'il y a des notifs).
                    { xtype: 'button', itemId: 'uspNotif', cls: 'usp-icbtn', margin: '0 6 0 0',
                      text: '🔔', tooltip: 'Centre de notifications',
                      handler: function () { Usp.notifications.ouvrir(); } },
                    // À propos : icône seule, animée au survol.
                    { xtype: 'button', itemId: 'uspAbout', cls: 'usp-icbtn', text: 'ℹ️', margin: '0 10 0 0',
                      tooltip: 'À propos (version, développeur)', handler: function () { Usp.apropos(); } },
                    { xtype: 'component', itemId: 'uspHeaderAvatar', margin: '0 6 0 0',
                      html: Usp.avatarRond(Usp.user && Usp.user.photo) },
                    { xtype: 'tbtext', text: Usp.user ? 'Bienvenu(e), ' + Usp.user.nomComplet : '' },
                    { tooltip: 'Déconnexion', cls: 'usp-logout usp-logout-round usp-icbtn',
                      text: '<img src="data:image/svg+xml,' +
                          "%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' " +
                          "stroke='%23c62828' stroke-width='2.2' stroke-linecap='round' stroke-linejoin='round'%3E" +
                          "%3Cpath d='M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4'/%3E" +
                          "%3Cpolyline points='16 17 21 12 16 7'/%3E%3Cline x1='21' y1='12' x2='9' y2='12'/%3E" +
                          "%3C/svg%3E" +
                          '" style="width:18px;height:18px;vertical-align:middle"/>',
                      handler: function () {
                        // Recharge seulement APRÈS la déconnexion (sinon la session n'est pas clôturée).
                        Usp.ajax({ url: '/auth/logout', method: 'POST',
                            callback: function () { location.reload(); } });
                    } }
                ]
            },
            {
                region: 'west',
                title: 'Menu',
                width: 220,
                collapsible: true,
                xtype: 'treepanel',
                itemId: 'menuTree',
                cls: 'usp-menu',
                rootVisible: false,
                store: Ext.create('Ext.data.TreeStore', {
                    fields: ['text', 'baseText', 'view', 'leaf'],
                    root: { expanded: true, children: Usp.menuChildren() }
                }),
                listeners: {
                    // La pastille + la sélection sont gérées de façon centralisée par
                    // Usp.ouvrirVue (aussi utilisé lors des navigations programmatiques).
                    itemclick: function (v, rec) {
                        Usp.ouvrirVue(rec.get('view') || (rec.raw && rec.raw.view));
                    }
                }
            },
            {
                region: 'center',
                xtype: 'panel',
                itemId: 'center',
                layout: 'fit',
                items: [Usp.dashboardPanel()]
            }
        ]
    });
    // Surveillance des escalades du bot (notification aux agents).
    Usp.escalades.demarrer();
    // Centre de notifications : première charge + rafraîchissement périodique.
    Usp.notifications.demarrer();
};

/* ------------------------------------------------------------------
 * Notification d'escalade du bot : badge header + toast lorsqu'une
 * discussion passe en « À reprendre » (le bot a passé la main).
 * ------------------------------------------------------------------ */
Usp.escalades = {
    _task: null,
    _dernierTotal: 0,
    _init: false,
    demarrer: function () {
        if (this._task) { return; }
        if (!Usp.canSee(['ADMIN', 'SUPERVISEUR', 'AGENT', 'MARKETING'])) { return; }
        var self = this;
        this._task = Ext.TaskManager.start({ interval: 20000, run: function () { self.verifier(); } });
        this.verifier();
    },
    arreter: function () {
        if (this._task) { Ext.TaskManager.stop(this._task); this._task = null; }
    },
    verifier: function () {
        if (!Usp.token) { return; }
        var self = this;
        Usp.ajax({ url: '/conversations?statut=A_REPRENDRE&limit=5', method: 'GET',
            success: function (resp) {
                var r = Ext.decode(resp.responseText) || {};
                var total = r.total || 0;
                self.majBadge(total);
                if (self._init && total > self._dernierTotal) {
                    var data = r.data || [];
                    var nom = data.length ? (data[0].nomAffiche || data[0].numeroWhatsapp || '') : '';
                    Usp.toast('🙋 Le bot a passé la main' + (nom ? ' : ' + nom : '') + ' — discussion à reprendre.', 'info');
                    Usp.beep();
                }
                self._dernierTotal = total;
                self._init = true;
            }
        });
    },
    majBadge: function (total) {
        var b = Ext.ComponentQuery.query('#uspEscaladeBadge')[0];
        if (!b) { return; }
        if (total > 0) { b.setText('🙋 ' + total + ' à reprendre'); b.show(); }
        else { b.hide(); }
    },
    ouvrir: function () {
        Usp.ouvrirVue('inbox');
        // Filtre la liste sur les discussions à reprendre.
        Ext.defer(function () {
            var grid = Ext.ComponentQuery.query('#convGrid')[0];
            if (grid && grid.getStore()) {
                grid.getStore().getProxy().extraParams = { statut: 'A_REPRENDRE' };
                grid.getStore().load();
            }
        }, 300);
    }
};

Usp.loadCenter = function (cmp) {
    var center = Ext.ComponentQuery.query('#center')[0];
    center.removeAll();
    center.add(cmp);
};

/* Validation des numéros WhatsApp (chiffres, format international). */
Ext.apply(Ext.form.field.VTypes, {
    numwa: function (v) { return /^[0-9]{6,15}$/.test(v); },
    numwaText: 'Numéro international en chiffres uniquement, ex. 2250102030405',
    numwaMask: /[0-9]/
});

/* ------------------------------------------------------------------
 * Améliorations globales des fenêtres de création / modification :
 *  #2 bouton « Annuler » auto-ajouté à côté d'« Enregistrer/Valider/Créer »
 *  #1 bouton « agrandir » (maximize) sur ces mêmes fenêtres
 *  #3 focus automatique sur le premier champ de saisie à l'ouverture
 * ------------------------------------------------------------------ */
Ext.override(Ext.window.Window, {
    initComponent: function () {
        var b = this.buttons;
        if (Ext.isArray(b)) {
            var aEnregistrer = false, aAnnuler = false;
            Ext.each(b, function (it) {
                var t = (it && it.text) ? String(it.text) : '';
                if (t.indexOf('Enregistrer') >= 0 || t.indexOf('Valider') >= 0 || t.indexOf('Créer') >= 0) { aEnregistrer = true; }
                if (t.indexOf('Annuler') >= 0 || t.indexOf('Fermer') >= 0) { aAnnuler = true; }
            });
            if (aEnregistrer) {
                if (!aAnnuler) {
                    b.push({ text: 'Annuler', tooltip: 'Fermer sans enregistrer',
                        handler: function (btn) { var w = btn.up('window'); if (w) { w.close(); } } });
                }
                if (this.maximizable === undefined) { this.maximizable = true; }
            }
        }
        this.callParent(arguments);
    },
    afterShow: function () {
        this.callParent(arguments);
        var w = this;
        Ext.defer(function () {
            if (w.isDestroyed) { return; }
            var champs = w.query ? w.query('field') : [];
            for (var i = 0; i < champs.length; i++) {
                var f = champs[i], xt = f.getXType && f.getXType();
                if (xt === 'displayfield' || xt === 'hiddenfield' || xt === 'hidden') { continue; }
                if (f.isDisabled && f.isDisabled()) { continue; }
                if (!f.rendered || f.hidden) { continue; }
                if (f.focus) { f.focus(false); break; }
            }
        }, 60);
    }
});

Ext.onReady(function () {
    Ext.QuickTips.init();
    Usp.showLogin();
});
