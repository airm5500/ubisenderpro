/*
 * UbiSenderPro - Module MARKETING / Promotions (P1).
 * Promotions enrichies (statuts auto, responsable), produits promotionnels (UG)
 * et import Excel. Dépend de app.js (objet Usp). N'affecte pas l'onglet
 * Catalogue -> Promotions existant (legacy).
 */
Ext.define('Usp.marketing', { singleton: true });

Usp.marketing._stores = [];

Usp.marketing.COULEUR_STATUT = {
    ACTIVE: '#2e7d32', PROGRAMMEE: '#1976d2', INACTIVE: '#c62828',
    ARCHIVEE: '#777', ANNULEE: '#8e0000'
};

Usp.marketing.statutRenderer = function (v) {
    var c = Usp.marketing.COULEUR_STATUT[v] || '#333';
    var style = 'color:' + c + ';font-weight:bold' + (v === 'ANNULEE' ? ';text-decoration:line-through' : '');
    return '<span style="' + style + '">' + (v || '') + '</span>';
};

Usp.marketing.fdate = function (v) { return v ? String(v).substring(0, 10) : ''; };

Usp.marketing.reloadAll = function () {
    Usp.marketing._stores.forEach(function (s) { s.load(); });
};

/* Vue principale : un onglet par statut. */
Usp.marketing.panel = function () {
    Usp.marketing._stores = [];
    return {
        xtype: 'tabpanel', title: 'Promotions', listeners: Usp.tabListeners,
        items: [
            Usp.marketing.grille('ACTIVE', '🟢 Actives'),
            Usp.marketing.grille('PROGRAMMEE', '🔵 Programmées'),
            Usp.marketing.grille('INACTIVE', '🔴 Inactives'),
            Usp.marketing.grille('ARCHIVEE', '⚪ Archivées'),
            Usp.marketing.grille('ANNULEE', '⛔ Annulées')
        ]
    };
};

Usp.marketing.grille = function (statut, libelleTab) {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'code', 'nom', 'description', 'dateDebut', 'dateFin', 'statut', 'responsable'],
        autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/promotions',
            extraParams: { statut: statut },
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } }
    });
    Usp.marketing._stores.push(store);

    return {
        xtype: 'grid', title: libelleTab, store: store,
        columns: [
            { text: 'Code', dataIndex: 'code', width: 120 },
            { text: 'Nom', dataIndex: 'nom', flex: 1 },
            { text: 'Début', dataIndex: 'dateDebut', width: 100, renderer: Usp.marketing.fdate },
            { text: 'Fin', dataIndex: 'dateFin', width: 100, renderer: Usp.marketing.fdate },
            { text: 'Statut', dataIndex: 'statut', width: 110, renderer: Usp.marketing.statutRenderer },
            { text: 'Responsable', dataIndex: 'responsable', width: 140 },
            { text: 'Actions', width: 230, sortable: false, menuDisabled: true, dataIndex: 'id',
              renderer: function (v, m, rec) {
                  var s = rec.get('statut');
                  var h = '<span class="pm-edit" title="Voir / modifier" style="cursor:pointer;margin:0 4px">✏️</span>' +
                          '<span class="pm-dup" title="Dupliquer" style="cursor:pointer;margin:0 4px">📑</span>';
                  if (s !== 'ANNULEE' && s !== 'ARCHIVEE') {
                      h += '<span class="pm-cancel" title="Annuler la promotion" style="cursor:pointer;margin:0 4px;color:#c62828">⛔</span>';
                  }
                  if (s !== 'ARCHIVEE') {
                      h += '<span class="pm-archive" title="Archiver" style="cursor:pointer;margin:0 4px;color:#777">🗄️</span>';
                  }
                  return h;
              } }
        ],
        tbar: [
            { text: '➕ Nouvelle promotion', tooltip: 'Créer une promotion', handler: function () { Usp.marketing.promotionForm(store, null); } },
            { text: '🔄 Rafraîchir', handler: function () { store.load(); } }
        ].concat(Usp.export.boutons('Promotions ' + statut)),
        listeners: {
            itemdblclick: function (g, rec) { Usp.marketing.promotionForm(store, rec); },
            cellclick: function (g, td, ci, rec, tr, ri, e) {
                if (e.getTarget('.pm-edit')) { Usp.marketing.promotionForm(store, rec); }
                else if (e.getTarget('.pm-dup')) { Usp.marketing.action(rec, 'dupliquer', 'Dupliquer cette promotion ?'); }
                else if (e.getTarget('.pm-cancel')) { Usp.marketing.action(rec, 'annuler', 'Annuler la promotion « ' + Ext.String.htmlEncode(rec.get('nom')) + ' » ? (arrêt avant terme, historique conservé)'); }
                else if (e.getTarget('.pm-archive')) { Usp.marketing.action(rec, 'archiver', 'Archiver la promotion « ' + Ext.String.htmlEncode(rec.get('nom')) + ' » ?'); }
            }
        }
    };
};

