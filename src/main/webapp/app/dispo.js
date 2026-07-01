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
            Usp.dispo.grille({ historique: true }, '🗂️ Historique'),
            Usp.dispo.regles()
        ]
    };
};

/* Onglet « Règles (risque) » : programmation configurable du risque de rupture (§11). */
Usp.dispo.regles = function () {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'libelle', 'type', 'jourMois', 'heure', 'audience', 'canal', 'actif'],
        autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/dispo-regles',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } }
    });
    var audienceLib = function (v) {
        var m = Ext.Array.findBy(Usp.dispo.AUDIENCES, function (a) { return a[0] === v; });
        return m ? m[1] : (v || '');
    };
    return {
        xtype: 'grid', title: '⚙️ Règles (risque)', store: store,
        columns: [
            { text: 'Libellé', dataIndex: 'libelle', flex: 1 },
            { text: 'Jour du mois', dataIndex: 'jourMois', width: 100, align: 'right' },
            { text: 'Heure', dataIndex: 'heure', width: 70, align: 'right', renderer: function (v) { return v + ' h'; } },
            { text: 'Audience', dataIndex: 'audience', width: 200, renderer: audienceLib },
            { text: 'Canal', dataIndex: 'canal', width: 80, renderer: function (v) { return v === 'API' ? 'API' : 'WA Web'; } },
            { text: 'Active', dataIndex: 'actif', width: 70, renderer: function (v) { return v ? '✅' : '—'; } },
            { text: 'Actions', width: 90, align: 'center', sortable: false, menuDisabled: true, dataIndex: 'id',
              renderer: function () {
                  return '<span class="dr-edit" title="Modifier" style="cursor:pointer;margin:0 4px">✏️</span>' +
                      '<span class="dr-del" title="Supprimer" style="cursor:pointer;margin:0 4px;color:#c62828">🗑️</span>';
              } }
        ],
        tbar: [
            Usp.permBtn('dispo', 'CREER', { text: '➕ Nouvelle règle', handler: function () { Usp.dispo.regleForm(store, null); } }),
            { text: '🔄 Rafraîchir', handler: function () { store.load(); } },
            '->',
            { xtype: 'tbtext', text: '<span style="color:#888">Le risque de rupture est proposé selon ces règles (jour du mois + audience).</span>' }
        ],
        listeners: {
            itemdblclick: function (g, rec) { Usp.dispo.regleForm(store, rec); },
            cellclick: function (g, td, ci, rec, tr, ri, e) {
                if (e.getTarget('.dr-edit')) { Usp.dispo.regleForm(store, rec); }
                else if (e.getTarget('.dr-del')) {
                    Ext.Msg.confirm('Supprimer', 'Supprimer la règle « ' + Ext.String.htmlEncode(rec.get('libelle')) + ' » ?',
                        function (btn) {
                            if (btn !== 'yes') { return; }
                            Usp.ajax({ url: '/dispo-regles/' + rec.get('id'), method: 'DELETE',
                                success: function () { store.load(); Usp.toast('Règle supprimée.'); },
                                failure: function () { Ext.Msg.alert('Erreur', 'Suppression impossible.'); } });
                        });
                }
            }
        }
    };
};

