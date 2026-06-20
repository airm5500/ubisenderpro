/*
 * UbiSenderPro - coquille ExtJS 4.2 (Phase 1)
 * Login, menu principal, grille des comptes clients et assistant d'import.
 * Cible REST : /ubisenderpro/api/v1
 */
Ext.Loader.setConfig({ enabled: true });

var Usp = {
    apiBase: 'api/v1',
    token: null,
    user: null
};

/* ---------- Appels REST avec jeton de session ---------- */
Usp.ajax = function (options) {
    options.url = Usp.apiBase + options.url;
    options.headers = options.headers || {};
    if (Usp.token) {
        options.headers['Authorization'] = 'Bearer ' + Usp.token;
    }
    if (options.jsonData && typeof options.jsonData === 'object') {
        options.headers['Content-Type'] = 'application/json';
    }
    Ext.Ajax.request(options);
};

/* ---------- Fenêtre de connexion ---------- */
Usp.showLogin = function () {
    Ext.create('Ext.window.Window', {
        title: 'UbiSenderPro - Connexion',
        width: 360,
        modal: true,
        closable: false,
        bodyPadding: 15,
        items: [{
            xtype: 'form',
            border: false,
            defaults: { anchor: '100%', labelWidth: 90 },
            items: [
                { xtype: 'textfield', name: 'login', fieldLabel: 'Identifiant', allowBlank: false, value: 'admin' },
                { xtype: 'textfield', name: 'motDePasse', fieldLabel: 'Mot de passe', inputType: 'password', allowBlank: false }
            ]
        }],
        buttons: [{
            text: 'Se connecter',
            formBind: true,
            handler: function (btn) {
                var win = btn.up('window');
                var form = win.down('form').getForm();
                if (!form.isValid()) { return; }
                var v = form.getValues();
                Usp.ajax({
                    url: '/auth/login',
                    method: 'POST',
                    jsonData: v,
                    success: function (resp) {
                        var data = Ext.decode(resp.responseText);
                        Usp.token = data.token;
                        Usp.user = data.user;
                        win.close();
                        Usp.showMain();
                    },
                    failure: function () {
                        Ext.Msg.alert('Erreur', 'Identifiants invalides.');
                    }
                });
            }
        }]
    }).show();
};

/* ---------- Store des comptes clients ---------- */
Usp.createClientStore = function () {
    return Ext.create('Ext.data.Store', {
        fields: ['id', 'numeroClient', 'nomCompte', 'agence', 'region', 'emailPrincipal', 'statut'],
        pageSize: 25,
        proxy: {
            type: 'ajax',
            url: Usp.apiBase + '/clients',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') },
            reader: { type: 'json', root: 'data', totalProperty: 'total' }
        },
        autoLoad: true
    });
};

/* ---------- Grille des comptes clients ---------- */
Usp.clientsPanel = function () {
    var store = Usp.createClientStore();
    return {
        xtype: 'grid',
        title: 'Comptes clients',
        store: store,
        columns: [
            { text: 'N° client', dataIndex: 'numeroClient', width: 110 },
            { text: 'Nom du compte', dataIndex: 'nomCompte', flex: 1 },
            { text: 'Agence', dataIndex: 'agence', width: 120 },
            { text: 'Région', dataIndex: 'region', width: 150 },
            { text: 'E-mail', dataIndex: 'emailPrincipal', width: 200 },
            { text: 'Statut', dataIndex: 'statut', width: 90 }
        ],
        tbar: [
            { xtype: 'textfield', emptyText: 'Rechercher...', width: 220, listeners: {
                change: function (f, val) {
                    store.getProxy().extraParams = { q: val };
                    store.loadPage(1);
                }, buffer: 400 } },
            '->',
            { text: 'Importer Excel/CSV', iconCls: 'x-fa', handler: Usp.showImport }
        ],
        bbar: {
            xtype: 'pagingtoolbar',
            store: store,
            displayInfo: true
        }
    };
};

/* ---------- Assistant d'import (simplifié) ---------- */
Usp.showImport = function () {
    // Délègue à l'assistant d'import générique (mapping, modèles, rejets).
    Usp.importer.show('CLIENTS', '/imports/clients', function () {
        var grid = Ext.ComponentQuery.query('#center grid')[0];
        if (grid && grid.getStore()) { grid.getStore().load(); }
    });
};

