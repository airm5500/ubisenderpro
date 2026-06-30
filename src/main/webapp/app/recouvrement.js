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
        items: [Usp.recouvrement.fichesPanel(), Usp.recouvrement.referentielsPanel()]
    };
};

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
            { text: 'Actions', width: 230, sortable: false, menuDisabled: true, dataIndex: 'id',
              renderer: function () {
                  return '<span class="rec-mouv" title="Créances / paiements" style="cursor:pointer;color:#1976d2;margin-right:10px">📂 Créances</span>'
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