Usp.dispo.regleForm = function (store, rec) {
    var win = Ext.create('Ext.window.Window', {
        title: rec ? 'Modifier la règle' : 'Nouvelle règle de risque', width: 520, modal: true, bodyPadding: 12, layout: 'fit',
        items: [{ xtype: 'form', border: false, defaults: { anchor: '100%', labelWidth: 160 }, items: [
            { xtype: 'textfield', name: 'libelle', fieldLabel: 'Libellé', allowBlank: false, value: rec ? rec.get('libelle') : '' },
            { xtype: 'numberfield', name: 'jourMois', fieldLabel: 'Jour du mois', minValue: 1, maxValue: 31,
              allowBlank: false, value: rec ? rec.get('jourMois') : 1 },
            { xtype: 'numberfield', name: 'heure', fieldLabel: 'Heure (0-23)', minValue: 0, maxValue: 23,
              value: rec ? rec.get('heure') : 8 },
            { xtype: 'combobox', name: 'audience', fieldLabel: 'Audience', editable: false, queryMode: 'local',
              store: Usp.dispo.AUDIENCES, value: rec ? rec.get('audience') : 'DIAMOND_ET_PLATINIUM' },
            { xtype: 'combobox', name: 'canal', fieldLabel: 'Canal', editable: false, queryMode: 'local',
              store: [['WEB', 'WhatsApp Web'], ['API', 'API WhatsApp']], value: rec ? (rec.get('canal') || 'WEB') : 'WEB' },
            { xtype: 'checkbox', name: 'actif', fieldLabel: 'Active', checked: rec ? rec.get('actif') : true }
        ] }],
        buttons: [
            { text: 'Annuler', handler: function () { win.close(); } },
            { text: 'Enregistrer', formBind: true, handler: function (b) {
                var form = b.up('window').down('form').getForm();
                if (!form.isValid()) { return; }
                var v = form.getValues();
                v.type = 'RISQUE_RUPTURE';
                v.actif = form.findField('actif').getValue();
                Usp.ajax({ url: rec ? '/dispo-regles/' + rec.get('id') : '/dispo-regles',
                    method: rec ? 'PUT' : 'POST', jsonData: v,
                    success: function () { win.close(); store.load(); Usp.toastEnregistre('Règle', !!rec); },
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
                Usp.permBtn('dispo', 'CREER', { text: '➕ Nouvel événement', tooltip: 'Créer un événement disponibilité / rupture',
                  handler: function () { Usp.dispo.evenementForm(store, null, filtre.type); } }),
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
            failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
    });
};

/* Fiche d'un événement : informations + produits (si existant). */
Usp.dispo.evenementForm = function (store, rec, typeParDefaut) {
    var items = [
        { xtype: 'textfield', name: 'code', fieldLabel: 'Code', allowBlank: false,
          value: rec ? rec.get('code') : Usp.codeAuto(), emptyText: 'Généré — modifiable' },
        { xtype: 'combobox', name: 'type', fieldLabel: 'Type', editable: false, queryMode: 'local', allowBlank: false,
          store: Usp.dispo.TYPES, value: rec ? rec.get('type') : (typeParDefaut || 'ANNONCE_DISPONIBILITE') },
        { xtype: 'textfield', name: 'titre', fieldLabel: 'Titre', allowBlank: false, value: rec ? rec.get('titre') : '' },
        { xtype: 'textarea', name: 'description', fieldLabel: 'Description', height: 50, value: rec ? rec.get('description') : '' },
        { xtype: 'datefield', name: 'dateDebut', fieldLabel: 'Date de début', format: 'd/m/Y', submitFormat: 'Y-m-d\\TH:i:s', editable: false,
          value: rec && rec.get('dateDebut') ? Ext.Date.parse(String(rec.get('dateDebut')).substring(0, 10), 'Y-m-d') : null },
        { xtype: 'datefield', name: 'dateFin', fieldLabel: 'Date de fin', format: 'd/m/Y', submitFormat: 'Y-m-d\\TH:i:s', editable: false,
          value: rec && rec.get('dateFin') ? Ext.Date.parse(String(rec.get('dateFin')).substring(0, 10), 'Y-m-d') : null },
        Usp.referentielCombo('AGENCE', { name: 'agence', fieldLabel: 'Agence', value: rec ? rec.get('agence') : '' }),
        { xtype: 'textfield', name: 'societe', fieldLabel: 'Société',
          value: rec ? rec.get('societe') : (Usp.societeParDefaut || '') },
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
        width: 880, height: rec ? 640 : 560, modal: true,
        layout: { type: 'vbox', align: 'stretch' }, bodyPadding: 12,
        maxHeight: Ext.getBody().getViewSize().height - 20,
        items: formItems,
        buttons: [{ text: 'Enregistrer', handler: function (b) {
            var form = b.up('window').down('#deForm').getForm();
            if (!form.isValid()) { return; }
            if (!Usp.periodeValide(form.findField('dateDebut').getValue(), form.findField('dateFin').getValue())) { return; }
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
    var dForm = win.down('#deForm').getForm();
    Usp.lierPeriode(dForm.findField('dateDebut'), dForm.findField('dateFin'));
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
            { text: 'Agence', dataIndex: 'agence', width: 120 },
            { text: 'Disponibilité', dataIndex: 'statut', width: 130, renderer: Usp.dispo.prodStatutRenderer },
            { text: 'Catalogue', dataIndex: 'articleId', width: 75, align: 'center',
              renderer: function (v) { return v ? '✅' : '—'; } },
            { text: 'Actions', width: 80, align: 'center', sortable: false, menuDisabled: true, dataIndex: 'id',
              renderer: function () {
                  return '<span class="dp-edit" title="Modifier" style="cursor:pointer;margin:0 4px">✏️</span>' +
                      '<span class="dp-del" title="Retirer" style="cursor:pointer;margin:0 4px;color:#c62828">🗑️</span>';
              } }
        ],
        tbar: [
            Usp.permBtn('dispo', 'CREER', { text: '➕ Ajouter un produit', handler: function () { Usp.dispo.produitForm(store, evenementId, null); } }),
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
                  // En ExtJS 4.2, « select » renvoie un tableau d'enregistrements.
                  var a = Ext.isArray(r) ? r[0] : r;
                  if (!a) { return; }
                  fld('cip7').setValue(a.get('cip') || '');
                  fld('cip13').setValue(a.get('codeBarres') || '');
                  fld('nomProduit').setValue(a.get('designation') || '');
                  fld('articleId').setValue(a.get('id'));
              } } },
            { xtype: 'displayfield', value: '<span style="color:#888">Choisissez un article du catalogue ' +
              '(recommandé, évite les doublons) <b>ou</b> saisissez manuellement le CIP.</span>' },
            { xtype: 'hidden', name: 'articleId', value: rec ? rec.get('articleId') : null },
            Usp.cip7Field(rec ? rec.get('cip7') : ''),
            { xtype: 'textfield', name: 'cip13', fieldLabel: 'CIP13', value: rec ? rec.get('cip13') : '' },
            { xtype: 'textfield', name: 'nomProduit', fieldLabel: 'Nom du produit', value: rec ? rec.get('nomProduit') : '', listeners: Usp.majListeners },
            // On informe simplement le client : Disponible ou Stock limité (pas de quantité/seuil).
            { xtype: 'combobox', name: 'dispoStatut', fieldLabel: 'Disponibilité', editable: false, queryMode: 'local',
              store: [['DISPONIBLE', 'Disponible'], ['STOCK_LIMITE', 'Stock limité']],
              value: rec && rec.get('stockLimite') ? 'STOCK_LIMITE' : 'DISPONIBLE' },
            Usp.referentielCombo('AGENCE', { name: 'agence', fieldLabel: 'Agence de disponibilité',
              value: rec ? rec.get('agence') : '', emptyText: 'Toutes les agences (laisser vide)' }),
            { xtype: 'datefield', name: 'datePeremption', fieldLabel: 'Date de péremption', format: 'd/m/Y', submitFormat: 'Y-m-d', editable: false,
              value: rec && rec.get('datePeremption') ? Ext.Date.parse(String(rec.get('datePeremption')).substring(0, 10), 'Y-m-d') : null },
            { xtype: 'textfield', name: 'numeroLot', fieldLabel: 'Numéro de lot', value: rec ? rec.get('numeroLot') : '' },
            { xtype: 'checkbox', name: 'actif', fieldLabel: 'Actif', checked: rec ? rec.get('actif') : true }
        ] }],
        buttons: [
            { text: 'Annuler', handler: function () { win.close(); } },
            { text: 'Enregistrer', formBind: true, handler: function (b) {
                var form = b.up('window').down('form').getForm();
                if (!form.isValid()) { return; }
                var aid = form.findField('articleId').getValue();
                var dispoStatut = form.findField('dispoStatut').getValue();
                var payload = {
                    articleId: aid ? Number(aid) : null,
                    cip7: form.findField('cip7').getValue() || null,
                    cip13: form.findField('cip13').getValue() || null,
                    nomProduit: form.findField('nomProduit').getValue() || null,
                    datePeremption: form.findField('datePeremption').getSubmitValue() || null,
                    numeroLot: form.findField('numeroLot').getValue() || null,
                    agence: form.findField('agence').getValue() || null,
                    // Statut calculé côté serveur à partir de « stock limité ».
                    stockLimite: dispoStatut === 'STOCK_LIMITE',
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
