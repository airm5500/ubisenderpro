/*
 * UbiSenderPro - Gestion des utilisateurs applicatifs (section 26 de la spec).
 * Réservé au rôle ADMIN. Dépend de app.js (objet Usp).
 */
Ext.define('Usp.users', { singleton: true });

Usp.users.ROLES = [
    ['ADMIN', 'Administrateur'], ['MARKETING', 'Responsable marketing'],
    ['SUPERVISEUR', 'Superviseur'], ['AGENT', 'Agent'],
    ['CATALOGUE', 'Gestionnaire catalogue'], ['LECTURE', 'Lecture seule']
];

/* Avatars proposés à la création/modification d'un utilisateur. */
Usp.users.AVATARS = ['👤', '👨', '👩', '🧑', '👨‍💼', '👩‍💼', '🧑‍💻', '👨‍💻', '👩‍💻',
    '🛠️', '📞', '📊', '⭐', '🦊', '🐱', '🐼', '🚀', '🎯'];

Usp.users.fmtDate = function (v) {
    return v ? Ext.String.htmlEncode(String(v).replace('T', ' ').substring(0, 16)) : '';
};

Usp.users.fmtDuree = function (s) {
    if (s === null || s === undefined || s === '') { return ''; }
    s = parseInt(s, 10);
    var h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60);
    return (h > 0 ? h + ' h ' : '') + m + ' min';
};

/* ---------- Onglets : Utilisateurs + Historique des connexions ---------- */
Usp.users.panel = function () {
    return {
        xtype: 'tabpanel', title: 'Utilisateurs', listeners: Usp.tabListeners,
        items: [Usp.users.gridPanel(), Usp.users.connexionsPanel(), Usp.users.journalPanel()]
    };
};

Usp.users.gridPanel = function () {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'login', 'nomComplet', 'avatar', 'email', 'actif', 'roles', 'derniereConnexion'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/users',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
        autoLoad: true
    });

    return {
        xtype: 'grid', title: 'Utilisateurs', store: store,
        columns: [
            { text: '', dataIndex: 'avatar', width: 44, align: 'center', sortable: false, menuDisabled: true,
              renderer: function (v) { return '<span style="font-size:16px">' + (v || '👤') + '</span>'; } },
            { text: 'Login', dataIndex: 'login', width: 140 },
            { text: 'Nom complet', dataIndex: 'nomComplet', flex: 1 },
            { text: 'E-mail', dataIndex: 'email', width: 190 },
            { text: 'Rôles', dataIndex: 'roles', flex: 1,
              renderer: function (v) { return Ext.isArray(v) ? v.join(', ') : (v || ''); } },
            { text: 'Dernière connexion', dataIndex: 'derniereConnexion', width: 140, renderer: Usp.users.fmtDate },
            { text: 'Actif', dataIndex: 'actif', width: 70, align: 'center', renderer: function (v) {
                return v ? '<span style="color:#2e7d32">✔ Oui</span>' : '<span style="color:#c62828">✖ Non</span>'; } },
            { text: 'Actions', width: 130, align: 'center', sortable: false, menuDisabled: true, dataIndex: 'id',
              renderer: function (v, m, rec) {
                  var toggle = rec.get('actif')
                      ? '<span class="usr-toggle" title="Désactiver" style="cursor:pointer">⛔</span>'
                      : '<span class="usr-toggle" title="Activer" style="cursor:pointer">✅</span>';
                  return '<span class="usr-edit" title="Modifier" style="cursor:pointer;margin-right:8px">✏️</span>' +
                      '<span class="usr-reset" title="Réinitialiser le mot de passe" style="cursor:pointer;margin-right:8px">🔑</span>' +
                      toggle;
              } }
        ],
        tbar: [
            { text: 'Nouvel utilisateur', handler: function () { Usp.users.form(store, null); } },
            { text: 'Rafraîchir', handler: function () { store.load(); } }
        ],
        listeners: {
            cellclick: function (g, td, ci, rec, tr, ri, e) {
                if (e.getTarget('.usr-edit')) { Usp.users.form(store, rec); return; }
                if (e.getTarget('.usr-reset')) { Usp.users.reset(rec); return; }
                if (e.getTarget('.usr-toggle')) { Usp.users.toggle(rec, store); return; }
            },
            itemdblclick: function (g, rec) { Usp.users.form(store, rec); }
        }
    };
};

/* Active / désactive un utilisateur depuis la ligne. */
Usp.users.toggle = function (rec, store) {
    var action = rec.get('actif') ? 'deactivate' : 'activate';
    Usp.ajax({ url: '/users/' + rec.get('id') + '/' + action, method: 'POST',
        success: function () { store.load(); },
        failure: function () { Ext.Msg.alert('Erreur', 'Action impossible.'); } });
};

/* Réinitialise le mot de passe d'un utilisateur. */
Usp.users.reset = function (rec) {
    Ext.Msg.prompt('Réinitialiser le mot de passe',
        'Nouveau mot de passe pour « ' + Ext.String.htmlEncode(rec.get('login')) +
        ' » (laisser vide = Change@2026) :',
        function (btn, val) {
            if (btn !== 'ok') { return; }
            Usp.ajax({ url: '/users/' + rec.get('id') + '/reset-password', method: 'POST',
                jsonData: { motDePasse: val || '' },
                success: function (resp) {
                    var r = Ext.decode(resp.responseText) || {};
                    Ext.Msg.alert('Mot de passe réinitialisé',
                        'Nouveau mot de passe : <b>' + Ext.String.htmlEncode(r.motDePasse || '') + '</b>');
                },
                failure: function () { Ext.Msg.alert('Erreur', 'Réinitialisation impossible.'); } });
        });
};

