/*
 * UbiSenderPro - Module DISPONIBILITÉS & RUPTURES (Phase R1 : socle).
 * Événements (disponibilité, retour de rupture, risque, rupture, stock limité)
 * et produits concernés (CRUD + import Excel + sélection catalogue).
 * Dépend de app.js (objet Usp).
 */
Ext.define('Usp.dispo', { singleton: true });

Usp.dispo._stores = [];

Usp.dispo.LIB_TYPE = {
    ANNONCE_DISPONIBILITE: '🟢 Disponibilité', RETOUR_RUPTURE: '🔵 Retour de rupture',
    RISQUE_RUPTURE: '🟠 Risque de rupture', RUPTURE_CONFIRMEE: '🔴 Rupture confirmée',
    STOCK_LIMITE: '🟡 Stock limité'
};
Usp.dispo.COULEUR_STATUT = {
    BROUILLON: '#777', PROGRAMMEE: '#1976d2', ENVOYEE: '#999', ANNULEE: '#8e0000', ARCHIVEE: '#555'
};
Usp.dispo.COULEUR_PROD = {
    DISPONIBLE: '#2e7d32', STOCK_LIMITE: '#f9a825', RISQUE_RUPTURE: '#ef6c00',
    EN_RUPTURE: '#c62828', RETOUR_RUPTURE: '#1976d2', INACTIF: '#999', ARCHIVE: '#555'
};

Usp.dispo.AUDIENCES = [
    ['TOUS_LES_SEGMENTS', 'Tous les segments'], ['DIAMOND', 'Diamond'], ['PLATINIUM', 'Platinium'],
    ['DIAMOND_ET_PLATINIUM', 'Diamond et Platinium'], ['SEGMENTS_SELECTIONNES', 'Segments sélectionnés'],
    ['LISTE_DE_DIFFUSION', 'Liste de diffusion'], ['CLIENTS_ACHETEURS_DU_PRODUIT', 'Clients acheteurs du produit'],
    ['CLIENTS_AYANT_DEMANDE_LE_PRODUIT', 'Clients ayant demandé le produit']
];
Usp.dispo.TYPES = [
    ['ANNONCE_DISPONIBILITE', '🟢 Disponibilité'], ['RETOUR_RUPTURE', '🔵 Retour de rupture'],
    ['RISQUE_RUPTURE', '🟠 Risque de rupture'], ['RUPTURE_CONFIRMEE', '🔴 Rupture confirmée'],
    ['STOCK_LIMITE', '🟡 Stock limité']
];
Usp.dispo.STATUTS_PROD = [
    ['DISPONIBLE', 'Disponible'], ['STOCK_LIMITE', 'Stock limité'], ['RISQUE_RUPTURE', 'Risque de rupture'],
    ['EN_RUPTURE', 'En rupture'], ['RETOUR_RUPTURE', 'Retour de rupture'], ['INACTIF', 'Inactif'], ['ARCHIVE', 'Archivé']
];

Usp.dispo.fdate = function (v) { return v ? String(v).substring(0, 10) : ''; };
Usp.dispo.typeRenderer = function (v) { return Usp.dispo.LIB_TYPE[v] || v || ''; };
Usp.dispo.statutRenderer = function (v) {
    var c = Usp.dispo.COULEUR_STATUT[v] || '#333';
    return '<span style="color:' + c + ';font-weight:bold">' + (v || '') + '</span>';
};
Usp.dispo.prodStatutRenderer = function (v) {
    var c = Usp.dispo.COULEUR_PROD[v] || '#333';
    return '<span style="color:' + c + ';font-weight:bold">' + (v || '') + '</span>';
};

Usp.dispo.reloadAll = function () { Usp.dispo._stores.forEach(function (s) { s.load(); }); };

