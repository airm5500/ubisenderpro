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
        fields: ['id', 'numeroWhatsapp', 'nomAffiche', 'statut', 'botActif', 'agentId', 'canal', 'waWebSessionId',
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
            { text: '👤 Affecter', tooltip: 'Affecter cette discussion à un agent', handler: function () { Usp.inbox.action('assign'); } },
            { text: '✅ Clôturer', tooltip: 'Marquer la discussion comme traitée (fermée)', handler: function () { Usp.inbox.action('close'); } },
            { text: '↩️ Rouvrir', tooltip: 'Rouvrir une discussion clôturée', handler: function () { Usp.inbox.action('reopen'); } },
            '-',
            { text: '🙋 Reprendre', tooltip: 'Reprendre la main (le bot cesse de répondre sur cette discussion)', handler: function () { Usp.inbox.bot(false); } },
            { text: '🤖 Rendre au bot', tooltip: 'Réactiver les réponses automatiques du bot sur cette discussion', handler: function () { Usp.inbox.bot(true); } }
        ],
        bbar: [
            { xtype: 'textfield', itemId: 'msgInput', flex: 1, emptyText: 'Écrire un message...',
              listeners: { specialkey: function (f, e) { if (e.getKey() === e.ENTER) { Usp.inbox.envoyer(f); } } } },
            { xtype: 'button', text: '📨 Envoyer', tooltip: 'Envoyer le message saisi', handler: function (b) { Usp.inbox.envoyer(b.up('toolbar').down('#msgInput')); } },
            { xtype: 'button', text: '🔗 Lien', tooltip: 'Envoyer un média par URL publique', handler: function () { Usp.inbox.media(); } },
            { xtype: 'button', text: '📎 Joindre', tooltip: 'Téléverser et envoyer un fichier', handler: function () { Usp.inbox.joindre(); } },
            { xtype: 'button', text: '🗒️ Note', tooltip: 'Ajouter une note interne (non envoyée au client)', handler: function (b) { Usp.inbox.note(b.up('toolbar').down('#msgInput')); } }
        ]
    });

    msgStore.on('load', function (store) {
        // Ne rafraîchit l'affichage que si le contenu a changé (évite le clignotement au polling).
        var convId = Usp.inbox.currentConv ? Usp.inbox.currentConv.get('id') : 0;
        var sig = convId + ':' + store.getCount();
        if (sig === Usp.inbox._msgSig) { return; }
        Usp.inbox._msgSig = sig;
        var html = '';
        store.each(function (r) { html += Usp.inbox.renderMessage(r); });
        discussion.update(html || '<div style="padding:20px;color:#999">Aucun message</div>');
        var el = discussion.body; if (el) { el.scroll('bottom', 100000); }
    });

    var convReselect = function () {
        if (!Usp.inbox.currentConv) { return; }
        var g = Ext.ComponentQuery.query('#convGrid')[0];
        if (!g) { return; }
        var idx = convStore.findExact('id', Usp.inbox.currentConv.get('id'));
        if (idx >= 0) { g.getSelectionModel().select(idx, false, true); }
    };
    convStore.on('load', convReselect);

    var contactPanel = Ext.create('Ext.panel.Panel', {
        region: 'east', title: 'Fiche contact', width: 280, collapsible: true, bodyPadding: 10,
        html: '<div style="color:#999">Aucune conversation sélectionnée</div>'
    });

    var loadConversation = function (rec) {
        Usp.inbox.currentConv = rec;
        Usp.inbox._msgSig = null; // force le rafraîchissement à l'ouverture
        msgStore.getProxy().url = Usp.apiBase + '/conversations/' + rec.get('id') + '/messages';
        msgStore.load();
        Usp.ajax({ url: '/conversations/' + rec.get('id') + '/read', method: 'POST' });
        if (rec.get('nonLu')) { rec.set('nonLu', 0); } // retire le badge tout de suite
        var botEtat = rec.get('botActif')
            ? '<span style="color:#2e7d32">🤖 actif</span>'
            : '<span style="color:#e65100">🙋 humain (bot en pause)</span>';
        contactPanel.update(
            '<b>' + Ext.String.htmlEncode(rec.get('nomAffiche') || rec.get('numeroWhatsapp')) + '</b><hr/>' +
            '<div><b>Numéro WhatsApp :</b><br/>' + Ext.String.htmlEncode(rec.get('numeroWhatsapp') || '') + '</div><br/>' +
            '<div><b>Statut :</b> ' + Ext.String.htmlEncode(rec.get('statut') || '') + '</div>' +
            '<div><b>Bot :</b> ' + botEtat + '</div>');
    };

    return {
        xtype: 'panel', title: 'Discussions', layout: 'border',
        listeners: {
            afterrender: function () { Usp.inbox.demarrerAutoRefresh(convStore); },
            beforedestroy: function () { Usp.inbox.arreterAutoRefresh(); }
        },
        items: [
            {
                region: 'west', width: 300, xtype: 'grid', itemId: 'convGrid', title: 'Conversations', hideHeaders: true,
                store: convStore,
                columns: [{
                    flex: 1, dataIndex: 'nomAffiche',
                    renderer: function (v, m, rec) {
                        var titre = Ext.String.htmlEncode(v || rec.get('numeroWhatsapp') || '');
                        var apercu = Ext.String.htmlEncode(rec.get('dernierMessage') || '');
                        var canal = rec.get('canal') === 'WEB'
                            ? '<span style="background:#e8f0fe;color:#1967d2;border-radius:3px;padding:0 4px;font-size:10px;margin-right:4px">Web</span>' : '';
                        var del = '<span class="conv-del" title="Supprimer cette discussion" ' +
                            'style="float:right;cursor:pointer;color:#c62828;margin-left:6px">🗑️</span>';
                        var badge = rec.get('nonLu') > 0
                            ? '<span style="float:right;background:#25d366;color:#fff;border-radius:10px;padding:0 6px;font-size:11px;margin-left:6px">' + rec.get('nonLu') + '</span>' : '';
                        var repr = rec.get('statut') === 'A_REPRENDRE'
                            ? '<span title="Le bot a passé la main : à reprendre par un humain" style="background:#ffe0b2;color:#e65100;border-radius:3px;padding:0 4px;font-size:10px;margin-right:4px">🙋 À reprendre</span>' : '';
                        return '<div>' + repr + canal + '<b>' + titre + '</b>' + del + badge + '</div>' +
                            '<div style="color:#888;font-size:11px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">' + apercu + '</div>';
                    }
                }],
                tbar: [
                    Usp.permBtn('inbox', 'CREER', { xtype: 'button', text: '➕ Nouveau', tooltip: 'Créer un nouveau chat (message texte/image hors modèle)', handler: function () { Usp.inbox.nouveauMessage(); } }),
                    { xtype: 'button', text: '📋 Toutes', tooltip: 'Afficher toutes les discussions', handler: function () { convStore.getProxy().extraParams = {}; convStore.load(); } },
                    { xtype: 'button', text: '🔔 Non lues', tooltip: 'N\'afficher que les discussions ayant des messages non lus', handler: function () { convStore.getProxy().extraParams = { nonLu: true }; convStore.load(); } },
                    '->',
                    { xtype: 'button', text: 'Rafraîchir', handler: function () { convStore.load(); } }
                ],
                listeners: {
                    cellclick: function (g, td, ci, rec, tr, ri, e) {
                        if (e.getTarget('.conv-del')) { Usp.inbox.supprimerConversation(rec, convStore); return false; }
                        loadConversation(rec);
                    }
                }
            },
            discussion,
            contactPanel
        ]
    };
};

