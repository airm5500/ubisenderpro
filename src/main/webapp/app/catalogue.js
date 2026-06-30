/*
 * UbiSenderPro - Écrans catalogue (sections 11 à 14 de la spec).
 * Onglets : Articles | Catégories | Marques. Dépend de app.js (objet Usp).
 */
Ext.define('Usp.catalogue', { singleton: true });

Usp.catalogue.store = function (url, fields, root) {
    return Ext.create('Ext.data.Store', {
        fields: fields, pageSize: 25,
        proxy: {
            type: 'ajax', url: Usp.apiBase + url,
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') },
            reader: { type: 'json', root: root || 'data', totalProperty: 'total' }
        },
        autoLoad: true
    });
};

/* ---------- Articles ---------- */
Usp.catalogue.articlesPanel = function () {
    var store = Usp.catalogue.store('/articles',
        ['id', 'pscode', 'designation', 'cip', 'codeBarres', 'prixVente', 'prixVentePublic',
         'prixPromotionnel', 'quantiteCommandee', 'quantiteUg', 'nomPromo', 'codePromo', 'promotions',
         'dateDebutPromotion', 'dateFinPromotion', 'stockDisponible', 'unite', 'actif', 'publiable']);

    var promoNoms = function (v) {
        if (!Ext.isArray(v) || !v.length) { return ''; }
        return Ext.String.htmlEncode(v.map(function (p) { return p.nom || p.code; }).join(', '));
    };

    var prix = function (v) { return v ? Ext.util.Format.number(v, '0,000') + ' F' : ''; };

    return {
        xtype: 'grid', title: '📦 Articles', store: store,
        columns: [
            { text: 'PS Code', dataIndex: 'pscode', width: 100 },
            { text: 'Désignation', dataIndex: 'designation', flex: 1 },
            { text: 'CIP', dataIndex: 'cip', width: 80 },
            { text: 'Prix', dataIndex: 'prixVente', width: 85, align: 'right', renderer: prix },
            { text: 'Prix public', dataIndex: 'prixVentePublic', width: 90, align: 'right', renderer: prix },
            { text: 'Promo', dataIndex: 'prixPromotionnel', width: 85, align: 'right', renderer: prix },
            { text: 'Qté cmd', dataIndex: 'quantiteCommandee', width: 75, align: 'right' },
            { text: 'UG', dataIndex: 'quantiteUg', width: 55, align: 'right' },
            { text: 'Promotions', dataIndex: 'promotions', width: 160, sortable: false, renderer: promoNoms },
            { text: 'Stock', dataIndex: 'stockDisponible', width: 75, align: 'right' },
            { text: 'Actif', dataIndex: 'actif', width: 55, renderer: function (v) { return v ? 'Oui' : 'Non'; } },
            { text: 'Actions', width: 100, align: 'center', sortable: false, menuDisabled: true, dataIndex: 'id',
              renderer: function () {
                  return '<span class="art-edit" title="Modifier" style="cursor:pointer;margin:0 4px">✏️</span>' +
                      '<span class="art-del" title="Supprimer" style="cursor:pointer;margin:0 4px;color:#c62828">🗑️</span>';
              } }
        ],
        tbar: [
            { xtype: 'textfield', emptyText: 'Rechercher (désignation, PS code, code promo)...', width: 280, listeners: {
                change: function (f, v) { store.getProxy().extraParams = { q: v }; store.loadPage(1); }, buffer: 400 } },
            '->',
            Usp.permBtn('catalogue', 'CREER', { text: '➕ Nouvel article', tooltip: 'Créer un nouvel article', handler: function () { Usp.catalogue.articleForm(store, null); } }),
            Usp.permBtn('catalogue', 'AJUSTER_STOCK', { text: 'Ajuster stock', handler: function (b) {
                var rec = b.up('grid').getSelectionModel().getSelection()[0];
                if (!rec) { Ext.Msg.alert('Info', 'Sélectionnez un article.'); return; }
                Usp.catalogue.stockForm(store, rec);
            } }),
            Usp.permBtn('catalogue', 'MAJ_PROMO', { text: 'Mettre à jour une promo', handler: function () { Usp.catalogue.majPromo(store); } }),
            Usp.permBtn('catalogue', 'CREER', { text: '📥 Importer', tooltip: 'Importer des articles depuis un fichier Excel/CSV', handler: function () { Usp.catalogue.importArticles(store); } })
        ].concat(Usp.export.boutons('Catalogue articles')),
        bbar: { xtype: 'pagingtoolbar', store: store, displayInfo: true },
        listeners: {
            itemdblclick: function (g, rec) { Usp.catalogue.articleForm(store, rec); },
            cellclick: function (g, td, ci, rec, tr, ri, e) {
                if (e.getTarget('.art-edit')) { Usp.catalogue.articleForm(store, rec); }
                else if (e.getTarget('.art-del')) {
                    Ext.Msg.confirm('Supprimer', 'Supprimer l\'article « ' + Ext.String.htmlEncode(rec.get('designation')) + ' » ?',
                        function (btn) {
                            if (btn !== 'yes') { return; }
                            Usp.ajax({ url: '/articles/' + rec.get('id'), method: 'DELETE',
                                success: function () { store.load(); Usp.toast('Article supprimé avec succès.'); },
                                failure: function () { Ext.Msg.alert('Erreur', 'Suppression impossible.'); } });
                        });
                }
            }
        }
    };
};

