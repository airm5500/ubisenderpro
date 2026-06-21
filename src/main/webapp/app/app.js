/*
 * UbiSenderPro - coquille ExtJS 4.2 (Phase 1)
 * Login, menu principal, grille des comptes clients et assistant d'import.
 * Cible REST : /ubisenderpro/api/v1
 */
Ext.Loader.setConfig({ enabled: true });

var Usp = {
    apiBase: 'api/v1',
    token: null,
    user: null,
    mode: 'API',   // mode d'envoi par défaut (API officielle | WEB) — chargé à la connexion
    prefixe: '225' // préfixe pays par défaut — chargé à la connexion
};

/* Normalise un numéro : chiffres seuls ; préfixe pays ajouté si saisie locale. */
Usp.normNumero = function (n) {
    var d = String(n || '').replace(/[^0-9]/g, '');
    if (!d) { return ''; }
    var p = Usp.prefixe || '';
    if (p && d.indexOf(p) !== 0 && d.length <= 10) {
        d = p + d.replace(/^0+/, '');
    }
    return d;
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
                        // Charge les paramètres globaux (mode + préfixe) puis ouvre l'application.
                        var ouvrir = function () { Usp.showMain(); };
                        Usp.ajax({ url: '/parametres/whatsapp.mode_envoi', method: 'GET',
                            success: function (r) {
                                Usp.mode = (Ext.decode(r.responseText) || {}).valeur || 'API';
                                Usp.ajax({ url: '/parametres/whatsapp.prefixe_pays', method: 'GET',
                                    success: function (r2) {
                                        Usp.prefixe = (Ext.decode(r2.responseText) || {}).valeur || '225';
                                        ouvrir();
                                    }, failure: ouvrir });
                            },
                            failure: ouvrir });
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
            { text: 'Statut', dataIndex: 'statut', width: 90 },
            { text: 'Contacts', width: 100, sortable: false, menuDisabled: true, dataIndex: 'id',
              renderer: function () {
                  return '<span class="cli-contacts" title="Voir / ajouter les contacts" ' +
                      'style="cursor:pointer;color:#1976d2">👥 contacts</span>';
              } }
        ],
        tbar: [
            { xtype: 'textfield', emptyText: 'Rechercher...', width: 220, listeners: {
                change: function (f, val) {
                    store.getProxy().extraParams = { q: val };
                    store.loadPage(1);
                }, buffer: 400 } },
            '->',
            { text: 'Nouveau client', handler: function () { Usp.clientForm(store, null); } },
            { text: 'Importer Excel/CSV', handler: Usp.showImport }
        ],
        bbar: {
            xtype: 'pagingtoolbar',
            store: store,
            displayInfo: true
        },
        listeners: {
            itemdblclick: function (g, rec) { Usp.clientForm(store, rec); },
            cellclick: function (g, td, ci, rec, tr, ri, e) {
                if (e.getTarget('.cli-contacts')) { Usp.contactsWindow(rec.get('id'), rec.get('nomCompte')); }
            }
        }
    };
};

/* ---------- Formulaire client ---------- */
Usp.segmentationCombo = function (cfg) {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'libelle'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/segmentations',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
        autoLoad: true
    });
    return Ext.apply({ xtype: 'combobox', store: store, valueField: 'id', displayField: 'libelle',
        queryMode: 'local', editable: false, anchor: '100%' }, cfg || {});
};

