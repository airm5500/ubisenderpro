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
        xtype: 'grid', title: '📱 Comptes WhatsApp', store: store,
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
            { text: '➕ Nouveau compte', tooltip: 'Configurer un nouveau compte WhatsApp', handler: function () { Usp.settings.accountForm(store, null); } },
            Usp.permBtn('settings', 'MODIFIER', { text: '✏️ Modifier', handler: function (b) {
                var rec = b.up('grid').getSelectionModel().getSelection()[0];
                if (!rec) { Ext.Msg.alert('Info', 'Sélectionnez un compte.'); return; }
                Usp.settings.accountForm(store, rec);
            } }),
            Usp.permBtn('settings', 'SUPPRIMER', { text: '🗑️ Supprimer', handler: function (b) {
                var rec = b.up('grid').getSelectionModel().getSelection()[0];
                if (!rec) { Ext.Msg.alert('Info', 'Sélectionnez un compte.'); return; }
                Ext.Msg.confirm('Supprimer', 'Supprimer le compte WhatsApp « ' + Ext.String.htmlEncode(rec.get('libelle')) + ' » ?',
                    function (btn) {
                        if (btn !== 'yes') { return; }
                        Usp.ajax({ url: '/whatsapp/accounts/' + rec.get('id'), method: 'DELETE',
                            success: function () { store.load(); Usp.toast('Compte supprimé.'); },
                            failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
                    });
            } }),
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
        width: 680, modal: true, bodyPadding: 12,
        items: [{
            xtype: 'form', border: false, defaults: { anchor: '100%', labelWidth: 170 },
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
                    success: function () {
                        win.close(); store.load();
                        Usp.toastEnregistre('Compte WhatsApp « ' + (data.libelle || '') + ' »', !!rec);
                    },
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
         'enteteMediaUrl', 'corps', 'piedDePage', 'boutonsJson', 'nomModeleWhatsapp', 'paramsCorps',
         'segmentationId', 'statutApprobation', 'actif']);

    return {
        xtype: 'grid', title: '📝 Modèles de messages', store: store,
        columns: [
            { text: 'Nom', dataIndex: 'nom', flex: 1 },
            { text: 'Type', dataIndex: 'typeModele', width: 120 },
            { text: 'Langue', dataIndex: 'langue', width: 70 },
            { text: 'Nom Meta', dataIndex: 'nomModeleWhatsapp', width: 160 },
            { text: 'Approbation', dataIndex: 'statutApprobation', width: 120 },
            { text: 'Export', width: 90, align: 'center', sortable: false, menuDisabled: true, dataIndex: 'id',
              renderer: function () {
                  return '<span class="tpl-docx" title="Exporter ce modèle au format Word (.docx)" ' +
                      'style="cursor:pointer;color:#1976d2">📤 .docx</span>';
              } },
            { text: 'Actions', width: 100, align: 'center', sortable: false, menuDisabled: true, dataIndex: 'id',
              renderer: function () {
                  return '<span class="tpl-edit" title="Modifier" style="cursor:pointer;margin:0 4px">✏️</span>' +
                      '<span class="tpl-del" title="Supprimer" style="cursor:pointer;margin:0 4px;color:#c62828">🗑️</span>';
              } }
        ],
        tbar: [
            { text: '➕ Nouveau modèle', tooltip: 'Créer un nouveau modèle de message', handler: function () { Usp.settings.templateForm(store, null); } },
            { xtype: 'filefield', buttonOnly: true, hideLabel: true, buttonText: '📥 Importer un .docx',
              listeners: { change: function (f) { Usp.settings.importerModeleDocx(f, store); } } }
        ],
        listeners: {
            itemdblclick: function (g, rec) { Usp.settings.templateForm(store, rec); },
            cellclick: function (g, td, ci, rec, tr, ri, e) {
                if (e.getTarget('.tpl-docx')) { Usp.settings.exporterModeleDocx(rec); }
                else if (e.getTarget('.tpl-edit')) { Usp.settings.templateForm(store, rec); }
                else if (e.getTarget('.tpl-del')) {
                    Ext.Msg.confirm('Supprimer', 'Supprimer le modèle « ' + Ext.String.htmlEncode(rec.get('nom')) + ' » ?',
                        function (btn) {
                            if (btn !== 'yes') { return; }
                            Usp.ajax({ url: '/templates/' + rec.get('id'), method: 'DELETE',
                                success: function () { store.load(); Usp.toast('Modèle supprimé avec succès.'); },
                                failure: function () { Ext.Msg.alert('Erreur', 'Suppression impossible.'); } });
                        });
                }
            }
        }
    };
};

/* Exporte un modèle au format .docx (téléchargement). */
Usp.settings.exporterModeleDocx = function (rec) {
    Usp.ajax({ url: '/templates/' + rec.get('id') + '/docx', method: 'GET',
        success: function (resp) {
            var r = Ext.decode(resp.responseText) || {};
            Usp.telechargerBase64(r.nomFichier, r.base64, r.mime);
            Usp.toast('Modèle exporté : ' + r.nomFichier);
        },
        failure: function () { Ext.Msg.alert('Erreur', 'Export impossible.'); } });
};

Usp.settings.templateForm = function (store, rec) {
    var win = Ext.create('Ext.window.Window', {
        title: rec ? 'Modifier le modèle' : 'Nouveau modèle',
        width: 1180, height: 840, maxHeight: Ext.getBody().getViewSize().height - 20,
        maximizable: true, modal: true, layout: 'fit',
        items: [{
            xtype: 'form', border: false, bodyPadding: 12, autoScroll: true, defaults: { anchor: '100%' },
            items: [
                { xtype: 'textfield', name: 'nom', fieldLabel: 'Nom', allowBlank: false },
                { xtype: 'combobox', name: 'typeModele', fieldLabel: 'Type', value: 'marketing',
                  store: ['marketing', 'utilitaire', 'authentification', 'accueil',
                          'confirmation_commande', 'commande_prete', 'relance', 'promotion',
                          'produit_disponible', 'fidelite'], queryMode: 'local' },
                { xtype: 'textfield', name: 'langue', fieldLabel: 'Langue', value: 'fr' },
                { xtype: 'textfield', name: 'categorie', fieldLabel: 'Catégorie' },
                { xtype: 'combobox', name: 'segmentationId', itemId: 'segmentationCombo',
                  fieldLabel: 'Segmentation dédiée', emptyText: 'Tous les clients (aucune segmentation)',
                  store: Usp.settings.jsonStore('/segmentations', ['id', 'libelle']),
                  valueField: 'id', displayField: 'libelle', queryMode: 'local',
                  editable: false, allowBlank: true, triggers: {
                      clear: { cls: 'x-form-clear-trigger',
                          handler: function (c) { c.clearValue(); } }
                  } },
                { xtype: 'displayfield',
                  value: '<span style="color:#888">Segmentation pour laquelle ce modèle est conçu ' +
                         '(facultatif). Utilisez le bouton ✖ pour retirer la segmentation.</span>' },
                { xtype: 'displayfield',
                  value: '<b>En-tête</b> : ligne mise en avant en haut du message. ' +
                         'Soit un <i>texte</i> court, soit un <i>média</i> (image / vidéo / document) — pas les deux.' },
                { xtype: 'textfield', name: 'enteteTexte', fieldLabel: 'En-tête (texte)',
                  emptyText: 'Titre court affiché en gras en haut',
                  listeners: { focus: function (f) { Usp.waweb._lastMsgField = f; } } },
                { xtype: 'combobox', name: 'enteteMediaType', fieldLabel: 'En-tête (média)', value: 'AUCUN',
                  store: [['AUCUN', 'Aucun'], ['IMAGE', 'Image'], ['VIDEO', 'Vidéo'], ['DOCUMENT', 'Document']],
                  queryMode: 'local', editable: false },
                { xtype: 'textfield', name: 'enteteMediaUrl', fieldLabel: 'URL du média',
                  emptyText: 'https://… ou importez un fichier ci-dessous' },
                { xtype: 'displayfield',
                  value: '<span style="color:#888">« URL du média » : adresse du fichier d\'en-tête. ' +
                         'Renseignez-la manuellement ou importez un fichier (l\'URL se remplit toute seule).</span>' },
                { xtype: 'fieldcontainer', fieldLabel: 'Importer un fichier', layout: 'hbox',
                  items: [
                    { xtype: 'filefield', name: 'mediaFichier', buttonOnly: true, hideLabel: true,
                      buttonText: 'Parcourir...',
                      listeners: { change: function (f) { Usp.settings.uploadMedia(f); } } },
                    { xtype: 'box', margin: '4 0 0 8',
                      html: '<span style="color:#888;font-size:11px">image / vidéo / document depuis votre ordinateur</span>' }
                  ] },
                { xtype: 'component', itemId: 'mediaPreview', margin: '0 0 6 0', html: '' },
                Usp.waweb.barreVariables('corps'),
                { xtype: 'textareafield', name: 'corps', fieldLabel: 'Corps', height: 100, allowBlank: false,
                  emptyText: 'Bonjour [NOM], bienvenue chez [SOCIETE].',
                  listeners: { focus: function (f) { Usp.waweb._lastMsgField = f; } } },
                { xtype: 'textfield', name: 'piedDePage', fieldLabel: 'Pied de page',
                  listeners: { focus: function (f) { Usp.waweb._lastMsgField = f; } } },
                { xtype: 'textareafield', name: 'boutonsJson', fieldLabel: 'Boutons (JSON)', height: 50,
                  emptyText: '[{"type":"URL","text":"Commander","url":"https://..."}]',
                  listeners: { focus: function (f) { Usp.waweb._lastMsgField = f; } } },
                { xtype: 'textfield', name: 'nomModeleWhatsapp', fieldLabel: 'Nom du modèle Meta',
                  emptyText: 'Nom approuvé côté Meta (requis pour le canal API)' },
                { xtype: 'button', text: '📥 Importer depuis Meta', margin: '0 0 8 130',
                  handler: function (b) { Usp.settings.importerTemplatesMeta(b.up('form')); } },
                { xtype: 'textfield', name: 'paramsCorps', fieldLabel: 'Paramètres du corps (Meta)',
                  emptyText: 'Variables ordonnées pour {{1}},{{2}}… ex. nom_contact,nom_compte (vide = aucun)' },
                { xtype: 'combobox', name: 'statutApprobation', fieldLabel: 'Approbation', value: 'BROUILLON',
                  store: ['BROUILLON', 'EN_ATTENTE', 'APPROUVE', 'REJETE'], queryMode: 'local' },
                { xtype: 'displayfield',
                  value: 'Par contact : nom_contact, nom_compte, civilite_complete, segmentation, article, prix, ' +
                         'prix_promotionnel, numero_commande, montant_commande, agent.<br>' +
                         'Contexte campagne (modèles auto promo/dispo/info) : mois_promotion, nom_promotion, ' +
                         'date_debut, date_fin, avantage_ug, nombre_produits, jours_restants…' }
            ]
        }],
        buttons: [{
            text: 'Enregistrer', formBind: true,
            handler: function (b) {
                var form = b.up('window').down('form').getForm();
                if (!form.isValid()) { return; }
                var vals = form.getValues();
                Usp.ajax({
                    url: rec ? '/templates/' + rec.get('id') : '/templates',
                    method: rec ? 'PUT' : 'POST', jsonData: vals,
                    success: function () {
                        win.close(); store.load();
                        Usp.toastEnregistre('Modèle « ' + (vals.nom || '') + ' »', !!rec);
                    },
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

/* Sélecteur des modèles (templates) approuvés côté Meta : remplit Nom Meta + langue. */
Usp.settings.importerTemplatesMeta = function (form) {
    var accStore = Ext.create('Ext.data.Store', { fields: ['id', 'libelle'], autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/whatsapp/accounts',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } } });
    var tplStore = Ext.create('Ext.data.Store', { fields: ['name', 'language', 'status', 'category', 'bodyText', 'headerFormat', 'nbParams'] });
    var charger = function (win) {
        var id = win.down('[name=acc]').getValue();
        if (!id) { return; }
        Usp.ajax({ url: '/whatsapp/accounts/' + id + '/templates', method: 'GET',
            success: function (resp) { var d = []; try { d = Ext.decode(resp.responseText) || []; } catch (e) {} tplStore.loadData(d); },
            failure: function (resp) {
                var m = 'Récupération impossible.';
                try { m = Ext.decode(resp.responseText).erreur || m; } catch (e) {}
                tplStore.removeAll(); Ext.Msg.alert('Erreur', m);
            } });
    };
    var win = Ext.create('Ext.window.Window', {
        title: 'Modèles approuvés par Meta', width: 660, height: 460, modal: true, layout: 'fit',
        tbar: ['Compte :',
            { xtype: 'combobox', name: 'acc', store: accStore, valueField: 'id', displayField: 'libelle',
              queryMode: 'local', editable: false, width: 240,
              listeners: {
                  select: function (c) { charger(c.up('window')); },
                  afterrender: function (c) {
                      accStore.on('load', function () {
                          if (accStore.getCount()) { c.setValue(accStore.getAt(0).get('id')); charger(c.up('window')); }
                      });
                  }
              } },
            '->', { text: '🔄 Rafraîchir', handler: function (b) { charger(b.up('window')); } }],
        items: [{ xtype: 'grid', store: tplStore, columns: [
            { text: 'Nom Meta', dataIndex: 'name', flex: 1 },
            { text: 'Langue', dataIndex: 'language', width: 70 },
            { text: 'Params', dataIndex: 'nbParams', width: 70, align: 'center' },
            { text: 'En-tête', dataIndex: 'headerFormat', width: 90 },
            { text: 'Catégorie', dataIndex: 'category', width: 110 },
            { text: 'Statut', dataIndex: 'status', width: 120,
              renderer: function (v) { return v === 'APPROVED' ? '✅ ' + v : v; } }
        ],
        listeners: { itemdblclick: function (g, rec) {
            form.down('[name=nomModeleWhatsapp]').setValue(rec.get('name'));
            var lf = form.down('[name=langue]'); if (lf) { lf.setValue(rec.get('language')); }
            // Corps (canal WEB) : pré-rempli si vide, à partir du corps Meta.
            var cf = form.down('[name=corps]');
            if (cf && !cf.getValue() && rec.get('bodyText')) { cf.setValue(rec.get('bodyText')); }
            // Paramètres du corps : pré-remplis selon le nombre de {{n}} si vide.
            var pf = form.down('[name=paramsCorps]');
            var nb = rec.get('nbParams') || 0;
            if (pf && !pf.getValue() && nb > 0) {
                var arr = [];
                for (var i = 0; i < nb; i++) { arr.push(i === 0 ? 'nom_contact' : 'a_definir'); }
                pf.setValue(arr.join(','));
            }
            // En-tête média : si le template a un en-tête média, on positionne le type.
            var hf = (rec.get('headerFormat') || '').toUpperCase();
            var mt = form.down('[name=enteteMediaType]');
            if (mt && !mt.getValue() && (hf === 'IMAGE' || hf === 'VIDEO' || hf === 'DOCUMENT')) { mt.setValue(hf); }
            win.close();
            var msg = 'Modèle Meta « ' + rec.get('name') + ' » sélectionné.';
            if (nb > 1) { msg += ' ⚠️ ' + nb + ' paramètres : ajustez « Paramètres du corps » (a_definir).'; }
            if (hf === 'IMAGE' || hf === 'VIDEO' || hf === 'DOCUMENT') { msg += ' En-tête ' + hf + ' : ajoutez le média.'; }
            Usp.toast(msg);
        } } }],
        bbar: [{ xtype: 'tbtext', text: '<span style="color:#888">Double-cliquez un modèle approuvé pour le sélectionner.</span>' }]
    });
    win.show();
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
        title: '⚙️ Général', bodyPadding: 14, border: false, defaults: { anchor: '100%', labelWidth: 220 },
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
              value: '225', maskRe: /[0-9]/, allowBlank: false, maxLength: 4, enforceMaxLength: true,
              regex: /^[0-9]{1,4}$/, regexText: 'Le préfixe pays doit contenir de 1 à 4 chiffres (ex. 225).',
              emptyText: 'Ex. 225 (Côte d\'Ivoire), 226, 33…' },
            { xtype: 'displayfield',
              value: '<span style="color:#888">Pré-rempli automatiquement dans les champs « numéro » ' +
                     'et ajouté aux numéros saisis en format local.</span>' },
            { xtype: 'textfield', name: 'societe', itemId: 'societeField', fieldLabel: 'Société émettrice', width: 520,
              emptyText: 'Nom de votre société (variable [SOCIETE] dans les messages)' },
            { xtype: 'textfield', name: 'societeTel', itemId: 'societeTelField',
              fieldLabel: 'Téléphone(s) société', width: 520,
              emptyText: 'Si plusieurs numéros, séparés par ; (variable [TEL_SOCIETE])' },
            { xtype: 'textfield', name: 'site', itemId: 'siteField',
              fieldLabel: 'Lien du site société', width: 520,
              emptyText: 'https://… (variable [SITE])' },
            { xtype: 'textfield', name: 'lienCommande', itemId: 'lienCommandeField',
              fieldLabel: 'Lien de commande', width: 520,
              emptyText: 'https://… (variable [LIEN_COMMANDE])' },
            { xtype: 'textfield', name: 'urlBase', itemId: 'urlBaseField',
              fieldLabel: 'URL publique (HTTPS)', width: 520,
              emptyText: 'https://domaine/ubisenderpro/api/v1 — pour que Meta télécharge images & pièces jointes' },
            { xtype: 'displayfield',
              value: '<span style="color:#888">Base HTTPS publique de l\'API. Sert à générer les liens ' +
                     'd\'images de template et de bulletins .xlsx téléchargeables par Meta (derrière un reverse proxy). ' +
                     'Laisser vide en accès direct.</span>' },
            { xtype: 'fieldcontainer', fieldLabel: 'Logo de la société', layout: 'hbox', items: [
                { xtype: 'hiddenfield', itemId: 'logoField' },
                { xtype: 'component', itemId: 'logoApercu', margin: '0 8 0 0',
                  html: '<span style="color:#888;font-size:11px">aucun logo</span>' },
                { xtype: 'filefield', buttonOnly: true, hideLabel: true, buttonText: 'Choisir une image…',
                  listeners: { change: function (f) { Usp.settings.uploadLogo(f); } } },
                { xtype: 'button', text: 'Retirer', margin: '0 0 0 6', handler: function (b) {
                    var p = b.up('panel');
                    p.down('#logoField').setValue('');
                    p.down('#logoApercu').update('<span style="color:#888;font-size:11px">aucun logo</span>');
                } }
            ] },
            { xtype: 'displayfield',
              value: '<span style="color:#888">Logo de votre société (PNG/SVG conseillé). ' +
                     'Stocké pour l\'en-tête de l\'application et les documents. Appliqué après enregistrement.</span>' },
            { xtype: 'fieldcontainer', fieldLabel: 'Icône de l\'application', layout: 'hbox', items: [
                { xtype: 'hiddenfield', itemId: 'faviconField' },
                { xtype: 'component', itemId: 'faviconApercu', margin: '0 8 0 0',
                  html: '<span style="color:#888;font-size:11px">icône par défaut</span>' },
                { xtype: 'filefield', buttonOnly: true, hideLabel: true, buttonText: 'Choisir une image…',
                  listeners: { change: function (f) { Usp.settings.uploadFavicon(f); } } },
                { xtype: 'button', text: 'Réinitialiser', margin: '0 0 0 6', handler: function (b) {
                    var p = b.up('panel');
                    p.down('#faviconField').setValue('');
                    p.down('#faviconApercu').update('<span style="color:#888;font-size:11px">icône par défaut</span>');
                } }
            ] },
            { xtype: 'displayfield',
              value: '<span style="color:#888">Image affichée dans l\'onglet du navigateur ' +
                     '(PNG/SVG conseillé). Vide = icône verte par défaut. Appliquée après enregistrement.</span>' }
        ],
        bbar: ['->', { text: 'Enregistrer', handler: function (b) {
            var p = b.up('panel');
            var mode = p.down('#modeField').getValue();
            var prefixe = (p.down('#prefixeField').getValue() || '').replace(/[^0-9]/g, '');
            var societe = p.down('#societeField').getValue() || '';
            var societeTel = p.down('#societeTelField').getValue() || '';
            var site = p.down('#siteField').getValue() || '';
            var lienCommande = p.down('#lienCommandeField').getValue() || '';
            var urlBase = (p.down('#urlBaseField').getValue() || '').trim();
            var logo = p.down('#logoField').getValue() || '';
            var favicon = p.down('#faviconField').getValue() || '';
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
                    put('app.societe', societe)(function () {
                    put('app.societe_tel', societeTel)(function () {
                        put('app.site', site)(function () {
                            put('app.lien_commande', lienCommande)(function () {
                            put('app.url_base', urlBase)(function () {
                            put('app.logo', logo)(function () {
                            put('app.favicon', favicon)(function () {
                                Usp.appliquerFavicon(favicon);
                                Usp.toast('Paramètres enregistrés avec succès.');
                            });
                            });
                            });
                            });
                        });
                    });
                });
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
        charger('app.societe_tel', 'societeTelField', '');
        charger('app.site', 'siteField', '');
        charger('app.lien_commande', 'lienCommandeField', '');
        charger('app.url_base', 'urlBaseField', '');
        Usp.ajax({ url: '/parametres/app.logo', method: 'GET', success: function (resp) {
            var url = (Ext.decode(resp.responseText) || {}).valeur || '';
            form.down('#logoField').setValue(url);
            if (url) {
                form.down('#logoApercu').update('<img src="' + url +
                    '" style="height:32px;vertical-align:middle;border:1px solid #ddd;border-radius:4px"/>');
            }
        } });
        Usp.ajax({ url: '/parametres/app.favicon', method: 'GET', success: function (resp) {
            var url = (Ext.decode(resp.responseText) || {}).valeur || '';
            form.down('#faviconField').setValue(url);
            if (url) {
                form.down('#faviconApercu').update('<img src="' + url +
                    '" style="height:24px;vertical-align:middle;border:1px solid #ddd;border-radius:4px"/>');
            }
        } });
    });
    return form;
};

/* Téléverse le logo de la société choisi et l'affiche en aperçu. */
Usp.settings.uploadLogo = function (f) {
    var file = f.fileInputEl.dom.files[0];
    if (!file) { return; }
    var panel = f.up('panel');
    var reader = new FileReader();
    reader.onload = function (e) {
        var b64 = e.target.result.split(',')[1];
        Usp.ajax({
            url: '/media/upload', method: 'POST',
            jsonData: { fichierBase64: b64, mimeType: file.type || 'image/png', nomFichier: file.name },
            success: function (resp) {
                var r = Ext.decode(resp.responseText);
                panel.down('#logoField').setValue(r.url);
                panel.down('#logoApercu').update('<img src="' + r.url +
                    '" style="height:32px;vertical-align:middle;border:1px solid #ddd;border-radius:4px"/>');
            },
            failure: function () { Ext.Msg.alert('Erreur', 'Téléversement du logo impossible.'); }
        });
    };
    reader.readAsDataURL(file);
};

/* Téléverse l'icône d'application choisie et l'affiche en aperçu. */
Usp.settings.uploadFavicon = function (f) {
    var file = f.fileInputEl.dom.files[0];
    if (!file) { return; }
    var panel = f.up('panel');
    var reader = new FileReader();
    reader.onload = function (e) {
        var b64 = e.target.result.split(',')[1];
        Usp.ajax({
            url: '/media/upload', method: 'POST',
            jsonData: { fichierBase64: b64, mimeType: file.type || 'image/png', nomFichier: file.name },
            success: function (resp) {
                var r = Ext.decode(resp.responseText);
                panel.down('#faviconField').setValue(r.url);
                panel.down('#faviconApercu').update('<img src="' + r.url +
                    '" style="height:24px;vertical-align:middle;border:1px solid #ddd;border-radius:4px"/>');
            },
            failure: function () { Ext.Msg.alert('Erreur', 'Téléversement de l\'icône impossible.'); }
        });
    };
    reader.readAsDataURL(file);
};

Usp.settings.tabs = function () {
    return {
        xtype: 'tabpanel', title: 'Paramètres', listeners: Usp.tabListeners,
        items: [Usp.settings.generalPanel(), Usp.settings.accountsPanel(),
                Usp.settings.referentielsPanel(), Usp.settings.templatesPanel(), Usp.settings.botPanel()]
    };
};

/* ---------- Référentiels géographiques (Pays / Régions / Villes / Communes / Agences) ---------- */
Usp.settings.refGeoGrid = function (type, titre) {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'code', 'libelle', 'actif'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/referentiels/' + type,
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
        autoLoad: true
    });
    var form = function (rec) {
        var win = Ext.create('Ext.window.Window', {
            title: (rec ? 'Modifier' : 'Ajouter') + ' — ' + titre, width: 420, modal: true, bodyPadding: 12,
            items: [{ xtype: 'form', border: false, defaults: { anchor: '100%' }, items: [
                { xtype: 'textfield', name: 'code', fieldLabel: 'Code',
                  emptyText: 'Laisser vide = généré automatiquement' },
                { xtype: 'textfield', name: 'libelle', fieldLabel: 'Libellé', allowBlank: false }
            ] }],
            buttons: [{ text: 'Enregistrer', formBind: true, handler: function (b) {
                var f = b.up('window').down('form').getForm();
                if (!f.isValid()) { return; }
                var vals = f.getValues();
                Usp.ajax({
                    url: '/referentiels/' + type + (rec ? '/' + rec.get('id') : ''),
                    method: rec ? 'PUT' : 'POST', jsonData: vals,
                    success: function () { win.close(); store.load(); Usp.toastEnregistre(titre + ' « ' + (vals.libelle || '') + ' »', !!rec); },
                    failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); }
                });
            } }]
        });
        win.show();
        if (rec) {
            // Toujours repartir de la donnée fraîche du store (évite d'afficher
            // une valeur périmée après une modification précédente).
            var frais = store.getById(rec.get('id')) || rec;
            win.down('form').getForm().setValues(frais.getData());
        }
    };
    var basculerActif = function (rec) {
        Usp.ajax({ url: '/referentiels/' + type + '/' + rec.get('id') + '/actif?actif=' + (!rec.get('actif')),
            method: 'PUT', success: function () { store.load(); },
            failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
    };
    return {
        xtype: 'grid', title: titre, store: store, flex: 1,
        columns: [
            { text: 'Code', dataIndex: 'code', width: 160 },
            { text: 'Libellé', dataIndex: 'libelle', flex: 1 },
            { text: 'Actif', dataIndex: 'actif', width: 70, align: 'center',
              renderer: function (v) { return v ? '✅' : '—'; } },
            { text: 'Actions', width: 200, sortable: false, menuDisabled: true, dataIndex: 'id',
              renderer: function (v, m, rec) {
                  return '<span class="rg-edit" title="Modifier" style="cursor:pointer;margin-right:12px">✏️ Modifier</span>' +
                      '<span class="rg-act" title="Activer / Désactiver" style="cursor:pointer;color:' +
                      (rec.get('actif') ? '#c62828' : '#2e7d32') + '">' +
                      (rec.get('actif') ? '⛔ Désactiver' : '✅ Activer') + '</span>';
              } }
        ],
        tbar: [
            { text: '➕ Ajouter', handler: function () { form(null); } },
            '->',
            { text: '📥 Importer (CSV code;libellé)', handler: function () { Usp.settings.importerRefGeo(type, store); } },
            { text: '🔄', tooltip: 'Rafraîchir', handler: function () { store.load(); } }
        ],
        listeners: {
            cellclick: function (g, td, ci, rec, tr, ri, e) {
                if (e.getTarget('.rg-edit')) { form(rec); }
                else if (e.getTarget('.rg-act')) { basculerActif(rec); }
            }
        }
    };
};

Usp.settings.referentielsPanel = function () {
    return {
        title: '🌍 Référentiels', xtype: 'panel', layout: 'fit', bodyPadding: 6,
        items: [{
            xtype: 'tabpanel',
            items: [
                Usp.settings.refGeoGrid('PAYS', 'Pays'),
                Usp.settings.refGeoGrid('REGION', 'Régions'),
                Usp.settings.refGeoGrid('VILLE', 'Villes'),
                Usp.settings.refGeoGrid('COMMUNE', 'Communes'),
                Usp.settings.refGeoGrid('AGENCE', 'Agences'),
                Usp.settings.refGeoGrid('TOURNEE', 'Tournées')
            ]
        }]
    };
};

/* Assistant d'import d'un référentiel géographique : saisie/fichier -> aperçu
 * (comptage + échantillon) -> import -> rapport (créés / ignorés). */
Usp.settings.importerRefGeo = function (type, store) {
    var majApercu = function (win) {
        var contenu = win.down('[name=contenu]').getValue() || '';
        var cmp = win.down('#refApercu');
        var lignes = contenu.split(/\r?\n/).filter(function (l) { return l.trim() !== ''; });
        // Ignore une éventuelle ligne d'en-tête (contient id/code/nom/libellé).
        if (lignes.length && /(^|;|,|\t)\s*(id|code|nom|libell)/i.test(lignes[0])) { lignes = lignes.slice(1); }
        if (!lignes.length) { cmp.update('<span style="color:#888">Aucune ligne à importer pour le moment.</span>'); return; }
        var apercu = lignes.slice(0, 8).map(function (l) {
            var c = l.split(/[;,\t]/);
            var code = c.length >= 2 ? c[0].trim() : '<i>(auto)</i>';
            var nom = (c.length >= 2 ? c[1] : c[0]).trim();
            return '<tr><td style="padding:1px 10px 1px 0;color:#1976d2">' + Ext.String.htmlEncode(code) +
                '</td><td>' + Ext.String.htmlEncode(nom) + '</td></tr>';
        }).join('');
        cmp.update('<div style="font-size:12px"><b>' + lignes.length + '</b> valeur(s) détectée(s). Aperçu :' +
            '<table style="margin-top:4px;border-collapse:collapse"><tr style="color:#888">' +
            '<td style="padding-right:10px">Code</td><td>Libellé</td></tr>' + apercu +
            (lignes.length > 8 ? '<tr><td colspan="2" style="color:#888">… (+' + (lignes.length - 8) + ')</td></tr>' : '') +
            '</table></div>');
    };
    var win = Ext.create('Ext.window.Window', {
        title: 'Assistant d\'import — ' + type, width: 560, modal: true, bodyPadding: 12,
        items: [{ xtype: 'form', border: false, defaults: { anchor: '100%' }, items: [
            { xtype: 'displayfield', value: '<span style="color:#888"><b>Étape 1</b> — Collez vos données ou ' +
                'choisissez un fichier .csv. Format <b>code;libellé</b> (séparateur <b>;</b>, sans colonne id). ' +
                'Ex. <code>CI;COTE D\'IVOIRE</code>. Une ligne d\'en-tête éventuelle est ignorée.</span>' },
            { xtype: 'textareafield', name: 'contenu', height: 150, emptyText: 'CI;COTE D\'IVOIRE\nABJ;ABIDJAN',
              listeners: { change: function (f) { majApercu(f.up('window')); }, buffer: 300 } },
            { xtype: 'filefield', buttonOnly: false, fieldLabel: 'ou fichier .csv', msgTarget: 'side',
              listeners: { change: function (f) {
                  var file = f.fileInputEl.dom.files[0]; if (!file) { return; }
                  var reader = new FileReader();
                  reader.onload = function (e) {
                      f.up('form').down('[name=contenu]').setValue(e.target.result);
                      majApercu(f.up('window'));
                  };
                  reader.readAsText(file);
              } } },
            { xtype: 'displayfield', value: '<span style="color:#888"><b>Étape 2</b> — Vérifiez l\'aperçu :</span>' },
            { xtype: 'component', itemId: 'refApercu', style: 'padding:6px;background:#fafafa;border:1px solid #eee;border-radius:4px',
              html: '<span style="color:#888">Aucune ligne à importer pour le moment.</span>' }
        ] }],
        buttons: [
            { text: '📄 Exporter un exemplaire', tooltip: 'Télécharger un modèle CSV',
              handler: function () { Usp.telechargerCsv('modele_' + String(type).toLowerCase() + '.csv',
                  'code;libelle\nCI;COTE D\'IVOIRE\nABJ;ABIDJAN\n'); } },
            { text: 'Annuler', handler: function () { win.close(); } },
            { text: '📥 Importer', handler: function (b) {
                var contenu = b.up('window').down('[name=contenu]').getValue();
                if (!contenu || !contenu.trim()) { Ext.Msg.alert('Info', 'Aucune donnée à importer.'); return; }
                b.disable(); win.setLoading('Import en cours…');
                Usp.ajax({ url: '/referentiels/' + type + '/import', method: 'POST', jsonData: { contenu: contenu },
                    success: function (resp) {
                        win.setLoading(false); win.close(); store.load();
                        var r = Ext.decode(resp.responseText) || {};
                        Ext.Msg.show({ title: 'Rapport d\'import', buttons: Ext.Msg.OK, width: 380,
                            msg: '<div style="font-family:sans-serif">' +
                                '📄 Lues : <b>' + (r.lues || 0) + '</b><br>' +
                                '✅ Créées : <b style="color:#2e7d32">' + (r.crees || 0) + '</b><br>' +
                                '⏭️ Ignorées (doublons/vides) : <b>' + (r.ignores || 0) + '</b></div>' });
                        Usp.toast((r.crees || 0) + ' valeur(s) importée(s).');
                    },
                    failure: function (resp) { win.setLoading(false); b.enable(); Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
            } }
        ]
    });
    win.show();
};

/* Importe un modèle depuis un fichier .docx exporté (lecture base64 -> POST). */
Usp.settings.importerModeleDocx = function (f, store) {
    var file = f.fileInputEl.dom.files[0];
    if (!file) { return; }
    if (!/\.docx$/i.test(file.name)) {
        Ext.Msg.alert('Import', 'Veuillez choisir un fichier .docx exporté depuis UbiSenderPro.');
        f.reset();
        return;
    }
    var reader = new FileReader();
    reader.onload = function (e) {
        var b64 = (e.target.result || '').split(',')[1];
        Usp.ajax({ url: '/templates/import-docx', method: 'POST',
            jsonData: { fichierBase64: b64, nomFichier: file.name },
            success: function () { store.load(); Usp.toast('Modèle importé avec succès.'); f.reset(); },
            failure: function (resp) {
                var m = 'Import impossible.';
                try { m = Ext.decode(resp.responseText).erreur || m; } catch (ex) {}
                Ext.Msg.alert('Erreur', m);
                f.reset();
            } });
    };
    reader.readAsDataURL(file);
};

/* ---------- Assistant (Bot) : réglages + base de connaissance (FAQ) ---------- */
Usp.settings.botPanel = function () {
    var faqStore = Ext.create('Ext.data.Store', {
        fields: ['id', 'declencheurs', 'reponse', 'ordre', 'actif'], autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/bot/faq',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } }
    });

    var chargerParams = function (p) {
        var lire = function (cle, setter) {
            Usp.ajax({ url: '/parametres/' + cle, method: 'GET', success: function (r) {
                setter((Ext.decode(r.responseText) || {}).valeur);
            } });
        };
        var txt = function (id) { return function (v) { p.down('#' + id).setValue(v || ''); }; };
        var bool = function (id) { return function (v) { p.down('#' + id).setValue(v === 'true' || v === true); }; };
        lire('bot.actif', bool('botActif'));
        lire('bot.message_transfert', txt('botTransfert'));
        lire('bot.mots_cles_humain', txt('botHumain'));
        lire('bot.mots_cles_escalade', txt('botEscalade'));
        lire('bot.adresse', txt('botAdresse'));
        lire('bot.horaires', txt('botHoraires'));
        lire('bot.email_escalade', bool('botEmail'));
        lire('mail.smtp.host', txt('smtpHost'));
        lire('mail.smtp.port', txt('smtpPort'));
        lire('mail.smtp.user', txt('smtpUser'));
        lire('mail.smtp.password', txt('smtpPass'));
        lire('mail.smtp.from', txt('smtpFrom'));
        lire('mail.smtp.tls', bool('smtpTls'));
    };

    var enregistrerParams = function (p) {
        // Enregistre une liste [clé, valeur] en série, puis affiche une confirmation.
        var actif = p.down('#botActif').getValue();
        var paires = [
            ['bot.actif', actif ? 'true' : 'false'],
            ['bot.message_transfert', p.down('#botTransfert').getValue()],
            ['bot.mots_cles_humain', p.down('#botHumain').getValue()],
            ['bot.mots_cles_escalade', p.down('#botEscalade').getValue()],
            ['bot.adresse', p.down('#botAdresse').getValue()],
            ['bot.horaires', p.down('#botHoraires').getValue()],
            ['bot.email_escalade', p.down('#botEmail').getValue() ? 'true' : 'false'],
            ['mail.smtp.host', p.down('#smtpHost').getValue()],
            ['mail.smtp.port', p.down('#smtpPort').getValue()],
            ['mail.smtp.user', p.down('#smtpUser').getValue()],
            ['mail.smtp.password', p.down('#smtpPass').getValue()],
            ['mail.smtp.from', p.down('#smtpFrom').getValue()],
            ['mail.smtp.tls', p.down('#smtpTls').getValue() ? 'true' : 'false']
        ];
        var i = 0;
        var suivant = function () {
            if (i >= paires.length) {
                Usp.botActif = actif;
                Usp.toast('Réglages du bot enregistrés' + (actif ? ' — bot activé.' : ' — bot désactivé.'));
                return;
            }
            var c = paires[i++];
            Usp.ajax({ url: '/parametres/' + c[0], method: 'PUT', jsonData: { valeur: String(c[1] == null ? '' : c[1]) },
                success: suivant,
                failure: function () { Ext.Msg.alert('Erreur', 'Enregistrement impossible (' + c[0] + ').'); } });
        };
        suivant();
    };

    return {
        xtype: 'panel', title: '🤖 Assistant (Bot)', autoScroll: true, bodyPadding: 14, layout: 'anchor',
        listeners: { afterrender: function (p) { chargerParams(p); } },
        items: [
            { xtype: 'component', html: '<div style="color:#555;margin-bottom:8px">' +
                'Le bot répond automatiquement aux messages entrants (WhatsApp API et WhatsApp Web) ' +
                'à partir de la base de connaissance ci-dessous et du catalogue/promotions. ' +
                'Quand il ne sait pas répondre, ou sur un mot-clé sensible, il <b>passe la main à un humain</b> ' +
                '(la discussion bascule en « À reprendre »). <b>Désactivé par défaut.</b></div>' },
            { xtype: 'fieldset', title: 'Réglages', defaults: { anchor: '100%', labelWidth: 230 }, items: [
                { xtype: 'checkbox', itemId: 'botActif', fieldLabel: 'Activer le bot',
                  boxLabel: 'Réponses automatiques activées' },
                { xtype: 'textfield', itemId: 'botTransfert', fieldLabel: 'Message de transfert vers un humain' },
                { xtype: 'textfield', itemId: 'botHumain', fieldLabel: 'Mots-clés « parler à un humain »',
                  emptyText: 'conseiller, agent, humain…' },
                { xtype: 'textfield', itemId: 'botEscalade', fieldLabel: 'Mots-clés sensibles (escalade)',
                  emptyText: 'réclamation, remboursement, litige…' }
            ] },
            { xtype: 'fieldset', title: 'Informations communiquées par le bot', defaults: { anchor: '100%', labelWidth: 230 }, items: [
                { xtype: 'textarea', itemId: 'botAdresse', fieldLabel: 'Adresse', height: 50,
                  emptyText: 'Ex. Rue du Commerce, Plateau, Abidjan' },
                { xtype: 'textarea', itemId: 'botHoraires', fieldLabel: 'Horaires', height: 50,
                  emptyText: 'Ex. Du lundi au samedi, de 8h à 19h.' }
            ] },
            { xtype: 'fieldset', title: 'Notification e-mail des superviseurs (escalade)', defaults: { anchor: '100%', labelWidth: 230 }, items: [
                { xtype: 'checkbox', itemId: 'botEmail', fieldLabel: 'Notifier par e-mail',
                  boxLabel: 'Envoyer un e-mail aux superviseurs/admins à chaque escalade' },
                { xtype: 'displayfield', value: '<span style="color:#888">Renseignez le serveur SMTP ci-dessous. ' +
                  'Les e-mails partent aux utilisateurs actifs ayant le rôle Superviseur ou Administrateur.</span>' },
                { xtype: 'textfield', itemId: 'smtpHost', fieldLabel: 'Serveur SMTP', emptyText: 'smtp.gmail.com' },
                { xtype: 'textfield', itemId: 'smtpPort', fieldLabel: 'Port', emptyText: '587', width: 360 },
                { xtype: 'textfield', itemId: 'smtpUser', fieldLabel: 'Utilisateur SMTP' },
                { xtype: 'textfield', itemId: 'smtpPass', fieldLabel: 'Mot de passe SMTP', inputType: 'password' },
                { xtype: 'textfield', itemId: 'smtpFrom', fieldLabel: 'Adresse expéditeur', vtype: 'email' },
                { xtype: 'checkbox', itemId: 'smtpTls', fieldLabel: 'STARTTLS', boxLabel: 'Connexion chiffrée (recommandé)' }
            ] },
            { xtype: 'button', text: '💾 Enregistrer les réglages', margin: '0 0 10 0',
              handler: function (b) { enregistrerParams(b.up('panel')); } },
            { xtype: 'grid', title: 'Base de connaissance (FAQ)', height: 320, anchor: '100%', store: faqStore,
              columns: [
                { text: 'Déclencheurs (mots-clés)', dataIndex: 'declencheurs', flex: 1,
                  renderer: function (v) { return Ext.String.htmlEncode(v || ''); } },
                { text: 'Réponse', dataIndex: 'reponse', flex: 2,
                  renderer: function (v) { return Ext.String.htmlEncode(v || ''); } },
                { text: 'Ordre', dataIndex: 'ordre', width: 60 },
                { text: 'Active', dataIndex: 'actif', width: 70, align: 'center',
                  renderer: function (v) { return v ? '✅' : '—'; } },
                { text: 'Suppr.', width: 60, align: 'center', sortable: false, menuDisabled: true, dataIndex: 'id',
                  renderer: function () { return '<span class="faq-del" title="Supprimer" style="cursor:pointer;color:#c62828">🗑️</span>'; } }
              ],
              tbar: [
                { text: '➕ Nouvelle entrée', tooltip: 'Ajouter une question/réponse', handler: function () { Usp.settings.faqForm(faqStore, null); } },
                { text: '🔄 Rafraîchir', handler: function () { faqStore.load(); } }
              ],
              listeners: {
                itemdblclick: function (g, rec) { Usp.settings.faqForm(faqStore, rec); },
                cellclick: function (g, td, ci, rec, tr, ri, e) {
                    if (e.getTarget('.faq-del')) {
                        Ext.Msg.confirm('Supprimer', 'Supprimer cette entrée de la FAQ ?', function (btn) {
                            if (btn === 'yes') {
                                Usp.ajax({ url: '/bot/faq/' + rec.get('id'), method: 'DELETE',
                                    success: function () { faqStore.load(); Usp.toast('Entrée supprimée.'); } });
                            }
                        });
                    }
                }
              }
            }
        ]
    };
};

/* Formulaire de création/modification d'une entrée FAQ du bot. */
Usp.settings.faqForm = function (store, rec) {
    var win = Ext.create('Ext.window.Window', {
        title: rec ? 'Modifier l\'entrée FAQ' : 'Nouvelle entrée FAQ',
        width: 560, modal: true, bodyPadding: 12,
        items: [{ xtype: 'form', border: false, defaults: { anchor: '100%' }, items: [
            { xtype: 'textfield', name: 'declencheurs', fieldLabel: 'Déclencheurs', allowBlank: false,
              emptyText: 'Mots-clés séparés par des virgules : horaire, ouvert, heure' },
            { xtype: 'displayfield', value: '<span style="color:#888">Si le message du client contient l\'un de ces ' +
              'mots-clés, le bot envoie la réponse ci-dessous.</span>' },
            { xtype: 'textareafield', name: 'reponse', fieldLabel: 'Réponse', allowBlank: false, height: 110 },
            { xtype: 'numberfield', name: 'ordre', fieldLabel: 'Ordre', value: 0, minValue: 0 },
            { xtype: 'checkbox', name: 'actif', fieldLabel: 'Active', checked: true }
        ] }],
        buttons: [{ text: 'Enregistrer', formBind: true, handler: function (b) {
            var form = b.up('window').down('form').getForm();
            if (!form.isValid()) { return; }
            var data = form.getValues();
            data.actif = form.findField('actif').getValue();
            data.ordre = form.findField('ordre').getValue() || 0;
            Usp.ajax({ url: rec ? '/bot/faq/' + rec.get('id') : '/bot/faq',
                method: rec ? 'PUT' : 'POST', jsonData: data,
                success: function () { win.close(); store.load(); Usp.toastEnregistre('Entrée FAQ', !!rec); },
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