/* Vue principale : un onglet par catégorie. */
Usp.dispo.panel = function () {
    Usp.dispo._stores = [];
    return {
        xtype: 'tabpanel', title: 'Disponibilités & Ruptures', listeners: Usp.tabListeners,
        items: [
            Usp.dispo.grille({ type: 'ANNONCE_DISPONIBILITE' }, '🟢 Produits disponibles'),
            Usp.dispo.grille({ type: 'RETOUR_RUPTURE' }, '🔵 Retours de rupture'),
            Usp.dispo.grille({ type: 'RISQUE_RUPTURE' }, '🟠 Risques de rupture'),
            Usp.dispo.grille({ type: 'RUPTURE_CONFIRMEE' }, '🔴 Ruptures confirmées'),
            Usp.dispo.grille({ statut: 'PROGRAMMEE' }, '📅 Annonces programmées'),
            Usp.dispo.grille({ historique: true }, '🗂️ Historique')
        ]
    };
};

Usp.dispo.grille = function (filtre, libelleTab) {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'code', 'type', 'titre', 'description', 'dateDebut', 'dateFin', 'agence',
                 'societe', 'audience', 'segmentationId', 'canal', 'modeleId', 'statut', 'responsable'],
        autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/dispo-evenements',
            extraParams: filtre,
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } }
    });
    Usp.dispo._stores.push(store);

    var historique = !!filtre.historique;
    return {
        xtype: 'grid', title: libelleTab, store: store,
        columns: [
            { text: 'Code', dataIndex: 'code', width: 120 },
            { text: 'Type', dataIndex: 'type', width: 150, renderer: Usp.dispo.typeRenderer },
            { text: 'Titre', dataIndex: 'titre', flex: 1 },
            { text: 'Début', dataIndex: 'dateDebut', width: 100, renderer: Usp.dispo.fdate },
            { text: 'Fin', dataIndex: 'dateFin', width: 100, renderer: Usp.dispo.fdate },
            { text: 'Agence', dataIndex: 'agence', width: 110 },
            { text: 'Statut', dataIndex: 'statut', width: 110, renderer: Usp.dispo.statutRenderer },
            { text: 'Actions', width: 230, sortable: false, menuDisabled: true, dataIndex: 'id',
              renderer: function (v, m, rec) {
                  var s = rec.get('statut');
                  var h = '<span class="de-edit" title="Voir / modifier" style="cursor:pointer;margin:0 4px">✏️</span>' +
                          '<span class="de-dup" title="Dupliquer" style="cursor:pointer;margin:0 4px">📑</span>';
                  if (s !== 'PROGRAMMEE' && s !== 'ENVOYEE' && s !== 'ANNULEE' && s !== 'ARCHIVEE') {
                      h += '<span class="de-prog" title="Programmer" style="cursor:pointer;margin:0 4px;color:#1976d2">📅</span>';
                  }
                  if (s !== 'ANNULEE' && s !== 'ARCHIVEE') {
                      h += '<span class="de-cancel" title="Annuler" style="cursor:pointer;margin:0 4px;color:#c62828">⛔</span>';
                  }
                  if (s !== 'ARCHIVEE') {
                      h += '<span class="de-archive" title="Archiver" style="cursor:pointer;margin:0 4px;color:#777">🗄️</span>';
                  }
                  return h;
              } }
        ],
        tbar: historique ? [{ text: '🔄 Rafraîchir', handler: function () { store.load(); } }]
                .concat(Usp.export.boutons('Disponibilités historique'))
            : [
                { text: '➕ Nouvel événement', tooltip: 'Créer un événement disponibilité / rupture',
                  handler: function () { Usp.dispo.evenementForm(store, null, filtre.type); } },
                { text: '🔄 Rafraîchir', handler: function () { store.load(); } }
            ].concat(Usp.export.boutons('Disponibilités ' + (filtre.type || filtre.statut || ''))),
        listeners: {
            itemdblclick: function (g, rec) { Usp.dispo.evenementForm(store, rec); },
            cellclick: function (g, td, ci, rec, tr, ri, e) {
                if (e.getTarget('.de-edit')) { Usp.dispo.evenementForm(store, rec); }
                else if (e.getTarget('.de-dup')) { Usp.dispo.action(rec, 'dupliquer', 'Dupliquer cet événement ?'); }
                else if (e.getTarget('.de-prog')) { Usp.dispo.action(rec, 'programmer', 'Programmer l\'événement « ' + Ext.String.htmlEncode(rec.get('titre')) + ' » ?'); }
                else if (e.getTarget('.de-cancel')) { Usp.dispo.action(rec, 'annuler', 'Annuler l\'événement « ' + Ext.String.htmlEncode(rec.get('titre')) + ' » ?'); }
                else if (e.getTarget('.de-archive')) { Usp.dispo.action(rec, 'archiver', 'Archiver l\'événement « ' + Ext.String.htmlEncode(rec.get('titre')) + ' » ?'); }
            }
        }
    };
};

