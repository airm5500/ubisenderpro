/*
 * UbiSenderPro - Assistant de campagne (section 21 de la spec).
 * Étapes : Informations -> Destinataires -> Contenu -> Programmation -> Validation.
 * Dépend de app.js (objet global Usp).
 */
Ext.define('Usp.campaign', { singleton: true });

Usp.campaign.combo = function (url, root, valueField, displayField, cfg) {
    var store = Ext.create('Ext.data.Store', {
        fields: [valueField, displayField],
        proxy: {
            type: 'ajax', url: Usp.apiBase + url,
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') },
            reader: { type: 'json', root: root || '' }
        },
        autoLoad: true
    });
    return Ext.apply({
        xtype: 'combobox', store: store, queryMode: 'local',
        valueField: valueField, displayField: displayField,
        anchor: '100%', editable: false
    }, cfg || {});
};

Usp.campaign.show = function (store) {
    var campagneId = null;

    var step1 = {
        title: '1. Informations', bodyPadding: 12, border: false, layout: 'anchor', autoScroll: false,
        defaults: { anchor: '100%', labelWidth: 165 },
        items: [
            { xtype: 'textfield', name: 'nom', fieldLabel: 'Nom', allowBlank: false },
            { xtype: 'textfield', name: 'objectif', fieldLabel: 'Objectif' },
            { xtype: 'textarea', name: 'description', fieldLabel: 'Description', height: 60 },
            // Canal d'envoi (#5) : API WhatsApp officielle ou WhatsApp Web. Le champ
            // « compte » affiché dépend du canal choisi (toggle ci-dessous).
            { xtype: 'combobox', name: 'canal', itemId: 'fCanal', fieldLabel: 'Canal d\'envoi', anchor: '100%',
              queryMode: 'local', editable: false, value: (Usp.mode === 'WEB' ? 'WEB' : 'API'),
              store: [['API', 'API WhatsApp (officielle)'], ['WEB', 'WhatsApp Web']],
              listeners: {
                  change: function (cb, val) { Usp.campaign.toggleCanal(cb.up('form'), val); },
                  afterrender: function (cb) { Usp.campaign.toggleCanal(cb.up('form'), cb.getValue()); }
              } },
            Usp.campaign.combo('/whatsapp/accounts', '', 'id', 'libelle',
                { name: 'whatsappAccountId', itemId: 'fAccount', fieldLabel: 'Compte WhatsApp', allowBlank: false }),
            { xtype: 'combobox', name: 'waWebSessionId', itemId: 'fWebSession', fieldLabel: 'Session WhatsApp Web',
              anchor: '100%', queryMode: 'local', editable: false, hidden: true, allowBlank: true,
              store: Usp.waweb.sessionComboStore(), valueField: 'id', displayField: 'libelle' },
            Usp.campaign.combo('/templates', '', 'id', 'nom',
                { name: 'modeleId', fieldLabel: 'Modèle de message', allowBlank: false })
        ]
    };

    var step2 = {
        title: '2. Destinataires', bodyPadding: 12, border: false, layout: 'anchor', autoScroll: false,
        defaults: { anchor: '100%', labelWidth: 165 },
        items: [
            { xtype: 'displayfield', value: 'Ciblez par segmentation client, et/ou liste statique, et/ou segment dynamique.' },
            Usp.campaign.combo('/segmentations', '', 'id', 'libelle',
                { name: 'segmentationId', fieldLabel: 'Segmentation client' }),
            Usp.campaign.combo('/lists', '', 'id', 'nom',
                { name: 'listeId', fieldLabel: 'Liste de diffusion' }),
            Usp.campaign.combo('/segments', '', 'id', 'nom',
                { name: 'segmentId', fieldLabel: 'Segment dynamique' }),
            { xtype: 'displayfield', value: 'Les doublons et les contacts désabonnés sont exclus automatiquement.' }
        ]
    };

    var step3 = {
        title: '3. Contenu', bodyPadding: 12, border: false, layout: 'anchor', autoScroll: false,
        items: [
            { xtype: 'displayfield', value: 'Le contenu provient du modèle WhatsApp approuvé sélectionné.' },
            { xtype: 'component', itemId: 'apercu', html: '<div style="border:1px solid #ddd;padding:10px;background:#dcf8c6;border-radius:8px;max-width:320px">Aperçu du modèle…</div>' }
        ]
    };

    var step4 = {
        title: '4. Programmation', bodyPadding: 12, border: false, layout: 'anchor', autoScroll: false,
        defaults: { anchor: '100%', labelWidth: 165 },
        items: [
            { xtype: 'radiogroup', fieldLabel: 'Envoi', columns: 1, items: [
                { boxLabel: 'Immédiat', name: 'prog', inputValue: 'now', checked: true },
                { boxLabel: 'Programmé', name: 'prog', inputValue: 'later' }
            ] },
            { xtype: 'datefield', name: 'dateProg', fieldLabel: 'Date', format: 'Y-m-d' },
            { xtype: 'timefield', name: 'heureProg', fieldLabel: 'Heure', format: 'H:i' }
        ]
    };

    var step5 = {
        title: '5. Validation', bodyPadding: 12, border: false, layout: 'anchor', autoScroll: false,
        items: [{ xtype: 'component', itemId: 'recap', html: 'Cliquez sur « Construire » pour calculer les destinataires.' }]
    };

    var wizard = Ext.create('Ext.window.Window', {
        title: 'Nouvelle campagne', width: 720, height: 480, modal: true, layout: 'fit',
        items: [{
            xtype: 'form', itemId: 'wizForm', layout: 'card', activeItem: 0, border: false,
            items: [step1, step2, step3, step4, step5]
        }],
        bbar: ['->',
            { text: '« Précédent', itemId: 'prev', disabled: true, handler: function () { Usp.campaign.nav(wizard, -1); } },
            { text: 'Suivant »', itemId: 'next', handler: function () { Usp.campaign.nav(wizard, 1); } },
            { text: 'Construire', itemId: 'build', hidden: true, handler: function () { Usp.campaign.build(wizard); } },
            { text: 'Lancer', itemId: 'launch', hidden: true, disabled: true, handler: function () { Usp.campaign.launch(wizard); } }
        ]
    });
    wizard.campagneId = null;
    wizard.refreshStore = store || null;
    wizard.show();
};