Usp.catalogue.articleForm = function (store, rec) {
    var win = Ext.create('Ext.window.Window', {
        title: rec ? 'Modifier l\'article' : 'Nouvel article',
        width: 520, modal: true, bodyPadding: 12, layout: 'anchor',
        items: [{
            xtype: 'form', border: false, defaults: { anchor: '100%' },
            items: [
                { xtype: 'textfield', name: 'pscode', fieldLabel: 'PS Code', allowBlank: false },
                { xtype: 'textfield', name: 'designation', fieldLabel: 'Désignation', allowBlank: false },
                { xtype: 'textfield', name: 'cip', fieldLabel: 'CIP' },
                { xtype: 'textfield', name: 'codeBarres', fieldLabel: 'Code-barres' },
                { xtype: 'numberfield', name: 'prixVente', fieldLabel: 'Prix de vente', allowBlank: false,
                  minValue: 0.01, minText: 'Le prix de vente doit être strictement supérieur à 0.' },
                { xtype: 'numberfield', name: 'prixVentePublic', fieldLabel: 'Prix de vente public', minValue: 0 },
                { xtype: 'numberfield', name: 'prixPromotionnel', fieldLabel: 'Prix promotionnel', minValue: 0 },
                { xtype: 'fieldcontainer', fieldLabel: 'Quantités', layout: 'hbox', defaults: { labelWidth: 70, width: 150 }, items: [
                    { xtype: 'numberfield', name: 'quantiteCommandee', fieldLabel: 'Commandée', minValue: 0 },
                    { xtype: 'numberfield', name: 'quantiteUg', fieldLabel: 'UG', minValue: 0, labelWidth: 30, width: 110, margin: '0 0 0 8' }
                ] },
                { xtype: 'combobox', name: 'promotionIds', itemId: 'promoCombo', fieldLabel: 'Promotions',
                  queryMode: 'local', editable: false, multiSelect: true, emptyText: 'Aucune promotion',
                  store: Usp.catalogue.promotionStore(), valueField: 'id', displayField: 'nom',
                  listConfig: { getInnerTpl: function () { return '{nom} <span style="color:#888">({code})</span>'; } } },
                { xtype: 'numberfield', name: 'stockDisponible', fieldLabel: 'Stock disponible (facultatif)', minValue: 0 },
                { xtype: 'textfield', name: 'unite', fieldLabel: 'Unité' },
                { xtype: 'textfield', name: 'imageUrl', fieldLabel: 'Image URL' },
                { xtype: 'checkbox', name: 'publiable', fieldLabel: 'Publiable', checked: true }
            ]
        }],
        buttons: [{
            text: 'Enregistrer',
            handler: function (b) {
                var form = b.up('window').down('form').getForm();
                if (!form.isValid()) {
                    var manquant = form.getFields().findBy(function (f) { return !f.isValid(); });
                    if (manquant) {
                        manquant.focus(true, 50);
                        Ext.Msg.alert('Champ obligatoire',
                            'Veuillez renseigner : <b>' + (manquant.fieldLabel || manquant.getName()) +
                            '</b> (les champs requis sont en rouge).');
                    }
                    return;
                }
                // N'envoie que les valeurs renseignées (un champ numérique vide ne doit pas être transmis).
                var data = {};
                form.getFields().each(function (f) {
                    var n = f.getName();
                    if (!n) { return; }
                    var v = f.getValue();
                    if (f.isXType('checkboxfield')) { data[n] = v; return; }
                    if (n === 'promotionIds') { data[n] = Ext.isArray(v) ? v : (v != null ? [v] : []); return; }
                    if (v === null || v === '' || (Ext.isArray(v) && !v.length)) { return; }
                    data[n] = v;
                });
                Usp.ajax({
                    url: rec ? '/articles/' + rec.get('id') : '/articles',
                    method: rec ? 'PUT' : 'POST', jsonData: data,
                    success: function () {
                        win.close(); store.load();
                        Usp.toastEnregistre('Article « ' + (data.designation || data.pscode || '') + ' »', !!rec);
                    },
                    failure: function (resp) {
                        var msg = 'Enregistrement impossible.';
                        try { msg = Ext.decode(resp.responseText).erreur || msg; } catch (e) {}
                        Ext.Msg.alert('Erreur', msg);
                    }
                });
            }
        }]
    });
    win.show();
    if (rec) {
        win.down('form').getForm().setValues(rec.getData());
        var promos = rec.get('promotions') || [];
        win.down('#promoCombo').setValue(promos.map(function (p) { return p.id; }));
    }
};

