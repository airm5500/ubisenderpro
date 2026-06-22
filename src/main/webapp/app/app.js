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

/* Boutons « Export CSV » / « Export PDF » à ajouter à la tbar d'une grille. */
Usp.export.boutons = function (titre) {
    var faire = function (b, format) {
        var grid = b.up('grid');
        Usp.export.recuperer(grid, function (recs) {
            var cols = Usp.export.colonnes(grid);
            if (format === 'csv') { Usp.export.csv(titre, cols, recs); } else { Usp.export.pdf(titre, cols, recs); }
        });
    };
    return [
        { text: 'Export CSV', tooltip: 'Exporter en CSV (Excel)', handler: function (b) { faire(b, 'csv'); } },
        { text: 'Export PDF', tooltip: 'Exporter en PDF (impression)', handler: function (b) { faire(b, 'pdf'); } }
    ];
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
        '              <span class="pl-pharmacy-card__label">Plateforme d\'envoi</span>',
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
                Usp.ajax({ url: '/parametres/whatsapp.mode_envoi', method: 'GET',
                    success: function (r) {
                        Usp.mode = (Ext.decode(r.responseText) || {}).valeur || 'API';
                        Usp.ajax({ url: '/parametres/whatsapp.prefixe_pays', method: 'GET',
                            success: function (r2) {
                                Usp.prefixe = (Ext.decode(r2.responseText) || {}).valeur || '225';
                                Usp.ajax({ url: '/parametres/app.favicon', method: 'GET',
                                    success: function (r3) {
                                        Usp.appliquerFavicon((Ext.decode(r3.responseText) || {}).valeur);
                                        ouvrir();
                                    }, failure: ouvrir });
                            }, failure: ouvrir });
                    }, failure: ouvrir });
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
Usp.createClientStore = function () {
    return Ext.create('Ext.data.Store', {
        fields: ['id', 'numeroClient', 'nomCompte', 'agence', 'region', 'emailPrincipal', 'statut'],
        pageSize: 25,
        proxy: {
            type: 'ajax',
            url: Usp.apiBase + '/clients',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') },
            reader: { type: 'json', root: 'data', totalProperty: 'total' }
        },
        autoLoad: true
    });
};

/* ---------- Comptes clients : 2 onglets (liste + vérification de numéros) ---------- */
Usp.clientsPanel = function () {
    return {
        xtype: 'tabpanel',
        title: 'Comptes clients',
        listeners: Usp.tabListeners,
        items: [
            Usp.clientsGrid(),
            // Onglet déplacé depuis « WhatsApp Web » (#4) : la vérification de
            // numéros vit désormais à côté de la liste des comptes clients.
            Usp.waweb.filterPanel()
        ]
    };
};

/* Onglet « Liste des comptes » : la grille des comptes clients (existant intact). */
Usp.clientsGrid = function () {
    var store = Usp.createClientStore();
    return {
        xtype: 'grid',
        title: 'Liste des comptes',
        store: store,
        columns: [
            { text: 'N° client', dataIndex: 'numeroClient', width: 110 },
            { text: 'Nom du compte', dataIndex: 'nomCompte', flex: 1 },
            { text: 'Agence', dataIndex: 'agence', width: 120 },
            { text: 'Région', dataIndex: 'region', width: 150 },
            { text: 'E-mail', dataIndex: 'emailPrincipal', width: 200 },
            { text: 'Statut', dataIndex: 'statut', width: 90 },
            { text: 'Contacts', width: 100, sortable: false, menuDisabled: true, dataIndex: 'id',
              renderer: function () {
                  return '<span class="cli-contacts" title="Voir / ajouter les contacts" ' +
                      'style="cursor:pointer;color:#1976d2">👥 contacts</span>';
              } }
        ],
        tbar: [
            { xtype: 'textfield', emptyText: 'Rechercher...', width: 220, listeners: {
                change: function (f, val) {
                    store.getProxy().extraParams = { q: val };
                    store.loadPage(1);
                }, buffer: 400 } },
            '->',
            { text: 'Nouveau client', handler: function () { Usp.clientForm(store, null); } },
            { text: 'Gérer les segmentations', handler: function () { Usp.segmentationsManager(); } },
            { text: 'Importer Excel/CSV', handler: Usp.showImport }
        ].concat(Usp.export.boutons('Comptes clients')),
        bbar: {
            xtype: 'pagingtoolbar',
            store: store,
            displayInfo: true
        },
        listeners: {
            itemdblclick: function (g, rec) { Usp.clientForm(store, rec); },
            cellclick: function (g, td, ci, rec, tr, ri, e) {
                if (e.getTarget('.cli-contacts')) { Usp.contactsWindow(rec.get('id'), rec.get('nomCompte')); }
            }
        }
    };
};

/* ---------- Gestion des segmentations (CRUD) ---------- */
Usp.segmentationsManager = function () {
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
        if (rec) { win.down('form').getForm().setValues(rec.getData()); }
    };
    Ext.create('Ext.window.Window', {
        title: 'Segmentations clients', width: 640, height: 440, modal: true, layout: 'fit',
        items: [{
            xtype: 'grid', store: store,
            columns: [
                { text: 'Code', dataIndex: 'code', width: 120 },
                { text: 'Libellé', dataIndex: 'libelle', flex: 1 },
                { text: 'Description', dataIndex: 'description', flex: 1 },
                { text: 'Ordre', dataIndex: 'ordreAffichage', width: 70 },
                { text: 'Active', dataIndex: 'actif', width: 70, renderer: function (v) { return v ? 'Oui' : 'Non'; } },
                { text: 'Suppr.', width: 60, align: 'center', sortable: false, menuDisabled: true, dataIndex: 'id',
                  renderer: function () { return '<span class="seg-del" title="Supprimer" style="cursor:pointer;color:#c62828">🗑️</span>'; } }
            ],
            tbar: [
                { text: 'Nouvelle segmentation', handler: function () { form(null); } },
                { text: 'Rafraîchir', handler: function () { store.load(); } }
            ],
            listeners: {
                cellclick: function (g, td, ci, rec, tr, ri, e) {
                    if (e.getTarget('.seg-del')) {
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
        }]
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

Usp.clientForm = function (store, rec) {
    var win = Ext.create('Ext.window.Window', {
        title: rec ? 'Modifier le client' : 'Nouveau client',
        width: 520, modal: true, bodyPadding: 12, autoScroll: true,
        items: [{
            xtype: 'form', border: false, defaults: { anchor: '100%' },
            items: [
                { xtype: 'textfield', name: 'numeroClient', fieldLabel: 'Numéro client', allowBlank: false },
                { xtype: 'textfield', name: 'nomCompte', fieldLabel: 'Nom du compte', allowBlank: false },
                { xtype: 'textfield', name: 'agence', fieldLabel: 'Agence' },
                { xtype: 'textfield', name: 'region', fieldLabel: 'Région' },
                { xtype: 'textfield', name: 'emailPrincipal', fieldLabel: 'E-mail', vtype: 'email' },
                Usp.segmentationCombo({ name: 'segmentationId', fieldLabel: 'Segmentation' }),
                { xtype: 'textfield', name: 'ville', fieldLabel: 'Ville' },
                { xtype: 'textfield', name: 'commune', fieldLabel: 'Commune' },
                { xtype: 'textfield', name: 'pays', fieldLabel: 'Pays' },
                { xtype: 'combobox', name: 'statut', fieldLabel: 'Statut', value: 'ACTIF',
                  store: ['PROSPECT', 'ACTIF', 'INACTIF', 'SUSPENDU', 'ARCHIVE'], queryMode: 'local' },
                { xtype: 'textareafield', name: 'notes', fieldLabel: 'Notes', height: 50 }
            ]
        }],
        buttons: [{
            text: 'Enregistrer', formBind: true,
            handler: function (b) {
                var form = b.up('window').down('form').getForm();
                if (!form.isValid()) { return; }
                Usp.ajax({
                    url: rec ? '/clients/' + rec.get('id') : '/clients',
                    method: rec ? 'PUT' : 'POST', jsonData: form.getValues(),
                    success: function () { win.close(); store.load(); },
                    failure: function (resp) {
                        // Message clair renvoyé par le serveur + surlignage du champ fautif (#3/#6).
                        var msg = 'Enregistrement impossible.', champ = null;
                        try {
                            var r = Ext.decode(resp.responseText);
                            msg = r.erreur || msg; champ = r.champ || null;
                        } catch (e) {}
                        if (champ) {
                            var f = form.findField(champ);
                            if (f) { f.markInvalid(msg); f.focus(true, 50); }
                        }
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
    }
};

/* ---------- Contacts d'un client ---------- */
Usp.contactsWindow = function (clientId, nomCompte) {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'nomComplet', 'fonction', 'telephonePrincipal', 'numeroWhatsapp',
                 'email', 'contactPrincipal', 'consentementWhatsapp', 'desabonne'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/clients/' + clientId + '/contacts',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') },
            reader: { type: 'json', root: 'data', totalProperty: 'total' } },
        autoLoad: true
    });
    Ext.create('Ext.window.Window', {
        title: 'Contacts — ' + nomCompte, width: 720, height: 420, modal: true, layout: 'fit',
        items: [{
            xtype: 'grid', store: store,
            columns: [
                { text: 'Nom', dataIndex: 'nomComplet', flex: 1 },
                { text: 'Fonction', dataIndex: 'fonction', width: 120 },
                { text: 'Téléphone', dataIndex: 'telephonePrincipal', width: 120 },
                { text: 'WhatsApp', dataIndex: 'numeroWhatsapp', width: 130 },
                { text: 'Principal', dataIndex: 'contactPrincipal', width: 70, renderer: function (v) { return v ? 'Oui' : ''; } },
                { text: 'Désab.', dataIndex: 'desabonne', width: 60, renderer: function (v) { return v ? 'Oui' : ''; } }
            ],
            tbar: [{ text: 'Nouveau contact', handler: function () { Usp.contactForm(clientId, store, null); } }],
            listeners: { itemdblclick: function (g, rec) { Usp.contactForm(clientId, store, rec); } }
        }]
    }).show();
};

Usp.contactForm = function (clientId, store, rec) {
    var win = Ext.create('Ext.window.Window', {
        title: rec ? 'Modifier le contact' : 'Nouveau contact', width: 480, modal: true, bodyPadding: 12,
        items: [{
            xtype: 'form', border: false, defaults: { anchor: '100%' },
            items: [
                { xtype: 'textfield', name: 'nomComplet', fieldLabel: 'Nom complet', allowBlank: false },
                { xtype: 'textfield', name: 'fonction', fieldLabel: 'Fonction' },
                { xtype: 'textfield', name: 'telephonePrincipal', fieldLabel: 'Téléphone' },
                { xtype: 'textfield', name: 'numeroWhatsapp', fieldLabel: 'Numéro WhatsApp',
                  emptyText: 'Format international, ex. 2250700000000' },
                { xtype: 'textfield', name: 'email', fieldLabel: 'E-mail', vtype: 'email' },
                { xtype: 'checkbox', name: 'contactPrincipal', boxLabel: 'Contact principal' },
                { xtype: 'checkbox', name: 'consentementWhatsapp', boxLabel: 'Consentement WhatsApp' }
            ]
        }],
        buttons: [{
            text: 'Enregistrer', formBind: true,
            handler: function (b) {
                var form = b.up('window').down('form').getForm();
                if (!form.isValid()) { return; }
                var data = form.getValues();
                data.clientId = clientId;
                data.contactPrincipal = form.findField('contactPrincipal').getValue();
                data.consentementWhatsapp = form.findField('consentementWhatsapp').getValue();
                Usp.ajax({
                    url: rec ? '/contacts/' + rec.get('id') : '/contacts',
                    method: rec ? 'PUT' : 'POST', jsonData: data,
                    success: function () { win.close(); store.load(); },
                    failure: function () { Ext.Msg.alert('Erreur', 'Enregistrement impossible.'); }
                });
            }
        }]
    });
    win.show();
    if (rec) { win.down('form').getForm().setValues(rec.getData()); }
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
    { text: 'Promotions',          view: 'promotions', icon: '🏷️', roles: ['ADMIN', 'CATALOGUE'] },
    { text: 'Campagnes',           view: 'campaigns',  icon: '🚀', roles: ['ADMIN', 'MARKETING'] },
    { text: 'WhatsApp Web',        view: 'waweb',      iconHtml: Usp.ICON_WA, roles: ['ADMIN', 'MARKETING'] },
    { text: 'Historique des envois', view: 'historique', icon: '🗂️', roles: ['ADMIN', 'MARKETING'] },
    { text: 'CRM / Opportunités',  view: 'crm',        icon: '🎯', roles: ['ADMIN', 'SUPERVISEUR', 'AGENT', 'MARKETING'] },
    { text: 'Paramètres',          view: 'settings',   icon: '⚙️', roles: ['ADMIN'] },
    { text: 'Utilisateurs',        view: 'users',      icon: '👤', roles: ['ADMIN'] }
];

Usp.canSee = function (roles) {
    if (!roles || roles.length === 0) { return true; }
    var mine = (Usp.user && Usp.user.roles) || [];
    if (mine.indexOf('ADMIN') !== -1) { return true; }
    return roles.some(function (r) { return mine.indexOf(r) !== -1; });
};

/* Pastille sur l'onglet actif d'un tabpanel. */
Usp.tabPastille = function (tp, active) {
    if (!tp || !tp.items) { return; }
    tp.items.each(function (t) {
        if (t.baseTitle === undefined) { t.baseTitle = t.title; }
        t.setTitle(t.baseTitle + (t === active ? ' <span style="color:#25d366">●</span>' : ''));
    });
};
Usp.tabListeners = {
    afterrender: function (tp) { Usp.tabPastille(tp, tp.getActiveTab()); },
    tabchange: function (tp, nc) { Usp.tabPastille(tp, nc); }
};

Usp.menuChildren = function () {
    return Usp.MENU.filter(function (m) { return Usp.canSee(m.roles); })
        .map(function (m) {
            var pre = m.iconHtml ? m.iconHtml + ' ' : (m.icon ? m.icon + '  ' : '');
            var t = pre + m.text;
            return { text: t, baseText: t, leaf: true, view: m.view };
        });
};

/* Charge la vue correspondante dans la zone centrale (menu + cartes du tableau de bord). */
Usp.ouvrirVue = function (vue) {
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
        case 'promotions': Usp.loadCenter(Usp.catalogue.promotionsPanel()); break;
        case 'campaigns': Usp.loadCenter(Usp.campaign.listPanel()); break;
        case 'waweb': Usp.loadCenter(Usp.waweb.tabs()); break;
        case 'historique': Usp.loadCenter(Usp.history.panel()); break;
        case 'crm': Usp.loadCenter(Usp.crm.tabs()); break;
        case 'settings': Usp.loadCenter(Usp.settings.tabs()); break;
        case 'clients': Usp.loadCenter(Usp.clientsPanel()); break;
        case 'users': Usp.loadCenter(Usp.users.panel()); break;
        case 'dashboard': Usp.loadCenter(Usp.dashboardPanel()); break;
        case 'import': Usp.showImport(); break;
        default: Usp.loadCenter(Usp.dashboardPanel());
    }
};

/* ---------- Viewport principal ---------- */
Usp.showMain = function () {
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
                    { xtype: 'tbtext', text: Usp.user ? Usp.user.nomComplet : '' },
                    { tooltip: 'Déconnexion', cls: 'usp-logout',
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
                cls: 'usp-menu',
                rootVisible: false,
                store: Ext.create('Ext.data.TreeStore', {
                    fields: ['text', 'baseText', 'view', 'leaf'],
                    root: { expanded: true, children: Usp.menuChildren() }
                }),
                listeners: {
                    itemclick: function (v, rec) {
                        // Pastille sur le menu actif (rec.parentNode = racine, items en enfants directs).
                        var root = rec.parentNode || rec.store.getRootNode();
                        if (root) {
                            root.eachChild(function (n) {
                                if (!n.data.baseText) { n.data.baseText = n.get('text'); }
                                n.set('text', n.data.baseText);
                            });
                        }
                        if (!rec.data.baseText) { rec.data.baseText = rec.get('text'); }
                        rec.set('text', rec.data.baseText + ' <span style="color:#25d366">●</span>');

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

Ext.onReady(function () {
    Ext.QuickTips.init();
    Usp.showLogin();
});
