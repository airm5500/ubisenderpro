/*
 * UbiSenderPro - Paramètres : Comptes WhatsApp Business + Modèles de messages.
 * Configuration de la connexion à la Meta Cloud API depuis l'interface.
 * Dépend de app.js (objet Usp).
 */
Ext.define('Usp.settings', { singleton: true });

Usp.settings.jsonStore = function (url, fields) {
    return Ext.create('Ext.data.Store', {
        fields: fields,
        proxy: { type: 'ajax', url: Usp.apiBase + url,
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
        autoLoad: true
    });
};

/* ---------- Comptes WhatsApp ---------- */
Usp.settings.accountsPanel = function () {
    var store = Usp.settings.jsonStore('/whatsapp/accounts',
        ['id', 'libelle', 'phoneNumberId', 'businessAccountId', 'numeroAffiche',
         'accessToken', 'verifyToken', 'apiVersion', 'actif']);

    return {
        xtype: 'grid', title: 'Comptes WhatsApp', store: store,
        columns: [
            { text: 'Libellé', dataIndex: 'libelle', flex: 1 },
            { text: 'Phone Number ID', dataIndex: 'phoneNumberId', width: 160 },
            { text: 'Numéro affiché', dataIndex: 'numeroAffiche', width: 150 },
            { text: 'API', dataIndex: 'apiVersion', width: 70 },
            { text: 'Actif', dataIndex: 'actif', width: 60, renderer: function (v) { return v ? 'Oui' : 'Non'; } }
        ],
        tbar: [
            { text: 'Nouveau compte', handler: function () { Usp.settings.accountForm(store, null); } },
            { text: 'Webhook', tooltip: 'Rappel de l\'URL de webhook', handler: function () {
                var base = window.location.origin + window.location.pathname.replace(/\/$/, '');
                Ext.Msg.alert('URL de webhook Meta',
                    'Configurez dans la console Meta :<br/><br/>' +
                    '<b>URL de rappel :</b><br/>' + base + '/api/v1/webhooks/whatsapp<br/><br/>' +
                    '<b>Jeton de vérification :</b> la valeur « verifyToken » du compte<br/>' +
                    '<b>Champs abonnés :</b> messages');
            } }
        ],
        listeners: { itemdblclick: function (g, rec) { Usp.settings.accountForm(store, rec); } }
    };
};

Usp.settings.accountForm = function (store, rec) {
    var win = Ext.create('Ext.window.Window', {
        title: rec ? 'Modifier le compte WhatsApp' : 'Nouveau compte WhatsApp',
        width: 540, modal: true, bodyPadding: 12,
        items: [{
            xtype: 'form', border: false, defaults: { anchor: '100%' },
            items: [
                { xtype: 'displayfield', value: 'Identifiants issus de Meta for Developers (produit WhatsApp).' },
                { xtype: 'textfield', name: 'libelle', fieldLabel: 'Libellé', allowBlank: false },
                { xtype: 'textfield', name: 'phoneNumberId', fieldLabel: 'Phone Number ID', allowBlank: false },
                { xtype: 'textfield', name: 'businessAccountId', fieldLabel: 'WABA ID' },
                { xtype: 'textfield', name: 'numeroAffiche', fieldLabel: 'Numéro affiché' },
                { xtype: 'textareafield', name: 'accessToken', fieldLabel: 'Access token', height: 60 },
                { xtype: 'textfield', name: 'verifyToken', fieldLabel: 'Verify token (webhook)' },
                { xtype: 'textfield', name: 'apiVersion', fieldLabel: 'Version API', value: 'v19.0' },
                { xtype: 'checkbox', name: 'actif', fieldLabel: 'Actif', checked: true }
            ]
        }],
        buttons: [{
            text: 'Enregistrer', formBind: true,
            handler: function (b) {
                var form = b.up('window').down('form').getForm();
                if (!form.isValid()) { return; }
                var data = form.getValues();
                data.actif = form.findField('actif').getValue();
                Usp.ajax({
                    url: rec ? '/whatsapp/accounts/' + rec.get('id') : '/whatsapp/accounts',
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

/* ---------- Modèles de messages ---------- */
Usp.settings.templatesPanel = function () {
    var store = Usp.settings.jsonStore('/templates',
        ['id', 'nom', 'typeModele', 'langue', 'categorie', 'enteteTexte', 'corps',
         'piedDePage', 'nomModeleWhatsapp', 'statutApprobation', 'actif']);

    return {
        xtype: 'grid', title: 'Modèles de messages', store: store,
        columns: [
            { text: 'Nom', dataIndex: 'nom', flex: 1 },
            { text: 'Type', dataIndex: 'typeModele', width: 120 },
            { text: 'Langue', dataIndex: 'langue', width: 70 },
            { text: 'Nom Meta', dataIndex: 'nomModeleWhatsapp', width: 160 },
            { text: 'Approbation', dataIndex: 'statutApprobation', width: 120 }
        ],
        tbar: [
            { text: 'Nouveau modèle', handler: function () { Usp.settings.templateForm(store, null); } }
        ],
        listeners: { itemdblclick: function (g, rec) { Usp.settings.templateForm(store, rec); } }
    };
};

Usp.settings.templateForm = function (store, rec) {
    var win = Ext.create('Ext.window.Window', {
        title: rec ? 'Modifier le modèle' : 'Nouveau modèle',
        width: 560, modal: true, bodyPadding: 12,
        items: [{
            xtype: 'form', border: false, defaults: { anchor: '100%' },
            items: [
                { xtype: 'textfield', name: 'nom', fieldLabel: 'Nom', allowBlank: false },
                { xtype: 'combobox', name: 'typeModele', fieldLabel: 'Type', value: 'marketing',
                  store: ['marketing', 'utilitaire', 'authentification', 'accueil',
                          'confirmation_commande', 'commande_prete', 'relance', 'promotion',
                          'produit_disponible', 'fidelite'], queryMode: 'local' },
                { xtype: 'textfield', name: 'langue', fieldLabel: 'Langue', value: 'fr' },
                { xtype: 'textfield', name: 'categorie', fieldLabel: 'Catégorie' },
                { xtype: 'textfield', name: 'enteteTexte', fieldLabel: 'En-tête' },
                { xtype: 'textareafield', name: 'corps', fieldLabel: 'Corps', height: 100, allowBlank: false,
                  emptyText: 'Bonjour {{nom_contact}}, l\'article {{article}} est disponible à {{prix}} F.' },
                { xtype: 'textfield', name: 'piedDePage', fieldLabel: 'Pied de page' },
                { xtype: 'textfield', name: 'nomModeleWhatsapp', fieldLabel: 'Nom du modèle Meta',
                  emptyText: 'Nom approuvé côté Meta (pour les campagnes)' },
                { xtype: 'combobox', name: 'statutApprobation', fieldLabel: 'Approbation', value: 'BROUILLON',
                  store: ['BROUILLON', 'EN_ATTENTE', 'APPROUVE', 'REJETE'], queryMode: 'local' },
                { xtype: 'displayfield',
                  value: 'Variables disponibles : {{nom_contact}}, {{nom_compte}}, {{article}}, {{prix}}, ' +
                         '{{prix_promotionnel}}, {{numero_commande}}, {{montant_commande}}, {{agent}}' }
            ]
        }],
        buttons: [{
            text: 'Enregistrer', formBind: true,
            handler: function (b) {
                var form = b.up('window').down('form').getForm();
                if (!form.isValid()) { return; }
                Usp.ajax({
                    url: rec ? '/templates/' + rec.get('id') : '/templates',
                    method: rec ? 'PUT' : 'POST', jsonData: form.getValues(),
                    success: function () { win.close(); store.load(); },
                    failure: function () { Ext.Msg.alert('Erreur', 'Enregistrement impossible.'); }
                });
            }
        }]
    });
    win.show();
    if (rec) { win.down('form').getForm().setValues(rec.getData()); }
};

Usp.settings.tabs = function () {
    return {
        xtype: 'tabpanel', title: 'Paramètres',
        items: [Usp.settings.accountsPanel(), Usp.settings.templatesPanel()]
    };
};