Usp.catalogue.stockForm = function (store, rec) {
    Ext.Msg.prompt('Ajuster le stock', 'Nouveau stock pour ' + rec.get('designation') + ' :', function (btn, val) {
        if (btn !== 'ok') { return; }
        Usp.ajax({
            url: '/articles/' + rec.get('id') + '/stock', method: 'POST',
            jsonData: { quantite: val, type: 'AJUSTEMENT_POSITIF', commentaire: 'Ajustement manuel' },
            success: function () { store.load(); },
            failure: function () { Ext.Msg.alert('Erreur', 'Ajustement impossible.'); }
        });
    }, this, false, String(rec.get('stockDisponible')));
};

Usp.catalogue.importArticles = function (store) {
    Usp.importer.show('ARTICLES', '/imports/articles', function () { store.load(); });
};

/* Mise à jour sélective des dates d'une promotion (tous les articles du code promo). */
Usp.catalogue.majPromo = function (store) {
    var fmtDate = function (v) { return v ? String(v).substring(0, 10) : ''; };
    var apercu = Ext.create('Ext.data.Store', {
        fields: ['pscode', 'designation', 'dateDebutPromotion', 'dateFinPromotion'] });

    var charger = function (win) {
        var code = win.down('[name=codePromo]').getValue();
        if (!code) { apercu.removeAll(); return; }
        Usp.ajax({ url: '/articles/promo?code=' + encodeURIComponent(code), method: 'GET',
            success: function (resp) {
                apercu.loadData(Ext.decode(resp.responseText) || []);
                win.down('#promoCount').update(apercu.getCount() + ' article(s) concerné(s)');
            },
            failure: function () { apercu.removeAll(); } });
    };

    var win = Ext.create('Ext.window.Window', {
        title: 'Mettre à jour une promotion', width: 620, height: 460, modal: true, layout: 'border',
        items: [
            { region: 'north', xtype: 'form', border: false, bodyPadding: 12, defaults: { anchor: '100%' },
              items: [
                { xtype: 'displayfield',
                  value: 'Applique les dates à <b>tous les articles</b> portant ce code promo.' },
                { xtype: 'fieldcontainer', layout: 'hbox', items: [
                    { xtype: 'textfield', name: 'codePromo', fieldLabel: 'Code promo', allowBlank: false,
                      emptyText: 'ex. 6553', width: 280, labelWidth: 90,
                      listeners: { change: { buffer: 400, fn: function (f) { charger(f.up('window')); } } } },
                    { xtype: 'button', text: 'Aperçu', margin: '0 0 0 8',
                      handler: function (b) { charger(b.up('window')); } }
                ] },
                { xtype: 'fieldcontainer', layout: 'hbox', items: [
                    { xtype: 'datefield', name: 'dateDebut', fieldLabel: 'Date de début', format: 'd/m/Y',
                      submitFormat: 'Y-m-d\\TH:i:s', editable: false, width: 230, labelWidth: 90 },
                    { xtype: 'datefield', name: 'dateFin', fieldLabel: 'Date de fin', format: 'd/m/Y',
                      submitFormat: 'Y-m-d\\TH:i:s', editable: false, width: 210, labelWidth: 80, margin: '0 0 0 8' }
                ] }
              ] },
            { region: 'center', xtype: 'grid', store: apercu, title: 'Articles concernés',
              columns: [
                { text: 'PS Code', dataIndex: 'pscode', width: 100 },
                { text: 'Désignation', dataIndex: 'designation', flex: 1 },
                { text: 'Début actuel', dataIndex: 'dateDebutPromotion', width: 100, renderer: fmtDate },
                { text: 'Fin actuelle', dataIndex: 'dateFinPromotion', width: 100, renderer: fmtDate }
              ],
              tbar: [{ xtype: 'tbtext', itemId: 'promoCount', text: 'Saisissez un code promo' }]
            }
        ],
        buttons: [{
            text: 'Valider', formBind: true, handler: function (b) {
                var f = b.up('window').down('form').getForm();
                if (!f.isValid()) { return; }
                var v = f.getValues();
                Usp.ajax({ url: '/articles/promo', method: 'POST',
                    jsonData: { codePromo: v.codePromo, dateDebut: v.dateDebut || null, dateFin: v.dateFin || null },
                    success: function (resp) {
                        var r = Ext.decode(resp.responseText) || {};
                        win.close(); store.load();
                        Ext.Msg.alert('Promotion mise à jour', (r.misAJour || 0) + ' article(s) mis à jour.');
                    },
                    failure: function (resp) {
                        var msg = 'Mise à jour impossible.';
                        try { msg = Ext.decode(resp.responseText).erreur || msg; } catch (e) {}
                        Ext.Msg.alert('Erreur', msg);
                    } });
            }
        }]
    });
    win.show();
};

