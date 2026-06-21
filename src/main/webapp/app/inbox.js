/*
 * UbiSenderPro - Boîte de réception WhatsApp (section 19 de la spec).
 * Présentation en trois colonnes : conversations | discussion | fiche contact.
 * Dépend de app.js (objet global Usp).
 */
Ext.define('Usp.inbox', { singleton: true });

Usp.inbox.currentConv = null;
Usp.inbox.msgStore = null;

Usp.inbox.conversationStore = function () {
    return Ext.create('Ext.data.Store', {
        fields: ['id', 'numeroWhatsapp', 'nomAffiche', 'statut', 'agentId', 'canal', 'waWebSessionId',
                 'nonLu', 'dernierMessage', 'dateDernierMessage', 'whatsappAccountId', 'contactId'],
        proxy: {
            type: 'ajax',
            url: Usp.apiBase + '/conversations',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') },
            reader: { type: 'json', root: 'data', totalProperty: 'total' }
        },
        autoLoad: true
    });
};

Usp.inbox.renderMessage = function (rec) {
    var entrant = rec.get('direction') === 'ENTRANT';
    var note = rec.get('noteInterne');
    var align = entrant ? 'left' : 'right';
    var bg = note ? '#fff7d6' : (entrant ? '#ffffff' : '#dcf8c6');
    var statut = (!entrant && rec.get('statut'))
        ? '<div style="font-size:10px;color:#888;text-align:right">' + rec.get('statut') + '</div>' : '';
    var heure = rec.get('createdAt')
        ? Ext.String.htmlEncode(String(rec.get('createdAt')).replace('T', ' ').substring(0, 16)) : '';
    return '<div style="text-align:' + align + ';margin:4px 8px">' +
        '<div style="display:inline-block;max-width:70%;background:' + bg +
        ';padding:6px 10px;border-radius:8px;box-shadow:0 1px 1px rgba(0,0,0,.1);text-align:left">' +
        (note ? '<div style="font-size:10px;color:#b8860b">Note interne</div>' : '') +
        Ext.String.htmlEncode(rec.get('contenu') || '') +
        '<div style="font-size:10px;color:#999">' + heure + '</div>' + statut +
        '</div></div>';
};

