/*
 * UbiSenderPro - Canal WhatsApp Web (non officiel, via service compagnon Baileys).
 * Deux onglets : Comptes (connexion par QR) et Envoi en masse (5 variantes + pièce jointe).
 * Dépend de app.js (objet global Usp).
 */
Ext.define('Usp.waweb', { singleton: true });

/* ---------- Comptes WhatsApp Web ---------- */
Usp.waweb.sessionStore = function () {
    return Ext.create('Ext.data.Store', {
        fields: ['id', 'libelle', 'numero', 'statut', 'actif'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/wa-web/sessions',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
        autoLoad: true
    });
};

Usp.waweb.sessionsPanel = function () {
    var store = Usp.waweb.sessionStore();
    var selected = function (g) { return g.up('grid').getSelectionModel().getSelection()[0]; };
    return {
        xtype: 'grid', title: 'Comptes WhatsApp Web', store: store,
        columns: [
            { text: 'Libellé', dataIndex: 'libelle', flex: 1 },
            { text: 'Numéro', dataIndex: 'numero', width: 160 },
            { text: 'Statut', dataIndex: 'statut', width: 120, renderer: function (v) {
                var c = v === 'CONNECTE' ? '#2e7d32' : (v === 'QR' ? '#ef6c00' : '#999');
                return '<span style="color:' + c + '">' + (v || '') + '</span>';
            } }
        ],
        tbar: [
            { text: 'Nouveau compte', handler: function () {
                Ext.Msg.prompt('Nouveau compte', 'Libellé :', function (b, val) {
                    if (b === 'ok' && val) {
                        Usp.ajax({ url: '/wa-web/sessions', method: 'POST', jsonData: { libelle: val },
                            success: function () { store.load(); } });
                    }
                });
            } },
            { text: 'Connecter (QR)', handler: function (b) {
                var rec = selected(b); if (!rec) { Ext.Msg.alert('Info', 'Sélectionnez un compte.'); return; }
                Usp.waweb.connect(rec.get('id'), store);
            } },
            { text: 'Déconnecter', handler: function (b) {
                var rec = selected(b); if (!rec) { return; }
                Usp.ajax({ url: '/wa-web/sessions/' + rec.get('id') + '/logout', method: 'POST',
                    success: function () { store.load(); } });
            } },
            { text: 'Supprimer', handler: function (b) {
                var rec = selected(b); if (!rec) { return; }
                Ext.Msg.confirm('Supprimer', 'Supprimer ce compte ?', function (btn) {
                    if (btn === 'yes') {
                        Usp.ajax({ url: '/wa-web/sessions/' + rec.get('id'), method: 'DELETE',
                            success: function () { store.load(); } });
                    }
                });
            } },
            '->',
            { text: 'Rafraîchir', handler: function () { store.load(); } }
        ],
        listeners: { itemdblclick: function (g, rec) { Usp.waweb.connect(rec.get('id'), store); } }
    };
};

/* Fenêtre de connexion : démarre la session puis sonde le statut/QR. */
Usp.waweb.connect = function (id, store) {
    var win = Ext.create('Ext.window.Window', {
        title: 'Connexion WhatsApp Web', width: 360, height: 420, modal: true, bodyPadding: 12,
        layout: 'fit',
        items: [{ xtype: 'component', itemId: 'zone',
            html: '<div style="text-align:center;color:#666">Démarrage…</div>' }],
        listeners: { close: function () { win.polling = false; } }
    });
    win.show();
    var zone = win.down('#zone');
    win.polling = true;

    var render = function (etat) {
        if (!win.polling) { return; }
        var st = etat && etat.status;
        if (st === 'CONNECTE') {
            zone.update('<div style="text-align:center;color:#2e7d32;padding-top:40px">' +
                '<div style="font-size:40px">✔</div><b>Connecté</b><br/>' +
                (etat.me && etat.me.id ? Ext.String.htmlEncode(etat.me.id.split(/[:@]/)[0]) : '') + '</div>');
            win.polling = false;
            if (store) { store.load(); }
            return;
        }
        if (st === 'QR' && etat.qr) {
            zone.update('<div style="text-align:center">' +
                '<div style="margin-bottom:8px;color:#666">Scannez avec WhatsApp → Appareils connectés</div>' +
                '<img src="' + etat.qr + '" style="width:280px;height:280px"/></div>');
        } else {
            zone.update('<div style="text-align:center;color:#666;padding-top:40px">' +
                'Statut : ' + (st || 'CONNEXION') + '…<br/>(préparation du QR)</div>');
        }
        Ext.defer(poll, 2000);
    };

    var poll = function () {
        if (!win.polling) { return; }
        Usp.ajax({ url: '/wa-web/sessions/' + id + '/status', method: 'GET',
            success: function (resp) { render(Ext.decode(resp.responseText)); },
            failure: function () { if (win.polling) { Ext.defer(poll, 3000); } } });
    };

    Usp.ajax({ url: '/wa-web/sessions/' + id + '/start', method: 'POST',
        success: function (resp) { render(Ext.decode(resp.responseText)); },
        failure: function () { zone.update('<div style="color:#a00">Service WhatsApp Web injoignable.</div>'); } });
};

/* ---------- Envoi en masse ---------- */
Usp.waweb.bulkPanel = function () {
    var jobStore = Ext.create('Ext.data.Store', {
        fields: ['id', 'nom', 'statut', 'total', 'envoyes', 'echoues'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/wa-bulk',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
        autoLoad: true
    });
    var sessionCombo = Ext.create('Ext.data.Store', {
        fields: ['id', 'libelle'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/wa-web/sessions',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
        autoLoad: true
    });
    var media = { url: null, type: null, mime: null, nom: null };

    var form = {
        xtype: 'form', region: 'center', autoScroll: true, bodyPadding: 12, border: false,
        defaults: { anchor: '100%' },
        items: [
            { xtype: 'combobox', name: 'sessionId', fieldLabel: 'Compte WhatsApp Web', allowBlank: false,
              store: sessionCombo, valueField: 'id', displayField: 'libelle', queryMode: 'local', editable: false },
            { xtype: 'textfield', name: 'nom', fieldLabel: 'Nom de l\'envoi' },
            { xtype: 'displayfield', value: '<b>Variantes</b> — une au hasard est envoyée à chaque contact (anti-spam). ' +
              'Utilisez <b>[NAME]</b> pour insérer le nom.' },
            { xtype: 'textareafield', name: 'msg1', fieldLabel: 'Message 1', height: 60,
              emptyText: 'Bonjour [NAME], ...' },
            { xtype: 'textareafield', name: 'msg2', fieldLabel: 'Message 2', height: 50 },
            { xtype: 'textareafield', name: 'msg3', fieldLabel: 'Message 3', height: 50 },
            { xtype: 'textareafield', name: 'msg4', fieldLabel: 'Message 4', height: 50 },
            { xtype: 'textareafield', name: 'msg5', fieldLabel: 'Message 5', height: 50 },
            { xtype: 'fieldcontainer', fieldLabel: 'Pièce jointe', layout: 'hbox', items: [
                { xtype: 'filefield', buttonOnly: true, hideLabel: true, buttonText: 'Parcourir...',
                  listeners: { change: function (f) { Usp.waweb.uploadPiece(f, media); } } },
                { xtype: 'component', itemId: 'pjInfo', margin: '4 0 0 8',
                  html: '<span style="color:#888;font-size:11px">optionnel (image/vidéo/document)</span>' }
            ] },
            { xtype: 'fieldcontainer', fieldLabel: 'Débit', layout: 'hbox', defaults: { width: 120, labelWidth: 70 }, items: [
                { xtype: 'numberfield', name: 'attenteMin', fieldLabel: 'Attente', value: 4, minValue: 0 },
                { xtype: 'numberfield', name: 'attenteMax', fieldLabel: 'à', value: 8, minValue: 0, labelWidth: 20 },
                { xtype: 'displayfield', value: 's entre messages', width: 110 }
            ] },
            { xtype: 'fieldcontainer', fieldLabel: '', layout: 'hbox', defaults: { width: 120, labelWidth: 70 }, items: [
                { xtype: 'numberfield', name: 'pauseApres', fieldLabel: 'Pause après', value: 10, minValue: 0 },
                { xtype: 'numberfield', name: 'pauseMin', fieldLabel: 'msgs:', value: 10, minValue: 0, labelWidth: 40 },
                { xtype: 'numberfield', name: 'pauseMax', fieldLabel: 'à', value: 20, minValue: 0, labelWidth: 20 }
            ] },
            { xtype: 'textareafield', name: 'destinatairesTexte', fieldLabel: 'Destinataires', height: 110,
              emptyText: 'Une ligne par contact : numero;nom\nEx. 2250700000000;Awa' }
        ],
        bbar: ['->',
            { text: 'Créer et lancer', formBind: true, handler: function (b) {
                var f = b.up('form').getForm();
                if (!f.isValid()) { return; }
                var v = f.getValues();
                v.mediaUrl = media.url; v.mediaType = media.type; v.mediaMime = media.mime; v.mediaNom = media.nom;
                Usp.ajax({ url: '/wa-bulk', method: 'POST', jsonData: v,
                    success: function (resp) {
                        var job = Ext.decode(resp.responseText);
                        Usp.ajax({ url: '/wa-bulk/' + job.id + '/launch', method: 'POST',
                            success: function () {
                                Ext.Msg.alert('Lancé', 'Envoi en masse démarré (' + job.total + ' destinataires).');
                                jobStore.load();
                            },
                            failure: function (r) { Ext.Msg.alert('Erreur', Usp.waweb.err(r, 'Lancement impossible.')); } });
                    },
                    failure: function (r) { Ext.Msg.alert('Erreur', Usp.waweb.err(r, 'Création impossible.')); } });
            } }
        ]
    };

    var jobs = {
        xtype: 'grid', region: 'south', height: 200, title: 'Envois', store: jobStore, split: true,
        columns: [
            { text: 'Nom', dataIndex: 'nom', flex: 1 },
            { text: 'Statut', dataIndex: 'statut', width: 110 },
            { text: 'Total', dataIndex: 'total', width: 70 },
            { text: 'Envoyés', dataIndex: 'envoyes', width: 80 },
            { text: 'Échoués', dataIndex: 'echoues', width: 80 }
        ],
        tbar: [{ text: 'Rafraîchir', handler: function () { jobStore.load(); } }]
    };

    return { xtype: 'panel', title: 'Envoi en masse', layout: 'border', items: [form, jobs] };
};

Usp.waweb.uploadPiece = function (f, media) {
    var file = f.fileInputEl.dom.files[0];
    if (!file) { return; }
    var info = f.up('fieldcontainer').down('#pjInfo');
    var reader = new FileReader();
    reader.onload = function (e) {
        var b64 = e.target.result.split(',')[1];
        var mime = file.type || 'application/octet-stream';
        Usp.ajax({ url: '/media/upload', method: 'POST',
            jsonData: { fichierBase64: b64, mimeType: mime, nomFichier: file.name },
            success: function (resp) {
                var r = Ext.decode(resp.responseText);
                media.url = r.url; media.mime = mime; media.nom = file.name;
                media.type = mime.indexOf('image/') === 0 ? 'image'
                    : mime.indexOf('video/') === 0 ? 'video'
                    : mime.indexOf('audio/') === 0 ? 'audio' : 'document';
                if (info) { info.update('<span style="color:#2e7d32;font-size:11px">✔ ' +
                    Ext.String.htmlEncode(file.name) + '</span>'); }
            },
            failure: function () { Ext.Msg.alert('Erreur', 'Téléversement de la pièce jointe impossible.'); } });
    };
    reader.readAsDataURL(file);
};

Usp.waweb.err = function (resp, def) {
    try { var r = Ext.decode(resp.responseText); if (r && r.erreur) { return r.erreur; } } catch (e) {}
    return def;
};

Usp.waweb.tabs = function () {
    return {
        xtype: 'tabpanel', title: 'WhatsApp Web',
        items: [Usp.waweb.sessionsPanel(), Usp.waweb.bulkPanel()]
    };
};