/* ---------- Promotions ---------- */
Usp.catalogue.promotionStore = function () {
    return Ext.create('Ext.data.Store', {
        fields: ['id', 'code', 'nom'], autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/promotions',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } }
    });
};

Usp.catalogue.promotionsPanel = function () {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'code', 'nom', 'description', 'dateDebut', 'dateFin', 'actif'], autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/promotions',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } }
    });
    var fdate = function (v) { return v ? String(v).substring(0, 10) : ''; };
    return {
        xtype: 'grid', title: '🏷️ Promotions', store: store,
        columns: [
            { text: 'Code', dataIndex: 'code', width: 110 },
            { text: 'Nom', dataIndex: 'nom', flex: 1 },
            { text: 'Début', dataIndex: 'dateDebut', width: 110, renderer: fdate },
            { text: 'Fin', dataIndex: 'dateFin', width: 110, renderer: fdate },
            { text: 'Active', dataIndex: 'actif', width: 70, align: 'center',
              renderer: function (v) { return v ? 'Oui' : 'Non'; } },
            { text: 'Actions', width: 100, align: 'center', sortable: false, menuDisabled: true, dataIndex: 'id',
              renderer: function () {
                  return '<span class="promo-edit" title="Modifier" style="cursor:pointer;margin:0 4px">✏️</span>' +
                      '<span class="promo-del" title="Supprimer" style="cursor:pointer;margin:0 4px;color:#c62828">🗑️</span>';
              } }
        ],
        tbar: [
            Usp.permBtn('promotions', 'CREER', { text: '➕ Nouvelle promotion', tooltip: 'Créer une nouvelle promotion', handler: function () { Usp.catalogue.promotionForm(store, null); } }),
            { text: 'Rafraîchir', handler: function () { store.load(); } }
        ].concat(Usp.export.boutons('Promotions')),
        listeners: {
            itemdblclick: function (g, rec) { Usp.catalogue.promotionForm(store, rec); },
            cellclick: function (g, td, ci, rec, tr, ri, e) {
                if (e.getTarget('.promo-edit')) { Usp.catalogue.promotionForm(store, rec); }
                else if (e.getTarget('.promo-del')) {
                    Ext.Msg.confirm('Supprimer', 'Supprimer la promotion « ' + Ext.String.htmlEncode(rec.get('nom')) + ' » ?',
                        function (btn) {
                            if (btn !== 'yes') { return; }
                            Usp.ajax({ url: '/promotions/' + rec.get('id'), method: 'DELETE',
                                success: function () { store.load(); Usp.toast('Promotion supprimée avec succès.'); },
                                failure: function () { Ext.Msg.alert('Erreur', 'Suppression impossible.'); } });
                        });
                }
            }
        }
    };
};

