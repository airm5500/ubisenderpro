/*
 * UbiSenderPro - CRM commercial (sections 23 et 24 de la spec).
 * Pipeline d'opportunités en Kanban avec glisser-déposer (HTML5) + commandes.
 * Dépend de app.js (objet Usp).
 */
Ext.define('Usp.crm', { singleton: true });

Usp.crm.STATUTS = [
    'NOUVEAU_CONTACT', 'CONTACTE', 'INTERESSE', 'PRODUITS_PROPOSES',
    'DEVIS_OU_COMMANDE', 'COMMANDE_CONFIRMEE', 'COMMANDE_PRETE',
    'COMMANDE_LIVREE', 'CLIENT_FIDELISE', 'PERDU'
];

Usp.crm._reload = null;

/* Libellé client normalisé : « (code) nom entreprise » (#8). */
Usp.crm.libelleClient = function (rec) {
    var code = rec.clientCode ? '(' + Ext.String.htmlEncode(rec.clientCode) + ') ' : '';
    var nom = rec.clientNom ? Ext.String.htmlEncode(rec.clientNom) : ('#' + (rec.clientId || '-'));
    var ent = rec.clientEntreprise ? ' <span style="color:#999">' + Ext.String.htmlEncode(rec.clientEntreprise) + '</span>' : '';
    return code + nom + ent;
};

Usp.crm.carte = function (rec) {
    var montant = rec.montantEstime ? Ext.util.Format.number(rec.montantEstime, '0,000') + ' F' : '';
    var lienClient = rec.clientId
        ? '<div style="font-size:11px"><span onclick="event.stopPropagation();Usp.crm.ouvrirClient(' + rec.clientId + ')" ' +
          'style="cursor:pointer;color:#1976d2;text-decoration:underline">👤 ' + Usp.crm.libelleClient(rec) + '</span></div>'
        : '<div style="font-size:11px;color:#555">Client : -</div>';
    return '<div class="usp-card" draggable="true" ondragstart="Usp.crm.drag(event,' + rec.id + ')" ' +
        'data-id="' + rec.id + '" style="background:#fff;border:1px solid #ddd;border-radius:6px;' +
        'padding:8px;margin:6px;cursor:move;box-shadow:0 1px 2px rgba(0,0,0,.1)">' +
        '<div style="font-weight:bold">Opp #' + rec.id + '</div>' +
        lienClient +
        (rec.contactNom ? '<div style="font-size:11px;color:#555">Contact : ' + Ext.String.htmlEncode(rec.contactNom) + '</div>' : '') +
        (rec.agentNom ? '<div style="font-size:11px;color:#555">Agent : ' + Ext.String.htmlEncode(rec.agentNom) + '</div>' : '') +
        (montant ? '<div style="font-size:11px;color:#2a7">' + montant + '</div>' : '') +
        (rec.prochaineAction ? '<div style="font-size:10px;color:#999">' + Ext.String.htmlEncode(rec.prochaineAction) + '</div>' : '') +
        '</div>';
};

/* Ouvre la fiche du client rattaché à une opportunité. */
Usp.crm.ouvrirClient = function (clientId) {
    if (!clientId || !Usp.clientForm || !Usp.createClientStore) { return; }
    var store = Usp.createClientStore(true);
    var rec = Ext.create(store.model, { id: clientId });
    Usp.clientForm(store, rec);
};

/* ---- Handlers de glisser-déposer (HTML5 natif) ---- */
Usp.crm.drag = function (ev, id) {
    ev.dataTransfer.setData('text/plain', id);
    ev.dataTransfer.effectAllowed = 'move';
};
Usp.crm.allow = function (ev) { ev.preventDefault(); ev.dataTransfer.dropEffect = 'move'; };
Usp.crm.drop = function (ev, statut) {
    ev.preventDefault();
    var id = ev.dataTransfer.getData('text/plain');
    if (id) { Usp.crm.changerStatut(id, statut, Usp.crm._reload); }
};