/* Supprime une discussion (et tout son contenu) après confirmation. */
Usp.inbox.supprimerConversation = function (rec, convStore) {
    var nom = rec.get('nomAffiche') || rec.get('numeroWhatsapp') || '';
    Ext.Msg.show({
        title: 'Supprimer la discussion',
        msg: 'Supprimer définitivement la discussion avec <b>' + Ext.String.htmlEncode(nom) +
             '</b> et tous ses messages ? Cette action est irréversible.',
        width: 460, buttons: Ext.Msg.YESNO, icon: Ext.Msg.WARNING,
        fn: function (btn) {
            if (btn !== 'yes') { return; }
            Usp.ajax({
                url: '/conversations/' + rec.get('id'), method: 'DELETE',
                success: function () {
                    if (Usp.inbox.currentConv && Usp.inbox.currentConv.get('id') === rec.get('id')) {
                        Usp.inbox.currentConv = null;
                    }
                    if (convStore) { convStore.load(); }
                },
                failure: function () { Ext.Msg.alert('Erreur', 'Suppression impossible.'); }
            });
        }
    });
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

/* Rafraîchissement automatique de l'inbox (messages toutes les 6 s, liste toutes les 12 s). */
Usp.inbox.demarrerAutoRefresh = function (convStore) {
    Usp.inbox.arreterAutoRefresh();
    var tick = 0;
    Usp.inbox._task = Ext.TaskManager.start({
        interval: 6000,
        run: function () {
            if (Usp.inbox.currentConv && Usp.inbox.msgStore) { Usp.inbox.msgStore.load(); }
            tick++;
            if (tick % 2 === 0 && convStore) { convStore.load(); }
        }
    });
};

Usp.inbox.arreterAutoRefresh = function () {
    if (Usp.inbox._task) {
        try { Ext.TaskManager.stop(Usp.inbox._task); } catch (e) { /* ignore */ }
        Usp.inbox._task = null;
    }
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

/* Met à jour l'affichage de la liste des pièces jointes du composeur. */
Usp.inbox.majNjList = function (fieldcontainer, fichiers) {
    var info = fieldcontainer.down('#njInfo');
    if (!info) { return; }
    if (!fichiers || !fichiers.length) {
        info.update('<span style="color:#888;font-size:11px">optionnel — plusieurs fichiers possibles</span>');
        return;
    }
    var noms = fichiers.map(function (f) { return Ext.String.htmlEncode(f.nom || 'fichier'); });
    info.update('<span style="color:#2e7d32;font-size:11px">✔ ' + fichiers.length + ' fichier(s) : ' +
        noms.join(', ') + '</span>');
};

/* Composer un message (texte et/ou image) hors modèle, via le canal choisi (API officielle / WhatsApp Web). */
Usp.inbox.nouveauMessage = function () {
    var fichiers = []; // pièces jointes multiples : [{base64, nom, mime}]
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
            { xtype: 'fieldcontainer', fieldLabel: 'Pièces jointes', layout: 'hbox', items: [
                { xtype: 'filefield', name: 'fichier', buttonOnly: true, hideLabel: true, buttonText: 'Ajouter un fichier…',
                  listeners: { change: function (f) {
                      var file = f.fileInputEl.dom.files[0];
                      if (!file) { return; }
                      var fc = f.up('fieldcontainer');
                      var reader = new FileReader();
                      reader.onload = function (e) {
                          fichiers.push({ base64: e.target.result.split(',')[1], nom: file.name,
                              mime: file.type || 'application/octet-stream' });
                          Usp.inbox.majNjList(fc, fichiers);
                      };
                      reader.readAsDataURL(file);
                  } } },
                { xtype: 'button', text: 'Vider', margin: '0 0 0 6', handler: function (b) {
                    fichiers.length = 0; Usp.inbox.majNjList(b.up('fieldcontainer'), fichiers); } },
                { xtype: 'component', itemId: 'njInfo', margin: '4 0 0 8',
                  html: '<span style="color:#888;font-size:11px">optionnel — plusieurs fichiers possibles</span>' }
            ] }
        ] }],
        buttons: [{ text: 'Envoyer', formBind: true, handler: function (b) {
            var form = b.up('window').down('form').getForm();
            if (!form.isValid()) { return; }
            var v = form.getValues();
            var webSel = v.canal === 'WEB';
            var cibleSel = webSel ? v.sessionId : v.accountId;
            if (!cibleSel) { Ext.Msg.alert('Info', 'Choisissez un compte ' + (webSel ? 'WhatsApp Web' : 'API') + '.'); return; }
            var numeros = String(v.numero || '').split(/[;,\n]/)
                .map(function (n) { return Usp.normNumero(n); })
                .filter(function (n) { return n.length >= 8; });
            if (!numeros.length) { Ext.Msg.alert('Info', 'Aucun numéro valide (format international, ex. 2250102030405).'); return; }
            if (!fichiers.length && !v.texte) { Ext.Msg.alert('Info', 'Saisissez un texte ou joignez un fichier.'); return; }
            var apiId = apiStore.getCount() ? apiStore.getAt(0).get('id') : null;

            // Exécute l'envoi selon un routage [{numero, canal, cible}] (1er contact -> API).
            var demarrer = function (routage) {
                b.disable();
                var res = { ok: 0, ko: 0 };
                // Préparation des pièces jointes : refs par canal (URL pour Web, mediaId pour API).
                var prep = fichiers.map(function (f) {
                    return { base64: f.base64, mime: f.mime, nom: f.nom,
                        type: Usp.inbox.typeMedia(f.mime), webUrl: null, apiMediaId: null };
                });
                var termine = function () {
                    b.enable();
                    if (Usp.inbox.convStore) { Usp.inbox.convStore.load(); }
                    Ext.Msg.alert('Envoi terminé', routage.length + ' destinataire(s) : ' +
                        res.ok + ' envoyé(s), ' + res.ko + ' échec(s).');
                    if (res.ok > 0) { win.close(); }
                };
                // Envoi à un destinataire : toutes les pièces (légende sur la 1re) puis fin ; sinon texte seul.
                var envoyerUn = function (item, next) {
                    if (!prep.length) {
                        if (item.canal === 'WEB') {
                            Usp.ajax({ url: '/wa-web/sessions/' + item.cible + '/send', method: 'POST',
                                jsonData: { numero: item.numero, texte: v.texte },
                                success: function (resp) { var r = Ext.decode(resp.responseText) || {};
                                    if (r.success) { res.ok++; } else { res.ko++; } next(); },
                                failure: function () { res.ko++; next(); } });
                        } else {
                            Usp.ajax({ url: '/whatsapp/messages/text', method: 'POST',
                                jsonData: { accountId: item.cible, numero: item.numero, texte: v.texte },
                                success: function () { res.ok++; next(); }, failure: function () { res.ko++; next(); } });
                        }
                        return;
                    }
                    var fi = 0;
                    var echoue = function () { res.ko++; next(); };
                    var suivant = function () {
                        if (fi >= prep.length) { res.ok++; next(); return; }
                        var p = prep[fi++];
                        var legende = (fi === 1) ? (v.texte || null) : null; // légende sur la 1re pièce
                        if (item.canal === 'WEB') {
                            Usp.ajax({ url: '/wa-web/sessions/' + item.cible + '/send-media', method: 'POST',
                                jsonData: { numero: item.numero, type: p.type, mediaUrl: p.webUrl,
                                            mimeType: p.mime, fileName: p.nom, caption: legende },
                                success: function (resp) { var r = Ext.decode(resp.responseText) || {};
                                    if (r.success) { suivant(); } else { echoue(); } },
                                failure: echoue });
                        } else {
                            Usp.ajax({ url: '/whatsapp/messages/media', method: 'POST',
                                jsonData: { accountId: item.cible, numero: item.numero, type: p.type,
                                            mediaId: p.apiMediaId, mimeType: p.mime, nomFichier: p.nom, legende: legende },
                                success: function () { suivant(); }, failure: echoue });
                        }
                    };
                    suivant();
                };
                var lancer = function () {
                    var i = 0;
                    var next = function () { if (i >= routage.length) { termine(); return; } envoyerUn(routage[i++], next); };
                    next();
                };
                var echecUpload = function () { b.enable(); Ext.Msg.alert('Erreur', 'Téléversement d\'un fichier impossible.'); };
                var besoinWeb = prep.length && routage.some(function (r) { return r.canal === 'WEB'; });
                var besoinApi = prep.length && routage.some(function (r) { return r.canal === 'API'; });
                // Téléverse chaque pièce vers les canaux réellement utilisés, puis itère sur les destinataires.
                var ui = 0;
                var uploadSuivant = function () {
                    if (ui >= prep.length) { lancer(); return; }
                    var p = prep[ui];
                    var apresWeb = function () {
                        if (!besoinApi) { ui++; uploadSuivant(); return; }
                        Usp.ajax({ url: '/whatsapp/media', method: 'POST',
                            jsonData: { accountId: apiId, fichierBase64: p.base64, mimeType: p.mime, nomFichier: p.nom },
                            success: function (resp) { p.apiMediaId = (Ext.decode(resp.responseText) || {}).mediaId; ui++; uploadSuivant(); },
                            failure: echecUpload });
                    };
                    if (besoinWeb) {
                        Usp.ajax({ url: '/media/upload', method: 'POST',
                            jsonData: { fichierBase64: p.base64, mimeType: p.mime, nomFichier: p.nom },
                            success: function (resp) { p.webUrl = (Ext.decode(resp.responseText) || {}).url; apresWeb(); },
                            failure: echecUpload });
                    } else { apresWeb(); }
                };
                if (prep.length) { uploadSuivant(); } else { lancer(); }
            };

            var routageDefaut = numeros.map(function (n) { return { numero: n, canal: v.canal, cible: cibleSel }; });

            // Canal WEB + compte API disponible : on propose de router les 1ers contacts via l'API (notification fiable).
            if (webSel && apiId) {
                Usp.ajax({ url: '/conversations/premier-contact', method: 'POST', jsonData: { numeros: numeros },
                    success: function (resp) {
                        var nouveaux = (Ext.decode(resp.responseText) || {}).nouveaux || [];
                        if (!nouveaux.length) { demarrer(routageDefaut); return; }
                        Ext.Msg.show({
                            title: 'Premier contact détecté',
                            msg: nouveaux.length + ' premier(s) contact(s) sans historique. WhatsApp Web ne garantit pas ' +
                                 'la notification du 1<sup>er</sup> message. Router ces premiers contacts via l\'API officielle ' +
                                 '(notification fiable) ?<br/><br/><span style="color:#888">Rappel : pour un tout premier message, ' +
                                 'l\'API Meta peut exiger un modèle approuvé.</span>',
                            width: 520, buttons: Ext.Msg.YESNO, icon: Ext.Msg.QUESTION,
                            fn: function (btn) {
                                if (btn === 'yes') {
                                    demarrer(numeros.map(function (n) {
                                        return nouveaux.indexOf(n) >= 0
                                            ? { numero: n, canal: 'API', cible: apiId }
                                            : { numero: n, canal: 'WEB', cible: cibleSel };
                                    }));
                                } else { demarrer(routageDefaut); }
                            }
                        });
                    },
                    failure: function () { demarrer(routageDefaut); }
                });
            } else { demarrer(routageDefaut); }
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
        var store = Ext.create('Ext.data.Store', {
            fields: ['id', 'nomComplet'], autoLoad: true,
            proxy: { type: 'ajax', url: Usp.apiBase + '/users/affectables',
                headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } }
        });
        var win = Ext.create('Ext.window.Window', {
            title: 'Affecter la discussion', width: 360, modal: true, bodyPadding: 14, layout: 'fit',
            items: [{ xtype: 'combobox', itemId: 'agentCombo', fieldLabel: 'Agent', labelWidth: 60,
                emptyText: 'Choisir un agent…', store: store, queryMode: 'local', editable: false,
                valueField: 'id', displayField: 'nomComplet' }],
            buttons: [
                { text: 'Affecter', handler: function (b) {
                    var v = b.up('window').down('#agentCombo').getValue();
                    if (!v) { Ext.Msg.alert('Affecter', 'Veuillez choisir un agent.'); return; }
                    Usp.ajax({ url: '/conversations/' + conv.get('id') + '/assign', method: 'POST',
                        jsonData: { agentId: v },
                        success: function () { win.close(); Usp.toast('Discussion affectée avec succès.'); },
                        failure: function () { Ext.Msg.alert('Erreur', 'Affectation impossible.'); } });
                } },
                { text: 'Annuler', handler: function (b) { b.up('window').close(); } }
            ]
        });
        win.show();
        return;
    }
    Usp.ajax({ url: '/conversations/' + conv.get('id') + '/' + action, method: 'POST' });
};

/* Active/désactive le bot sur la conversation courante (#5 bot). */
Usp.inbox.bot = function (actif) {
    var conv = Usp.inbox.currentConv;
    if (!conv) { return; }
    Usp.ajax({ url: '/conversations/' + conv.get('id') + (actif ? '/bot-on' : '/bot-off'), method: 'POST',
        success: function () {
            conv.set('botActif', actif);
            if (actif && conv.get('statut') === 'A_REPRENDRE') { conv.set('statut', 'OUVERTE'); }
            Usp.toast(actif ? 'Le bot répondra de nouveau sur cette discussion.'
                            : 'Vous avez repris la main ; le bot ne répondra plus ici.');
        },
        failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
};

Usp.inbox.reloadMessages = function () {
    if (Usp.inbox.msgStore && Usp.inbox.currentConv) {
        Usp.inbox.msgStore.getProxy().url = Usp.apiBase + '/conversations/' + Usp.inbox.currentConv.get('id') + '/messages';
        Usp.inbox.msgStore.load();
    }
};
