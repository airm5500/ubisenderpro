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
        ['id', 'codeArticle', 'designation', 'cip', 'codeBarres', 'prixVente',
         'prixPromotionnel', 'stockDisponible', 'unite', 'actif', 'publiable']);

    return {
        xtype: 'grid', title: 'Articles', store: store,
        columns: [
            { text: 'Code', dataIndex: 'codeArticle', width: 100 },
            { text: 'Désignation', dataIndex: 'designation', flex: 1 },
            { text: 'CIP', dataIndex: 'cip', width: 90 },
            { text: 'Prix', dataIndex: 'prixVente', width: 90, align: 'right',
              renderer: function (v) { return Ext.util.Format.number(v, '0,000') + ' F'; } },
            { text: 'Promo', dataIndex: 'prixPromotionnel', width: 90, align: 'right',
              renderer: function (v) { return v ? Ext.util.Format.number(v, '0,000') + ' F' : ''; } },
            { text: 'Stock', dataIndex: 'stockDisponible', width: 80, align: 'right' },
            { text: 'Unité', dataIndex: 'unite', width: 70 },
            { text: 'Actif', dataIndex: 'actif', width: 60, renderer: function (v) { return v ? 'Oui' : 'Non'; } }
        ],
        tbar: [
            { xtype: 'textfield', emptyText: 'Rechercher...', width: 220, listeners: {
                change: function (f, v) { store.getProxy().extraParams = { q: v }; store.loadPage(1); }, buffer: 400 } },
            '->',
            { text: 'Nouvel article', handler: function () { Usp.catalogue.articleForm(store, null); } },
            { text: 'Ajuster stock', handler: function (b) {
                var rec = b.up('grid').getSelectionModel().getSelection()[0];
                if (!rec) { Ext.Msg.alert('Info', 'Sélectionnez un article.'); return; }
                Usp.catalogue.stockForm(store, rec);
            } },
            { text: 'Importer', handler: function () { Usp.catalogue.importArticles(store); } }
        ],
        bbar: { xtype: 'pagingtoolbar', store: store, displayInfo: true },
        listeners: { itemdblclick: function (g, rec) { Usp.catalogue.articleForm(store, rec); } }
    };
};

Usp.catalogue.articleForm = function (store, rec) {
    var win = Ext.create('Ext.window.Window', {
        title: rec ? 'Modifier l\'article' : 'Nouvel article',
        width: 520, modal: true, bodyPadding: 12, layout: 'anchor',
        items: [{
            xtype: 'form', border: false, defaults: { anchor: '100%' },
            items: [
                { xtype: 'textfield', name: 'codeArticle', fieldLabel: 'Code article', allowBlank: false },
                { xtype: 'textfield', name: 'designation', fieldLabel: 'Désignation', allowBlank: false },
                { xtype: 'textfield', name: 'cip', fieldLabel: 'CIP' },
                { xtype: 'textfield', name: 'codeBarres', fieldLabel: 'Code-barres' },
                { xtype: 'numberfield', name: 'prixVente', fieldLabel: 'Prix de vente', allowBlank: false, minValue: 0 },
                { xtype: 'numberfield', name: 'prixPromotionnel', fieldLabel: 'Prix promotionnel', minValue: 0 },
                { xtype: 'numberfield', name: 'stockDisponible', fieldLabel: 'Stock disponible', minValue: 0 },
                { xtype: 'textfield', name: 'unite', fieldLabel: 'Unité' },
                { xtype: 'textfield', name: 'imageUrl', fieldLabel: 'Image URL' },
                { xtype: 'checkbox', name: 'publiable', fieldLabel: 'Publiable', checked: true }
            ]
        }],
        buttons: [{
            text: 'Enregistrer', formBind: true,
            handler: function (b) {
                var form = b.up('window').down('form').getForm();
                if (!form.isValid()) { return; }
                var data = form.getValues();
                data.publiable = form.findField('publiable').getValue();
                Usp.ajax({
                    url: rec ? '/articles/' + rec.get('id') : '/articles',
                    method: rec ? 'PUT' : 'POST', jsonData: data,
                    success: function () { win.close(); store.load(); },
                    failure: function () { Ext.Msg.alert('Erreur', 'Enregistrement impossible (code en doublon ?).'); }
                });
            }
        }]
    });
    win.show();
    if (rec) { win.down('form').getForm().setValues(rec.getData()); }
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

/* ---------- Catégories et marques ---------- */
Usp.catalogue.simplePanel = function (titre, url, fields, formFields, root) {
    var store = Usp.catalogue.store(url, fields, root || '');
    return {
        xtype: 'grid', title: titre, store: store,
        columns: fields.filter(function (f) { return f !== 'id'; }).map(function (f) {
            return { text: f, dataIndex: f, flex: 1 };
        }),
        tbar: [{ text: 'Nouveau', handler: function () {
            var win = Ext.create('Ext.window.Window', {
                title: titre, width: 420, modal: true, bodyPadding: 12,
                items: [{ xtype: 'form', border: false, defaults: { anchor: '100%' }, items: formFields }],
                buttons: [{ text: 'Enregistrer', handler: function (b) {
                    var form = b.up('window').down('form').getForm();
                    if (!form.isValid()) { return; }
                    Usp.ajax({ url: url, method: 'POST', jsonData: form.getValues(),
                        success: function () { win.close(); store.load(); },
                        failure: function () { Ext.Msg.alert('Erreur', 'Enregistrement impossible.'); } });
                } }]
            });
            win.show();
        } }]
    };
};

Usp.catalogue.panel = function () {
    return {
        xtype: 'tabpanel', title: 'Catalogue',
        items: [
            Usp.catalogue.articlesPanel(),
            Usp.catalogue.simplePanel('Catégories', '/catalogue/categories',
                ['id', 'code', 'libelle', 'description'],
                [{ xtype: 'textfield', name: 'code', fieldLabel: 'Code', allowBlank: false },
                 { xtype: 'textfield', name: 'libelle', fieldLabel: 'Libellé', allowBlank: false },
                 { xtype: 'textfield', name: 'description', fieldLabel: 'Description' }]),
            Usp.catalogue.simplePanel('Marques', '/catalogue/marques',
                ['id', 'code', 'nom', 'description'],
                [{ xtype: 'textfield', name: 'code', fieldLabel: 'Code', allowBlank: false },
                 { xtype: 'textfield', name: 'nom', fieldLabel: 'Nom', allowBlank: false },
                 { xtype: 'textfield', name: 'description', fieldLabel: 'Description' }])
        ]
    };
};