Usp.crm.changerStatut = function (id, statut, onDone) {
    Usp.ajax({
        url: '/opportunities/' + id + '/status', method: 'POST', jsonData: { statut: statut },
        success: function () { if (onDone) { onDone(); } },
        failure: function () { Ext.Msg.alert('Erreur', 'Changement de statut impossible.'); }
    });
};

Usp.crm.panel = function () {
    var board = Ext.create('Ext.panel.Panel', {
        title: 'Opportunités (pipeline)', layout: { type: 'hbox', align: 'stretch' },
        autoScroll: true, bodyStyle: 'background:#eceff1',
        tbar: [
            { text: 'Nouvelle opportunité', handler: function () { Usp.crm.oppForm(reload); } },
            { text: 'Rafraîchir', handler: function () { reload(); } },
            '->', { xtype: 'tbtext', text: 'Glissez une carte d\'une colonne à l\'autre pour changer d\'étape' }
        ]
    });

    function reload() {
        Usp.ajax({
            url: '/opportunities', method: 'GET',
            success: function (resp) {
                var data = Ext.decode(resp.responseText) || [];
                var parStatut = {};
                Usp.crm.STATUTS.forEach(function (s) { parStatut[s] = []; });
                data.forEach(function (o) { (parStatut[o.statut] || (parStatut[o.statut] = [])).push(o); });

                board.removeAll();
                Usp.crm.STATUTS.forEach(function (s) {
                    var cartes = (parStatut[s] || []).map(Usp.crm.carte).join('');
                    board.add({
                        xtype: 'panel', width: 200, margin: 4,
                        title: s + ' (' + (parStatut[s] || []).length + ')',
                        bodyStyle: 'background:#eceff1', autoScroll: true,
                        html: '<div class="usp-col" style="min-height:100%" ' +
                              'ondragover="Usp.crm.allow(event)" ondrop="Usp.crm.drop(event,\'' + s + '\')">' +
                              (cartes || '<div style="color:#999;padding:8px;font-size:11px">Déposez ici…</div>') +
                              '</div>'
                    });
                });
            }
        });
    }

    Usp.crm._reload = reload;
    board.on('afterrender', reload);
    return board;
};

/* Sélecteur de client (recherche distante par nom). */
Usp.crm.clientCombo = function (cfg) {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'nomCompte', 'numeroClient', 'entreprise'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/clients', queryParam: 'q',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') },
            reader: { type: 'json', root: 'data', totalProperty: 'total' } } });
    return Ext.apply({ xtype: 'combobox', store: store, valueField: 'id', displayField: 'nomCompte',
        queryMode: 'remote', minChars: 2, anchor: '100%', emptyText: 'Tapez 2 lettres…',
        listConfig: { getInnerTpl: function () {
            // Affichage normalisé « (code) nom entreprise » (#8).
            return '<b>({numeroClient})</b> {nomCompte} <span style="color:#999">{entreprise}</span>'; } } }, cfg || {});
};

/* Sélecteur de contact d'un client : peuplé après le choix du client
 * (un contact appartient à un client — correction #8). Local, alimenté via
 * Usp.crm.chargerContacts. */
Usp.crm.contactCombo = function (cfg) {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'nomComplet', 'telephonePrincipal', 'numeroWhatsapp', 'contactPrincipal'] });
    return Ext.apply({ xtype: 'combobox', store: store, valueField: 'id', displayField: 'nomComplet',
        queryMode: 'local', editable: false, anchor: '100%', disabled: true,
        emptyText: 'Choisissez d\'abord un client…',
        listConfig: { getInnerTpl: function () {
            return '{nomComplet} <span style="color:#999">{numeroWhatsapp}{[values.contactPrincipal ? " ★" : ""]}</span>'; } } }, cfg || {});
};