Usp.dispo.action = function (rec, action, message) {
    Ext.Msg.confirm('Confirmation', message, function (btn) {
        if (btn !== 'yes') { return; }
        Usp.ajax({ url: '/dispo-evenements/' + rec.get('id') + '/' + action, method: 'POST',
            success: function () { Usp.dispo.reloadAll(); Usp.toast('Opération effectuée avec succès.'); },
            failure: function () { Ext.Msg.alert('Erreur', 'Opération impossible.'); } });
    });
};

/* Fiche d'un événement : informations + produits (si existant). */
Usp.dispo.evenementForm = function (store, rec, typeParDefaut) {
    var items = [
        { xtype: 'textfield', name: 'code', fieldLabel: 'Code', allowBlank: false, value: rec ? rec.get('code') : '' },
        { xtype: 'combobox', name: 'type', fieldLabel: 'Type', editable: false, queryMode: 'local', allowBlank: false,
          store: Usp.dispo.TYPES, value: rec ? rec.get('type') : (typeParDefaut || 'ANNONCE_DISPONIBILITE') },
        { xtype: 'textfield', name: 'titre', fieldLabel: 'Titre', allowBlank: false, value: rec ? rec.get('titre') : '' },
        { xtype: 'textarea', name: 'description', fieldLabel: 'Description', height: 50, value: rec ? rec.get('description') : '' },
        { xtype: 'datefield', name: 'dateDebut', fieldLabel: 'Date de début', format: 'd/m/Y', submitFormat: 'Y-m-d\\TH:i:s', editable: false,
          value: rec && rec.get('dateDebut') ? Ext.Date.parse(String(rec.get('dateDebut')).substring(0, 10), 'Y-m-d') : null },
        { xtype: 'datefield', name: 'dateFin', fieldLabel: 'Date de fin', format: 'd/m/Y', submitFormat: 'Y-m-d\\TH:i:s', editable: false,
          value: rec && rec.get('dateFin') ? Ext.Date.parse(String(rec.get('dateFin')).substring(0, 10), 'Y-m-d') : null },
        { xtype: 'textfield', name: 'agence', fieldLabel: 'Agence', value: rec ? rec.get('agence') : '' },
        { xtype: 'textfield', name: 'societe', fieldLabel: 'Société', value: rec ? rec.get('societe') : '' },
        { xtype: 'combobox', name: 'audience', fieldLabel: 'Audience', editable: false, queryMode: 'local',
          store: Usp.dispo.AUDIENCES, value: rec ? rec.get('audience') : 'TOUS_LES_SEGMENTS' },
        { xtype: 'combobox', name: 'canal', fieldLabel: 'Canal d\'envoi', editable: false, queryMode: 'local',
          store: [['WEB', 'WhatsApp Web'], ['API', 'API WhatsApp (officielle)']], value: rec ? (rec.get('canal') || 'WEB') : 'WEB' },
        { xtype: 'textfield', name: 'responsable', fieldLabel: 'Responsable', value: rec ? rec.get('responsable') : '' }
    ];

    var formItems = [{ xtype: 'form', itemId: 'deForm', border: false, bodyPadding: '0 0 8 0',
        defaults: { anchor: '100%', labelWidth: 130 }, items: items }];
    if (rec) {
        formItems.push(Usp.dispo.produitsGrid(rec.get('id')));
    } else {
        formItems.push({ xtype: 'component', margin: '4 0 0 0',
            html: '<span style="color:#888">Enregistrez l\'événement pour pouvoir y ajouter des produits.</span>' });
    }

    var win = Ext.create('Ext.window.Window', {
        title: rec ? 'Événement — ' + Ext.String.htmlEncode(rec.get('titre')) : 'Nouvel événement',
        width: 860, height: rec ? 620 : 360, modal: true,
        layout: { type: 'vbox', align: 'stretch' }, bodyPadding: 12,
        maxHeight: Ext.getBody().getViewSize().height - 20,
        items: formItems,
        buttons: [{ text: 'Enregistrer', handler: function (b) {
            var form = b.up('window').down('#deForm').getForm();
            if (!form.isValid()) { return; }
            var v = form.getValues();
            Usp.ajax({ url: rec ? '/dispo-evenements/' + rec.get('id') : '/dispo-evenements',
                method: rec ? 'PUT' : 'POST', jsonData: v,
                success: function (resp) {
                    win.close();
                    Usp.dispo.reloadAll();
                    Usp.toastEnregistre('Événement « ' + v.titre + ' »', !!rec);
                    if (!rec) {
                        var cree = Ext.decode(resp.responseText) || {};
                        var m = Ext.create(store.model, cree);
                        Usp.dispo.evenementForm(store, m);
                    }
                },
                failure: function (resp) {
                    var msg = 'Enregistrement impossible.';
                    try { msg = Ext.decode(resp.responseText).erreur || msg; } catch (e) {}
                    Ext.Msg.alert('Erreur', msg);
                } });
        } }]
    });
    win.show();
};