Usp.inbox.panel = function () {
    var convStore = Usp.inbox.conversationStore();
    Usp.inbox.convStore = convStore;

    var msgStore = Ext.create('Ext.data.Store', {
        fields: ['id', 'direction', 'typeMessage', 'contenu', 'statut', 'noteInterne', 'erreur', 'createdAt'],
        proxy: {
            type: 'ajax',
            url: Usp.apiBase + '/conversations/0/messages',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') },
            reader: { type: 'json' }
        }
    });
    Usp.inbox.msgStore = msgStore;

    var discussion = Ext.create('Ext.panel.Panel', {
        region: 'center', title: 'Discussion', autoScroll: true, bodyPadding: 4,
        bodyStyle: 'background:#efeae2',
        html: '<div style="padding:20px;color:#999">Sélectionnez une conversation</div>',
        tbar: [
            { text: 'Affecter', handler: function () { Usp.inbox.action('assign'); } },
            { text: 'Clôturer', handler: function () { Usp.inbox.action('close'); } },
            { text: 'Rouvrir', handler: function () { Usp.inbox.action('reopen'); } }
        ],
        bbar: [
            { xtype: 'textfield', itemId: 'msgInput', flex: 1, emptyText: 'Écrire un message...',
              listeners: { specialkey: function (f, e) { if (e.getKey() === e.ENTER) { Usp.inbox.envoyer(f); } } } },
            { xtype: 'button', text: 'Envoyer', handler: function (b) { Usp.inbox.envoyer(b.up('toolbar').down('#msgInput')); } },
            { xtype: 'button', text: 'Lien', tooltip: 'Envoyer un média par URL publique', handler: function () { Usp.inbox.media(); } },
            { xtype: 'button', text: 'Joindre', tooltip: 'Téléverser et envoyer un fichier', handler: function () { Usp.inbox.joindre(); } },
            { xtype: 'button', text: 'Note', tooltip: 'Note interne', handler: function (b) { Usp.inbox.note(b.up('toolbar').down('#msgInput')); } }
        ]
    });

    msgStore.on('load', function (store) {
        var html = '';
        store.each(function (r) { html += Usp.inbox.renderMessage(r); });
        discussion.update(html || '<div style="padding:20px;color:#999">Aucun message</div>');
        var el = discussion.body; if (el) { el.scroll('bottom', 100000); }
    });

    var contactPanel = Ext.create('Ext.panel.Panel', {
        region: 'east', title: 'Fiche contact', width: 280, collapsible: true, bodyPadding: 10,
        html: '<div style="color:#999">Aucune conversation sélectionnée</div>'
    });

    var loadConversation = function (rec) {
        Usp.inbox.currentConv = rec;
        msgStore.getProxy().url = Usp.apiBase + '/conversations/' + rec.get('id') + '/messages';
        msgStore.load();
        Usp.ajax({ url: '/conversations/' + rec.get('id') + '/read', method: 'POST' });
        contactPanel.update(
            '<b>' + Ext.String.htmlEncode(rec.get('nomAffiche') || rec.get('numeroWhatsapp')) + '</b><hr/>' +
            '<div><b>Numéro WhatsApp :</b><br/>' + Ext.String.htmlEncode(rec.get('numeroWhatsapp') || '') + '</div><br/>' +
            '<div><b>Statut :</b> ' + Ext.String.htmlEncode(rec.get('statut') || '') + '</div>');
    };

    return {
        xtype: 'panel', title: 'Discussions', layout: 'border',
        items: [
            {
                region: 'west', width: 300, xtype: 'grid', title: 'Conversations', hideHeaders: true,
                store: convStore,
                columns: [{
                    flex: 1, dataIndex: 'nomAffiche',
                    renderer: function (v, m, rec) {
                        var titre = Ext.String.htmlEncode(v || rec.get('numeroWhatsapp') || '');
                        var apercu = Ext.String.htmlEncode(rec.get('dernierMessage') || '');
                        var canal = rec.get('canal') === 'WEB'
                            ? '<span style="background:#e8f0fe;color:#1967d2;border-radius:3px;padding:0 4px;font-size:10px;margin-right:4px">Web</span>' : '';
                        var badge = rec.get('nonLu') > 0
                            ? '<span style="float:right;background:#25d366;color:#fff;border-radius:10px;padding:0 6px;font-size:11px">' + rec.get('nonLu') + '</span>' : '';
                        return '<div>' + canal + '<b>' + titre + '</b>' + badge + '</div>' +
                            '<div style="color:#888;font-size:11px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">' + apercu + '</div>';
                    }
                }],
                tbar: [
                    { xtype: 'button', text: 'Nouveau', tooltip: 'Composer un message (texte/image) hors modèle', handler: function () { Usp.inbox.nouveauMessage(); } },
                    { xtype: 'button', text: 'Toutes', handler: function () { convStore.getProxy().extraParams = {}; convStore.load(); } },
                    { xtype: 'button', text: 'Non lues', handler: function () { convStore.getProxy().extraParams = { statut: 'OUVERTE' }; convStore.load(); } },
                    '->',
                    { xtype: 'button', text: 'Rafraîchir', handler: function () { convStore.load(); } }
                ],
                listeners: { itemclick: function (g, rec) { loadConversation(rec); } }
            },
            discussion,
            contactPanel
        ]
    };
};