/** Affiche le champ « compte » adapté au canal choisi (API ↔ WhatsApp Web). */
Usp.campaign.toggleCanal = function (form, canal) {
    if (!form) { return; }
    var web = canal === 'WEB';
    var acc = form.down('#fAccount');
    var ses = form.down('#fWebSession');
    if (acc) { acc.setVisible(!web); acc.allowBlank = web; if (web) { acc.clearInvalid(); } }
    if (ses) { ses.setVisible(web); ses.allowBlank = !web; if (!web) { ses.clearInvalid(); } }
};

Usp.campaign.nav = function (wizard, dir) {
    var form = wizard.down('#wizForm');
    var layout = form.getLayout();
    var idx = form.items.indexOf(layout.getActiveItem());

    // Création de la campagne au passage de l'étape 1 vers l'étape 2.
    if (dir === 1 && idx === 0) {
        var v = form.getForm().getValues();
        if (!v.nom || !v.modeleId) {
            Ext.Msg.alert('Champs requis', 'Le nom et le modèle de message sont obligatoires.');
            return;
        }
        if (v.canal === 'WEB' && !v.waWebSessionId) {
            Ext.Msg.alert('Champs requis', 'Choisissez la session WhatsApp Web (canal WhatsApp Web).');
            return;
        }
        if (v.canal !== 'WEB' && !v.whatsappAccountId) {
            Ext.Msg.alert('Champs requis', 'Choisissez le compte WhatsApp (canal API officielle).');
            return;
        }
    }

    var nb = form.items.getCount();
    var target = Math.max(0, Math.min(nb - 1, idx + dir));
    layout.setActiveItem(target);

    wizard.down('#prev').setDisabled(target === 0);
    wizard.down('#next').setVisible(target < nb - 1);
    wizard.down('#build').setVisible(target === nb - 1);
    wizard.down('#launch').setVisible(target === nb - 1);
};