/* Sous-grille des produits d'un événement. */
Usp.dispo.produitsGrid = function (evenementId) {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'cip7', 'cip13', 'nomProduit', 'quantiteDisponible', 'seuilRupture', 'couvertureJours',
                 'datePeremption', 'numeroLot', 'agence', 'stockLimite', 'lienReservation', 'statut', 'articleId', 'actif'],
        autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/dispo-evenements/' + evenementId + '/produits',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } }
    });
    return {
        xtype: 'grid', title: 'Produits concernés', flex: 1, minHeight: 200, margin: '10 0 0 0', store: store,
        columns: [
            { text: 'CIP7', dataIndex: 'cip7', width: 90 },
            { text: 'CIP13', dataIndex: 'cip13', width: 120 },
            { text: 'Produit', dataIndex: 'nomProduit', flex: 1 },
            { text: 'Qté dispo', dataIndex: 'quantiteDisponible', width: 80, align: 'right' },
            { text: 'Seuil', dataIndex: 'seuilRupture', width: 70, align: 'right' },
            { text: 'Statut', dataIndex: 'statut', width: 120, renderer: Usp.dispo.prodStatutRenderer },
            { text: 'Catalogue', dataIndex: 'articleId', width: 75, align: 'center',
              renderer: function (v) { return v ? '✅' : '—'; } },
            { text: 'Actions', width: 80, align: 'center', sortable: false, menuDisabled: true, dataIndex: 'id',
              renderer: function () {
                  return '<span class="dp-edit" title="Modifier" style="cursor:pointer;margin:0 4px">✏️</span>' +
                      '<span class="dp-del" title="Retirer" style="cursor:pointer;margin:0 4px;color:#c62828">🗑️</span>';
              } }
        ],
        tbar: [
            { text: '➕ Ajouter un produit', handler: function () { Usp.dispo.produitForm(store, evenementId, null); } },
            { xtype: 'filefield', buttonOnly: true, hideLabel: true, buttonText: '📥 Importer Excel',
              listeners: { change: function (f) { Usp.dispo.importProduits(f, evenementId, store); } } }
        ],
        listeners: {
            itemdblclick: function (g, rec) { Usp.dispo.produitForm(store, evenementId, rec); },
            cellclick: function (g, td, ci, rec, tr, ri, e) {
                if (e.getTarget('.dp-edit')) { Usp.dispo.produitForm(store, evenementId, rec); }
                else if (e.getTarget('.dp-del')) {
                    Ext.Msg.confirm('Retirer', 'Retirer ce produit de l\'événement ?', function (btn) {
                        if (btn !== 'yes') { return; }
                        Usp.ajax({ url: '/dispo-evenements/' + evenementId + '/produits/' + rec.get('id'), method: 'DELETE',
                            success: function () { store.load(); Usp.toast('Produit retiré.'); },
                            failure: function () { Ext.Msg.alert('Erreur', 'Suppression impossible.'); } });
                    });
                }
            }
        }
    };
};

