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
        fields: ['id', 'numeroWhatsapp', 'nomAffiche', 'statut', 'agentId',
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
                        var badge = rec.get('nonLu') > 0
                            ? '<span style="float:right;background:#25d366;color:#fff;border-radius:10px;padding:0 6px;font-size:11px">' + rec.get('nonLu') + '</span>' : '';
                        return '<div><b>' + titre + '</b>' + badge + '</div>' +
                            '<div style="color:#888;font-size:11px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">' + apercu + '</div>';
                    }
                }],
                tbar: [
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
    Usp.ajax({
        url: '/whatsapp/messages/text', method: 'POST',
        jsonData: { accountId: conv.get('whatsappAccountId'), numero: conv.get('numeroWhatsapp'), texte: texte },
        success: function () { field.setValue(''); Usp.inbox.reloadMessages(); },
        failure: function () { Ext.Msg.alert('Erreur', 'Envoi impossible (vérifiez le compte WhatsApp et la fenêtre de 24h).'); }
    });
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