Usp.campaign.build = function (wizard) {
    var form = wizard.down('#wizForm').getForm();
    var v = form.getValues();
    var canal = v.canal || 'API';
    var payload = {
        nom: v.nom, objectif: v.objectif, description: v.description,
        canal: canal,
        whatsappAccountId: canal === 'WEB' ? null : (v.whatsappAccountId || null),
        waWebSessionId: canal === 'WEB' ? (v.waWebSessionId || null) : null,
        modeleId: v.modeleId,
        listeId: v.listeId || null, segmentId: v.segmentId || null,
        segmentationId: v.segmentationId || null,
        statut: 'BROUILLON'
    };

    var finishBuild = function (id) {
        Usp.ajax({
            url: '/campaigns/' + id + '/recipients', method: 'POST',
            success: function (resp) {
                var r = Ext.decode(resp.responseText);
                wizard.down('#recap').update(
                    '<b>Campagne prête</b><hr/>' +
                    'Destinataires valides : <b>' + r.nbDestinataires + '</b><br/>' +
                    'Les désabonnés et numéros invalides ont été exclus.<br/><br/>' +
                    'Cliquez sur « Lancer » pour démarrer l\'envoi en arrière-plan.');
                wizard.down('#launch').setDisabled(r.nbDestinataires === 0);
            },
            failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp, 'Construction des destinataires impossible.')); }
        });
    };

    if (wizard.campagneId) {
        finishBuild(wizard.campagneId);
    } else {
        Usp.ajax({
            url: '/campaigns', method: 'POST', jsonData: payload,
            success: function (resp) {
                wizard.campagneId = Ext.decode(resp.responseText).id;
                finishBuild(wizard.campagneId);
            },
            failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp, 'Création de la campagne impossible.')); }
        });
    }
};

Usp.campaign.launch = function (wizard) {
    if (!wizard.campagneId) { return; }
    Usp.ajax({
        url: '/campaigns/' + wizard.campagneId + '/launch', method: 'POST',
        success: function () {
            var store = wizard.refreshStore;
            wizard.close();
            // Actualise la grille des campagnes dès le clic sur « OK » (#5).
            Ext.Msg.alert('Campagne lancée',
                'L\'envoi progresse en arrière-plan. Suivez les statuts dans le menu Campagnes.',
                function () { if (store) { store.load(); } });
        },
        failure: function (resp) {
            Ext.Msg.alert('Erreur', 'Lancement impossible : ' + (resp.responseText || ''));
        }
    });
};

/* Couleurs des statuts de campagne (#5) : EN_COURS orange, distribué bleu,
   lu vert, échoué rouge, terminé gris. */
Usp.campaign.COULEUR_STATUT = {
    BROUILLON: '#777', EN_ATTENTE: '#f57c00', EN_COURS: '#f57c00', SUSPENDUE: '#f57c00',
    ENVOYE: '#1976d2', DISTRIBUE: '#1976d2', LU: '#2e7d32', TERMINEE: '#777',
    ECHOUE: '#c62828', ECHOUEE: '#c62828', ANNULEE: '#c62828', DESABONNE: '#777', NUMERO_INVALIDE: '#c62828'
};

Usp.campaign.statutRenderer = function (v) {
    var c = Usp.campaign.COULEUR_STATUT[v] || '#333';
    return '<span style="color:' + c + ';font-weight:bold">' + (v || '') + '</span>';
};

/* Colorise un compteur (>0) d'une couleur donnée, gris si nul. */
Usp.campaign.compteurRenderer = function (couleur) {
    return function (v) {
        var n = v || 0;
        return n > 0 ? '<span style="color:' + couleur + ';font-weight:bold">' + n + '</span>'
                     : '<span style="color:#bbb">0</span>';
    };
};

