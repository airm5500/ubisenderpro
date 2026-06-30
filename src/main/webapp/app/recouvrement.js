/*
 * UbiSenderPro - Module RECOUVREMENT (Lot 1 : socle financier).
 * Indépendant du Marketing. Droits via le menu « recouvrement » (RBAC).
 * Dépend de app.js (objet Usp).
 */
Ext.define('Usp.recouvrement', { singleton: true });

Usp.recouvrement.CANAUX = [['WHATSAPP', 'WhatsApp'], ['EMAIL', 'Email']];

/* Formatage monétaire. */
Usp.recouvrement.money = function (v) {
    if (v === null || v === undefined || v === '') { return ''; }
    return Ext.util.Format.number(parseFloat(v), '0,000.00');
};

/* Combo d'un référentiel du module (SEGMENT_COMMERCIAL / PROFIL_PAIEMENT / STATUT_RECOUVREMENT). */
Usp.recouvrement.refCombo = function (type, cfg) {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'code', 'libelle'], autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/recouvrement/referentiels/' + type,
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } }
    });
    return Ext.apply({ xtype: 'combobox', store: store, valueField: 'libelle', displayField: 'libelle',
        queryMode: 'local', forceSelection: false, anchor: '100%' }, cfg || {});
};

/* Combo de sélection d'un client (recherche distante sur /clients). */
Usp.recouvrement.clientCombo = function (cfg) {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'nomCompte', 'numeroClient'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/clients', queryParam: 'q',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') },
            reader: { type: 'json', root: 'data', totalProperty: 'total' } } });
    return Ext.apply({ xtype: 'combobox', store: store, valueField: 'id', displayField: 'nomCompte',
        queryMode: 'remote', minChars: 2, anchor: '100%', emptyText: 'Tapez 2 lettres…',
        listConfig: { getInnerTpl: function () { return '{nomCompte} <span style="color:#999">{numeroClient}</span>'; } } }, cfg || {});
};

/* ============================ Panneau principal ============================ */
Usp.recouvrement.panel = function () {
    return {
        xtype: 'tabpanel', title: 'Suivi Relance et Recouvrements', listeners: Usp.tabListeners,
        items: [Usp.recouvrement.fichesPanel(), Usp.recouvrement.assistantPanel(),
                Usp.recouvrement.campagnesPanel(), Usp.recouvrement.modelesPanel(),
                Usp.recouvrement.historiquePanel(), Usp.recouvrement.importPanel(),
                Usp.recouvrement.referentielsPanel()]
    };
};

Usp.recouvrement.TYPES_MODELE = [
    ['RELANCE_PREVENTIVE', 'Relance préventive'], ['FACTURE_ECHUE', 'Facture échue'],
    ['IMPAYE', 'Impayé'], ['MISE_EN_DEMEURE', 'Mise en demeure'], ['DIVERS', 'Divers']
];

/* ---------------------------- Clients & encours ---------------------------- */
Usp.recouvrement.fichesPanel = function () {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'clientId', 'nomCompte', 'numeroClient', 'agence', 'segmentCommercial',
                 'profilPaiement', 'responsable', 'statut', 'canalPrefere',
                 'encoursInitial', 'totalFactures', 'totalPaiements', 'solde'],
        autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/recouvrement/fiches',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } }
    });
    var recharger = function () { store.load(); };

    return {
        xtype: 'grid', title: '💼 Clients & encours', store: store,
        columns: [
            { text: 'N° client', dataIndex: 'numeroClient', width: 100 },
            { text: 'Client', dataIndex: 'nomCompte', flex: 1 },
            { text: 'Agence', dataIndex: 'agence', width: 110 },
            { text: 'Segment', dataIndex: 'segmentCommercial', width: 110 },
            { text: 'Profil paiement', dataIndex: 'profilPaiement', width: 120 },
            { text: 'Responsable', dataIndex: 'responsable', width: 120 },
            { text: 'Statut', dataIndex: 'statut', width: 110 },
            { text: 'Encours init.', dataIndex: 'encoursInitial', width: 110, align: 'right', renderer: Usp.recouvrement.money },
            { text: 'Réglé', dataIndex: 'totalPaiements', width: 110, align: 'right', renderer: Usp.recouvrement.money },
            { text: 'Solde', dataIndex: 'solde', width: 120, align: 'right',
              renderer: function (v) {
                  var n = parseFloat(v || 0);
                  return '<span style="color:' + (n > 0 ? '#c62828' : '#2e7d32') + ';font-weight:bold">'
                      + Usp.recouvrement.money(v) + '</span>';
              } },
            { text: 'Actions', width: 320, sortable: false, menuDisabled: true, dataIndex: 'id',
              renderer: function () {
                  return '<span class="rec-mouv" title="Créances / paiements" style="cursor:pointer;color:#1976d2;margin-right:10px">📂 Créances</span>'
                      + '<span class="rec-send" title="Envoyer une relance" style="cursor:pointer;color:#2e7d32;margin-right:10px">📨 Relancer</span>'
                      + '<span class="rec-edit" title="Modifier la fiche" style="cursor:pointer">✏️ Fiche</span>';
              } }
        ],
        tbar: [
            { xtype: 'textfield', emptyText: 'Rechercher client…', width: 220,
              listeners: { change: function (f, v) { store.getProxy().extraParams = { q: v }; store.load(); }, buffer: 400 } },
            '->',
            Usp.permBtn('recouvrement', 'CREER', { text: '➕ Nouvelle fiche', handler: function () { Usp.recouvrement.ficheForm(store, null); } }),
            { text: '🔄 Rafraîchir', handler: recharger }
        ].concat(Usp.export.boutons('Recouvrement - encours')),
        listeners: {
            cellclick: function (g, td, ci, rec, tr, ri, e) {
                if (e.getTarget('.rec-mouv')) { Usp.recouvrement.mouvementsWindow(rec, store); }
                else if (e.getTarget('.rec-send')) {
                    if (!Usp.can('recouvrement', 'ENVOYER')) { Usp.refusPermission(); return; }
                    Usp.recouvrement.relanceForm(rec);
                }
                else if (e.getTarget('.rec-edit')) { Usp.recouvrement.ficheForm(store, rec); }
            },
            itemdblclick: function (g, rec) { Usp.recouvrement.mouvementsWindow(rec, store); }
        }
    };
};

