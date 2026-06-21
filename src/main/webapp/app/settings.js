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
         'accessToken', 'verifyToken', 'apiVersion', 'actif', 'modeTest']);

    return {
        xtype: 'grid', title: 'Comptes WhatsApp', store: store,
        columns: [
            { text: 'Libellé', dataIndex: 'libelle', flex: 1 },
            { text: 'Phone Number ID', dataIndex: 'phoneNumberId', width: 160 },
            { text: 'Numéro affiché', dataIndex: 'numeroAffiche', width: 150 },
            { text: 'API', dataIndex: 'apiVersion', width: 70 },
            { text: 'Actif', dataIndex: 'actif', width: 60, renderer: function (v) { return v ? 'Oui' : 'Non'; } },
            { text: 'Mode test', dataIndex: 'modeTest', width: 80,
              renderer: function (v) { return v ? '🧪 Oui' : 'Non'; } }
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
                { xtype: 'checkbox', name: 'actif', fieldLabel: 'Actif', checked: true },
                { xtype: 'checkbox', name: 'modeTest', fieldLabel: 'Mode test',
                  boxLabel: 'Simuler les envois (aucun appel à Meta) — pour tester sans token' }
            ]
        }],
        buttons: [{
            text: 'Enregistrer', formBind: true,
            handler: function (b) {
                var form = b.up('window').down('form').getForm();
                if (!form.isValid()) { return; }
                var data = form.getValues();
                data.actif = form.findField('actif').getValue();
                data.modeTest = form.findField('modeTest').getValue();
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
        ['id', 'nom', 'typeModele', 'langue', 'categorie', 'enteteTexte', 'enteteMediaType',
         'enteteMediaUrl', 'corps', 'piedDePage', 'boutonsJson', 'nomModeleWhatsapp',
         'statutApprobation', 'actif']);

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
                { xtype: 'textfield', name: 'enteteTexte', fieldLabel: 'En-tête (texte)' },
                { xtype: 'combobox', name: 'enteteMediaType', fieldLabel: 'En-tête (média)', value: 'AUCUN',
                  store: [['AUCUN', 'Aucun'], ['IMAGE', 'Image'], ['VIDEO', 'Vidéo'], ['DOCUMENT', 'Document']],
                  queryMode: 'local', editable: false },
                { xtype: 'textfield', name: 'enteteMediaUrl', fieldLabel: 'URL du média',
                  emptyText: 'https://… ou importez un fichier ci-dessous' },
                { xtype: 'fieldcontainer', fieldLabel: 'Importer un fichier', layout: 'hbox',
                  items: [
                    { xtype: 'filefield', name: 'mediaFichier', buttonOnly: true, hideLabel: true,
                      buttonText: 'Parcourir...',
                      listeners: { change: function (f) { Usp.settings.uploadMedia(f); } } },
                    { xtype: 'box', margin: '4 0 0 8',
                      html: '<span style="color:#888;font-size:11px">image / vidéo / document depuis votre ordinateur</span>' }
                  ] },
                { xtype: 'component', itemId: 'mediaPreview', margin: '0 0 6 0', html: '' },
                { xtype: 'textareafield', name: 'corps', fieldLabel: 'Corps', height: 100, allowBlank: false,
                  emptyText: 'Bonjour {{nom_contact}}, l\'article {{article}} est disponible à {{prix}} F.' },
                { xtype: 'textfield', name: 'piedDePage', fieldLabel: 'Pied de page' },
                { xtype: 'textareafield', name: 'boutonsJson', fieldLabel: 'Boutons (JSON)', height: 50,
                  emptyText: '[{"type":"URL","text":"Commander","url":"https://..."}]' },
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
    if (rec) {
        win.down('form').getForm().setValues(rec.getData());
        Usp.settings.previewMedia(win.down('form'), rec.get('enteteMediaUrl'), rec.get('enteteMediaType'));
    }
};

/* Téléverse le fichier choisi vers l'app, remplit l'URL et le type d'en-tête, affiche l'aperçu. */
Usp.settings.uploadMedia = function (f) {
    var file = f.fileInputEl.dom.files[0];
    if (!file) { return; }
    var form = f.up('form');
    var reader = new FileReader();
    reader.onload = function (e) {
        var b64 = e.target.result.split(',')[1];
        Usp.ajax({
            url: '/media/upload', method: 'POST',
            jsonData: { fichierBase64: b64, mimeType: file.type || 'application/octet-stream', nomFichier: file.name },
            success: function (resp) {
                var r = Ext.decode(resp.responseText);
                var mime = file.type || '';
                var t = mime.indexOf('image/') === 0 ? 'IMAGE'
                      : mime.indexOf('video/') === 0 ? 'VIDEO' : 'DOCUMENT';
                form.down('[name=enteteMediaUrl]').setValue(r.url);
                form.down('[name=enteteMediaType]').setValue(t);
                Usp.settings.previewMedia(form, r.url, t);
            },
            failure: function (resp) {
                var msg = 'Téléversement impossible.';
                try { var r = Ext.decode(resp.responseText); if (r && r.erreur) { msg = r.erreur; } } catch (ex) {}
                Ext.Msg.alert('Erreur', msg);
            }
        });
    };
    reader.readAsDataURL(file);
};

/* Aperçu du média d'en-tête (vignette pour une image, lien sinon). */
Usp.settings.previewMedia = function (form, url, type) {
    var c = form.down('#mediaPreview');
    if (!c) { return; }
    if (!url) { c.update(''); return; }
    if (type === 'IMAGE') {
        c.update('<img src="' + url + '" style="max-height:90px;border:1px solid #ddd;border-radius:4px"/>');
    } else {
        c.update('<a href="' + url + '" target="_blank">Média importé ✔ (ouvrir)</a>');
    }
};

/* ---------- Général : mode de fonctionnement ---------- */
Usp.settings.generalPanel = function () {
    var form = Ext.create('Ext.form.Panel', {
        title: 'Général', bodyPadding: 14, border: false, defaults: { anchor: '100%', labelWidth: 220 },
        items: [
            { xtype: 'displayfield',
              value: '<b>Mode de fonctionnement</b> : choisissez le canal d\'envoi par défaut.' },
            { xtype: 'combobox', name: 'mode', itemId: 'modeField', fieldLabel: 'Mode d\'envoi par défaut',
              width: 520, value: 'API', editable: false, queryMode: 'local',
              store: [['API', 'API officielle (WhatsApp Cloud API / Meta)'],
                      ['WEB', 'WhatsApp Web (non officiel, scan QR — risque de bannissement)']] },
            { xtype: 'displayfield',
              value: '<span style="color:#888">API : conforme, nécessite un compte Meta. ' +
                     'WEB : sans Meta (scan QR), à débit lent. Ce choix présélectionne le canal ' +
                     'dans le composeur « Nouveau message ».</span>' },
            { xtype: 'textfield', name: 'prefixe', itemId: 'prefixeField', fieldLabel: 'Préfixe pays', width: 360,
              value: '225', maskRe: /[0-9]/,
              emptyText: 'Ex. 225 (Côte d\'Ivoire), 226, 33…' },
            { xtype: 'displayfield',
              value: '<span style="color:#888">Pré-rempli automatiquement dans les champs « numéro » ' +
                     'et ajouté aux numéros saisis en format local.</span>' },
            { xtype: 'textfield', name: 'societe', itemId: 'societeField', fieldLabel: 'Société émettrice', width: 520,
              emptyText: 'Nom de votre société (variable [SOCIETE] dans les messages)' }
        ],
        bbar: ['->', { text: 'Enregistrer', handler: function (b) {
            var p = b.up('panel');
            var mode = p.down('#modeField').getValue();
            var prefixe = (p.down('#prefixeField').getValue() || '').replace(/[^0-9]/g, '');
            var societe = p.down('#societeField').getValue() || '';
            var put = function (cle, valeur) {
                return function (cb) {
                    Usp.ajax({ url: '/parametres/' + cle, method: 'PUT', jsonData: { valeur: valeur },
                        success: cb, failure: function () { Ext.Msg.alert('Erreur', 'Enregistrement impossible (' + cle + ').'); } });
                };
            };
            put('whatsapp.mode_envoi', mode)(function () {
                Usp.mode = mode;
                put('whatsapp.prefixe_pays', prefixe)(function () {
                    Usp.prefixe = prefixe;
                    put('app.societe', societe)(function () { Ext.Msg.alert('OK', 'Paramètres enregistrés.'); });
                });
            });
        } }]
    });
    form.on('afterrender', function () {
        var charger = function (cle, itemId, defaut) {
            Usp.ajax({ url: '/parametres/' + cle, method: 'GET', success: function (resp) {
                form.down('#' + itemId).setValue((Ext.decode(resp.responseText) || {}).valeur || defaut);
            } });
        };
        charger('whatsapp.mode_envoi', 'modeField', 'API');
        charger('whatsapp.prefixe_pays', 'prefixeField', '225');
        charger('app.societe', 'societeField', '');
    });
    return form;
};

Usp.settings.tabs = function () {
    return {
        xtype: 'tabpanel', title: 'Paramètres',
        items: [Usp.settings.generalPanel(), Usp.settings.accountsPanel(), Usp.settings.templatesPanel()]
    };
};
