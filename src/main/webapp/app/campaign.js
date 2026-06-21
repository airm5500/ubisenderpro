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

Usp.campaign.show = function () {
    var campagneId = null;

    var step1 = {
        title: '1. Informations', bodyPadding: 12, border: false,
        defaults: { anchor: '100%' },
        items: [
            { xtype: 'textfield', name: 'nom', fieldLabel: 'Nom', allowBlank: false },
            { xtype: 'textfield', name: 'objectif', fieldLabel: 'Objectif' },
            { xtype: 'textarea', name: 'description', fieldLabel: 'Description', height: 60 },
            Usp.campaign.combo('/whatsapp/accounts', '', 'id', 'libelle',
                { name: 'whatsappAccountId', fieldLabel: 'Compte WhatsApp', allowBlank: false }),
            Usp.campaign.combo('/templates', '', 'id', 'nom',
                { name: 'modeleId', fieldLabel: 'Modèle de message', allowBlank: false })
        ]
    };

    var step2 = {
        title: '2. Destinataires', bodyPadding: 12, border: false,
        defaults: { anchor: '100%' },
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
        title: '3. Contenu', bodyPadding: 12, border: false,
        items: [
            { xtype: 'displayfield', value: 'Le contenu provient du modèle WhatsApp approuvé sélectionné.' },
            { xtype: 'component', itemId: 'apercu', html: '<div style="border:1px solid #ddd;padding:10px;background:#dcf8c6;border-radius:8px;max-width:320px">Aperçu du modèle…</div>' }
        ]
    };

    var step4 = {
        title: '4. Programmation', bodyPadding: 12, border: false,
        defaults: { anchor: '100%' },
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
        title: '5. Validation', bodyPadding: 12, border: false,
        items: [{ xtype: 'component', itemId: 'recap', html: 'Cliquez sur « Construire » pour calculer les destinataires.' }]
    };

    var wizard = Ext.create('Ext.window.Window', {
        title: 'Nouvelle campagne', width: 560, height: 440, modal: true, layout: 'fit',
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
    wizard.show();
};

Usp.campaign.nav = function (wizard, dir) {
    var form = wizard.down('#wizForm');
    var layout = form.getLayout();
    var idx = form.items.indexOf(layout.getActiveItem());

    // Création de la campagne au passage de l'étape 1 vers l'étape 2.
    if (dir === 1 && idx === 0) {
        var v = form.getForm().getValues();
        if (!v.nom || !v.whatsappAccountId || !v.modeleId) {
            Ext.Msg.alert('Champs requis', 'Nom, compte WhatsApp et modèle sont obligatoires.');
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
    var payload = {
        nom: v.nom, objectif: v.objectif, description: v.description,
        whatsappAccountId: v.whatsappAccountId, modeleId: v.modeleId,
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
            failure: function () { Ext.Msg.alert('Erreur', 'Construction des destinataires impossible.'); }
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
            failure: function () { Ext.Msg.alert('Erreur', 'Création de la campagne impossible.'); }
        });
    }
};

Usp.campaign.launch = function (wizard) {
    if (!wizard.campagneId) { return; }
    Usp.ajax({
        url: '/campaigns/' + wizard.campagneId + '/launch', method: 'POST',
        success: function () {
            Ext.Msg.alert('Campagne lancée',
                'L\'envoi progresse en arrière-plan. Suivez les statuts dans le menu Campagnes.');
            wizard.close();
        },
        failure: function (resp) {
            Ext.Msg.alert('Erreur', 'Lancement impossible : ' + (resp.responseText || ''));
        }
    });
};

/* ---------- Grille de suivi des campagnes ---------- */
Usp.campaign.listPanel = function () {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'nom', 'statut', 'nbDestinataires', 'nbEnvoyes', 'nbDistribues', 'nbLus', 'nbEchoues'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/campaigns',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
        autoLoad: true
    });
    return {
        xtype: 'grid', title: 'Campagnes', store: store,
        columns: [
            { text: 'Nom', dataIndex: 'nom', flex: 1 },
            { text: 'Statut', dataIndex: 'statut', width: 110 },
            { text: 'Cibles', dataIndex: 'nbDestinataires', width: 80 },
            { text: 'Envoyés', dataIndex: 'nbEnvoyes', width: 80 },
            { text: 'Distribués', dataIndex: 'nbDistribues', width: 90 },
            { text: 'Lus', dataIndex: 'nbLus', width: 70 },
            { text: 'Échoués', dataIndex: 'nbEchoues', width: 80 }
        ],
        tbar: [
            { text: 'Nouvelle campagne', handler: Usp.campaign.show },
            { text: 'Rafraîchir', handler: function () { store.load(); } }
        ].concat(Usp.export.boutons('Campagnes'))
    };
};