/* Formulaire fiche recouvrement. */
Usp.recouvrement.ficheForm = function (store, rec) {
    var items = [];
    if (rec) {
        items.push({ xtype: 'displayfield', fieldLabel: 'Client', value: rec.get('nomCompte') + ' (' + (rec.get('numeroClient') || '') + ')' });
        items.push({ xtype: 'hiddenfield', name: 'clientId', value: rec.get('clientId') });
    } else {
        items.push(Usp.recouvrement.clientCombo({ name: 'clientId', fieldLabel: 'Client', allowBlank: false }));
    }
    items.push(
        Usp.recouvrement.refCombo('SEGMENT_COMMERCIAL', { name: 'segmentCommercial', fieldLabel: 'Segment commercial' }),
        Usp.recouvrement.refCombo('PROFIL_PAIEMENT', { name: 'profilPaiement', fieldLabel: 'Profil de paiement' }),
        Usp.recouvrement.refCombo('STATUT_RECOUVREMENT', { name: 'statut', fieldLabel: 'Statut de recouvrement' }),
        { xtype: 'textfield', name: 'responsable', fieldLabel: 'Responsable recouvrement' },
        { xtype: 'combobox', name: 'canalPrefere', fieldLabel: 'Canal préféré', queryMode: 'local',
          editable: false, store: Usp.recouvrement.CANAUX, anchor: '100%' },
        { xtype: 'numberfield', name: 'encoursInitial', fieldLabel: 'Encours initial', value: 0, hideTrigger: true },
        { xtype: 'datefield', name: 'dateSituation', fieldLabel: 'Date de situation', format: 'd/m/Y', submitFormat: 'Y-m-d', editable: false },
        { xtype: 'textareafield', name: 'observations', fieldLabel: 'Observations', height: 60 }
    );
    var win = Ext.create('Ext.window.Window', {
        title: rec ? 'Fiche recouvrement — ' + rec.get('nomCompte') : 'Nouvelle fiche recouvrement',
        width: 560, modal: true, bodyPadding: 12, autoScroll: true,
        items: [{ xtype: 'form', border: false, defaults: { anchor: '100%', labelWidth: 160 }, items: items }],
        buttons: [{ text: 'Enregistrer', formBind: true, handler: function (b) {
            var form = b.up('window').down('form').getForm();
            if (!form.isValid()) { return; }
            var vals = form.getValues();
            Usp.ajax({
                url: rec ? '/recouvrement/fiches/' + rec.get('id') : '/recouvrement/fiches',
                method: rec ? 'PUT' : 'POST', jsonData: vals,
                success: function () { win.close(); store.load(); Usp.toastEnregistre('Fiche', !!rec); },
                failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); }
            });
        } }]
    });
    win.show();
    if (rec) {
        Usp.ajax({ url: '/recouvrement/fiches/' + rec.get('id'), method: 'GET', success: function (resp) {
            win.down('form').getForm().setValues(Ext.decode(resp.responseText));
        } });
    }
};

