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

Usp.users.panel = function () {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'login', 'nomComplet', 'email', 'actif', 'roles', 'derniereConnexion'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/users',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
        autoLoad: true
    });

    return {
        xtype: 'grid', title: 'Utilisateurs', store: store,
        columns: [
            { text: 'Login', dataIndex: 'login', width: 140 },
            { text: 'Nom complet', dataIndex: 'nomComplet', flex: 1 },
            { text: 'E-mail', dataIndex: 'email', width: 200 },
            { text: 'Rôles', dataIndex: 'roles', flex: 1,
              renderer: function (v) { return Ext.isArray(v) ? v.join(', ') : (v || ''); } },
            { text: 'Actif', dataIndex: 'actif', width: 60, renderer: function (v) { return v ? 'Oui' : 'Non'; } }
        ],
        tbar: [
            { text: 'Nouvel utilisateur', handler: function () { Usp.users.form(store, null); } },
            { text: 'Activer / Désactiver', handler: function (b) {
                var rec = b.up('grid').getSelectionModel().getSelection()[0];
                if (!rec) { Ext.Msg.alert('Info', 'Sélectionnez un utilisateur.'); return; }
                var action = rec.get('actif') ? 'deactivate' : 'activate';
                Usp.ajax({ url: '/users/' + rec.get('id') + '/' + action, method: 'POST',
                    success: function () { store.load(); } });
            } },
            { text: 'Rafraîchir', handler: function () { store.load(); } }
        ],
        listeners: { itemdblclick: function (g, rec) { Usp.users.form(store, rec); } }
    };
};

Usp.users.form = function (store, rec) {
    var roleItems = Usp.users.ROLES.map(function (r) {
        return { boxLabel: r[1], name: 'role_' + r[0], inputValue: r[0] };
    });
    var win = Ext.create('Ext.window.Window', {
        title: rec ? 'Modifier l\'utilisateur' : 'Nouvel utilisateur',
        width: 460, modal: true, bodyPadding: 12, autoScroll: true,
        items: [{
            xtype: 'form', border: false, defaults: { anchor: '100%' },
            items: [
                { xtype: 'textfield', name: 'login', fieldLabel: 'Login', allowBlank: false, disabled: !!rec },
                { xtype: 'textfield', name: 'nomComplet', fieldLabel: 'Nom complet', allowBlank: false },
                { xtype: 'textfield', name: 'email', fieldLabel: 'E-mail', vtype: 'email' },
                { xtype: 'textfield', name: 'motDePasse', fieldLabel: 'Mot de passe', inputType: 'password',
                  emptyText: rec ? 'Laisser vide pour ne pas changer' : 'Par défaut : Change@2026' },
                { xtype: 'fieldset', title: 'Rôles (accès aux menus)', items: roleItems }
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
        form.setValues({ nomComplet: rec.get('nomComplet'), email: rec.get('email') });
        var roles = rec.get('roles') || [];
        roles.forEach(function (code) {
            var cb = form.findField('role_' + code);
            if (cb) { cb.setValue(true); }
        });
    }
};