Usp.clientForm = function (store, rec) {
    var win = Ext.create('Ext.window.Window', {
        title: rec ? 'Modifier le client' : 'Nouveau client',
        width: 520, modal: true, bodyPadding: 12, autoScroll: true,
        items: [{
            xtype: 'form', border: false, defaults: { anchor: '100%' },
            items: [
                { xtype: 'textfield', name: 'numeroClient', fieldLabel: 'Numéro client', allowBlank: false },
                { xtype: 'textfield', name: 'nomCompte', fieldLabel: 'Nom du compte', allowBlank: false },
                { xtype: 'textfield', name: 'agence', fieldLabel: 'Agence' },
                { xtype: 'textfield', name: 'region', fieldLabel: 'Région' },
                { xtype: 'textfield', name: 'emailPrincipal', fieldLabel: 'E-mail', vtype: 'email' },
                Usp.segmentationCombo({ name: 'segmentationId', fieldLabel: 'Segmentation' }),
                { xtype: 'textfield', name: 'ville', fieldLabel: 'Ville' },
                { xtype: 'textfield', name: 'commune', fieldLabel: 'Commune' },
                { xtype: 'textfield', name: 'pays', fieldLabel: 'Pays' },
                { xtype: 'combobox', name: 'statut', fieldLabel: 'Statut', value: 'ACTIF',
                  store: ['PROSPECT', 'ACTIF', 'INACTIF', 'SUSPENDU', 'ARCHIVE'], queryMode: 'local' },
                { xtype: 'textareafield', name: 'notes', fieldLabel: 'Notes', height: 50 }
            ]
        }],
        buttons: [{
            text: 'Enregistrer', formBind: true,
            handler: function (b) {
                var form = b.up('window').down('form').getForm();
                if (!form.isValid()) { return; }
                Usp.ajax({
                    url: rec ? '/clients/' + rec.get('id') : '/clients',
                    method: rec ? 'PUT' : 'POST', jsonData: form.getValues(),
                    success: function () { win.close(); store.load(); },
                    failure: function (resp) {
                        Ext.Msg.alert('Erreur', 'Enregistrement impossible (numéro client en doublon ?).');
                    }
                });
            }
        }]
    });
    win.show();
    if (rec) {
        Usp.ajax({ url: '/clients/' + rec.get('id'), method: 'GET', success: function (resp) {
            win.down('form').getForm().setValues(Ext.decode(resp.responseText));
        } });
    }
};

/* ---------- Contacts d'un client ---------- */
Usp.contactsWindow = function (clientId, nomCompte) {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'nomComplet', 'fonction', 'telephonePrincipal', 'numeroWhatsapp',
                 'email', 'contactPrincipal', 'consentementWhatsapp', 'desabonne'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/clients/' + clientId + '/contacts',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') },
            reader: { type: 'json', root: 'data', totalProperty: 'total' } },
        autoLoad: true
    });
    Ext.create('Ext.window.Window', {
        title: 'Contacts — ' + nomCompte, width: 720, height: 420, modal: true, layout: 'fit',
        items: [{
            xtype: 'grid', store: store,
            columns: [
                { text: 'Nom', dataIndex: 'nomComplet', flex: 1 },
                { text: 'Fonction', dataIndex: 'fonction', width: 120 },
                { text: 'Téléphone', dataIndex: 'telephonePrincipal', width: 120 },
                { text: 'WhatsApp', dataIndex: 'numeroWhatsapp', width: 130 },
                { text: 'Principal', dataIndex: 'contactPrincipal', width: 70, renderer: function (v) { return v ? 'Oui' : ''; } },
                { text: 'Désab.', dataIndex: 'desabonne', width: 60, renderer: function (v) { return v ? 'Oui' : ''; } }
            ],
            tbar: [{ text: 'Nouveau contact', handler: function () { Usp.contactForm(clientId, store, null); } }],
            listeners: { itemdblclick: function (g, rec) { Usp.contactForm(clientId, store, rec); } }
        }]
    }).show();
};

