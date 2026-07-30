/*
 * UbiSmartCRM Pro - Gestion de la licence (client).
 * État + alertes, import d'une clé/fichier .lic, génération de la demande
 * d'activation hors ligne (.licreq), journal local. Réservé ADMIN / SUPPORT
 * (l'état alimente aussi le bandeau d'alerte global).
 */
Ext.define('Usp.licence', { singleton: true });

Usp.licence.COULEUR = {
    ACTIVE: '#2e7d32', EXPIRE_BIENTOT: '#ef6c00', GRACE: '#ef6c00',
    EXPIREE: '#c62828', INVALIDE: '#c62828', HORLOGE: '#c62828', AUCUNE: '#777'
};
Usp.licence.LIB = {
    ACTIVE: 'Active', EXPIRE_BIENTOT: 'Expire bientôt', GRACE: 'Période de grâce',
    EXPIREE: 'Expirée', INVALIDE: 'Invalide', HORLOGE: 'Horloge suspecte', AUCUNE: 'Aucune licence'
};

Usp.licence.panel = function () {
    return {
        xtype: 'tabpanel', title: 'Licence', listeners: Usp.tabListeners,
        items: [Usp.licence.etatPanel(), Usp.licence.evenementsPanel()]
    };
};

/* ----------------------------- État & actions ----------------------------- */
Usp.licence.etatPanel = function () {
    var charger = function (panel) {
        var body = panel.down('#licBody');
        Usp.ajax({ url: '/licence/etat', method: 'GET',
            success: function (resp) {
                var e = Ext.decode(resp.responseText) || {};
                var c = Usp.licence.COULEUR[e.statut] || '#333';
                var ligne = function (l, v) {
                    return v === undefined || v === null || v === 'null' ? '' :
                        '<tr><td style="color:#888;padding:3px 14px 3px 0;white-space:nowrap">' + l +
                        '</td><td><b>' + Ext.String.htmlEncode(String(v)) + '</b></td></tr>';
                };
                body.update('<div style="padding:14px">' +
                    '<div style="display:inline-block;padding:10px 18px;border-radius:10px;background:#fff;' +
                    'border-left:6px solid ' + c + ';box-shadow:0 1px 4px rgba(0,0,0,.08);margin-bottom:14px">' +
                    '<span style="font-size:19px;font-weight:bold;color:' + c + '">' +
                    (Usp.licence.LIB[e.statut] || e.statut) + '</span>' +
                    (e.joursRestants !== undefined ? ' <span style="color:#666">— ' + e.joursRestants + ' jour(s) restant(s)</span>' : '') +
                    '<div style="color:#666;font-size:12px;margin-top:4px">' + Ext.String.htmlEncode(e.message || '') + '</div></div>' +
                    '<table style="border-collapse:collapse;font-size:13px">' +
                    ligne('Société', e.societe) + ligne('Identifiant client', e.clientId) +
                    ligne('Type', e.type) + ligne('Activation', e.dateActivation) +
                    ligne('Expiration', e.dateExpiration) + ligne('Utilisateurs max', e.maxUsers) +
                    ligne('Agences max', e.maxAgences) + ligne('Modules', e.modules || 'Tous') +
                    ligne('Licence obligatoire', e.obligatoire ? 'Oui' : 'Non (mode libre)') +
                    ligne('Empreinte de CE serveur', e.empreinteServeur) +
                    ligne('Version application', e.version) +
                    '</table></div>');
            },
            failure: function () { body.update('<div style="padding:14px;color:#c62828">État indisponible.</div>'); } });
    };
    return {
        xtype: 'panel', title: '🔑 État & activation', autoScroll: true, bodyStyle: 'background:#eef1f5',
        tbar: [
            { text: '🔄 Rafraîchir', handler: function (b) { charger(b.up('panel')); } }, '-',
            { text: '📥 Importer une licence', cls: 'usp-btn-pri',
              handler: function (b) { Usp.licence.importer(function () { charger(b.up('panel')); }); } },
            { text: '📤 Générer une demande (.licreq)', tooltip: 'À transmettre à l\'éditeur pour activation hors ligne',
              handler: function () { Usp.licence.demande(); } }
        ],
        items: [{ xtype: 'component', itemId: 'licBody', html: '<div style="padding:14px;color:#888">Chargement…</div>' }],
        listeners: { afterrender: function (p) { charger(p); } }
    };
};