Usp.catalogue.promotionForm = function (store, rec) {
    var win = Ext.create('Ext.window.Window', {
        title: rec ? 'Modifier la promotion' : 'Nouvelle promotion',
        width: 460, modal: true, bodyPadding: 12,
        items: [{
            xtype: 'form', border: false, defaults: { anchor: '100%' },
            items: [
                { xtype: 'textfield', name: 'code', fieldLabel: 'Code', allowBlank: false, emptyText: 'ex. 6553' },
                { xtype: 'textfield', name: 'nom', fieldLabel: 'Nom', allowBlank: false },
                { xtype: 'textfield', name: 'description', fieldLabel: 'Description' },
                { xtype: 'datefield', name: 'dateDebut', fieldLabel: 'Date de début', format: 'd/m/Y', submitFormat: 'Y-m-d\\TH:i:s', editable: false },
                { xtype: 'datefield', name: 'dateFin', fieldLabel: 'Date de fin', format: 'd/m/Y', submitFormat: 'Y-m-d\\TH:i:s', editable: false },
                { xtype: 'checkbox', name: 'actif', fieldLabel: 'Active', checked: true }
            ]
        }],
        buttons: [{
            text: 'Enregistrer',
            handler: function (b) {
                var form = b.up('window').down('form').getForm();
                if (!form.isValid()) {
                    var manquant = form.getFields().findBy(function (f) { return !f.isValid(); });
                    if (manquant) { manquant.focus(true, 50);
                        Ext.Msg.alert('Champ obligatoire', 'Veuillez renseigner : <b>' +
                            (manquant.fieldLabel || manquant.getName()) + '</b>.'); }
                    return;
                }
                var data = form.getValues();
                data.actif = form.findField('actif').getValue();
                Usp.ajax({
                    url: rec ? '/promotions/' + rec.get('id') : '/promotions',
                    method: rec ? 'PUT' : 'POST', jsonData: data,
                    success: function () {
                        win.close(); store.load();
                        Usp.toastEnregistre('Promotion « ' + (data.nom || data.code || '') + ' »', !!rec);
                    },
                    failure: function (resp) {
                        var msg = 'Enregistrement impossible.';
                        try { msg = Ext.decode(resp.responseText).erreur || msg; } catch (e) {}
                        Ext.Msg.alert('Erreur', msg);
                    }
                });
            }
        }]
    });
    win.show();
    if (rec) { win.down('form').getForm().setValues(rec.getData()); }
};