Usp.inbox.envoyer = function (field) {
    var conv = Usp.inbox.currentConv;
    if (!conv) { Ext.Msg.alert('Info', 'Sélectionnez une conversation.'); return; }
    var texte = field.getValue();
    if (!texte) { return; }
    var apres = function () { field.setValue(''); Usp.inbox.reloadMessages(); };
    var echec = function () { Ext.Msg.alert('Erreur', 'Envoi impossible (compte/connexion ou fenêtre de 24h).'); };
    if (conv.get('canal') === 'WEB') {
        Usp.ajax({ url: '/wa-web/sessions/' + conv.get('waWebSessionId') + '/send', method: 'POST',
            jsonData: { numero: conv.get('numeroWhatsapp'), texte: texte },
            success: function (resp) { var r = Ext.decode(resp.responseText) || {}; if (r.success) { apres(); } else { echec(); } },
            failure: echec });
    } else {
        Usp.ajax({ url: '/whatsapp/messages/text', method: 'POST',
            jsonData: { accountId: conv.get('whatsappAccountId'), numero: conv.get('numeroWhatsapp'), texte: texte },
            success: apres, failure: echec });
    }
};

/* Envoi d'un média par URL publique (lien hébergé). */
Usp.inbox.media = function () {
    var conv = Usp.inbox.currentConv;
    if (!conv) { Ext.Msg.alert('Info', 'Sélectionnez une conversation.'); return; }
    var win = Ext.create('Ext.window.Window', {
        title: 'Joindre un média', width: 460, modal: true, bodyPadding: 12,
        items: [{ xtype: 'form', border: false, defaults: { anchor: '100%' }, items: [
            { xtype: 'combobox', name: 'type', fieldLabel: 'Type', value: 'image',
              store: [['image', 'Image'], ['video', 'Vidéo'], ['document', 'Document'], ['audio', 'Audio']],
              queryMode: 'local', editable: false },
            { xtype: 'textfield', name: 'url', fieldLabel: 'URL du fichier', allowBlank: false,
              emptyText: 'https://… (lien public du fichier)' },
            { xtype: 'textfield', name: 'legende', fieldLabel: 'Légende' }
        ] }],
        buttons: [{ text: 'Envoyer', handler: function (b) {
            var f = b.up('window').down('form').getForm();
            if (!f.isValid()) { return; }
            var v = f.getValues();
            Usp.ajax({
                url: '/whatsapp/messages/media', method: 'POST',
                jsonData: { accountId: conv.get('whatsappAccountId'), numero: conv.get('numeroWhatsapp'),
                            type: v.type, url: v.url, legende: v.legende },
                success: function () { win.close(); Usp.inbox.reloadMessages(); },
                failure: function () { Ext.Msg.alert('Erreur', 'Envoi du média impossible.'); }
            });
        } }]
    });
    win.show();
};

/* Déduit le type WhatsApp (image/video/audio/document) à partir du type MIME. */
Usp.inbox.typeMedia = function (mime) {
    mime = mime || '';
    if (mime.indexOf('image/') === 0) { return 'image'; }
    if (mime.indexOf('video/') === 0) { return 'video'; }
    if (mime.indexOf('audio/') === 0) { return 'audio'; }
    return 'document';
};