/* Import d'une clé d'activation ou d'un fichier .lic. */
Usp.licence.importer = function (onDone) {
    var win = Ext.create('Ext.window.Window', {
        title: 'Importer une licence', width: 560, modal: true, bodyPadding: 12,
        items: [{ xtype: 'form', border: false, defaults: { anchor: '100%' }, items: [
            { xtype: 'displayfield', value: '<span style="color:#888">Collez la <b>clé d\'activation</b> ' +
                'fournie par l\'éditeur, ou chargez le fichier <b>.lic</b>.</span>' },
            { xtype: 'textareafield', name: 'contenu', height: 140, emptyText: 'eyJjbGllbnRJZCI6…​.MEUCIQ…' },
            { xtype: 'filefield', fieldLabel: 'ou fichier .lic', msgTarget: 'side',
              listeners: { change: function (f) {
                  var file = f.fileInputEl.dom.files[0]; if (!file) { return; }
                  var reader = new FileReader();
                  reader.onload = function (e) { f.up('form').down('[name=contenu]').setValue(String(e.target.result || '').trim()); };
                  reader.readAsText(file);
              } } }
        ] }],
        buttons: [
            { text: 'Annuler', handler: function () { win.close(); } },
            { text: '📥 Activer', cls: 'usp-btn-pri', handler: function (b) {
                var form = b.up('window').down('form').getForm();
                var contenu = (form.findField('contenu').getValue() || '').trim();
                if (!contenu) { Ext.Msg.alert('Info', 'Collez la clé ou chargez le fichier .lic.'); return; }
                b.disable();
                Usp.ajax({ url: '/licence/importer', method: 'POST', jsonData: { contenu: contenu },
                    success: function (resp) {
                        win.close();
                        var e = Ext.decode(resp.responseText) || {};
                        Usp.licence._etatBandeau = null; // le bandeau se recalculera
                        Ext.Msg.alert('Licence activée', 'Licence « ' + Ext.String.htmlEncode(e.societe || '') +
                            ' » active jusqu\'au <b>' + Ext.String.htmlEncode(e.dateExpiration || '—') + '</b>.',
                            function () { if (onDone) { onDone(); } Usp.licence.majBandeau(); });
                    },
                    failure: function (resp) { b.enable(); Usp.afficherErreurForm(form, resp, 'Activation impossible.'); } });
            } }
        ]
    });
    win.show();
};

/* Génère et télécharge la demande d'activation hors ligne. */
Usp.licence.demande = function () {
    Ext.Msg.prompt('Demande d\'activation', 'Nom de votre société :', function (btn, societe) {
        if (btn !== 'ok') { return; }
        Usp.ajax({ url: '/licence/demande', method: 'POST',
            jsonData: { societe: societe || '', email: (Usp.user && Usp.user.email) || '' },
            success: function (resp) {
                var r = Ext.decode(resp.responseText) || {};
                var uri = 'data:application/octet-stream,' + encodeURIComponent(r.contenu || '');
                var a = document.createElement('a');
                a.href = uri; a.download = r.fichier || 'REQUEST.licreq';
                document.body.appendChild(a); a.click(); document.body.removeChild(a);
                Ext.Msg.alert('Demande générée',
                    'Fichier <b>' + (r.fichier || 'REQUEST.licreq') + '</b> téléchargé.<br>' +
                    'Transmettez-le à l\'éditeur ; vous recevrez en retour votre licence (.lic).<br><br>' +
                    'Empreinte de ce serveur : <b>' + (r.empreinteServeur || '') + '</b>');
            },
            failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
    }, null, false, Usp.societeParDefaut || '');
};

/* ------------------------------- Journal ------------------------------- */
Usp.licence.evenementsPanel = function () {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'type', 'detail', 'utilisateur', 'createdAt'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/licence/evenements',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
        autoLoad: true
    });
    return {
        xtype: 'grid', title: '🗂️ Journal', store: store,
        columns: [
            { text: 'Date', dataIndex: 'createdAt', width: 140,
              renderer: function (v) { return v ? String(v).replace('T', ' ').substring(0, 16) : ''; } },
            { text: 'Événement', dataIndex: 'type', width: 140 },
            { text: 'Détail', dataIndex: 'detail', flex: 1 },
            { text: 'Par', dataIndex: 'utilisateur', width: 110 }
        ],
        tbar: [{ text: '🔄 Rafraîchir', handler: function () { store.load(); } }]
    };
};

/* -------------------- Bandeau d'alerte global (tous rôles) -------------------- */
/* Affiche un bandeau sous le header quand la licence approche de l'échéance ou
 * est expirée. Alertes : J-30 / J-15 / J-7 / J-1 (statut EXPIRE_BIENTOT), grâce,
 * expiration, horloge. Sans licence obligatoire : rien (mode libre). */
Usp.licence.majBandeau = function () {
    Usp.ajax({ url: '/licence/etat', method: 'GET', success: function (resp) {
        var e = {}; try { e = Ext.decode(resp.responseText) || {}; } catch (ex) {}
        Usp.licence._etatBandeau = e;
        var existant = document.getElementById('usp-lic-bandeau');
        var aAfficher = ['EXPIRE_BIENTOT', 'GRACE', 'EXPIREE', 'INVALIDE', 'HORLOGE'].indexOf(e.statut) !== -1
            || (e.obligatoire && e.statut === 'AUCUNE');
        if (!aAfficher) { if (existant) { existant.parentNode.removeChild(existant); } return; }
        var fond = (e.statut === 'EXPIRE_BIENTOT' || e.statut === 'GRACE') ? '#ef6c00' : '#c62828';
        var texte = Ext.String.htmlEncode(e.message || 'Vérifiez votre licence.') +
            (e.joursRestants !== undefined && e.joursRestants >= 0 ? ' (' + e.joursRestants + ' jour(s) restant(s))' : '');
        var html = '⚠️ ' + texte + ' — <a href="#" onclick="Usp.ouvrirVue(\'licence\');return false"' +
            ' style="color:#fff;text-decoration:underline">Ouvrir l\'écran Licence</a>';
        if (existant) { existant.innerHTML = html; existant.style.background = fond; return; }
        var div = document.createElement('div');
        div.id = 'usp-lic-bandeau';
        div.style.cssText = 'position:fixed;top:54px;left:0;right:0;z-index:9999;background:' + fond +
            ';color:#fff;font:12px "Segoe UI",sans-serif;padding:6px 14px;text-align:center;' +
            'box-shadow:0 2px 6px rgba(0,0,0,.25)';
        div.innerHTML = html;
        document.body.appendChild(div);
    } });
};