/* ---------------- Fenêtre des mouvements d'un client ---------------- */
Usp.recouvrement.mouvementsWindow = function (rec, ficheStore) {
    var clientId = rec.get('clientId');
    var base = '/recouvrement/clients/' + clientId;

    var jsonStore = function (suffixe, fields) {
        return Ext.create('Ext.data.Store', { fields: fields, autoLoad: true,
            proxy: { type: 'ajax', url: Usp.apiBase + base + suffixe,
                headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } } });
    };
    var creances = jsonStore('/creances', ['id', 'type', 'numero', 'dateEmission', 'dateEcheance', 'montant', 'statut', 'notes']);
    var paiements = jsonStore('/paiements', ['id', 'datePaiement', 'montant', 'mode', 'reference']);
    var promesses = jsonStore('/promesses', ['id', 'datePromesse', 'montant', 'statut', 'notes']);

    var entete = Ext.create('Ext.Component', { height: 30, html: '' });
    var rafraichirSituation = function () {
        Usp.ajax({ url: base + '/situation', method: 'GET', success: function (resp) {
            var s = Ext.decode(resp.responseText) || {};
            entete.update('<div style="padding:4px 8px">Encours initial : <b>' + Usp.recouvrement.money(s.encoursInitial)
                + '</b> &nbsp;|&nbsp; Factures : <b>' + Usp.recouvrement.money(s.totalFactures)
                + '</b> &nbsp;|&nbsp; Avoirs : <b>' + Usp.recouvrement.money(s.totalAvoirs)
                + '</b> &nbsp;|&nbsp; Réglé : <b>' + Usp.recouvrement.money(s.totalPaiements)
                + '</b> &nbsp;|&nbsp; <span style="font-size:14px">Solde : <b style="color:#c62828">'
                + Usp.recouvrement.money(s.solde) + '</b></span></div>');
        } });
    };
    var toutRafraichir = function () {
        creances.load(); paiements.load(); promesses.load(); rafraichirSituation();
        if (ficheStore) { ficheStore.load(); }
    };

    var fmtDate = function (v) { return v ? String(v).substring(0, 10) : ''; };
    var supprBtn = function (cls) {
        return '<span class="' + cls + '" title="Supprimer" style="cursor:pointer;color:#c62828">🗑️</span>';
    };

    var win = Ext.create('Ext.window.Window', {
        title: 'Créances — ' + rec.get('nomCompte'), width: 820, modal: true,
        height: Math.min(620, Ext.getBody().getViewSize().height - 60), layout: 'border',
        items: [
            { region: 'north', xtype: 'container', items: [entete], style: 'background:#f4f6f8;border-bottom:1px solid #ddd' },
            { region: 'center', xtype: 'tabpanel', items: [
                { title: '🧾 Factures / Avoirs', layout: 'fit', items: [{
                    xtype: 'grid', store: creances,
                    columns: [
                        { text: 'Type', dataIndex: 'type', width: 80 },
                        { text: 'N°', dataIndex: 'numero', width: 110 },
                        { text: 'Émission', dataIndex: 'dateEmission', width: 95, renderer: fmtDate },
                        { text: 'Échéance', dataIndex: 'dateEcheance', width: 95, renderer: fmtDate },
                        { text: 'Montant', dataIndex: 'montant', width: 120, align: 'right', renderer: Usp.recouvrement.money },
                        { text: 'Statut', dataIndex: 'statut', width: 100 },
                        { text: '', width: 40, align: 'center', sortable: false, menuDisabled: true, dataIndex: 'id',
                          renderer: function () { return supprBtn('cre-del'); } }
                    ],
                    tbar: [Usp.permBtn('recouvrement', 'CREER', { text: '➕ Facture / Avoir',
                        handler: function () { Usp.recouvrement.creanceForm(base, creances, toutRafraichir); } })],
                    listeners: { cellclick: function (g, td, ci, r, tr, ri, e) {
                        if (e.getTarget('.cre-del')) { Usp.recouvrement.suppr(base + '/creances/' + r.get('id'), toutRafraichir); } } }
                }] },
                { title: '💵 Règlements', layout: 'fit', items: [{
                    xtype: 'grid', store: paiements,
                    columns: [
                        { text: 'Date', dataIndex: 'datePaiement', width: 100, renderer: fmtDate },
                        { text: 'Montant', dataIndex: 'montant', width: 120, align: 'right', renderer: Usp.recouvrement.money },
                        { text: 'Mode', dataIndex: 'mode', width: 120 },
                        { text: 'Référence', dataIndex: 'reference', flex: 1 },
                        { text: '', width: 40, align: 'center', sortable: false, menuDisabled: true, dataIndex: 'id',
                          renderer: function () { return supprBtn('pai-del'); } }
                    ],
                    tbar: [Usp.permBtn('recouvrement', 'CREER', { text: '➕ Règlement',
                        handler: function () { Usp.recouvrement.paiementForm(base, toutRafraichir); } })],
                    listeners: { cellclick: function (g, td, ci, r, tr, ri, e) {
                        if (e.getTarget('.pai-del')) { Usp.recouvrement.suppr(base + '/paiements/' + r.get('id'), toutRafraichir); } } }
                }] },
                { title: '🤝 Promesses', layout: 'fit', items: [{
                    xtype: 'grid', store: promesses,
                    columns: [
                        { text: 'Date promesse', dataIndex: 'datePromesse', width: 120, renderer: fmtDate },
                        { text: 'Montant', dataIndex: 'montant', width: 120, align: 'right', renderer: Usp.recouvrement.money },
                        { text: 'Statut', dataIndex: 'statut', width: 120 },
                        { text: 'Notes', dataIndex: 'notes', flex: 1 },
                        { text: '', width: 40, align: 'center', sortable: false, menuDisabled: true, dataIndex: 'id',
                          renderer: function () { return supprBtn('pro-del'); } }
                    ],
                    tbar: [Usp.permBtn('recouvrement', 'CREER', { text: '➕ Promesse',
                        handler: function () { Usp.recouvrement.promesseForm(base, toutRafraichir); } })],
                    listeners: { cellclick: function (g, td, ci, r, tr, ri, e) {
                        if (e.getTarget('.pro-del')) { Usp.recouvrement.suppr(base + '/promesses/' + r.get('id'), toutRafraichir); } } }
                }] }
            ] }
        ]
    });
    win.show();
    rafraichirSituation();
};