/* ---------- Catégories et marques ---------- */
Usp.catalogue.simplePanel = function (titre, url, fields, formFields, root) {
    var store = Usp.catalogue.store(url, fields, root || '');
    // Ouvre le formulaire en création (rec null) ou modification.
    var ouvrir = function (rec) {
        var win = Ext.create('Ext.window.Window', {
            title: titre + (rec ? ' — modifier' : ' — nouveau'), width: 420, modal: true, bodyPadding: 12,
            items: [{ xtype: 'form', border: false, defaults: { anchor: '100%' }, items: Ext.clone(formFields) }],
            buttons: [{ text: 'Enregistrer', handler: function (b) {
                var form = b.up('window').down('form').getForm();
                if (!form.isValid()) { return; }
                Usp.ajax({ url: rec ? url + '/' + rec.get('id') : url, method: rec ? 'PUT' : 'POST',
                    jsonData: form.getValues(),
                    success: function () { win.close(); store.load(); Usp.toastEnregistre(titre, !!rec); },
                    failure: function (resp) {
                        var m = 'Enregistrement impossible.';
                        try { m = Ext.decode(resp.responseText).erreur || m; } catch (e) {}
                        Ext.Msg.alert('Erreur', m);
                    } });
            } }]
        });
        win.show();
        if (rec) { win.down('form').getForm().setValues(rec.getData()); }
    };
    var cols = fields.filter(function (f) { return f !== 'id'; }).map(function (f) {
        return { text: f, dataIndex: f, flex: 1 };
    });
    cols.push({ text: 'Actions', width: 100, align: 'center', sortable: false, menuDisabled: true, dataIndex: 'id',
        renderer: function () {
            return '<span class="sp-edit" title="Modifier" style="cursor:pointer;margin:0 4px">✏️</span>' +
                '<span class="sp-del" title="Supprimer" style="cursor:pointer;margin:0 4px;color:#c62828">🗑️</span>';
        } });
    return {
        xtype: 'grid', title: titre, store: store, columns: cols,
        tbar: [Usp.permBtn('catalogue', 'CREER', { text: '➕ Nouveau', tooltip: 'Ajouter une entrée', handler: function () { ouvrir(null); } })]
            .concat(Usp.export.boutons(titre)),
        listeners: {
            itemdblclick: function (g, rec) { ouvrir(rec); },
            cellclick: function (g, td, ci, rec, tr, ri, e) {
                if (e.getTarget('.sp-edit')) { ouvrir(rec); }
                else if (e.getTarget('.sp-del')) {
                    Ext.Msg.confirm('Supprimer', 'Supprimer cette entrée ? Les articles éventuellement liés ' +
                        'seront réaffectés à « Standard ».', function (btn) {
                        if (btn !== 'yes') { return; }
                        Usp.ajax({ url: url + '/' + rec.get('id'), method: 'DELETE',
                            success: function () { store.load(); Usp.toast('Entrée supprimée (articles réaffectés à « Standard »).'); },
                            failure: function (resp) {
                                var m = 'Suppression impossible.';
                                try { m = Ext.decode(resp.responseText).erreur || m; } catch (e) {}
                                Ext.Msg.alert('Erreur', m);
                            } });
                    });
                }
            }
        }
    };
};

Usp.catalogue.panel = function () {
    return {
        xtype: 'tabpanel', title: 'Catalogue', listeners: Usp.tabListeners,
        items: [
            Usp.catalogue.articlesPanel(),
            Usp.catalogue.simplePanel('🗂️ Catégories', '/catalogue/categories',
                ['id', 'code', 'libelle', 'description'],
                [{ xtype: 'textfield', name: 'code', fieldLabel: 'Code', allowBlank: false },
                 { xtype: 'textfield', name: 'libelle', fieldLabel: 'Libellé', allowBlank: false },
                 { xtype: 'textfield', name: 'description', fieldLabel: 'Description' }]),
            Usp.catalogue.simplePanel('™️ Marques', '/catalogue/marques',
                ['id', 'code', 'nom', 'description'],
                [{ xtype: 'textfield', name: 'code', fieldLabel: 'Code', allowBlank: false },
                 { xtype: 'textfield', name: 'nom', fieldLabel: 'Nom', allowBlank: false },
                 { xtype: 'textfield', name: 'description', fieldLabel: 'Description' }])
        ]
    };
};