/* Taux de réussite de l'envoi = envoyés / cibles. */
Usp.campaign.tauxRenderer = function (v, m, rec) {
    var total = rec.get('nbDestinataires') || 0;
    if (!total) { return '<span style="color:#bbb">—</span>'; }
    var pct = Math.round((rec.get('nbEnvoyes') || 0) / total * 100);
    var couleur = pct >= 80 ? '#2e7d32' : (pct >= 50 ? '#f57c00' : '#c62828');
    return '<span style="color:' + couleur + ';font-weight:bold">' + pct + ' %</span>';
};

/* ---------- Grille de suivi des campagnes ---------- */
Usp.campaign.listPanel = function () {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'nom', 'statut', 'canal', 'nbDestinataires', 'nbEnvoyes', 'nbDistribues', 'nbLus', 'nbEchoues'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/campaigns',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
        autoLoad: true
    });
    return {
        xtype: 'grid', title: '🚀 Campagnes', store: store,
        columns: [
            { text: 'Nom', dataIndex: 'nom', flex: 1 },
            { text: 'Canal', dataIndex: 'canal', width: 70, renderer: function (v) {
                return v === 'WEB' ? 'WA Web' : 'API'; } },
            { text: 'Statut', dataIndex: 'statut', width: 110, renderer: Usp.campaign.statutRenderer },
            { text: 'Cibles', dataIndex: 'nbDestinataires', width: 70, align: 'right' },
            { text: 'Envoyés', dataIndex: 'nbEnvoyes', width: 75, align: 'right' },
            { text: 'Distribués', dataIndex: 'nbDistribues', width: 85, align: 'right',
              renderer: Usp.campaign.compteurRenderer('#1976d2') },
            { text: 'Lus', dataIndex: 'nbLus', width: 65, align: 'right',
              renderer: Usp.campaign.compteurRenderer('#2e7d32') },
            { text: 'Échoués', dataIndex: 'nbEchoues', width: 75, align: 'right',
              renderer: Usp.campaign.compteurRenderer('#c62828') },
            { text: 'Taux', dataIndex: 'nbEnvoyes', width: 70, align: 'right', renderer: Usp.campaign.tauxRenderer },
            { text: 'Actions', width: 260, sortable: false, menuDisabled: true, dataIndex: 'id',
              renderer: function (v, m, rec) {
                  var s = '<span class="camp-details" title="Voir les destinataires" ' +
                          'style="cursor:pointer;color:#1976d2">🔍 Détails</span>';
                  if ((rec.get('nbEchoues') || 0) > 0) {
                      s += ' &nbsp;<span class="camp-relance" title="Relancer les envois en échec" ' +
                           'style="cursor:pointer;color:#c62828">↻ Relance</span>';
                  }
                  s += ' &nbsp;<span class="camp-edit" title="Modifier la campagne" style="cursor:pointer">✏️</span>' +
                       ' &nbsp;<span class="camp-del" title="Supprimer la campagne" style="cursor:pointer;color:#c62828">🗑️</span>';
                  return s;
              } }
        ],
        tbar: [
            Usp.permBtn('campaigns', 'CREER', { text: '➕ Nouvelle campagne', tooltip: 'Créer et lancer une nouvelle campagne', handler: function () { Usp.campaign.show(store); } }),
            { text: 'Rafraîchir', handler: function () { store.load(); } }
        ].concat(Usp.export.boutons('Campagnes')),
        listeners: {
            cellclick: function (g, td, ci, rec, tr, ri, e) {
                if (e.getTarget('.camp-details')) { Usp.campaign.details(rec); }
                else if (e.getTarget('.camp-relance')) { Usp.campaign.relance(rec, store); }
                else if (e.getTarget('.camp-edit')) { Usp.campaign.editForm(rec, store); }
                else if (e.getTarget('.camp-del')) { Usp.campaign.supprimer(rec, store); }
            }
        }
    };
};

/* Modifie une campagne. Si elle n'est pas lancée (BROUILLON) : édition complète
   (canal, compte, modèle, ciblage) avec reconstruction des destinataires.
   Sinon : métadonnées seules (nom/objectif/description) pour éviter toute incohérence. */