/* ---------- Tableau de bord ---------- */
Usp.dashboardPanel = function () {
    var panel = Ext.create('Ext.panel.Panel', {
        title: 'Tableau de bord', autoScroll: true, bodyPadding: 16,
        bodyStyle: 'background:#f4f6f8',
        html: '<div style="color:#999">Chargement des indicateurs…</div>',
        tbar: [{ text: 'Rafraîchir', handler: function () { charger(); } }]
    });
    var LIBELLES = {
        comptesClients: 'Comptes clients', contacts: 'Contacts',
        contactsWhatsapp: 'Contacts WhatsApp', contactsSansWhatsapp: 'Sans WhatsApp',
        contactsConsentement: 'Consentement', contactsDesabonnes: 'Désabonnés',
        articles: 'Articles', articlesActifs: 'Articles actifs', articlesRupture: 'En rupture',
        conversationsOuvertes: 'Conversations ouvertes', conversationsNonLues: 'Non lues',
        campagnesEnCours: 'Campagnes en cours', campagnesTerminees: 'Campagnes terminées',
        commandes: 'Commandes', opportunitesOuvertes: 'Opportunités ouvertes', imports: 'Imports'
    };
    function carte(lib, val) {
        return '<div style="display:inline-block;width:180px;margin:6px;padding:14px;background:#fff;' +
            'border:1px solid #e0e0e0;border-radius:8px;box-shadow:0 1px 2px rgba(0,0,0,.06)">' +
            '<div style="font-size:24px;font-weight:bold;color:#1976d2">' + (val == null ? '–' : val) + '</div>' +
            '<div style="font-size:12px;color:#666">' + lib + '</div></div>';
    }
    function charger() {
        Usp.ajax({
            url: '/dashboard', method: 'GET',
            success: function (resp) {
                var d = Ext.decode(resp.responseText) || {};
                var html = '';
                Ext.Object.each(LIBELLES, function (k, lib) { html += carte(lib, d[k]); });
                panel.update(html);
            },
            failure: function () { panel.update('<div style="color:#a00">Indicateurs indisponibles.</div>'); }
        });
    }
    panel.on('afterrender', charger);
    return panel;
};

/* ---------- Viewport principal ---------- */
Usp.showMain = function () {
    Ext.create('Ext.container.Viewport', {
        layout: 'border',
        items: [
            {
                region: 'north',
                xtype: 'toolbar',
                height: 40,
                items: [
                    { xtype: 'tbtext', text: '<b>UbiSenderPro</b>' },
                    '->',
                    { xtype: 'tbtext', text: Usp.user ? Usp.user.nomComplet : '' },
                    { text: 'Déconnexion', handler: function () {
                        Usp.ajax({ url: '/auth/logout', method: 'POST' });
                        location.reload();
                    } }
                ]
            },
            {
                region: 'west',
                title: 'Menu',
                width: 220,
                collapsible: true,
                xtype: 'treepanel',
                rootVisible: false,
                store: Ext.create('Ext.data.TreeStore', {
                    fields: ['text', 'view', 'leaf'],
                    root: { expanded: true, children: [
                        { text: 'Tableau de bord', leaf: true, view: 'dashboard' },
                        { text: 'Discussions', leaf: true, view: 'inbox' },
                        { text: 'Comptes clients', leaf: true, view: 'clients' },
                        { text: 'Contacts', leaf: true, view: 'clients' },
                        { text: 'Catalogue', leaf: true, view: 'catalogue' },
                        { text: 'Campagnes', leaf: true, view: 'campaigns' },
                        { text: 'CRM / Opportunités', leaf: true, view: 'crm' },
                        { text: 'Importations', leaf: true, view: 'import' },
                        { text: 'Paramètres', leaf: true, view: 'settings' }
                    ] }
                }),
                listeners: {
                    itemclick: function (v, rec) {
                        var vue = rec.get('view') || (rec.raw && rec.raw.view);
                        switch (vue) {
                            case 'inbox': Usp.loadCenter(Usp.inbox.panel()); break;
                            case 'catalogue': Usp.loadCenter(Usp.catalogue.panel()); break;
                            case 'campaigns': Usp.loadCenter(Usp.campaign.listPanel()); break;
                            case 'crm': Usp.loadCenter(Usp.crm.tabs()); break;
                            case 'settings': Usp.loadCenter(Usp.settings.tabs()); break;
                            case 'clients': Usp.loadCenter(Usp.clientsPanel()); break;
                            case 'dashboard': Usp.loadCenter(Usp.dashboardPanel()); break;
                            case 'import': Usp.showImport(); break;
                            default: Usp.loadCenter(Usp.clientsPanel());
                        }
                    }
                }
            },
            {
                region: 'center',
                xtype: 'panel',
                itemId: 'center',
                layout: 'fit',
                items: [Usp.dashboardPanel()]
            }
        ]
    });
};

Usp.loadCenter = function (cmp) {
    var center = Ext.ComponentQuery.query('#center')[0];
    center.removeAll();
    center.add(cmp);
};

Ext.onReady(function () {
    Ext.QuickTips.init();
    Usp.showLogin();
});