/* Téléverse un fichier local vers l'API WhatsApp (/media) puis l'envoie dans la conversation. */
Usp.inbox.joindre = function () {
    var conv = Usp.inbox.currentConv;
    if (!conv) { Ext.Msg.alert('Info', 'Sélectionnez une conversation.'); return; }
    var fileData = { base64: null, nom: null, mime: null };

    var win = Ext.create('Ext.window.Window', {
        title: 'Téléverser un fichier', width: 460, modal: true, bodyPadding: 12,
        items: [{
            xtype: 'form', border: false, defaults: { anchor: '100%' },
            items: [
                { xtype: 'filefield', name: 'fichier', fieldLabel: 'Fichier', buttonText: 'Parcourir...',
                  allowBlank: false,
                  listeners: { change: function (f) {
                      var file = f.fileInputEl.dom.files[0];
                      if (!file) { return; }
                      fileData.nom = file.name;
                      fileData.mime = file.type || 'application/octet-stream';
                      var reader = new FileReader();
                      reader.onload = function (e) { fileData.base64 = e.target.result.split(',')[1]; };
                      reader.readAsDataURL(file);
                  } } },
                { xtype: 'textfield', name: 'legende', fieldLabel: 'Légende',
                  emptyText: 'Optionnelle (image, vidéo, document)' }
            ]
        }],
        buttons: [{
            text: 'Envoyer', formBind: true,
            handler: function (b) {
                if (!fileData.base64) { Ext.Msg.alert('Erreur', 'Sélectionnez un fichier.'); return; }
                var legende = b.up('window').down('[name=legende]').getValue();
                b.disable();
                var web = conv.get('canal') === 'WEB';
                var koMedia = function () { b.enable(); Ext.Msg.alert('Erreur', 'Envoi du média impossible (connexion ou fenêtre de 24h).'); };
                if (web) {
                    Usp.ajax({ url: '/media/upload', method: 'POST',
                        jsonData: { fichierBase64: fileData.base64, mimeType: fileData.mime, nomFichier: fileData.nom },
                        success: function (resp) {
                            var up = Ext.decode(resp.responseText);
                            Usp.ajax({ url: '/wa-web/sessions/' + conv.get('waWebSessionId') + '/send-media', method: 'POST',
                                jsonData: { numero: conv.get('numeroWhatsapp'), type: Usp.inbox.typeMedia(fileData.mime),
                                            mediaUrl: up.url, mimeType: fileData.mime, fileName: fileData.nom, caption: legende || null },
                                success: function (r2) { var r = Ext.decode(r2.responseText) || {}; if (r.success) { win.close(); Usp.inbox.reloadMessages(); } else { koMedia(); } },
                                failure: koMedia });
                        },
                        failure: koMedia });
                    return;
                }
                Usp.ajax({
                    url: '/whatsapp/media', method: 'POST',
                    jsonData: { accountId: conv.get('whatsappAccountId'), fichierBase64: fileData.base64,
                                mimeType: fileData.mime, nomFichier: fileData.nom },
                    success: function (resp) {
                        var up = Ext.decode(resp.responseText);
                        Usp.ajax({
                            url: '/whatsapp/messages/media', method: 'POST',
                            jsonData: { accountId: conv.get('whatsappAccountId'), numero: conv.get('numeroWhatsapp'),
                                        type: Usp.inbox.typeMedia(fileData.mime), mediaId: up.mediaId,
                                        mimeType: fileData.mime, nomFichier: fileData.nom, legende: legende || null },
                            success: function () { win.close(); Usp.inbox.reloadMessages(); },
                            failure: function () { b.enable(); Ext.Msg.alert('Erreur', 'Envoi du média impossible (vérifiez la fenêtre de 24h).'); }
                        });
                    },
                    failure: function (resp) {
                        b.enable();
                        var msg = 'Téléversement impossible.';
                        try { var r = Ext.decode(resp.responseText); if (r && r.erreur) { msg = r.erreur; } } catch (e) {}
                        Ext.Msg.alert('Erreur', msg);
                    }
                });
            }
        }]
    });
    win.show();
};