Usp.users.form = function (store, rec) {
    var avatarData = Usp.users.AVATARS.map(function (a) { return { v: a }; });
    var roleItems = Usp.users.ROLES.map(function (r) {
        return { boxLabel: r[1], name: 'role_' + r[0], inputValue: r[0] };
    });
    var win = Ext.create('Ext.window.Window', {
        title: rec ? 'Modifier l\'utilisateur' : 'Nouvel utilisateur',
        width: 540, maxHeight: 560, modal: true, bodyPadding: 12, autoScroll: true,
        items: [{
            xtype: 'form', border: false, defaults: { anchor: '100%' },
            items: [
                { xtype: 'textfield', name: 'login', fieldLabel: 'Login', allowBlank: false, disabled: !!rec },
                { xtype: 'textfield', name: 'nomComplet', fieldLabel: 'Nom complet', allowBlank: false },
                { xtype: 'combobox', name: 'avatar', fieldLabel: 'Icône', value: '👤', editable: false,
                  queryMode: 'local', valueField: 'v', displayField: 'v',
                  store: Ext.create('Ext.data.Store', { fields: ['v'], data: avatarData }),
                  listConfig: { getInnerTpl: function () { return '<span style="font-size:18px">{v}</span>'; } } },
                { xtype: 'textfield', name: 'email', fieldLabel: 'E-mail', vtype: 'email' },
                { xtype: 'textfield', name: 'motDePasse', fieldLabel: 'Mot de passe', inputType: 'password',
                  emptyText: rec ? 'Laisser vide pour ne pas changer' : 'Par défaut : Change@2026' },
                { xtype: 'fieldset', title: 'Rôles (accès aux menus)', layout: 'column',
                  defaults: { columnWidth: 0.5, margin: '0 0 4 0' }, items: roleItems }
            ]
        }],
        buttons: [{
            text: 'Enregistrer', formBind: true,
            handler: function (b) {
                var form = b.up('window').down('form').getForm();
                if (!form.isValid()) { return; }
                var roles = [];
                Usp.users.ROLES.forEach(function (r) {
                    var cb = form.findField('role_' + r[0]);
                    if (cb && cb.getValue()) { roles.push(r[0]); }
                });
                var data = {
                    login: form.findField('login').getValue(),
                    nomComplet: form.findField('nomComplet').getValue(),
                    avatar: form.findField('avatar').getValue(),
                    email: form.findField('email').getValue(),
                    motDePasse: form.findField('motDePasse').getValue(),
                    roles: roles
                };
                Usp.ajax({
                    url: rec ? '/users/' + rec.get('id') : '/users',
                    method: rec ? 'PUT' : 'POST', jsonData: data,
                    success: function () { win.close(); store.load(); },
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
        var form = win.down('form').getForm();
        form.setValues({
            login: rec.get('login'),
            nomComplet: rec.get('nomComplet'),
            avatar: rec.get('avatar') || '👤',
            email: rec.get('email')
        });
        var roles = rec.get('roles') || [];
        roles.forEach(function (code) {
            var cb = form.findField('role_' + code);
            if (cb) { cb.setValue(true); }
        });
    }
};

/* ---------- Historique des connexions ---------- */
Usp.users.connexionsPanel = function () {
    var store = Ext.create('Ext.data.Store', {
        fields: ['login', 'ip', 'poste', 'lieu', 'connexionAt', 'deconnexionAt', 'dureeSecondes'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/users/connexions',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' },
            extraParams: { limit: 300 } },
        autoLoad: true
    });
    return {
        xtype: 'grid', title: 'Historique des connexions', store: store,
        columns: [
            { text: 'Utilisateur', dataIndex: 'login', width: 140 },
            { text: 'Connexion', dataIndex: 'connexionAt', width: 140, renderer: Usp.users.fmtDate },
            { text: 'Déconnexion', dataIndex: 'deconnexionAt', width: 140, renderer: function (v) {
                return v ? Usp.users.fmtDate(v) : '<span style="color:#1976d2">session active</span>'; } },
            { text: 'Temps de travail', dataIndex: 'dureeSecondes', width: 120, renderer: Usp.users.fmtDuree },
            { text: 'Adresse IP', dataIndex: 'ip', width: 130 },
            { text: 'Poste', dataIndex: 'poste', width: 160, renderer: function (v) { return v || ''; } },
            { text: 'Lieu', dataIndex: 'lieu', flex: 1, renderer: function (v) { return v || ''; } }
        ],
        tbar: [{ text: 'Rafraîchir', handler: function () { store.load(); } }]
    };
};

/* ---------- Journal d'actions ---------- */
Usp.users.journalPanel = function () {
    var store = Ext.create('Ext.data.Store', {
        fields: ['login', 'action', 'entite', 'entiteId', 'details', 'adresseIp', 'createdAt'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/users/journal',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' },
            extraParams: { limit: 300 } },
        autoLoad: true
    });
    return {
        xtype: 'grid', title: 'Journal d\'actions', store: store,
        columns: [
            { text: 'Date', dataIndex: 'createdAt', width: 140, renderer: Usp.users.fmtDate },
            { text: 'Utilisateur', dataIndex: 'login', width: 130 },
            { text: 'Action', dataIndex: 'action', width: 150 },
            { text: 'Entité', dataIndex: 'entite', width: 130 },
            { text: 'Réf.', dataIndex: 'entiteId', width: 60 },
            { text: 'Détails', dataIndex: 'details', flex: 1, renderer: function (v) {
                return v ? Ext.String.htmlEncode(v) : ''; } },
            { text: 'Adresse IP', dataIndex: 'adresseIp', width: 120 }
        ],
        tbar: [{ text: 'Rafraîchir', handler: function () { store.load(); } }]
    };
};