Usp.contactForm = function (clientId, store, rec) {
    var win = Ext.create('Ext.window.Window', {
        title: rec ? 'Modifier le contact' : 'Nouveau contact', width: 480, modal: true, bodyPadding: 12,
        items: [{
            xtype: 'form', border: false, defaults: { anchor: '100%' },
            items: [
                { xtype: 'textfield', name: 'nomComplet', fieldLabel: 'Nom complet', allowBlank: false },
                { xtype: 'textfield', name: 'fonction', fieldLabel: 'Fonction' },
                { xtype: 'textfield', name: 'telephonePrincipal', fieldLabel: 'Téléphone' },
                { xtype: 'textfield', name: 'numeroWhatsapp', fieldLabel: 'Numéro WhatsApp',
                  emptyText: 'Format international, ex. 2250700000000' },
                { xtype: 'textfield', name: 'email', fieldLabel: 'E-mail', vtype: 'email' },
                { xtype: 'checkbox', name: 'contactPrincipal', boxLabel: 'Contact principal' },
                { xtype: 'checkbox', name: 'consentementWhatsapp', boxLabel: 'Consentement WhatsApp' }
            ]
        }],
        buttons: [{
            text: 'Enregistrer', formBind: true,
            handler: function (b) {
                var form = b.up('window').down('form').getForm();
                if (!form.isValid()) { return; }
                var data = form.getValues();
                data.clientId = clientId;
                data.contactPrincipal = form.findField('contactPrincipal').getValue();
                data.consentementWhatsapp = form.findField('consentementWhatsapp').getValue();
                Usp.ajax({
                    url: rec ? '/contacts/' + rec.get('id') : '/contacts',
                    method: rec ? 'PUT' : 'POST', jsonData: data,
                    success: function () { win.close(); store.load(); },
                    failure: function () { Ext.Msg.alert('Erreur', 'Enregistrement impossible.'); }
                });
            }
        }]
    });
    win.show();
    if (rec) { win.down('form').getForm().setValues(rec.getData()); }
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

/* ---------- Menu filtré par rôle ---------- */
Usp.MENU = [
    { text: 'Tableau de bord',     view: 'dashboard',  roles: null },
    { text: 'Discussions',         view: 'inbox',      roles: ['ADMIN', 'SUPERVISEUR', 'AGENT', 'MARKETING'] },
    { text: 'Comptes clients',     view: 'clients',    roles: ['ADMIN', 'MARKETING', 'SUPERVISEUR', 'AGENT', 'LECTURE'] },
    { text: 'Catalogue',           view: 'catalogue',  roles: ['ADMIN', 'CATALOGUE', 'LECTURE'] },
    { text: 'Campagnes',           view: 'campaigns',  roles: ['ADMIN', 'MARKETING'] },
    { text: 'WhatsApp Web',        view: 'waweb',      roles: ['ADMIN', 'MARKETING'] },
    { text: 'CRM / Opportunités',  view: 'crm',        roles: ['ADMIN', 'SUPERVISEUR', 'AGENT', 'MARKETING'] },
    { text: 'Paramètres',          view: 'settings',   roles: ['ADMIN'] },
    { text: 'Utilisateurs',        view: 'users',      roles: ['ADMIN'] }
];

Usp.canSee = function (roles) {
    if (!roles || roles.length === 0) { return true; }
    var mine = (Usp.user && Usp.user.roles) || [];
    if (mine.indexOf('ADMIN') !== -1) { return true; }
    return roles.some(function (r) { return mine.indexOf(r) !== -1; });
};

/* Pastille sur l'onglet actif d'un tabpanel. */
Usp.tabPastille = function (tp, active) {
    if (!tp || !tp.items) { return; }
    tp.items.each(function (t) {
        if (t.baseTitle === undefined) { t.baseTitle = t.title; }
        t.setTitle(t.baseTitle + (t === active ? ' ●' : ''));
    });
};
Usp.tabListeners = {
    afterrender: function (tp) { Usp.tabPastille(tp, tp.getActiveTab()); },
    tabchange: function (tp, nc) { Usp.tabPastille(tp, nc); }
};

Usp.menuChildren = function () {
    return Usp.MENU.filter(function (m) { return Usp.canSee(m.roles); })
        .map(function (m) { return { text: m.text, baseText: m.text, leaf: true, view: m.view }; });
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
                    fields: ['text', 'baseText', 'view', 'leaf'],
                    root: { expanded: true, children: Usp.menuChildren() }
                }),
                listeners: {
                    itemclick: function (v, rec) {
                        // Pastille sur le menu actif.
                        var root = v.getStore().getRootNode();
                        root.eachChild(function (n) {
                            if (!n.data.baseText) { n.data.baseText = n.get('text'); }
                            n.set('text', n.data.baseText);
                        });
                        if (!rec.data.baseText) { rec.data.baseText = rec.get('text'); }
                        rec.set('text', rec.data.baseText + ' <span style="color:#25d366">●</span>');

                        var vue = rec.get('view') || (rec.raw && rec.raw.view);
                        switch (vue) {
                            case 'inbox': Usp.loadCenter(Usp.inbox.panel()); break;
                            case 'catalogue': Usp.loadCenter(Usp.catalogue.panel()); break;
                            case 'campaigns': Usp.loadCenter(Usp.campaign.listPanel()); break;
                            case 'waweb': Usp.loadCenter(Usp.waweb.tabs()); break;
                            case 'crm': Usp.loadCenter(Usp.crm.tabs()); break;
                            case 'settings': Usp.loadCenter(Usp.settings.tabs()); break;
                            case 'clients': Usp.loadCenter(Usp.clientsPanel()); break;
                            case 'users': Usp.loadCenter(Usp.users.panel()); break;
                            case 'dashboard': Usp.loadCenter(Usp.dashboardPanel()); break;
                            case 'import': Usp.showImport(); break;
                            default: Usp.loadCenter(Usp.dashboardPanel());
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

/* Validation des numéros WhatsApp (chiffres, format international). */
Ext.apply(Ext.form.field.VTypes, {
    numwa: function (v) { return /^[0-9]{6,15}$/.test(v); },
    numwaText: 'Numéro international en chiffres uniquement, ex. 2250102030405',
    numwaMask: /[0-9]/
});

Ext.onReady(function () {
    Ext.QuickTips.init();
    Usp.showLogin();
});
