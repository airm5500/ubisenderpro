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

/* Vue MARKETING : espace de travail transverse (calendrier de propositions,
 * et à venir les remontées Ruptures / Disponibilité). Ne contient PAS la
 * gestion des promotions, qui dispose de son propre menu (voir promotionsPanel). */
Usp.marketing.panel = function () {
    Usp.marketing._stores = [];
    return {
        xtype: 'tabpanel', title: 'Marketing', listeners: Usp.tabListeners,
        items: [
            Usp.marketing.calendrier(),
            Usp.marketing.propositions(),
            Usp.marketing.modelesMessages(),
            Usp.marketing.campagnesPromo(),
            Usp.marketing.performance()
        ]
    };
};

/* Vue PROMOTIONS : un onglet par statut. Menu dédié, distinct de Marketing. */
Usp.marketing.promotionsPanel = function () {
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
            Usp.permBtn('promotions', 'CREER', { text: '➕ Nouvelle promotion', tooltip: 'Créer une promotion', handler: function () { Usp.marketing.promotionForm(store, null); } }),
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
            failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
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

    var formItems = [{ xtype: 'form', itemId: 'promoForm', border: false, bodyPadding: '0 0 8 0',
        defaults: { anchor: '100%', labelWidth: 130 }, items: items }];

    if (rec) {
        formItems.push(Usp.marketing.produitsGrid(rec.get('id')));
    } else {
        formItems.push({ xtype: 'component', margin: '4 0 0 0',
            html: '<span style="color:#888">Enregistrez la promotion pour pouvoir y ajouter des produits.</span>' });
    }

    var win = Ext.create('Ext.window.Window', {
        title: rec ? 'Promotion — ' + Ext.String.htmlEncode(rec.get('nom')) : 'Nouvelle promotion',
        width: 840, height: rec ? 600 : 300, modal: true,
        layout: { type: 'vbox', align: 'stretch' }, bodyPadding: 12,
        maxHeight: Ext.getBody().getViewSize().height - 20,
        items: formItems,
        buttons: [{ text: 'Enregistrer', formBind: false, handler: function (b) {
            var form = b.up('window').down('#promoForm').getForm();
            if (!form.isValid()) { return; }
            if (!Usp.periodeValide(form.findField('dateDebut').getValue(), form.findField('dateFin').getValue())) { return; }
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
    var pForm = win.down('#promoForm').getForm();
    Usp.lierPeriode(pForm.findField('dateDebut'), pForm.findField('dateFin'));
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
        xtype: 'grid', title: 'Produits de la promotion', flex: 1, minHeight: 200, margin: '10 0 0 0', store: store,
        columns: [
            { text: 'CIP7', dataIndex: 'cip7', width: 90 },
            { text: 'CIP13', dataIndex: 'cip13', width: 120 },
            { text: 'Produit', dataIndex: 'nomProduit', flex: 1 },
            { text: 'Qté min', dataIndex: 'quantiteMinimale', width: 70, align: 'right' },
            { text: 'UG', dataIndex: 'tauxUg', width: 90, align: 'right',
              renderer: function (v, m, rec) {
                  if (v != null && v !== '') { return v + ' %'; }
                  var q = rec.get('quantiteUg');
                  return (q != null && q !== '') ? q + ' u.' : '';
              } },
            { text: 'Catalogue', dataIndex: 'articleId', width: 70, align: 'center',
              renderer: function (v) { return v ? '✅' : '—'; } },
            { text: 'Actions', width: 80, align: 'center', sortable: false, menuDisabled: true, dataIndex: 'id',
              renderer: function () {
                  return '<span class="pp-edit" title="Modifier" style="cursor:pointer;margin:0 4px">✏️</span>' +
                      '<span class="pp-del" title="Retirer" style="cursor:pointer;margin:0 4px;color:#c62828">🗑️</span>';
              } }
        ],
        tbar: [
            Usp.permBtn('promotions', 'CREER', { text: '➕ Ajouter un produit', handler: function () { Usp.marketing.produitForm(store, promotionId, null); } }),
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
    // Type d'UG déduit de l'enregistrement : pourcentage si un taux est présent, sinon nombre.
    var typeUg = 'TAUX', valeurUg = null;
    if (rec) {
        var t = rec.get('tauxUg'), q = rec.get('quantiteUg');
        if (t != null && t !== '' && Number(t) > 0) { typeUg = 'TAUX'; valeurUg = Number(t); }
        else if (q != null && q !== '' && Number(q) > 0) { typeUg = 'QUANTITE'; valeurUg = Number(q); }
    }

    // Recherche d'un article du catalogue : pré-remplit CIP7/CIP13/nom et lie l'article.
    var articleStore = Ext.create('Ext.data.Store', {
        fields: ['id', 'designation', 'cip', 'codeBarres'], pageSize: 20,
        proxy: { type: 'ajax', url: Usp.apiBase + '/articles', queryParam: 'q',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') },
            reader: { type: 'json', root: 'data', totalProperty: 'total' } }
    });

    var fld = function (n) { return win.down('form').getForm().findField(n); };

    var win = Ext.create('Ext.window.Window', {
        title: rec ? 'Modifier le produit' : 'Ajouter un produit', width: 560, modal: true, bodyPadding: 12,
        layout: 'fit',
        items: [{ xtype: 'form', border: false, defaults: { anchor: '100%', labelWidth: 180 }, items: [
            { xtype: 'combobox', fieldLabel: 'Produit du catalogue', name: '_recherche',
              store: articleStore, queryMode: 'remote', queryParam: 'q', minChars: 2, hideTrigger: true,
              displayField: 'designation', valueField: 'id', pageSize: 0,
              emptyText: 'Rechercher un article (désignation, CIP)…',
              listConfig: { getInnerTpl: function () {
                  return '{designation} <span style="color:#999">{cip}</span>'; } },
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
            { xtype: 'numberfield', name: 'quantiteMinimale', fieldLabel: 'Quantité minimale commandée',
              minValue: 0, value: rec ? rec.get('quantiteMinimale') : null },
            { xtype: 'fieldcontainer', fieldLabel: 'Unité gratuite (UG)', layout: 'hbox',
              items: [
                { xtype: 'combobox', name: 'typeUg', width: 150, editable: false, queryMode: 'local',
                  value: typeUg, valueField: 'v', displayField: 't',
                  store: { fields: ['v', 't'], data: [
                      { v: 'TAUX', t: 'Pourcentage (%)' }, { v: 'QUANTITE', t: 'Nombre d\'unités' }] } },
                { xtype: 'numberfield', name: 'valeurUg', flex: 1, margin: '0 0 0 8', minValue: 0,
                  allowBlank: false, value: valeurUg, emptyText: 'Valeur de l\'UG' }
              ] },
            { xtype: 'checkbox', name: 'actif', fieldLabel: 'Actif', checked: rec ? rec.get('actif') : true }
        ] }],
        buttons: [
            { text: 'Annuler', handler: function () { win.close(); } },
            { text: 'Enregistrer', formBind: true, handler: function (b) {
                var form = b.up('window').down('form').getForm();
                if (!form.isValid()) { return; }
                var type = form.findField('typeUg').getValue();
                var valeur = form.findField('valeurUg').getValue();
                var aid = form.findField('articleId').getValue();
                var payload = {
                    articleId: aid ? Number(aid) : null,
                    cip7: form.findField('cip7').getValue() || null,
                    cip13: form.findField('cip13').getValue() || null,
                    nomProduit: form.findField('nomProduit').getValue() || null,
                    quantiteMinimale: form.findField('quantiteMinimale').getValue(),
                    modeCalcul: type,
                    tauxUg: type === 'TAUX' ? valeur : null,
                    quantiteUg: type === 'QUANTITE' ? valeur : null,
                    actif: form.findField('actif').getValue()
                };
                Usp.ajax({ url: rec ? '/promotions/' + promotionId + '/produits/' + rec.get('id') : '/promotions/' + promotionId + '/produits',
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

/* =====================================================================
 * P2 - Calendrier marketing & propositions d'envoi
 * Les propositions sont générées automatiquement (annonce mensuelle,
 * lancement, rappels J-7/J-3/J-1) ; aucune ne devient une campagne sans
 * validation humaine.
 * ===================================================================== */

Usp.marketing.LIB_TYPE = {
    ANNONCE_MENSUELLE: '📣 Annonce', LANCEMENT: '🚀 Lancement',
    RAPPEL_J7: '⏰ Rappel J-7', RAPPEL_J3: '⏰ Rappel J-3', RAPPEL_J1: '⏰ Rappel J-1',
    // Types issus du module Disponibilités & Ruptures.
    ANNONCE_DISPONIBILITE: '🟢 Disponibilité', RETOUR_RUPTURE: '🔵 Retour de rupture',
    RISQUE_RUPTURE: '🟠 Risque de rupture', RUPTURE_CONFIRMEE: '🔴 Rupture confirmée',
    STOCK_LIMITE: '🟡 Stock limité',
    // Types issus du module Informations Clients.
    RETARD_LIVRAISON: '🚚 Retard livraison', MODIFICATION_TOURNEE: '🚚 Modif. tournée',
    ANNULATION_TOURNEE: '🚫 Annulation tournée', REPRISE_LIVRAISON: '✅ Reprise livraison',
    INFORMATION_GARDE: '🏥 Garde', INFORMATION_JOUR_FERIE: '📅 Jour férié',
    MODIFICATION_HORAIRES: '🕒 Horaires', FERMETURE_AGENCE: '🚪 Fermeture',
    INFORMATION_GENERALE: '📢 Info générale', ALERTE_URGENTE: '🚨 Alerte', ANNIVERSAIRE_CLIENT: '🎂 Anniversaire'
};
Usp.marketing.LIB_SOURCE = { PROMO: '🏷️ Promo', DISPO: '📦 Dispo', INFO: '📨 Info' };
Usp.marketing.COULEUR_PROP = {
    PROPOSEE: '#1976d2', VALIDEE: '#2e7d32', REJETEE: '#c62828', EXPIREE: '#777'
};

Usp.marketing.propStatutRenderer = function (v) {
    var c = Usp.marketing.COULEUR_PROP[v] || '#333';
    return '<span style="color:' + c + ';font-weight:bold">' + (v || '') + '</span>';
};

Usp.marketing.MOIS_FR = ['Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin',
    'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre'];

/* Onglet « Calendrier » : vue mensuelle visuelle des propositions par échéance. */
Usp.marketing.calendrier = function () {
    var now = new Date();
    var state = { y: now.getFullYear(), m: now.getMonth() };

    var render = function (panel) {
        var lbl = panel.down('#calLabel');
        if (lbl) { lbl.setText(Usp.marketing.MOIS_FR[state.m] + ' ' + state.y); }
        Usp.ajax({ url: '/propositions', method: 'GET',
            success: function (resp) {
                var data = []; try { data = Ext.decode(resp.responseText) || []; } catch (e) {}
                var c = panel.down('#calBody');
                if (c) { c.update(Usp.marketing._calHtml(state.y, state.m, data)); }
            },
            failure: function () {
                var c = panel.down('#calBody');
                if (c) { c.update('<div style="padding:20px;color:#c62828">Chargement impossible.</div>'); }
            } });
    };

    return {
        xtype: 'panel', title: '📅 Calendrier', layout: { type: 'vbox', align: 'stretch' },
        tbar: [
            { text: '◀', tooltip: 'Mois précédent', handler: function (b) {
                if (--state.m < 0) { state.m = 11; state.y--; } render(b.up('panel')); } },
            { xtype: 'tbtext', itemId: 'calLabel', text: Usp.marketing.MOIS_FR[state.m] + ' ' + state.y },
            { text: '▶', tooltip: 'Mois suivant', handler: function (b) {
                if (++state.m > 11) { state.m = 0; state.y++; } render(b.up('panel')); } },
            { text: 'Aujourd\'hui', handler: function (b) {
                var d = new Date(); state.y = d.getFullYear(); state.m = d.getMonth(); render(b.up('panel')); } },
            '->',
            { text: '🔄 Rafraîchir', handler: function (b) { render(b.up('panel')); } }
        ],
        items: [{ xtype: 'container', itemId: 'calBody', flex: 1, autoScroll: true,
            style: 'background:#fff;padding:6px',
            html: '<div style="padding:20px;color:#888">Chargement…</div>' }],
        listeners: { afterrender: function (p) { render(p); } }
    };
};

/* Construit le tableau HTML du mois (lundi -> dimanche). */
Usp.marketing._calHtml = function (y, m, data) {
    var parJour = {};
    (data || []).forEach(function (p) {
        if (!p.datePrevue) { return; }
        var s = String(p.datePrevue).substring(0, 10).split('-');
        if (parseInt(s[0], 10) === y && parseInt(s[1], 10) === (m + 1)) {
            var d = parseInt(s[2], 10);
            (parJour[d] = parJour[d] || []).push(p);
        }
    });

    var jourSemaine = (new Date(y, m, 1).getDay() + 6) % 7; // lundi = 0
    var nbJours = new Date(y, m + 1, 0).getDate();
    var au = new Date();
    var estAujd = function (d) { return au.getFullYear() === y && au.getMonth() === m && au.getDate() === d; };

    var vide = function () { return '<td style="border:1px solid #eee;height:96px;background:#fafafa"></td>'; };
    var jour = function (d, items, today) {
        var h = '<td style="border:1px solid #e0e0e0;height:96px;vertical-align:top;padding:3px;background:'
            + (today ? '#e3f2fd' : '#fff') + '">';
        h += '<div style="font-size:12px;font-weight:bold;color:#555">' + d + '</div>';
        items.forEach(function (p) {
            var c = Usp.marketing.COULEUR_PROP[p.statut] || '#1976d2';
            var lib = Usp.marketing.LIB_TYPE[p.type] || p.type || '';
            h += '<div title="' + Ext.String.htmlEncode((p.titre || '') + ' — ' + (p.statut || '')) + '"'
                + ' style="margin:2px 0;padding:1px 4px;border-left:3px solid ' + c + ';background:#f7f7f7;'
                + 'font-size:11px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">'
                + Ext.String.htmlEncode(lib) + '</div>';
        });
        return h + '</td>';
    };

    var html = '<table style="width:100%;border-collapse:collapse;table-layout:fixed"><tr>';
    ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'].forEach(function (e) {
        html += '<th style="border:1px solid #e0e0e0;padding:4px;background:#f5f5f5;font-size:12px">' + e + '</th>';
    });
    html += '</tr><tr>';
    var col = 0, i;
    for (i = 0; i < jourSemaine; i++) { html += vide(); col++; }
    for (var d = 1; d <= nbJours; d++) {
        if (col === 7) { html += '</tr><tr>'; col = 0; }
        html += jour(d, parJour[d] || [], estAujd(d));
        col++;
    }
    while (col < 7) { html += vide(); col++; }
    return html + '</tr></table>';
};

/* Onglet « Modèles de messages » : modèles marketing (promo + dispo/rupture).
 * Agrégateur commun (spec §10). Réutilise le formulaire des Paramètres ->
 * les modèles restent partagés (visibles dans le menu Modèles, et inversement). */
Usp.marketing.modelesMessages = function () {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'nom', 'typeModele', 'langue', 'categorie', 'enteteTexte', 'enteteMediaType',
                 'enteteMediaUrl', 'corps', 'piedDePage', 'boutonsJson', 'nomModeleWhatsapp',
                 'segmentationId', 'statutApprobation', 'actif', 'cleSysteme'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/templates',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
        autoLoad: true,
        filters: [{ filterFn: function (rec) {
            // Modèles marketing : catégorie MARKETING ou modèle système prédéfini.
            return String(rec.get('categorie') || '').toUpperCase() === 'MARKETING'
                || !!rec.get('cleSysteme'); } }]
    });
    return {
        xtype: 'grid', title: '📝 Modèles de messages', store: store,
        columns: [
            { text: 'Nom', dataIndex: 'nom', flex: 1 },
            { text: 'Type', dataIndex: 'typeModele', width: 140 },
            { text: 'Langue', dataIndex: 'langue', width: 70 },
            { text: 'Pièce jointe', dataIndex: 'enteteMediaType', width: 150, renderer: function (v, m, rec) {
                if (!v) { return '—'; }
                var url = rec.get('enteteMediaUrl');
                return url ? '<a href="' + Ext.String.htmlEncode(url) + '" target="_blank">' + Ext.String.htmlEncode(v) + '</a>' : v;
            } },
            { text: 'Approbation', dataIndex: 'statutApprobation', width: 120 },
            { text: 'Actions', width: 90, align: 'center', sortable: false, menuDisabled: true, dataIndex: 'id',
              renderer: function () {
                  return '<span class="mp-edit" title="Modifier" style="cursor:pointer;margin:0 4px">✏️</span>' +
                      '<span class="mp-del" title="Supprimer" style="cursor:pointer;margin:0 4px;color:#c62828">🗑️</span>';
              } }
        ],
        tbar: [
            Usp.permBtn('marketing', 'CREER', { text: '➕ Nouveau modèle', handler: function () { Usp.settings.templateForm(store, null); } }),
            { text: '🔄 Rafraîchir', handler: function () { store.load(); } }
        ],
        listeners: {
            itemdblclick: function (g, rec) { Usp.settings.templateForm(store, rec); },
            cellclick: function (g, td, ci, rec, tr, ri, e) {
                if (e.getTarget('.mp-edit')) { Usp.settings.templateForm(store, rec); }
                else if (e.getTarget('.mp-del')) {
                    Ext.Msg.confirm('Supprimer', 'Supprimer le modèle « ' + Ext.String.htmlEncode(rec.get('nom')) + ' » ?',
                        function (btn) {
                            if (btn !== 'yes') { return; }
                            Usp.ajax({ url: '/templates/' + rec.get('id'), method: 'DELETE',
                                success: function () { store.load(); Usp.toast('Modèle supprimé.'); },
                                failure: function () { Ext.Msg.alert('Erreur', 'Suppression impossible.'); } });
                        });
                }
            }
        }
    };
};

/* Onglet « Campagnes promo » : campagnes de catégorie PROMOTION + leurs stats. */
Usp.marketing.campagnesPromo = function () {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'nom', 'statut', 'canal', 'categorie', 'nbDestinataires',
                 'nbEnvoyes', 'nbDistribues', 'nbLus', 'nbEchoues'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/campaigns',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
        autoLoad: true,
        // Campagnes marketing : issues des promotions ou des disponibilités/ruptures.
        filters: [{ filterFn: function (rec) {
            var c = String(rec.get('categorie') || '').toUpperCase();
            return c === 'PROMOTION' || c === 'DISPONIBILITE' || c === 'INFORMATION'; } }]
    });
    var libCat = function (v) {
        var c = String(v || '').toUpperCase();
        return c === 'DISPONIBILITE' ? '📦 Dispo' : (c === 'PROMOTION' ? '🏷️ Promo'
            : (c === 'INFORMATION' ? '📨 Info' : (v || '')));
    };
    return {
        xtype: 'grid', title: '🚀 Campagnes', store: store,
        columns: [
            { text: 'Nom', dataIndex: 'nom', flex: 1 },
            { text: 'Source', dataIndex: 'categorie', width: 90, renderer: libCat },
            { text: 'Canal', dataIndex: 'canal', width: 70, renderer: function (v) { return v === 'WEB' ? 'WA Web' : 'API'; } },
            { text: 'Statut', dataIndex: 'statut', width: 110, renderer: Usp.campaign.statutRenderer },
            { text: 'Destinataires', dataIndex: 'nbDestinataires', width: 100, align: 'right' },
            { text: 'Envoyés', dataIndex: 'nbEnvoyes', width: 75, align: 'right' },
            { text: 'Distribués', dataIndex: 'nbDistribues', width: 85, align: 'right' },
            { text: 'Lus', dataIndex: 'nbLus', width: 60, align: 'right' },
            { text: 'Échoués', dataIndex: 'nbEchoues', width: 75, align: 'right' },
            { text: 'Taux', dataIndex: 'nbEnvoyes', width: 70, align: 'right', renderer: Usp.campaign.tauxRenderer }
        ],
        tbar: [
            { text: '🔄 Rafraîchir', handler: function () { store.load(); } },
            '->',
            { text: 'Ouvrir le menu Campagnes', tooltip: 'Aller à la gestion complète des campagnes',
              handler: function () { Usp.ouvrirVue('campaigns'); } }
        ]
    };
};

/* Onglet « Performance » (§18) : agrégats d'envoi des campagnes + filtres. */
Usp.marketing.performance = function () {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'nom', 'canal', 'categorie', 'statut', 'nbDestinataires', 'nbEnvoyes',
                 'nbDistribues', 'nbLus', 'nbRepondus', 'nbEchoues',
                 'tauxDistribution', 'tauxLecture', 'tauxReponse']
    });
    var charger = function (panel) {
        var tb = panel.down('toolbar');
        var du = tb.down('[name=du]').getSubmitValue() || '';
        var au = tb.down('[name=au]').getSubmitValue() || '';
        var canal = tb.down('[name=canal]').getValue() || '';
        var cat = tb.down('[name=categorie]').getValue() || '';
        Usp.ajax({ url: '/campaigns/performance?du=' + du + '&au=' + au + '&canal=' + canal + '&categorie=' + cat,
            method: 'GET',
            success: function (resp) {
                var r = Ext.decode(resp.responseText) || {};
                store.loadData(r.lignes || []);
                var s = panel.down('#perfSummary');
                if (s) { s.update(Usp.marketing._perfHtml(r.totaux || {})); }
            },
            failure: function () { Ext.Msg.alert('Erreur', 'Chargement de la performance impossible.'); } });
    };
    return {
        xtype: 'panel', title: '📈 Performance', layout: { type: 'vbox', align: 'stretch' },
        tbar: [
            'Du', { xtype: 'datefield', name: 'du', format: 'd/m/Y', submitFormat: 'Y-m-d', width: 110 },
            'Au', { xtype: 'datefield', name: 'au', format: 'd/m/Y', submitFormat: 'Y-m-d', width: 110 },
            { xtype: 'combobox', name: 'canal', width: 130, editable: false, queryMode: 'local', value: '',
              store: [['', 'Tous canaux'], ['WEB', 'WhatsApp Web'], ['API', 'API']] },
            { xtype: 'combobox', name: 'categorie', width: 150, editable: false, queryMode: 'local', value: '',
              store: [['', 'Toutes sources'], ['PROMOTION', 'Promotions'], ['DISPONIBILITE', 'Dispo / Ruptures'], ['INFORMATION', 'Informations']] },
            { text: '🔎 Appliquer', handler: function (b) { charger(b.up('panel')); } },
            '->',
            { text: '🔄 Rafraîchir', handler: function (b) { charger(b.up('panel')); } }
        ],
        items: [
            { xtype: 'component', itemId: 'perfSummary', style: 'padding:10px;background:#fafafa;border-bottom:1px solid #eee', html: '' },
            { xtype: 'grid', flex: 1, store: store, columns: [
                { text: 'Campagne', dataIndex: 'nom', flex: 1 },
                { text: 'Source', dataIndex: 'categorie', width: 100, renderer: function (v) {
                    return v === 'DISPONIBILITE' ? '📦 Dispo' : (v === 'PROMOTION' ? '🏷️ Promo' : (v || '')); } },
                { text: 'Canal', dataIndex: 'canal', width: 70, renderer: function (v) { return v === 'WEB' ? 'WA Web' : 'API'; } },
                { text: 'Ciblés', dataIndex: 'nbDestinataires', width: 75, align: 'right' },
                { text: 'Envoyés', dataIndex: 'nbEnvoyes', width: 75, align: 'right' },
                { text: 'Distribués', dataIndex: 'nbDistribues', width: 85, align: 'right' },
                { text: 'Lus', dataIndex: 'nbLus', width: 60, align: 'right' },
                { text: 'Réponses', dataIndex: 'nbRepondus', width: 80, align: 'right' },
                { text: 'Échoués', dataIndex: 'nbEchoues', width: 75, align: 'right' },
                { text: '% Distrib.', dataIndex: 'tauxDistribution', width: 80, align: 'right', renderer: function (v) { return (v || 0) + ' %'; } },
                { text: '% Lecture', dataIndex: 'tauxLecture', width: 80, align: 'right', renderer: function (v) { return (v || 0) + ' %'; } },
                { text: '% Réponse', dataIndex: 'tauxReponse', width: 80, align: 'right', renderer: function (v) { return (v || 0) + ' %'; } }
            ] }
        ],
        listeners: { afterrender: function (p) { charger(p); } }
    };
};

Usp.marketing._perfHtml = function (t) {
    var b = function (l, v) {
        return '<span style="display:inline-block;min-width:130px;margin:0 14px 6px 0">' +
            '<b style="font-size:16px">' + v + '</b><br><span style="color:#888;font-size:11px">' + l + '</span></span>';
    };
    return '<div style="font-family:sans-serif">' +
        b('Campagnes', t.nbCampagnes || 0) + b('Ciblés', t.cibles || 0) + b('Envoyés', t.envoyes || 0) +
        b('Distribués', (t.distribues || 0) + ' (' + (t.tauxDistribution || 0) + '%)') +
        b('Lus', (t.lus || 0) + ' (' + (t.tauxLecture || 0) + '%)') +
        b('Réponses', (t.repondus || 0) + ' (' + (t.tauxReponse || 0) + '%)') +
        b('Échoués', t.echoues || 0) + '</div>';
};

/* Un onglet = un statut de proposition (Proposées / Validées / Rejetées / Expirées / Toutes). */
Usp.marketing.propositionsGrid = function (statut, titre) {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'cle', 'type', 'source', 'promotionId', 'evenementId', 'titre', 'message', 'datePrevue',
                 'statut', 'campagneId', 'listeId', 'segmentId', 'motifRejet'],
        groupField: 'datePrevue',
        proxy: { type: 'ajax', url: Usp.apiBase + '/propositions',
            extraParams: { statut: statut },
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
        autoLoad: true
    });
    Usp.marketing._stores.push(store);

    var generer = function () {
        Usp.ajax({ url: '/propositions/generer', method: 'POST',
            success: function (resp) {
                var r = {}; try { r = Ext.decode(resp.responseText) || {}; } catch (e) {}
                Usp.marketing.reloadAll();
                Usp.toast((r.crees || 0) + ' proposition(s) créée(s), ' + (r.expirees || 0) + ' expirée(s).');
            },
            failure: function () { Ext.Msg.alert('Erreur', 'Génération impossible.'); } });
    };

    return {
        xtype: 'grid', title: titre, store: store,
        features: [{ ftype: 'grouping',
            groupHeaderTpl: 'Échéance : {name:this.fdate} ({rows.length})',
            startCollapsed: false,
            fdate: function (v) { return v ? String(v).substring(0, 10) : '(sans date)'; } }],
        columns: [
            { text: 'Source', dataIndex: 'source', width: 90,
              renderer: function (v) { return Usp.marketing.LIB_SOURCE[v] || v || '🏷️ Promo'; } },
            { text: 'Type', dataIndex: 'type', width: 140,
              renderer: function (v) { return Usp.marketing.LIB_TYPE[v] || v; } },
            { text: 'Titre', dataIndex: 'titre', flex: 1 },
            { text: 'Statut', dataIndex: 'statut', width: 100, renderer: Usp.marketing.propStatutRenderer },
            { text: 'Lien', dataIndex: 'campagneId', width: 110, align: 'center',
              renderer: function (v, m, rec) {
                  if (v) { return '<a class="pm-gocamp" href="#" data-id="' + v + '">→ Campagne</a>'; }
                  if (rec.get('statut') === 'REJETEE') {
                      return '<span title="' + Ext.String.htmlEncode(rec.get('motifRejet') || '') + '">motif</span>';
                  }
                  return '';
              } },
            { text: 'Actions', width: 200, align: 'left', sortable: false, menuDisabled: true, dataIndex: 'id',
              renderer: function (v, m, rec) {
                  var propose = rec.get('statut') === 'PROPOSEE';
                  var act = propose
                      ? '<span class="pm-ok" title="Valider (créer la campagne brouillon)" style="cursor:pointer;color:#2e7d32;margin-right:10px">✅ Valider</span>'
                        + '<span class="pm-no" title="Rejeter" style="cursor:pointer;color:#c62828;margin-right:10px">✖ Rejeter</span>'
                      : '';
                  return act + '<span class="pm-see" title="Voir le message proposé" style="cursor:pointer;color:#1976d2">👁️ Voir</span>';
              } }
        ],
        listeners: {
            cellclick: function (g, td, ci, rec, tr, ri, e) {
                var a = e.getTarget('.pm-gocamp');
                if (a) { e.preventDefault(); Usp.ouvrirVue('campaigns'); return; }
                if (e.getTarget('.pm-ok')) { Usp.marketing.validerProposition(rec, store); return; }
                if (e.getTarget('.pm-no')) { Usp.marketing.rejeterProposition(rec, store); return; }
                if (e.getTarget('.pm-see')) {
                    Ext.Msg.show({ title: Ext.String.htmlEncode(rec.get('titre')),
                        msg: '<pre style="white-space:pre-wrap;font-family:inherit">' +
                             Ext.String.htmlEncode(rec.get('message') || '') + '</pre>',
                        buttons: Ext.Msg.OK, width: 460 });
                    return;
                }
            }
        },
        tbar: ['->',
            { text: '🔄 Générer maintenant', tooltip: 'Régénère les propositions à partir des promotions', handler: generer },
            { text: '↻ Rafraîchir', handler: function () { store.load(); } }
        ]
    };
};

/* Propositions d'envoi présentées en sous-onglets par statut (au lieu d'une liste « Afficher : »). */
Usp.marketing.propositions = function () {
    return {
        xtype: 'tabpanel', title: '📨 Propositions d\'envoi', listeners: Usp.tabListeners,
        items: [
            Usp.marketing.propositionsGrid('PROPOSEE', 'Proposées'),
            Usp.marketing.propositionsGrid('VALIDEE', 'Validées'),
            Usp.marketing.propositionsGrid('REJETEE', 'Rejetées'),
            Usp.marketing.propositionsGrid('EXPIREE', 'Expirées'),
            Usp.marketing.propositionsGrid('', 'Toutes')
        ]
    };
};

/* Validation : crée une campagne brouillon ; audience facultative choisie ici. */
Usp.marketing.validerProposition = function (rec, store) {
    var win = Ext.create('Ext.window.Window', {
        title: 'Valider — ' + Ext.String.htmlEncode(rec.get('titre')),
        width: 480, modal: true, bodyPadding: 12, layout: 'fit',
        items: [{ xtype: 'form', itemId: 'vForm', border: false, defaults: { anchor: '100%', labelWidth: 140 },
            items: [
                { xtype: 'displayfield', value: '<span style="color:#888">La proposition deviendra une ' +
                  '<b>campagne en brouillon</b> (aucun envoi automatique). L\'audience est facultative ' +
                  'et modifiable ensuite dans le module Campagnes.</span>' },
                Usp.campaign.combo('/lists', '', 'id', 'nom',
                    { name: 'listeId', fieldLabel: 'Liste de diffusion', value: rec.get('listeId') }),
                Usp.campaign.combo('/segments', '', 'id', 'nom',
                    { name: 'segmentId', fieldLabel: 'Segment dynamique', value: rec.get('segmentId') })
            ] }],
        buttons: [
            { text: 'Annuler', handler: function (b) { b.up('window').close(); } },
            { text: 'Valider', formBind: false, handler: function (b) {
                var v = b.up('window').down('#vForm').getForm().getValues();
                Usp.ajax({ url: '/propositions/' + rec.get('id') + '/valider', method: 'POST',
                    jsonData: { listeId: v.listeId || null, segmentId: v.segmentId || null },
                    success: function () {
                        win.close(); store.load();
                        Usp.toast('Campagne brouillon créée. Complétez-la dans Campagnes.');
                    },
                    failure: function (resp) {
                        var m = 'Validation impossible.';
                        try { m = Ext.decode(resp.responseText).erreur || m; } catch (e) {}
                        Ext.Msg.alert('Erreur', m);
                    } });
            } }
        ]
    });
    win.show();
};

Usp.marketing.rejeterProposition = function (rec, store) {
    Ext.Msg.prompt('Rejeter la proposition', 'Motif (facultatif) :', function (btn, txt) {
        if (btn !== 'ok') { return; }
        Usp.ajax({ url: '/propositions/' + rec.get('id') + '/rejeter', method: 'POST',
            jsonData: { motif: txt || null },
            success: function () { store.load(); Usp.toast('Proposition rejetée.'); },
            failure: function () { Ext.Msg.alert('Erreur', 'Rejet impossible.'); } });
    });
};