Usp.dispo.produitForm = function (store, evenementId, rec) {
    var articleStore = Ext.create('Ext.data.Store', {
        fields: ['id', 'designation', 'cip', 'codeBarres'], pageSize: 20,
        proxy: { type: 'ajax', url: Usp.apiBase + '/articles', queryParam: 'q',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') },
            reader: { type: 'json', root: 'data', totalProperty: 'total' } }
    });
    var fld = function (n) { return win.down('form').getForm().findField(n); };

    var win = Ext.create('Ext.window.Window', {
        title: rec ? 'Modifier le produit' : 'Ajouter un produit', width: 580, modal: true, bodyPadding: 12, layout: 'fit',
        items: [{ xtype: 'form', border: false, defaults: { anchor: '100%', labelWidth: 180 }, items: [
            { xtype: 'combobox', fieldLabel: 'Produit du catalogue', name: '_recherche',
              store: articleStore, queryMode: 'remote', queryParam: 'q', minChars: 2, hideTrigger: true,
              displayField: 'designation', valueField: 'id', pageSize: 0,
              emptyText: 'Rechercher un article (désignation, CIP)…',
              listConfig: { getInnerTpl: function () { return '{designation} <span style="color:#999">{cip}</span>'; } },
              listeners: { select: function (cb, r) {
                  fld('cip7').setValue(r.get('cip') || '');
                  fld('cip13').setValue(r.get('codeBarres') || '');
                  fld('nomProduit').setValue(r.get('designation') || '');
                  fld('articleId').setValue(r.get('id'));
              } } },
            { xtype: 'displayfield', value: '<span style="color:#888">Choisissez un article du catalogue ' +
              '(recommandé, évite les doublons) <b>ou</b> saisissez manuellement le CIP.</span>' },
            { xtype: 'hidden', name: 'articleId', value: rec ? rec.get('articleId') : null },
            { xtype: 'textfield', name: 'cip7', fieldLabel: 'CIP7', value: rec ? rec.get('cip7') : '' },
            { xtype: 'textfield', name: 'cip13', fieldLabel: 'CIP13', value: rec ? rec.get('cip13') : '' },
            { xtype: 'textfield', name: 'nomProduit', fieldLabel: 'Nom du produit', value: rec ? rec.get('nomProduit') : '' },
            { xtype: 'numberfield', name: 'quantiteDisponible', fieldLabel: 'Quantité disponible', minValue: 0, value: rec ? rec.get('quantiteDisponible') : null },
            { xtype: 'numberfield', name: 'seuilRupture', fieldLabel: 'Seuil de rupture', minValue: 0, value: rec ? rec.get('seuilRupture') : null },
            { xtype: 'numberfield', name: 'couvertureJours', fieldLabel: 'Couverture estimée (jours)', minValue: 0, value: rec ? rec.get('couvertureJours') : null },
            { xtype: 'datefield', name: 'datePeremption', fieldLabel: 'Date de péremption', format: 'd/m/Y', submitFormat: 'Y-m-d', editable: false,
              value: rec && rec.get('datePeremption') ? Ext.Date.parse(String(rec.get('datePeremption')).substring(0, 10), 'Y-m-d') : null },
            { xtype: 'textfield', name: 'numeroLot', fieldLabel: 'Numéro de lot', value: rec ? rec.get('numeroLot') : '' },
            { xtype: 'textfield', name: 'agence', fieldLabel: 'Agence de disponibilité', value: rec ? rec.get('agence') : '' },
            { xtype: 'textfield', name: 'lienReservation', fieldLabel: 'Lien de réservation', value: rec ? rec.get('lienReservation') : '' },
            { xtype: 'combobox', name: 'statut', fieldLabel: 'Statut produit', editable: false, queryMode: 'local',
              store: Usp.dispo.STATUTS_PROD, value: rec ? rec.get('statut') : '', emptyText: 'Auto (selon type / quantités)' },
            { xtype: 'checkbox', name: 'stockLimite', fieldLabel: 'Stock limité', checked: rec ? rec.get('stockLimite') : false },
            { xtype: 'checkbox', name: 'actif', fieldLabel: 'Actif', checked: rec ? rec.get('actif') : true }
        ] }],
        buttons: [
            { text: 'Annuler', handler: function () { win.close(); } },
            { text: 'Enregistrer', formBind: true, handler: function (b) {
                var form = b.up('window').down('form').getForm();
                if (!form.isValid()) { return; }
                var aid = form.findField('articleId').getValue();
                var payload = {
                    articleId: aid ? Number(aid) : null,
                    cip7: form.findField('cip7').getValue() || null,
                    cip13: form.findField('cip13').getValue() || null,
                    nomProduit: form.findField('nomProduit').getValue() || null,
                    quantiteDisponible: form.findField('quantiteDisponible').getValue(),
                    seuilRupture: form.findField('seuilRupture').getValue(),
                    couvertureJours: form.findField('couvertureJours').getValue(),
                    datePeremption: form.findField('datePeremption').getSubmitValue() || null,
                    numeroLot: form.findField('numeroLot').getValue() || null,
                    agence: form.findField('agence').getValue() || null,
                    lienReservation: form.findField('lienReservation').getValue() || null,
                    statut: form.findField('statut').getValue() || null,
                    stockLimite: form.findField('stockLimite').getValue(),
                    actif: form.findField('actif').getValue()
                };
                Usp.ajax({ url: rec ? '/dispo-evenements/' + evenementId + '/produits/' + rec.get('id') : '/dispo-evenements/' + evenementId + '/produits',
                    method: rec ? 'PUT' : 'POST', jsonData: payload,
                    success: function () { win.close(); store.load(); Usp.toastEnregistre('Produit', !!rec); },
                    failure: function (resp) {
                        var msg = 'Enregistrement impossible.';
                        try { msg = Ext.decode(resp.responseText).erreur || msg; } catch (e) {}
                        Ext.Msg.alert('Erreur', msg);
                    } });
            } }
        ]
    });
    win.show();
};