Usp.campaign.editForm = function (rec, store) {
    Usp.ajax({ url: '/campaigns/' + rec.get('id'), method: 'GET', success: function (resp) {
        var camp = Ext.decode(resp.responseText) || {};
        var complet = (camp.statut === 'BROUILLON');

        var items = [
            { xtype: 'textfield', name: 'nom', fieldLabel: 'Nom', allowBlank: false, value: camp.nom },
            { xtype: 'textfield', name: 'objectif', fieldLabel: 'Objectif', value: camp.objectif },
            { xtype: 'textarea', name: 'description', fieldLabel: 'Description', height: 70, value: camp.description }
        ];
        if (complet) {
            items.push(
                { xtype: 'combobox', name: 'canal', itemId: 'fCanal', fieldLabel: 'Canal d\'envoi', anchor: '100%',
                  queryMode: 'local', editable: false, value: camp.canal || 'API',
                  store: [['API', 'API WhatsApp (officielle)'], ['WEB', 'WhatsApp Web']],
                  listeners: {
                      change: function (cb, val) { Usp.campaign.toggleCanal(cb.up('form'), val); },
                      afterrender: function (cb) { Usp.campaign.toggleCanal(cb.up('form'), cb.getValue()); }
                  } },
                Usp.campaign.combo('/whatsapp/accounts', '', 'id', 'libelle',
                    { name: 'whatsappAccountId', itemId: 'fAccount', fieldLabel: 'Compte WhatsApp', value: camp.whatsappAccountId }),
                { xtype: 'combobox', name: 'waWebSessionId', itemId: 'fWebSession', fieldLabel: 'Session WhatsApp Web',
                  anchor: '100%', queryMode: 'local', editable: false, hidden: true, value: camp.waWebSessionId,
                  store: Usp.waweb.sessionComboStore(), valueField: 'id', displayField: 'libelle' },
                Usp.campaign.combo('/templates', '', 'id', 'nom',
                    { name: 'modeleId', fieldLabel: 'Modèle de message', value: camp.modeleId }),
                { xtype: 'displayfield', value: '<span style="color:#888">Ciblage (les destinataires seront recalculés) :</span>' },
                Usp.campaign.combo('/segmentations', '', 'id', 'libelle',
                    { name: 'segmentationId', fieldLabel: 'Segmentation client', value: camp.segmentationId }),
                Usp.campaign.combo('/lists', '', 'id', 'nom',
                    { name: 'listeId', fieldLabel: 'Liste de diffusion', value: camp.listeId }),
                Usp.campaign.combo('/segments', '', 'id', 'nom',
                    { name: 'segmentId', fieldLabel: 'Segment dynamique', value: camp.segmentId }));
        } else {
            items.push({ xtype: 'displayfield',
                value: '<span style="color:#888">Campagne déjà lancée : seules les informations ' +
                       '(nom, objectif, description) sont modifiables.</span>' });
        }

        var win = Ext.create('Ext.window.Window', {
            title: 'Modifier la campagne' + (complet ? '' : ' (lancée)'),
            width: 560, modal: true, bodyPadding: 12, autoScroll: true,
            maxHeight: Ext.getBody().getViewSize().height - 40,
            items: [{ xtype: 'form', border: false, defaults: { anchor: '100%', labelWidth: 160 }, items: items }],
            buttons: [{ text: 'Enregistrer', formBind: true, handler: function (b) {
                var f = b.up('window').down('form').getForm();
                if (!f.isValid()) { return; }
                var v = f.getValues();
                camp.nom = v.nom; camp.objectif = v.objectif; camp.description = v.description;
                if (complet) {
                    camp.canal = v.canal;
                    camp.whatsappAccountId = (v.canal === 'WEB') ? null : (v.whatsappAccountId || null);
                    camp.waWebSessionId = (v.canal === 'WEB') ? (v.waWebSessionId || null) : null;
                    camp.modeleId = v.modeleId || null;
                    camp.segmentationId = v.segmentationId || null;
                    camp.listeId = v.listeId || null;
                    camp.segmentId = v.segmentId || null;
                    if (!camp.modeleId) { Ext.Msg.alert('Champ requis', 'Le modèle de message est obligatoire.'); return; }
                    if (v.canal === 'WEB' && !camp.waWebSessionId) { Ext.Msg.alert('Champ requis', 'Choisissez la session WhatsApp Web.'); return; }
                    if (v.canal !== 'WEB' && !camp.whatsappAccountId) { Ext.Msg.alert('Champ requis', 'Choisissez le compte WhatsApp.'); return; }
                }
                Usp.ajax({ url: '/campaigns/' + rec.get('id'), method: 'PUT', jsonData: camp,
                    success: function () {
                        var fin = function () { win.close(); store.load(); Usp.toastEnregistre('Campagne « ' + v.nom + ' »', true); };
                        // Recalcule les destinataires si le ciblage a pu changer (campagne non lancée).
                        if (complet) {
                            Usp.ajax({ url: '/campaigns/' + rec.get('id') + '/recipients', method: 'POST',
                                success: fin, failure: fin });
                        } else { fin(); }
                    },
                    failure: function () { Ext.Msg.alert('Erreur', 'Modification impossible.'); } });
            } }]
        });
        win.show();
    }, failure: function () { Ext.Msg.alert('Erreur', 'Chargement de la campagne impossible.'); } });
};