/* Charge les contacts (numéros) d'un client dans un combo « contact ». */
Usp.crm.chargerContacts = function (combo, clientId) {
    if (!combo) { return; }
    combo.clearValue();
    var store = combo.getStore();
    store.removeAll();
    if (!clientId) { combo.setDisabled(true); return; }
    Usp.ajax({ url: '/clients/' + clientId + '/contacts', method: 'GET', success: function (resp) {
        var d = {}; try { d = Ext.decode(resp.responseText) || {}; } catch (e) {}
        var rows = d.data || (Ext.isArray(d) ? d : []);
        store.loadData(rows);
        combo.setDisabled(rows.length === 0);
        if (!rows.length) { combo.emptyText = 'Aucun contact pour ce client'; if (combo.applyEmptyText) { combo.applyEmptyText(); } }
    } });
};

/* Sélecteur d'agent (utilisateurs affectables). */
Usp.crm.agentCombo = function (cfg) {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'nomComplet'], autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/users/affectables',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } } });
    return Ext.apply({ xtype: 'combobox', store: store, valueField: 'id', displayField: 'nomComplet',
        queryMode: 'local', editable: false, anchor: '100%', emptyText: 'Choisir un agent…' }, cfg || {});
};

Usp.crm.oppForm = function (reload) {
    var win = Ext.create('Ext.window.Window', {
        title: 'Nouvelle opportunité', width: 420, modal: true, bodyPadding: 12,
        items: [{ xtype: 'form', border: false, defaults: { anchor: '100%', labelWidth: 120 }, items: [
            Usp.crm.clientCombo({ name: 'clientId', fieldLabel: 'Client', allowBlank: false,
                listeners: { select: function (cb, r) {
                    var a = Ext.isArray(r) ? r[0] : r;
                    var contact = cb.up('form').down('[name=contactId]');
                    Usp.crm.chargerContacts(contact, a ? a.get('id') : cb.getValue());
                } } }),
            Usp.crm.contactCombo({ name: 'contactId', fieldLabel: 'Contact' }),
            Usp.crm.agentCombo({ name: 'agentId', fieldLabel: 'Agent' }),
            { xtype: 'numberfield', name: 'montantEstime', fieldLabel: 'Montant estimé', hideTrigger: true, minValue: 0 },
            { xtype: 'textfield', name: 'prochaineAction', fieldLabel: 'Prochaine action' },
            { xtype: 'combobox', name: 'statut', fieldLabel: 'Étape', value: 'NOUVEAU_CONTACT',
              store: Usp.crm.STATUTS, editable: false, queryMode: 'local' }
        ] }],
        buttons: [{ text: 'Créer', formBind: true, handler: function (b) {
            var form = b.up('window').down('form').getForm();
            if (!form.isValid()) { Ext.Msg.alert('Champs requis', 'Choisissez au moins un client.'); return; }
            var v = form.getValues();
            Usp.ajax({ url: '/opportunities', method: 'POST', jsonData: v,
                success: function () { win.close(); reload(); Usp.toastEnregistre('Opportunité', false); },
                failure: function () { Ext.Msg.alert('Erreur', 'Création impossible.'); } });
        } }]
    });
    win.show();
};

/* ---------- Commandes ---------- */
Usp.crm.ordersPanel = function () {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'numeroCommande', 'clientId', 'statut', 'dateCommande', 'montantTotal'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/orders',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
        autoLoad: true
    });
    return {
        xtype: 'grid', title: 'Commandes', store: store,
        columns: [
            { text: 'Numéro', dataIndex: 'numeroCommande', width: 160 },
            { text: 'Client', dataIndex: 'clientId', width: 90 },
            { text: 'Statut', dataIndex: 'statut', width: 160 },
            { text: 'Date', dataIndex: 'dateCommande', width: 150,
              renderer: function (v) { return v ? String(v).replace('T', ' ').substring(0, 16) : ''; } },
            { text: 'Total', dataIndex: 'montantTotal', flex: 1, align: 'right',
              renderer: function (v) { return Ext.util.Format.number(v, '0,000') + ' F'; } }
        ],
        tbar: [{ text: 'Rafraîchir', handler: function () { store.load(); } }]
    };
};

Usp.crm.tabs = function () {
    return { xtype: 'tabpanel', title: 'CRM', listeners: Usp.tabListeners, items: [Usp.crm.panel(), Usp.crm.ordersPanel()] };
};