/* Import Excel des produits + rapport. */
Usp.dispo.importProduits = function (f, evenementId, store) {
    var file = f.fileInputEl.dom.files[0];
    if (!file) { return; }
    if (!/\.xlsx?$/i.test(file.name)) { Ext.Msg.alert('Import', 'Choisissez un fichier Excel (.xlsx).'); f.reset(); return; }
    var reader = new FileReader();
    reader.onload = function (e) {
        var b64 = (e.target.result || '').split(',')[1];
        Usp.ajax({ url: '/dispo-evenements/' + evenementId + '/produits/import', method: 'POST',
            jsonData: { fichierBase64: b64, nomFichier: file.name },
            success: function (resp) {
                var r = Ext.decode(resp.responseText) || {};
                store.load(); f.reset();
                Usp.dispo.rapportImport(r);
            },
            failure: function (resp) {
                var m = 'Import impossible.';
                try { m = Ext.decode(resp.responseText).erreur || m; } catch (ex) {}
                Ext.Msg.alert('Erreur', m); f.reset();
            } });
    };
    reader.readAsDataURL(file);
};

Usp.dispo.rapportImport = function (r) {
    var err = (r.erreurs || []);
    var html = '<div style="font-family:sans-serif">' +
        '<b>' + (r.total || 0) + '</b> ligne(s) traitée(s)<br/>' +
        '✅ Créés : <b>' + (r.crees || 0) + '</b><br/>' +
        '♻️ Mis à jour : <b>' + (r.majs || 0) + '</b><br/>' +
        '⚠️ Erreurs : <b>' + err.length + '</b>';
    if (err.length) {
        html += '<hr/><div style="max-height:200px;overflow:auto"><ul style="margin:0;padding-left:18px">';
        err.forEach(function (e) { html += '<li>Ligne ' + e.ligne + ' : ' + Ext.String.htmlEncode(e.raison) + '</li>'; });
        html += '</ul></div>';
    }
    html += '</div>';
    Ext.Msg.show({ title: 'Rapport d\'import', message: html, buttons: Ext.Msg.OK, width: 460 });
};