/* Supprime une campagne (et ses destinataires) après confirmation. */
Usp.campaign.supprimer = function (rec, store) {
    Ext.Msg.confirm('Supprimer la campagne',
        'Supprimer définitivement la campagne « ' + Ext.String.htmlEncode(rec.get('nom')) +
        ' » et ses destinataires ?', function (btn) {
            if (btn !== 'yes') { return; }
            Usp.ajax({ url: '/campaigns/' + rec.get('id'), method: 'DELETE',
                success: function () { store.load(); Usp.toast('Campagne supprimée avec succès.'); },
                failure: function () { Ext.Msg.alert('Erreur', 'Suppression impossible.'); } });
        });
};

/* Fenêtre des détails d'une campagne : destinataires + statut d'envoi. */
Usp.campaign.details = function (rec) {
    var id = rec.get('id');
    var dStore = Ext.create('Ext.data.Store', {
        fields: ['id', 'numeroWhatsapp', 'nomContact', 'statut', 'tentatives', 'erreur'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/campaigns/' + id + '/recipients',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
        autoLoad: true
    });
    Ext.create('Ext.window.Window', {
        title: 'Destinataires — ' + rec.get('nom'), width: 720, height: 480, modal: true, layout: 'fit',
        items: [{
            xtype: 'grid', store: dStore, border: false,
            columns: [
                { text: 'Numéro', dataIndex: 'numeroWhatsapp', width: 150 },
                { text: 'Contact', dataIndex: 'nomContact', flex: 1 },
                { text: 'Statut', dataIndex: 'statut', width: 130, renderer: Usp.campaign.statutRenderer },
                { text: 'Tentatives', dataIndex: 'tentatives', width: 90, align: 'right' },
                { text: 'Erreur', dataIndex: 'erreur', flex: 1, renderer: function (v) {
                    return v ? '<span style="color:#c62828">' + Ext.String.htmlEncode(v) + '</span>' : ''; } }
            ],
            tbar: [{ text: 'Rafraîchir', handler: function () { dStore.load(); } }]
        }]
    }).show();
};

/* Relance les envois en échec d'une campagne. */
Usp.campaign.relance = function (rec, store) {
    var nb = rec.get('nbEchoues') || 0;
    Ext.Msg.confirm('Relancer les échecs',
        'Relancer les ' + nb + ' envoi(s) en échec de la campagne « ' + rec.get('nom') + ' » ?',
        function (btn) {
            if (btn !== 'yes') { return; }
            Usp.ajax({
                url: '/campaigns/' + rec.get('id') + '/relancer', method: 'POST',
                success: function (resp) {
                    var r = Ext.decode(resp.responseText || '{}');
                    Ext.Msg.alert('Relance',
                        (r.relances || 0) + ' envoi(s) remis en file. Le traitement progresse en arrière-plan.',
                        function () { if (store) { store.load(); } });
                },
                failure: function (resp) {
                    Ext.Msg.alert('Erreur', 'Relance impossible : ' + (resp.responseText || ''));
                }
            });
        });
};