Usp.marketing.action = function (rec, action, message) {
    Ext.Msg.confirm('Confirmation', message, function (btn) {
        if (btn !== 'yes') { return; }
        Usp.ajax({ url: '/promotions/' + rec.get('id') + '/' + action, method: 'POST',
            success: function () {
                Usp.marketing.reloadAll();
                Usp.toast('Opération effectuée avec succès.');
            },
            failure: function () { Ext.Msg.alert('Erreur', 'Opération impossible.'); } });
    });
};

/* Formulaire d'une promotion : informations générales + produits (si existante). */
Usp.marketing.promotionForm = function (store, rec) {
    var items = [
        { xtype: 'textfield', name: 'code', fieldLabel: 'Code', allowBlank: false, value: rec ? rec.get('code') : '' },
        { xtype: 'textfield', name: 'nom', fieldLabel: 'Nom', allowBlank: false, value: rec ? rec.get('nom') : '' },
        { xtype: 'textarea', name: 'description', fieldLabel: 'Description', height: 50, value: rec ? rec.get('description') : '' },
        { xtype: 'datefield', name: 'dateDebut', fieldLabel: 'Date de début', format: 'd/m/Y', submitFormat: 'Y-m-d\\TH:i:s', editable: false,
          value: rec && rec.get('dateDebut') ? Ext.Date.parse(String(rec.get('dateDebut')).substring(0, 10), 'Y-m-d') : null },
        { xtype: 'datefield', name: 'dateFin', fieldLabel: 'Date de fin', format: 'd/m/Y', submitFormat: 'Y-m-d\\TH:i:s', editable: false,
          value: rec && rec.get('dateFin') ? Ext.Date.parse(String(rec.get('dateFin')).substring(0, 10), 'Y-m-d') : null },
        { xtype: 'textfield', name: 'responsable', fieldLabel: 'Responsable', value: rec ? rec.get('responsable') : '' }
    ];

    var formItems = [{ xtype: 'form', itemId: 'promoForm', border: false, bodyPadding: 4,
        defaults: { anchor: '100%', labelWidth: 130 }, items: items }];

    if (rec) {
        formItems.push(Usp.marketing.produitsGrid(rec.get('id')));
    } else {
        formItems.push({ xtype: 'component', margin: '8 0 0 0',
            html: '<span style="color:#888">Enregistrez la promotion pour pouvoir y ajouter des produits.</span>' });
    }

    var win = Ext.create('Ext.window.Window', {
        title: rec ? 'Promotion — ' + Ext.String.htmlEncode(rec.get('nom')) : 'Nouvelle promotion',
        width: 820, height: rec ? 620 : 360, modal: true, layout: 'fit', autoScroll: true,
        maxHeight: Ext.getBody().getViewSize().height - 20,
        items: [{ xtype: 'panel', layout: 'anchor', bodyPadding: 12, autoScroll: true, defaults: { anchor: '100%' }, items: formItems }],
        buttons: [{ text: 'Enregistrer', formBind: false, handler: function (b) {
            var form = b.up('window').down('#promoForm').getForm();
            if (!form.isValid()) { return; }
            var v = form.getValues();
            Usp.ajax({ url: rec ? '/promotions/' + rec.get('id') : '/promotions',
                method: rec ? 'PUT' : 'POST', jsonData: v,
                success: function (resp) {
                    win.close();
                    Usp.marketing.reloadAll();
                    Usp.toastEnregistre('Promotion « ' + v.nom + ' »', !!rec);
                    if (!rec) {
                        // Rouvre en édition pour permettre d'ajouter les produits.
                        var cree = Ext.decode(resp.responseText) || {};
                        var m = Ext.create(store.model, cree);
                        Usp.marketing.promotionForm(store, m);
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

/* Sous-grille des produits d'une promotion (ajout/modif/suppression + import). */
Usp.marketing.produitsGrid = function (promotionId) {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'cip7', 'cip13', 'nomProduit', 'quantiteMinimale', 'tauxUg', 'tauxMaxUg',
                 'quantiteUg', 'quantiteUgMax', 'modeCalcul', 'articleId', 'actif'],
        autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/promotions/' + promotionId + '/produits',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } }
    });
    return {
        xtype: 'grid', title: 'Produits de la promotion', height: 300, margin: '10 0 0 0', store: store,
        columns: [
            { text: 'CIP7', dataIndex: 'cip7', width: 90 },
            { text: 'CIP13', dataIndex: 'cip13', width: 120 },
            { text: 'Produit', dataIndex: 'nomProduit', flex: 1 },
            { text: 'Qté min', dataIndex: 'quantiteMinimale', width: 70, align: 'right' },
            { text: 'UG %', dataIndex: 'tauxUg', width: 70, align: 'right',
              renderer: function (v) { return (v != null && v !== '') ? v + ' %' : ''; } },
            { text: 'UG nb', dataIndex: 'quantiteUg', width: 70, align: 'right' },
            { text: 'Taux max', dataIndex: 'tauxMaxUg', width: 75, align: 'right',
              renderer: function (v) { return (v != null && v !== '') ? v + ' %' : ''; } },
            { text: 'Lié', dataIndex: 'articleId', width: 50, align: 'center',
              renderer: function (v) { return v ? '✅' : '—'; } },
            { text: 'Actions', width: 80, align: 'center', sortable: false, menuDisabled: true, dataIndex: 'id',
              renderer: function () {
                  return '<span class="pp-edit" title="Modifier" style="cursor:pointer;margin:0 4px">✏️</span>' +
                      '<span class="pp-del" title="Retirer" style="cursor:pointer;margin:0 4px;color:#c62828">🗑️</span>';
              } }
        ],
        tbar: [
            { text: '➕ Ajouter un produit', handler: function () { Usp.marketing.produitForm(store, promotionId, null); } },
            { xtype: 'filefield', buttonOnly: true, hideLabel: true, buttonText: '📥 Importer Excel',
              listeners: { change: function (f) { Usp.marketing.importProduits(f, promotionId, store); } } }
        ],
        listeners: {
            itemdblclick: function (g, rec) { Usp.marketing.produitForm(store, promotionId, rec); },
            cellclick: function (g, td, ci, rec, tr, ri, e) {
                if (e.getTarget('.pp-edit')) { Usp.marketing.produitForm(store, promotionId, rec); }
                else if (e.getTarget('.pp-del')) {
                    Ext.Msg.confirm('Retirer', 'Retirer ce produit de la promotion ?', function (btn) {
                        if (btn !== 'yes') { return; }
                        Usp.ajax({ url: '/promotions/' + promotionId + '/produits/' + rec.get('id'), method: 'DELETE',
                            success: function () { store.load(); Usp.toast('Produit retiré.'); },
                            failure: function () { Ext.Msg.alert('Erreur', 'Suppression impossible.'); } });
                    });
                }
            }
        }
    };
};

Usp.marketing.produitForm = function (store, promotionId, rec) {
    var win = Ext.create('Ext.window.Window', {
        title: rec ? 'Modifier le produit' : 'Ajouter un produit', width: 520, modal: true, bodyPadding: 12,
        items: [{ xtype: 'form', border: false, defaults: { anchor: '100%', labelWidth: 170 }, items: [
            { xtype: 'displayfield', value: '<span style="color:#888">CIP7 ou CIP13 obligatoire. L\'avantage UG est ' +
              'défini <b>soit en pourcentage</b> (Taux UG), <b>soit en nombre</b> (Quantité UG).</span>' },
            { xtype: 'textfield', name: 'cip7', fieldLabel: 'CIP7', value: rec ? rec.get('cip7') : '' },
            { xtype: 'textfield', name: 'cip13', fieldLabel: 'CIP13', value: rec ? rec.get('cip13') : '' },
            { xtype: 'textfield', name: 'nomProduit', fieldLabel: 'Nom du produit', value: rec ? rec.get('nomProduit') : '' },
            { xtype: 'numberfield', name: 'quantiteMinimale', fieldLabel: 'Quantité minimale commandée', minValue: 0, value: rec ? rec.get('quantiteMinimale') : null },
            { xtype: 'numberfield', name: 'tauxUg', fieldLabel: 'UG en pourcentage (%)', minValue: 0, value: rec ? rec.get('tauxUg') : null,
              emptyText: 'ex. 20 pour 20 %' },
            { xtype: 'numberfield', name: 'quantiteUg', fieldLabel: 'UG en nombre', minValue: 0, value: rec ? rec.get('quantiteUg') : null,
              emptyText: 'ex. 20 unités gratuites' },
            { xtype: 'numberfield', name: 'tauxMaxUg', fieldLabel: 'Taux maximal UG autorisé (%)', minValue: 0, value: rec ? rec.get('tauxMaxUg') : null,
              emptyText: 'facultatif (issu de l\'import)' },
            { xtype: 'checkbox', name: 'actif', fieldLabel: 'Actif', checked: rec ? rec.get('actif') : true }
        ] }],
        buttons: [{ text: 'Enregistrer', formBind: true, handler: function (b) {
            var form = b.up('window').down('form').getForm();
            if (!form.isValid()) { return; }
            var v = form.getValues();
            v.actif = form.findField('actif').getValue();
            Usp.ajax({ url: rec ? '/promotions/' + promotionId + '/produits/' + rec.get('id') : '/promotions/' + promotionId + '/produits',
                method: rec ? 'PUT' : 'POST', jsonData: v,
                success: function () { win.close(); store.load(); Usp.toastEnregistre('Produit', !!rec); },
                failure: function (resp) {
                    var msg = 'Enregistrement impossible.';
                    try { msg = Ext.decode(resp.responseText).erreur || msg; } catch (e) {}
                    Ext.Msg.alert('Erreur', msg);
                } });
        } }]
    });
    win.show();
};

/* Import Excel des produits + rapport. */
Usp.marketing.importProduits = function (f, promotionId, store) {
    var file = f.fileInputEl.dom.files[0];
    if (!file) { return; }
    if (!/\.xlsx?$/i.test(file.name)) { Ext.Msg.alert('Import', 'Choisissez un fichier Excel (.xlsx).'); f.reset(); return; }
    var reader = new FileReader();
    reader.onload = function (e) {
        var b64 = (e.target.result || '').split(',')[1];
        Usp.ajax({ url: '/promotions/' + promotionId + '/produits/import', method: 'POST',
            jsonData: { fichierBase64: b64, nomFichier: file.name },
            success: function (resp) {
                var r = Ext.decode(resp.responseText) || {};
                store.load();
                f.reset();
                Usp.marketing.rapportImport(r);
            },
            failure: function (resp) {
                var m = 'Import impossible.';
                try { m = Ext.decode(resp.responseText).erreur || m; } catch (ex) {}
                Ext.Msg.alert('Erreur', m); f.reset();
            } });
    };
    reader.readAsDataURL(file);
};

Usp.marketing.rapportImport = function (r) {
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