/* Composer un message (texte et/ou image) hors modèle, via le canal choisi (API officielle / WhatsApp Web). */
Usp.inbox.nouveauMessage = function () {
    var fileData = { base64: null, nom: null, mime: null };
    var jsonStore = function (url) {
        return Ext.create('Ext.data.Store', { fields: ['id', 'libelle'],
            proxy: { type: 'ajax', url: Usp.apiBase + url,
                headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
            autoLoad: true });
    };
    var apiStore = jsonStore('/whatsapp/accounts');
    var webStore = jsonStore('/wa-web/sessions');

    var maj = function (win) {
        var web = win.down('#canal').getValue().canal === 'WEB';
        win.down('#cibleApi').setVisible(!web).setDisabled(web);
        win.down('#cibleWeb').setVisible(web).setDisabled(!web);
    };

    var win = Ext.create('Ext.window.Window', {
        title: 'Nouveau message', width: 480, modal: true, bodyPadding: 12,
        items: [{ xtype: 'form', border: false, defaults: { anchor: '100%' }, items: [
            { xtype: 'radiogroup', itemId: 'canal', fieldLabel: 'Canal', columns: 2,
              items: [
                { boxLabel: 'API officielle', name: 'canal', inputValue: 'API', checked: Usp.mode !== 'WEB' },
                { boxLabel: 'WhatsApp Web', name: 'canal', inputValue: 'WEB', checked: Usp.mode === 'WEB' }
              ],
              listeners: { change: function (g) { maj(g.up('window')); } } },
            { xtype: 'combobox', itemId: 'cibleApi', name: 'accountId', fieldLabel: 'Compte (API)',
              store: apiStore, valueField: 'id', displayField: 'libelle', queryMode: 'local', editable: false },
            { xtype: 'combobox', itemId: 'cibleWeb', name: 'sessionId', fieldLabel: 'Compte (Web)',
              store: webStore, valueField: 'id', displayField: 'libelle', queryMode: 'local', editable: false, hidden: true },
            { xtype: 'textfield', name: 'numero', fieldLabel: 'Numéro(s)', allowBlank: false, value: Usp.prefixe,
              emptyText: 'Un ou plusieurs séparés par ;  ex. 2250102030405;2250506070809' },
            { xtype: 'textareafield', name: 'texte', fieldLabel: 'Message', height: 80,
              emptyText: 'Texte du message (ou légende de l\'image)' },
            { xtype: 'fieldcontainer', fieldLabel: 'Image / fichier', layout: 'hbox', items: [
                { xtype: 'filefield', name: 'fichier', buttonOnly: true, hideLabel: true, buttonText: 'Parcourir...',
                  listeners: { change: function (f) {
                      var file = f.fileInputEl.dom.files[0];
                      var info = f.up('fieldcontainer').down('#njInfo');
                      if (!file) { return; }
                      fileData.nom = file.name; fileData.mime = file.type || 'application/octet-stream';
                      var reader = new FileReader();
                      reader.onload = function (e) {
                          fileData.base64 = e.target.result.split(',')[1];
                          if (info) { info.update('<span style="color:#2e7d32;font-size:11px">✔ ' +
                              Ext.String.htmlEncode(file.name) + '</span>'); }
                      };
                      reader.readAsDataURL(file);
                  } } },
                { xtype: 'component', itemId: 'njInfo', margin: '4 0 0 8',
                  html: '<span style="color:#888;font-size:11px">optionnel</span>' }
            ] }
        ] }],
        buttons: [{ text: 'Envoyer', formBind: true, handler: function (b) {
            var form = b.up('window').down('form').getForm();
            if (!form.isValid()) { return; }
            var v = form.getValues();
            var web = v.canal === 'WEB';
            var cible = web ? v.sessionId : v.accountId;
            if (!cible) { Ext.Msg.alert('Info', 'Choisissez un compte ' + (web ? 'WhatsApp Web' : 'API') + '.'); return; }
            var numeros = String(v.numero || '').split(/[;,\n]/)
                .map(function (n) { return Usp.normNumero(n); })
                .filter(function (n) { return n.length >= 8; });
            if (!numeros.length) { Ext.Msg.alert('Info', 'Aucun numéro valide (format international, ex. 2250102030405).'); return; }
            if (!fileData.base64 && !v.texte) { Ext.Msg.alert('Info', 'Saisissez un texte ou joignez un fichier.'); return; }
            b.disable();

            var res = { ok: 0, ko: 0 };
            var termine = function () {
                b.enable();
                if (Usp.inbox.convStore) { Usp.inbox.convStore.load(); }
                Ext.Msg.alert('Envoi terminé', numeros.length + ' destinataire(s) : ' +
                    res.ok + ' envoyé(s), ' + res.ko + ' échec(s).');
                if (res.ok > 0) { win.close(); }
            };
            var envoyerUn = function (numero, mediaRef, next) {
                var okCb = function () { res.ok++; next(); };
                var koCb = function () { res.ko++; next(); };
                var okWeb = function (resp) { var r = Ext.decode(resp.responseText) || {}; if (r.success) { okCb(); } else { koCb(); } };
                if (web && fileData.base64) {
                    Usp.ajax({ url: '/wa-web/sessions/' + cible + '/send-media', method: 'POST',
                        jsonData: { numero: numero, type: Usp.inbox.typeMedia(fileData.mime), mediaUrl: mediaRef,
                                    mimeType: fileData.mime, fileName: fileData.nom, caption: v.texte || null },
                        success: okWeb, failure: koCb });
                } else if (web) {
                    Usp.ajax({ url: '/wa-web/sessions/' + cible + '/send', method: 'POST',
                        jsonData: { numero: numero, texte: v.texte }, success: okWeb, failure: koCb });
                } else if (fileData.base64) {
                    Usp.ajax({ url: '/whatsapp/messages/media', method: 'POST',
                        jsonData: { accountId: cible, numero: numero, type: Usp.inbox.typeMedia(fileData.mime),
                                    mediaId: mediaRef, mimeType: fileData.mime, nomFichier: fileData.nom, legende: v.texte || null },
                        success: okCb, failure: koCb });
                } else {
                    Usp.ajax({ url: '/whatsapp/messages/text', method: 'POST',
                        jsonData: { accountId: cible, numero: numero, texte: v.texte }, success: okCb, failure: koCb });
                }
            };
            var lancer = function (mediaRef) {
                var i = 0;
                var next = function () { if (i >= numeros.length) { termine(); return; } envoyerUn(numeros[i++], mediaRef, next); };
                next();
            };
            var echecUpload = function () { b.enable(); Ext.Msg.alert('Erreur', 'Téléversement du fichier impossible.'); };

            // Téléverse le média une seule fois (mediaId pour API, url pour WEB) puis itère.
            if (fileData.base64 && web) {
                Usp.ajax({ url: '/media/upload', method: 'POST',
                    jsonData: { fichierBase64: fileData.base64, mimeType: fileData.mime, nomFichier: fileData.nom },
                    success: function (resp) { lancer((Ext.decode(resp.responseText) || {}).url); }, failure: echecUpload });
            } else if (fileData.base64) {
                Usp.ajax({ url: '/whatsapp/media', method: 'POST',
                    jsonData: { accountId: cible, fichierBase64: fileData.base64, mimeType: fileData.mime, nomFichier: fileData.nom },
                    success: function (resp) { lancer((Ext.decode(resp.responseText) || {}).mediaId); }, failure: echecUpload });
            } else {
                lancer(null);
            }
        } }]
    });
    win.show();
    maj(win);
};

Usp.inbox.note = function (field) {
    var conv = Usp.inbox.currentConv;
    if (!conv || !field.getValue()) { return; }
    Usp.ajax({
        url: '/conversations/' + conv.get('id') + '/notes', method: 'POST',
        jsonData: { note: field.getValue() },
        success: function () { field.setValue(''); Usp.inbox.reloadMessages(); }
    });
};

Usp.inbox.action = function (action) {
    var conv = Usp.inbox.currentConv;
    if (!conv) { return; }
    if (action === 'assign') {
        Ext.Msg.prompt('Affecter', 'ID de l\'agent :', function (btn, val) {
            if (btn === 'ok') {
                Usp.ajax({ url: '/conversations/' + conv.get('id') + '/assign', method: 'POST', jsonData: { agentId: val } });
            }
        });
        return;
    }
    Usp.ajax({ url: '/conversations/' + conv.get('id') + '/' + action, method: 'POST' });
};

Usp.inbox.reloadMessages = function () {
    if (Usp.inbox.msgStore && Usp.inbox.currentConv) {
        Usp.inbox.msgStore.getProxy().url = Usp.apiBase + '/conversations/' + Usp.inbox.currentConv.get('id') + '/messages';
        Usp.inbox.msgStore.load();
    }
};