Usp.recouvrement.suppr = function (url, cb) {
    Ext.Msg.confirm('Supprimer', 'Confirmer la suppression ?', function (b) {
        if (b !== 'yes') { return; }
        Usp.ajax({ url: url, method: 'DELETE', success: cb,
            failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
    });
};

Usp.recouvrement.creanceForm = function (base, store, cb) {
    var win = Ext.create('Ext.window.Window', {
        title: 'Facture / Avoir', width: 480, modal: true, bodyPadding: 12,
        items: [{ xtype: 'form', border: false, defaults: { anchor: '100%', labelWidth: 130 }, items: [
            { xtype: 'combobox', name: 'type', fieldLabel: 'Type', value: 'FACTURE', queryMode: 'local',
              editable: false, store: [['FACTURE', 'Facture'], ['AVOIR', 'Avoir']] },
            { xtype: 'textfield', name: 'numero', fieldLabel: 'N° pièce' },
            { xtype: 'datefield', name: 'dateEmission', fieldLabel: 'Date émission', format: 'd/m/Y', submitFormat: 'Y-m-d', editable: false },
            { xtype: 'datefield', name: 'dateEcheance', fieldLabel: 'Date échéance', format: 'd/m/Y', submitFormat: 'Y-m-d', editable: false },
            { xtype: 'numberfield', name: 'montant', fieldLabel: 'Montant', allowBlank: false, hideTrigger: true },
            { xtype: 'textfield', name: 'statut', fieldLabel: 'Statut' },
            { xtype: 'textfield', name: 'notes', fieldLabel: 'Notes' }
        ] }],
        buttons: [{ text: 'Enregistrer', formBind: true, handler: function (b) {
            var f = b.up('window').down('form').getForm();
            if (!f.isValid()) { return; }
            Usp.ajax({ url: base + '/creances', method: 'POST', jsonData: f.getValues(),
                success: function () { win.close(); cb(); },
                failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
        } }]
    });
    win.show();
};

Usp.recouvrement.paiementForm = function (base, cb) {
    var win = Ext.create('Ext.window.Window', {
        title: 'Règlement', width: 460, modal: true, bodyPadding: 12,
        items: [{ xtype: 'form', border: false, defaults: { anchor: '100%', labelWidth: 130 }, items: [
            { xtype: 'datefield', name: 'datePaiement', fieldLabel: 'Date', format: 'd/m/Y', submitFormat: 'Y-m-d', editable: false, value: new Date() },
            { xtype: 'numberfield', name: 'montant', fieldLabel: 'Montant', allowBlank: false, hideTrigger: true },
            { xtype: 'textfield', name: 'mode', fieldLabel: 'Mode (espèces, virement…)' },
            { xtype: 'textfield', name: 'reference', fieldLabel: 'Référence' }
        ] }],
        buttons: [{ text: 'Enregistrer', formBind: true, handler: function (b) {
            var f = b.up('window').down('form').getForm();
            if (!f.isValid()) { return; }
            Usp.ajax({ url: base + '/paiements', method: 'POST', jsonData: f.getValues(),
                success: function () { win.close(); cb(); },
                failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
        } }]
    });
    win.show();
};

Usp.recouvrement.promesseForm = function (base, cb) {
    var win = Ext.create('Ext.window.Window', {
        title: 'Promesse de paiement', width: 460, modal: true, bodyPadding: 12,
        items: [{ xtype: 'form', border: false, defaults: { anchor: '100%', labelWidth: 130 }, items: [
            { xtype: 'datefield', name: 'datePromesse', fieldLabel: 'Date promesse', format: 'd/m/Y', submitFormat: 'Y-m-d', editable: false },
            { xtype: 'numberfield', name: 'montant', fieldLabel: 'Montant', allowBlank: false, hideTrigger: true },
            { xtype: 'combobox', name: 'statut', fieldLabel: 'Statut', value: 'EN_ATTENTE', queryMode: 'local', editable: false,
              store: [['EN_ATTENTE', 'En attente'], ['TENUE', 'Tenue'], ['NON_TENUE', 'Non tenue']] },
            { xtype: 'textfield', name: 'notes', fieldLabel: 'Notes' }
        ] }],
        buttons: [{ text: 'Enregistrer', formBind: true, handler: function (b) {
            var f = b.up('window').down('form').getForm();
            if (!f.isValid()) { return; }
            Usp.ajax({ url: base + '/promesses', method: 'POST', jsonData: f.getValues(),
                success: function () { win.close(); cb(); },
                failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
        } }]
    });
    win.show();
};

/* ---------------------------- Référentiels ---------------------------- */
Usp.recouvrement.refGrid = function (type, titre) {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'code', 'libelle', 'actif'], autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/recouvrement/referentiels/' + type,
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } } });
    var form = function (rec) {
        var win = Ext.create('Ext.window.Window', {
            title: (rec ? 'Modifier' : 'Ajouter') + ' — ' + titre, width: 420, modal: true, bodyPadding: 12,
            items: [{ xtype: 'form', border: false, defaults: { anchor: '100%' }, items: [
                { xtype: 'textfield', name: 'code', fieldLabel: 'Code', emptyText: 'Laisser vide = généré' },
                { xtype: 'textfield', name: 'libelle', fieldLabel: 'Libellé', allowBlank: false }
            ] }],
            buttons: [{ text: 'Enregistrer', formBind: true, handler: function (b) {
                var f = b.up('window').down('form').getForm();
                if (!f.isValid()) { return; }
                Usp.ajax({ url: '/recouvrement/referentiels/' + type + (rec ? '/' + rec.get('id') : ''),
                    method: rec ? 'PUT' : 'POST', jsonData: f.getValues(),
                    success: function () { win.close(); store.load(); },
                    failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
            } }]
        });
        win.show();
        if (rec) { win.down('form').getForm().setValues(rec.getData()); }
    };
    return {
        xtype: 'grid', title: titre, store: store, flex: 1,
        columns: [
            { text: 'Code', dataIndex: 'code', width: 160 },
            { text: 'Libellé', dataIndex: 'libelle', flex: 1 }
        ],
        tbar: [
            Usp.permBtn('recouvrement', 'GERER_REFERENTIELS', { text: '➕ Ajouter', handler: function () { form(null); } }),
            Usp.permBtn('recouvrement', 'GERER_REFERENTIELS', { text: '✏️ Modifier', handler: function (b) {
                var rec = b.up('grid').getSelectionModel().getSelection()[0];
                if (!rec) { Ext.Msg.alert('Info', 'Sélectionnez une ligne.'); return; }
                form(rec);
            } }),
            '->',
            Usp.permBtn('recouvrement', 'IMPORTER', { text: '📥 Importer (CSV id,code,nom)',
                handler: function () { Usp.recouvrement.importRef(type, store); } }),
            { text: '🔄', tooltip: 'Rafraîchir', handler: function () { store.load(); } }
        ]
    };
};

Usp.recouvrement.importRef = function (type, store) {
    var win = Ext.create('Ext.window.Window', {
        title: 'Importer — ' + type, width: 520, modal: true, bodyPadding: 12,
        items: [{ xtype: 'form', border: false, defaults: { anchor: '100%' }, items: [
            { xtype: 'displayfield', value: '<span style="color:#888">Une valeur par ligne : ' +
                '<b>id,code,nom</b> ou <b>code,nom</b> ou <b>nom</b>. En-tête ignoré.</span>' },
            { xtype: 'textareafield', name: 'contenu', height: 170, emptyText: 'DIAMOND,Diamond\nGOLD,Gold' },
            { xtype: 'filefield', fieldLabel: 'ou fichier .csv', msgTarget: 'side',
              listeners: { change: function (f) {
                  var file = f.fileInputEl.dom.files[0]; if (!file) { return; }
                  var reader = new FileReader();
                  reader.onload = function (e) { f.up('form').down('[name=contenu]').setValue(e.target.result); };
                  reader.readAsText(file);
              } } }
        ] }],
        buttons: [{ text: 'Importer', handler: function (b) {
            var contenu = b.up('window').down('[name=contenu]').getValue();
            if (!contenu || !contenu.trim()) { Ext.Msg.alert('Info', 'Aucune donnée.'); return; }
            Usp.ajax({ url: '/recouvrement/referentiels/' + type + '/import', method: 'POST', jsonData: { contenu: contenu },
                success: function (resp) { win.close(); store.load();
                    Usp.toast(((Ext.decode(resp.responseText) || {}).crees || 0) + ' valeur(s) importée(s).'); },
                failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
        } }]
    });
    win.show();
};

Usp.recouvrement.referentielsPanel = function () {
    return {
        title: '⚙️ Référentiels', xtype: 'panel', layout: 'fit', bodyPadding: 6,
        items: [{ xtype: 'tabpanel', items: [
            Usp.recouvrement.refGrid('SEGMENT_COMMERCIAL', 'Segments commerciaux'),
            Usp.recouvrement.refGrid('PROFIL_PAIEMENT', 'Profils de paiement'),
            Usp.recouvrement.refGrid('STATUT_RECOUVREMENT', 'Statuts de recouvrement')
        ] }]
    };
};

/* ---------------------------- Envoyer une relance ---------------------------- */
Usp.recouvrement.relanceForm = function (rec) {
    var modeleStore = Ext.create('Ext.data.Store', {
        fields: ['id', 'nom', 'type', 'canal'], autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/recouvrement/modeles',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } } });
    var win = Ext.create('Ext.window.Window', {
        title: 'Relancer — ' + rec.get('nomCompte'), width: 480, modal: true, bodyPadding: 12,
        items: [{ xtype: 'form', border: false, defaults: { anchor: '100%', labelWidth: 120 }, items: [
            { xtype: 'displayfield', fieldLabel: 'Solde', value: Usp.recouvrement.money(rec.get('solde')) },
            { xtype: 'combobox', name: 'modeleId', fieldLabel: 'Modèle', allowBlank: false, store: modeleStore,
              valueField: 'id', displayField: 'nom', queryMode: 'local', editable: false, emptyText: 'Choisir un modèle…' },
            { xtype: 'combobox', name: 'canal', fieldLabel: 'Canal', queryMode: 'local', editable: false,
              store: Usp.recouvrement.CANAUX, value: rec.get('canalPrefere') || 'WHATSAPP' }
        ] }],
        buttons: [{ text: '📨 Envoyer', formBind: true, handler: function (b) {
            var f = b.up('window').down('form').getForm();
            if (!f.isValid()) { return; }
            var v = f.getValues();
            v.clientId = rec.get('clientId');
            Usp.ajax({ url: '/recouvrement/envois', method: 'POST', jsonData: v,
                success: function (resp) {
                    var e = Ext.decode(resp.responseText) || {};
                    win.close();
                    if (e.statut === 'ENVOYE') { Usp.toast('Relance envoyée (' + e.canal + ').'); }
                    else { Ext.Msg.alert('Envoi en échec', e.erreur || 'Échec de l\'envoi.'); }
                },
                failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
        } }]
    });
    win.show();
};

/* ---------------------------- Assistant de relance ---------------------------- */
Usp.recouvrement.MOTIFS = {
    RELANCE_PREVENTIVE: 'Relance préventive', FACTURE_ECHUE: 'Facture échue',
    DEUXIEME_RELANCE: 'Deuxième relance', PROMESSE_NON_TENUE: 'Promesse non tenue',
    PAIEMENT_PARTIEL: 'Paiement partiel', CLIENT_CRITIQUE: 'Client critique'
};
Usp.recouvrement.assistantPanel = function () {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'clientId', 'nomCompte', 'motif', 'priorite', 'joursRetard', 'montant',
                 'canalRecommande', 'modeleId', 'modeleNom'],
        autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/recouvrement/propositions',
            extraParams: { statut: 'PROPOSEE' },
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } } });
    var prioColor = { CRITIQUE: '#c62828', HAUTE: '#ef6c00', NORMALE: '#555' };
    return {
        xtype: 'grid', title: '🤖 Assistant', store: store,
        columns: [
            { text: 'Client', dataIndex: 'nomCompte', flex: 1 },
            { text: 'Motif', dataIndex: 'motif', width: 160, renderer: function (v) { return Usp.recouvrement.MOTIFS[v] || v; } },
            { text: 'Priorité', dataIndex: 'priorite', width: 90, renderer: function (v) {
                return '<span style="color:' + (prioColor[v] || '#555') + ';font-weight:bold">' + (v || '') + '</span>'; } },
            { text: 'Retard (j)', dataIndex: 'joursRetard', width: 80, align: 'right' },
            { text: 'Montant dû', dataIndex: 'montant', width: 120, align: 'right', renderer: Usp.recouvrement.money },
            { text: 'Canal', dataIndex: 'canalRecommande', width: 90 },
            { text: 'Modèle conseillé', dataIndex: 'modeleNom', width: 160 },
            { text: 'Actions', width: 200, sortable: false, menuDisabled: true, dataIndex: 'id',
              renderer: function () {
                  return '<span class="prop-ok" title="Valider et envoyer" style="cursor:pointer;color:#2e7d32;margin-right:12px">✅ Valider</span>'
                      + '<span class="prop-no" title="Rejeter" style="cursor:pointer;color:#c62828">✖ Rejeter</span>';
              } }
        ],
        tbar: [
            { xtype: 'combobox', fieldLabel: 'Statut', labelWidth: 45, width: 200, value: 'PROPOSEE',
              queryMode: 'local', editable: false, store: [['PROPOSEE', 'En attente'], ['VALIDEE', 'Validées'], ['REJETEE', 'Rejetées']],
              listeners: { select: function (c) { store.getProxy().extraParams = { statut: c.getValue() }; store.load(); } } },
            '->',
            Usp.permBtn('recouvrement', 'CREER', { text: '🔍 Analyser maintenant', handler: function () {
                Usp.ajax({ url: '/recouvrement/propositions/generer', method: 'POST',
                    success: function (resp) { store.load();
                        Usp.toast(((Ext.decode(resp.responseText) || {}).crees || 0) + ' proposition(s) générée(s).'); },
                    failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
            } }),
            { text: '🔄 Rafraîchir', handler: function () { store.load(); } }
        ],
        listeners: {
            cellclick: function (g, td, ci, rec, tr, ri, e) {
                if (e.getTarget('.prop-ok')) {
                    if (!Usp.can('recouvrement', 'ENVOYER')) { Usp.refusPermission(); return; }
                    Usp.recouvrement.validerProposition(rec, store);
                } else if (e.getTarget('.prop-no')) {
                    Usp.ajax({ url: '/recouvrement/propositions/' + rec.get('id') + '/rejeter', method: 'POST',
                        success: function () { store.load(); },
                        failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
                }
            }
        }
    };
};

Usp.recouvrement.validerProposition = function (rec, store) {
    var modeleStore = Ext.create('Ext.data.Store', {
        fields: ['id', 'nom'], autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/recouvrement/modeles',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } } });
    var win = Ext.create('Ext.window.Window', {
        title: 'Valider la relance — ' + rec.get('nomCompte'), width: 480, modal: true, bodyPadding: 12,
        items: [{ xtype: 'form', border: false, defaults: { anchor: '100%', labelWidth: 120 }, items: [
            { xtype: 'displayfield', fieldLabel: 'Motif', value: Usp.recouvrement.MOTIFS[rec.get('motif')] || rec.get('motif') },
            { xtype: 'combobox', name: 'modeleId', fieldLabel: 'Modèle', allowBlank: false, store: modeleStore,
              valueField: 'id', displayField: 'nom', queryMode: 'local', editable: false,
              value: rec.get('modeleId') || null, emptyText: 'Choisir un modèle…' },
            { xtype: 'combobox', name: 'canal', fieldLabel: 'Canal', queryMode: 'local', editable: false,
              store: Usp.recouvrement.CANAUX, value: rec.get('canalRecommande') || 'WHATSAPP' }
        ] }],
        buttons: [{ text: '✅ Valider et envoyer', formBind: true, handler: function (b) {
            var f = b.up('window').down('form').getForm();
            if (!f.isValid()) { return; }
            Usp.ajax({ url: '/recouvrement/propositions/' + rec.get('id') + '/valider', method: 'POST', jsonData: f.getValues(),
                success: function () { win.close(); store.load(); Usp.toast('Relance envoyée.'); },
                failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
        } }]
    });
    win.show();
};

/* ---------------------------- Campagnes ciblées ---------------------------- */
Usp.recouvrement.campagnesPanel = function () {
    var modeleStore = Ext.create('Ext.data.Store', {
        fields: ['id', 'nom'], autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/recouvrement/modeles',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } } });
    var filtre = function (form) {
        var v = form.getValues();
        return { agence: v.agence, responsable: v.responsable, segment: v.segment, profil: v.profil,
                 montantMin: v.montantMin, joursMin: v.joursMin };
    };
    return {
        title: '📣 Campagnes', xtype: 'form', bodyPadding: 14, autoScroll: true,
        defaults: { anchor: '60%', labelWidth: 170 },
        items: [
            { xtype: 'displayfield', value: '<b>Cibler les clients à relancer</b> selon les critères, puis envoyer un modèle.' },
            Usp.referentielCombo('AGENCE', { name: 'agence', fieldLabel: 'Agence' }),
            { xtype: 'textfield', name: 'responsable', fieldLabel: 'Responsable recouvrement' },
            Usp.recouvrement.refCombo('SEGMENT_COMMERCIAL', { name: 'segment', fieldLabel: 'Segment commercial' }),
            Usp.recouvrement.refCombo('PROFIL_PAIEMENT', { name: 'profil', fieldLabel: 'Profil de paiement' }),
            { xtype: 'numberfield', name: 'montantMin', fieldLabel: 'Montant dû minimum', hideTrigger: true, minValue: 0 },
            { xtype: 'numberfield', name: 'joursMin', fieldLabel: 'Ancienneté min. (jours retard)', hideTrigger: true, minValue: 0 },
            { xtype: 'combobox', name: 'modeleId', fieldLabel: 'Modèle de relance', store: modeleStore,
              valueField: 'id', displayField: 'nom', queryMode: 'local', editable: false, emptyText: 'Choisir…' },
            { xtype: 'combobox', name: 'canal', fieldLabel: 'Canal', queryMode: 'local', editable: false,
              store: Usp.recouvrement.CANAUX, value: 'WHATSAPP' },
            { xtype: 'component', itemId: 'apercu', margin: '6 0 0 0', html: '' }
        ],
        bbar: ['->',
            { text: '👁️ Aperçu', handler: function (b) {
                var form = b.up('form').getForm();
                Usp.ajax({ url: '/recouvrement/campagnes/preview', method: 'POST', jsonData: filtre(form),
                    success: function (resp) {
                        var r = Ext.decode(resp.responseText) || {};
                        b.up('form').down('#apercu').update('<span style="color:#1976d2"><b>' + (r.count || 0)
                            + '</b> client(s) ciblé(s).</span>');
                    },
                    failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
            } },
            Usp.permBtn('recouvrement', 'ENVOYER', { text: '📨 Envoyer la campagne', handler: function (b) {
                var form = b.up('form').getForm();
                var data = filtre(form);
                data.modeleId = form.findField('modeleId').getValue();
                data.canal = form.findField('canal').getValue();
                if (!data.modeleId) { Ext.Msg.alert('Modèle', 'Choisissez un modèle de relance.'); return; }
                Ext.Msg.confirm('Envoyer', 'Lancer la campagne de relance vers les clients ciblés ?', function (btn) {
                    if (btn !== 'yes') { return; }
                    Usp.ajax({ url: '/recouvrement/campagnes/envoyer', method: 'POST', jsonData: data,
                        success: function (resp) {
                            var r = Ext.decode(resp.responseText) || {};
                            Ext.Msg.alert('Campagne terminée', (r.envoyes || 0) + ' envoi(s) réussi(s), '
                                + (r.echecs || 0) + ' échec(s) sur ' + (r.cibles || 0) + ' cible(s).');
                        },
                        failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
                });
            } })
        ]
    };
};

/* ---------------------------- Modèles de relance ---------------------------- */
Usp.recouvrement.modelesPanel = function () {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'code', 'nom', 'type', 'canal', 'sujet', 'corps', 'nomModeleWhatsapp', 'paramsCorps', 'actif'],
        autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/recouvrement/modeles',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } } });
    var typeLib = function (v) {
        var m = Ext.Array.findBy(Usp.recouvrement.TYPES_MODELE, function (a) { return a[0] === v; });
        return m ? m[1] : (v || '');
    };
    return {
        xtype: 'grid', title: '✉️ Modèles', store: store,
        columns: [
            { text: 'Code', dataIndex: 'code', width: 130 },
            { text: 'Nom', dataIndex: 'nom', flex: 1 },
            { text: 'Type', dataIndex: 'type', width: 150, renderer: typeLib },
            { text: 'Canal', dataIndex: 'canal', width: 90 },
            { text: 'Actif', dataIndex: 'actif', width: 60, align: 'center', renderer: function (v) { return v ? '✔' : '✖'; } }
        ],
        tbar: [
            Usp.permBtn('recouvrement', 'CREER', { text: '➕ Nouveau modèle', handler: function () { Usp.recouvrement.modeleForm(store, null); } }),
            { text: '🔄 Rafraîchir', handler: function () { store.load(); } }
        ],
        listeners: { itemdblclick: function (g, rec) { Usp.recouvrement.modeleForm(store, rec); } }
    };
};

Usp.recouvrement.modeleForm = function (store, rec) {
    var win = Ext.create('Ext.window.Window', {
        title: rec ? 'Modifier le modèle' : 'Nouveau modèle de relance', width: 640, modal: true, bodyPadding: 12, autoScroll: true,
        items: [{ xtype: 'form', border: false, defaults: { anchor: '100%', labelWidth: 150 }, items: [
            { xtype: 'textfield', name: 'nom', fieldLabel: 'Nom', allowBlank: false },
            { xtype: 'combobox', name: 'type', fieldLabel: 'Type', queryMode: 'local', editable: false,
              store: Usp.recouvrement.TYPES_MODELE, value: 'DIVERS' },
            { xtype: 'combobox', name: 'canal', fieldLabel: 'Canal', queryMode: 'local', editable: false,
              store: [['TOUS', 'Tous'], ['WHATSAPP', 'WhatsApp'], ['EMAIL', 'Email']], value: 'TOUS' },
            { xtype: 'textfield', name: 'sujet', fieldLabel: 'Sujet (Email)' },
            { xtype: 'textareafield', name: 'corps', fieldLabel: 'Corps du message', height: 140, allowBlank: false },
            { xtype: 'textfield', name: 'nomModeleWhatsapp', fieldLabel: 'Nom modèle Meta', emptyText: 'pour le canal WhatsApp API (hors fenêtre 24h)' },
            { xtype: 'textfield', name: 'paramsCorps', fieldLabel: 'Paramètres {{1}},{{2}}', emptyText: 'ex. nom_client,montant_du,date_echeance' },
            { xtype: 'checkbox', name: 'actif', fieldLabel: 'Actif', checked: true },
            { xtype: 'displayfield', value: '<span style="color:#666">Variables : {nom_client}, {nom_societe}, ' +
                '{solde}, {montant_du}, {jours_retard}, {numero_facture}, {date_echeance}.</span>' }
        ] }],
        buttons: [{ text: 'Enregistrer', formBind: true, handler: function (b) {
            var f = b.up('window').down('form').getForm();
            if (!f.isValid()) { return; }
            var v = f.getValues();
            v.actif = f.findField('actif').getValue();
            Usp.ajax({ url: rec ? '/recouvrement/modeles/' + rec.get('id') : '/recouvrement/modeles',
                method: rec ? 'PUT' : 'POST', jsonData: v,
                success: function () { win.close(); store.load(); Usp.toastEnregistre('Modèle', !!rec); },
                failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
        } }]
    });
    win.show();
    if (rec) { win.down('form').getForm().setValues(rec.getData()); }
};

/* ---------------------------- Historique des envois ---------------------------- */
Usp.recouvrement.historiquePanel = function () {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'clientId', 'canal', 'destinataire', 'sujet', 'message', 'statut', 'erreur', 'creePar', 'createdAt'],
        autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/recouvrement/envois',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } } });
    return {
        xtype: 'grid', title: '🗂️ Historique', store: store,
        columns: [
            { text: 'Date', dataIndex: 'createdAt', width: 140, renderer: function (v) { return v ? String(v).replace('T', ' ').substring(0, 16) : ''; } },
            { text: 'Canal', dataIndex: 'canal', width: 90 },
            { text: 'Destinataire', dataIndex: 'destinataire', width: 160 },
            { text: 'Message', dataIndex: 'message', flex: 1, renderer: function (v) { return Ext.String.htmlEncode((v || '').substring(0, 120)); } },
            { text: 'Statut', dataIndex: 'statut', width: 90, renderer: function (v) {
                return '<span style="color:' + (v === 'ENVOYE' ? '#2e7d32' : '#c62828') + ';font-weight:bold">' + (v || '') + '</span>'; } },
            { text: 'Erreur', dataIndex: 'erreur', width: 200 },
            { text: 'Par', dataIndex: 'creePar', width: 110 }
        ],
        tbar: [{ text: '🔄 Rafraîchir', handler: function () { store.load(); } }]
            .concat(Usp.export.boutons('Recouvrement - historique'))
    };
};

/* ---------------------------- Import CSV ---------------------------- */
Usp.recouvrement.importPanel = function () {
    var bloc = function (titre, type, exemple) {
        return {
            xtype: 'fieldset', title: titre, margin: '0 0 10 0', defaults: { anchor: '100%' }, items: [
                { xtype: 'textareafield', itemId: 'ta_' + type, height: 110, emptyText: exemple },
                { xtype: 'fieldcontainer', layout: 'hbox', items: [
                    { xtype: 'filefield', flex: 1, buttonText: 'Fichier .csv…', buttonOnly: false, msgTarget: 'side',
                      listeners: { change: function (f) {
                          var file = f.fileInputEl.dom.files[0]; if (!file) { return; }
                          var reader = new FileReader();
                          reader.onload = function (e) { f.up('fieldset').down('#ta_' + type).setValue(e.target.result); };
                          reader.readAsText(file);
                      } } },
                    Usp.permBtn('recouvrement', 'IMPORTER', { xtype: 'button', text: '📥 Importer', margin: '0 0 0 8',
                      handler: function (b) {
                          var contenu = b.up('fieldset').down('#ta_' + type).getValue();
                          if (!contenu || !contenu.trim()) { Ext.Msg.alert('Info', 'Aucune donnée.'); return; }
                          Usp.ajax({ url: '/recouvrement/import/' + type, method: 'POST', jsonData: { contenu: contenu },
                              success: function (resp) {
                                  var r = Ext.decode(resp.responseText) || {};
                                  Ext.Msg.alert('Import terminé', (r.crees || 0) + ' créé(s), ' + (r.misAJour || 0)
                                      + ' mis à jour, ' + (r.ignores || 0) + ' ignoré(s) (client introuvable).');
                              },
                              failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
                      } })
                ] }
            ]
        };
    };
    return {
        title: '📥 Import', xtype: 'panel', autoScroll: true, bodyPadding: 12,
        items: [
            { xtype: 'displayfield', value: '<span style="color:#666">Séparateur <b>;</b> (point-virgule) ou tabulation. ' +
                '1ʳᵉ ligne = en-tête (noms de colonnes). Rattachement par <b>numero_client</b>.</span>' },
            bloc('Fiches (initialisation)', 'fiches', 'numero_client;encours_initial;segment;profil;responsable;statut\nC001;150000;Diamond;Paiement 30 jours;Awa;Sous surveillance'),
            bloc('Créances (factures / avoirs)', 'creances', 'numero_client;type;numero;date_emission;date_echeance;montant;statut\nC001;FACTURE;FA-2026-001;2026-06-01;2026-07-01;250000;ECHUE'),
            bloc('Règlements', 'paiements', 'numero_client;date_paiement;montant;mode;reference\nC001;2026-06-15;100000;Virement;VIR-123')
        ]
    };
};
