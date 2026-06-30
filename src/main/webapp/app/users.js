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
        items: [Usp.users.gridPanel(), Usp.users.permissionsPanel(),
                Usp.users.connexionsPanel(), Usp.users.journalPanel()]
    };
};

/* ---------- Rôles & permissions : une ligne par rôle + actions ---------- */
Usp.users.permissionsPanel = function () {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'code', 'libelle', 'description', 'actif'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/users/roles/all',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
        autoLoad: true
    });

    var nouveau = function () {
        Ext.Msg.prompt('Nouveau rôle', 'Libellé du rôle (ex. Responsable RH) :', function (btn, libelle) {
            if (btn !== 'ok' || !libelle || !libelle.trim()) { return; }
            Usp.ajax({ url: '/users/roles', method: 'POST', jsonData: { libelle: libelle.trim() },
                success: function (resp) {
                    store.load();
                    var r = Ext.decode(resp.responseText) || {};
                    Usp.toast('Rôle « ' + (r.libelle || '') + ' » créé. Cliquez « 🔐 Privilèges » pour le configurer.');
                },
                failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
        });
    };

    var modifier = function (rec) {
        var win = Ext.create('Ext.window.Window', {
            title: 'Modifier le rôle — ' + rec.get('code'), width: 440, modal: true, bodyPadding: 12,
            items: [{ xtype: 'form', border: false, defaults: { anchor: '100%' }, items: [
                { xtype: 'displayfield', fieldLabel: 'Code', value: rec.get('code') },
                { xtype: 'textfield', name: 'libelle', fieldLabel: 'Libellé', allowBlank: false, value: rec.get('libelle') },
                { xtype: 'textfield', name: 'description', fieldLabel: 'Description', value: rec.get('description') || '' }
            ] }],
            buttons: [{ text: 'Enregistrer', formBind: true, handler: function (b) {
                var f = b.up('window').down('form').getForm();
                if (!f.isValid()) { return; }
                Usp.ajax({ url: '/users/roles/' + rec.get('id'), method: 'PUT', jsonData: f.getValues(),
                    success: function () { win.close(); store.load(); Usp.toast('Rôle modifié.'); },
                    failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
            } }]
        });
        win.show();
    };

    var toggle = function (rec) {
        if (rec.get('code') === 'ADMIN' && rec.get('actif')) {
            Ext.Msg.alert('Action impossible', 'Le rôle ADMIN ne peut pas être désactivé.'); return;
        }
        Usp.ajax({ url: '/users/roles/' + rec.get('id') + '/actif?actif=' + (!rec.get('actif')), method: 'PUT',
            success: function () { store.load(); },
            failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
    };

    return {
        xtype: 'grid', title: '🔐 Rôles & permissions', store: store,
        columns: [
            { text: 'Code', dataIndex: 'code', width: 170 },
            { text: 'Libellé', dataIndex: 'libelle', flex: 1 },
            { text: 'Description', dataIndex: 'description', flex: 1 },
            { text: 'Actif', dataIndex: 'actif', width: 70, align: 'center',
              renderer: function (v) { return v ? '<span style="color:#2e7d32">✔</span>' : '<span style="color:#c62828">✖</span>'; } },
            { text: 'Actions', width: 300, sortable: false, menuDisabled: true, dataIndex: 'id',
              renderer: function (v, m, rec) {
                  var s = '<span class="role-priv" title="Privilèges" style="cursor:pointer;color:#1976d2;margin-right:10px">🔐 Privilèges</span>' +
                          '<span class="role-edit" title="Modifier" style="cursor:pointer;margin-right:10px">✏️ Modifier</span>';
                  if (rec.get('code') !== 'ADMIN') {
                      s += rec.get('actif')
                          ? '<span class="role-toggle" title="Désactiver" style="cursor:pointer;color:#c62828">⛔ Désactiver</span>'
                          : '<span class="role-toggle" title="Activer" style="cursor:pointer;color:#2e7d32">✅ Activer</span>';
                  }
                  return s;
              } }
        ],
        tbar: [
            { text: '➕ Nouveau rôle', tooltip: 'Créer un nouveau rôle', handler: nouveau },
            { text: '🔄 Rafraîchir', handler: function () { store.load(); } }
        ],
        listeners: {
            cellclick: function (g, td, ci, rec, tr, ri, e) {
                if (e.getTarget('.role-priv')) { Usp.users.privilegesWindow(rec); }
                else if (e.getTarget('.role-edit')) { modifier(rec); }
                else if (e.getTarget('.role-toggle')) { toggle(rec); }
            }
        }
    };
};

/* Fenêtre des privilèges d'un rôle : menus + actions cochables (état actuel) + Enregistrer. */
Usp.users.privilegesWindow = function (rec) {
    var code = rec.get('code');
    var win = Ext.create('Ext.window.Window', {
        title: 'Privilèges — ' + (rec.get('libelle') || code), width: 740, modal: true,
        height: Math.min(700, Ext.getBody().getViewSize().height - 60), layout: 'fit',
        items: [{ xtype: 'container', itemId: 'body', autoScroll: true, padding: 10,
                  html: '<span style="color:#888">Chargement…</span>' }],
        tbar: [
            { xtype: 'displayfield', value: code === 'ADMIN'
                ? '<b>ADMIN</b> dispose toujours de tous les droits.'
                : 'Cochez les menus (Voir / accéder) et les actions autorisés.' },
            '->',
            { text: 'Tout cocher', handler: function (b) { b.up('window').down('#body').query('checkbox').forEach(function (c) { c.setValue(true); }); } },
            { text: 'Tout décocher', handler: function (b) { b.up('window').down('#body').query('checkbox').forEach(function (c) { c.setValue(false); }); } }
        ],
        buttons: [
            { text: 'Fermer', handler: function () { win.close(); } },
            { text: '💾 Enregistrer', handler: function () {
                var perms = [];
                win.down('#body').query('checkbox').forEach(function (c) { if (c.getValue()) { perms.push(c.name); } });
                Usp.ajax({ url: '/permissions/roles/' + code, method: 'PUT', jsonData: { permissions: perms },
                    success: function () { win.close(); Usp.toast('Privilèges du rôle « ' + code + ' » enregistrés.'); },
                    failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
            } }
        ]
    });
    win.show();
    Usp.ajax({ url: '/permissions/menus', method: 'GET', success: function (r1) {
        var menus = Ext.decode(r1.responseText) || [];
        Usp.ajax({ url: '/permissions/roles/' + code, method: 'GET', success: function (r2) {
            var accordees = Ext.decode(r2.responseText) || [];
            var body = win.down('#body');
            body.removeAll();
            body.update('');
            body.add(menus.map(function (mn) {
                var cbs = (mn.actions || []).map(function (a) {
                    return { xtype: 'checkbox', boxLabel: a.libelle, name: mn.code + ':' + a.code,
                             checked: accordees.indexOf(mn.code + ':' + a.code) !== -1, margin: '0 14 4 0' };
                });
                return { xtype: 'fieldset', title: mn.libelle, margin: '0 0 8 0',
                         layout: { type: 'table', columns: 3 }, items: cbs };
            }));
        } });
    } });
};

Usp.users.gridPanel = function () {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'login', 'nomComplet', 'avatar', 'email', 'agence', 'actif', 'roles', 'derniereConnexion'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/users',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
        autoLoad: true
    });

    return {
        xtype: 'grid', title: '👤 Utilisateurs', store: store,
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
            Usp.permBtn('users', 'CREER', { text: '➕ Nouvel utilisateur', tooltip: 'Créer un nouvel utilisateur', handler: function () { Usp.users.form(store, null); } }),
            { text: '🔄 Rafraîchir', tooltip: 'Recharger la liste', handler: function () { store.load(); } }
        ].concat(Usp.export.boutons('Utilisateurs')),
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

/* Crée un rôle à la volée depuis un formulaire et le sélectionne dans le combo. */
Usp.users.creerRoleInline = function (roleStore, formPanel) {
    Ext.Msg.prompt('Nouveau rôle', 'Libellé du rôle (ex. Responsable RH) :', function (btn, libelle) {
        if (btn !== 'ok' || !libelle || !libelle.trim()) { return; }
        Usp.ajax({ url: '/users/roles', method: 'POST', jsonData: { libelle: libelle.trim() },
            success: function (resp) {
                var r = Ext.decode(resp.responseText) || {};
                roleStore.load({ callback: function () {
                    var combo = formPanel.down('combobox[name=role]');
                    if (combo) { combo.setValue(r.code); }
                } });
                Usp.toast('Rôle « ' + (r.libelle || '') + ' » créé. Configurez ses permissions dans « Rôles & permissions ».');
            },
            failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
    });
};

/* Active / désactive un utilisateur depuis la ligne. */
Usp.users.toggle = function (rec, store) {
    var action = rec.get('actif') ? 'deactivate' : 'activate';
    Usp.ajax({ url: '/users/' + rec.get('id') + '/' + action, method: 'POST',
        success: function () { store.load(); },
        failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
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
                failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
        });
};

Usp.users.form = function (store, rec) {
    var avatarData = Usp.users.AVATARS.map(function (a) { return { v: a }; });
    // Rôles depuis la table des rôles (liste déroulante, un seul rôle par utilisateur).
    var roleStore = Ext.create('Ext.data.Store', {
        fields: ['code', 'libelle'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/users/roles',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
        autoLoad: true
    });
    var win = Ext.create('Ext.window.Window', {
        title: rec ? 'Modifier l\'utilisateur' : 'Nouvel utilisateur',
        width: 680, modal: true, bodyPadding: 12, autoScroll: false,
        items: [{
            xtype: 'form', border: false, layout: { type: 'hbox', align: 'stretch' },
            items: [
                // Colonne gauche : champs du compte utilisateur.
                { xtype: 'container', flex: 1, layout: 'anchor', defaults: { anchor: '100%' }, items: [
                    { xtype: 'textfield', name: 'login', fieldLabel: 'Login', allowBlank: false, disabled: !!rec },
                    { xtype: 'textfield', name: 'nomComplet', fieldLabel: 'Nom complet', allowBlank: false },
                    { xtype: 'combobox', name: 'avatar', fieldLabel: 'Icône', value: '👤', editable: false,
                      queryMode: 'local', valueField: 'v', displayField: 'v',
                      store: Ext.create('Ext.data.Store', { fields: ['v'], data: avatarData }),
                      listConfig: { getInnerTpl: function () { return '<span style="font-size:18px">{v}</span>'; } } },
                    { xtype: 'textfield', name: 'email', fieldLabel: 'E-mail', vtype: 'email' },
                    Usp.referentielCombo('AGENCE', { name: 'agence', fieldLabel: 'Agence (recouvrement)',
                      emptyText: 'Vide = pas de cloisonnement' }),
                    { xtype: 'textfield', name: 'motDePasse', itemId: 'mdp', fieldLabel: 'Mot de passe', inputType: 'password',
                      emptyText: rec ? 'Laisser vide pour ne pas changer' : 'Par défaut : Change@2026',
                      listeners: { change: function (f) {
                          var c = f.up('form').down('#mdp2');
                          var afficher = !!f.getValue();
                          c.setVisible(afficher);
                          if (!afficher) { c.setValue(''); c.clearInvalid(); }
                      } } },
                    { xtype: 'textfield', name: 'motDePasse2', itemId: 'mdp2', fieldLabel: 'Confirmer', inputType: 'password',
                      hidden: true, emptyText: 'Confirmez le mot de passe' },
                    { xtype: 'fieldcontainer', fieldLabel: 'Rôle', layout: 'hbox', items: [
                        { xtype: 'combobox', name: 'role', allowBlank: false, flex: 1,
                          store: roleStore, valueField: 'code', displayField: 'libelle',
                          queryMode: 'local', editable: false, forceSelection: true,
                          emptyText: 'Sélectionner un rôle…' },
                        { xtype: 'button', text: '➕', tooltip: 'Créer un nouveau rôle', margin: '0 0 0 6',
                          handler: function (b) { Usp.users.creerRoleInline(roleStore, b.up('form')); } }
                    ] },
                    { xtype: 'displayfield',
                      value: '<span style="color:#888">Les droits (menus + actions) dépendent du rôle. ' +
                             'Gérez-les dans l\'onglet « Rôles & permissions ».</span>' }
                ] },
                // Colonne droite : photo de profil (cadre + Parcourir).
                { xtype: 'container', width: 180, margin: '0 0 0 16',
                  layout: { type: 'vbox', align: 'center', pack: 'start' }, items: [
                    { xtype: 'component', itemId: 'photoPreview', html: Usp.users.photoImgHtml(null) },
                    { xtype: 'filefield', itemId: 'photoFile', buttonOnly: true, hideLabel: true,
                      buttonText: 'Parcourir…', margin: '10 0 0 0', width: 150,
                      listeners: { change: function (f) { Usp.users.chargerPhoto(f); } } },
                    { xtype: 'button', itemId: 'photoClear', text: 'Retirer la photo', margin: '6 0 0 0', width: 150,
                      handler: function (b) { Usp.users.definirPhoto(b.up('window'), ''); } },
                    { xtype: 'hidden', name: 'photo', itemId: 'photoField' }
                  ] }
            ]
        }],
        buttons: [{
            text: 'Enregistrer', formBind: true,
            handler: function (b) {
                var form = b.up('window').down('form').getForm();
                if (!form.isValid()) { return; }
                var mdp = form.findField('motDePasse').getValue();
                var mdp2 = form.findField('motDePasse2').getValue();
                if (mdp && mdp !== mdp2) {
                    Ext.Msg.alert('Mot de passe', 'La confirmation ne correspond pas au mot de passe.');
                    return;
                }
                var roleSel = form.findField('role').getValue();
                var roles = roleSel ? [roleSel] : [];
                var data = {
                    login: form.findField('login').getValue(),
                    nomComplet: form.findField('nomComplet').getValue(),
                    avatar: form.findField('avatar').getValue(),
                    photo: form.findField('photo').getValue(),
                    email: form.findField('email').getValue(),
                    agence: form.findField('agence').getValue(),
                    motDePasse: form.findField('motDePasse').getValue(),
                    roles: roles
                };
                Usp.ajax({
                    url: rec ? '/users/' + rec.get('id') : '/users',
                    method: rec ? 'PUT' : 'POST', jsonData: data,
                    success: function () {
                        win.close(); store.load();
                        Usp.toastEnregistre('Utilisateur « ' + (data.nomComplet || data.login) + ' »', !!rec);
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
        var form = win.down('form').getForm();
        form.setValues({
            login: rec.get('login'),
            nomComplet: rec.get('nomComplet'),
            avatar: rec.get('avatar') || '👤',
            email: rec.get('email'),
            agence: rec.get('agence') || ''
        });
        var roles = rec.get('roles') || [];
        if (roles.length) { form.findField('role').setValue(roles[0]); }
        // Charge la photo existante dans le cadre (hors des listes, à la demande).
        Usp.ajax({ url: '/users/' + rec.get('id') + '/photo', method: 'GET',
            success: function (resp) {
                var r = Ext.decode(resp.responseText || '{}');
                Usp.users.definirPhoto(win, r.photo || '');
            } });
    }
};

/* Rendu du cadre photo (image ou placeholder « Aucune photo »). */
Usp.users.photoImgHtml = function (dataUri) {
    if (dataUri) {
        return '<div style="width:150px;height:150px;border-radius:14px;overflow:hidden;' +
            'border:1px solid #ccc;background:#f5f5f5">' +
            '<img src="' + dataUri + '" style="width:100%;height:100%;object-fit:cover"/></div>';
    }
    return '<div style="width:150px;height:150px;border-radius:14px;border:1px dashed #bbb;' +
        'background:#fafafa;display:flex;align-items:center;justify-content:center;' +
        'color:#aaa;font-size:13px;text-align:center">Aucune<br/>photo</div>';
};

/* Met à jour le cadre + le champ caché photo de la fenêtre utilisateur. */
Usp.users.definirPhoto = function (win, dataUri) {
    var prev = win.down('#photoPreview');
    var field = win.down('#photoField');
    if (field) { field.setValue(dataUri || ''); }
    if (prev) { prev.update(Usp.users.photoImgHtml(dataUri || null)); }
};

/* Lit le fichier image choisi (max 2 Mo) et l'affiche dans le cadre. */
Usp.users.chargerPhoto = function (f) {
    var file = f.fileInputEl.dom.files[0];
    if (!file) { return; }
    if (!/^image\//.test(file.type)) {
        Ext.Msg.alert('Photo', 'Veuillez choisir un fichier image.');
        return;
    }
    if (file.size > 2 * 1024 * 1024) {
        Ext.Msg.alert('Photo', 'Image trop lourde (maximum 2 Mo).');
        return;
    }
    var reader = new FileReader();
    reader.onload = function (e) { Usp.users.definirPhoto(f.up('window'), e.target.result); };
    reader.readAsDataURL(file);
};

/* Barre de filtres réutilisable (#1) : utilisateur + période (+ action).
   Pose les extraParams du store et recharge. */
Usp.users.filtreItems = function (store, withAction) {
    var appliquer = function (tb) {
        var p = { limit: 300 };
        var login = tb.down('#fLogin').getValue();
        var d1 = tb.down('#fDtStart').getValue();
        var d2 = tb.down('#fDtEnd').getValue();
        if (login) { p.login = login; }
        if (d1) { p.dtStart = Ext.Date.format(d1, 'Y-m-d'); }
        if (d2) { p.dtEnd = Ext.Date.format(d2, 'Y-m-d'); }
        if (withAction) { var a = tb.down('#fAction').getValue(); if (a) { p.action = a; } }
        store.getProxy().extraParams = p;
        store.load();
    };
    var surEntree = function (f, e) { if (e.getKey() === e.ENTER) { appliquer(f.up('toolbar')); } };
    var items = [
        { xtype: 'textfield', itemId: 'fLogin', emptyText: 'Utilisateur (login)', width: 150,
          listeners: { specialkey: surEntree } },
        { xtype: 'datefield', itemId: 'fDtStart', emptyText: 'Du…', format: 'd/m/Y', width: 105, editable: false },
        { xtype: 'datefield', itemId: 'fDtEnd', emptyText: 'Au…', format: 'd/m/Y', width: 105, editable: false }
    ];
    if (withAction) {
        items.push({ xtype: 'textfield', itemId: 'fAction', emptyText: 'Action (ex. CREATION)', width: 150,
            listeners: { specialkey: surEntree } });
    }
    items.push({ text: '🔎 Filtrer', tooltip: 'Appliquer les filtres', handler: function (b) { appliquer(b.up('toolbar')); } });
    items.push({ text: '♻️ Réinitialiser', tooltip: 'Effacer les filtres', handler: function (b) {
        var tb = b.up('toolbar');
        tb.down('#fLogin').setValue(''); tb.down('#fDtStart').setValue(''); tb.down('#fDtEnd').setValue('');
        if (withAction) { tb.down('#fAction').setValue(''); }
        store.getProxy().extraParams = { limit: 300 }; store.load();
    } });
    items.push('-');
    return items;
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
        xtype: 'grid', title: '🔑 Historique des connexions', store: store,
        columns: [
            { text: 'Utilisateur', dataIndex: 'login', width: 140 },
            { text: 'Connexion', dataIndex: 'connexionAt', width: 140, renderer: Usp.users.fmtDate },
            { text: 'Déconnexion', dataIndex: 'deconnexionAt', width: 140, renderer: function (v) {
                return v ? Usp.users.fmtDate(v) : '<span style="color:#1976d2">session active</span>'; } },
            { text: 'Temps de travail', dataIndex: 'dureeSecondes', width: 120, renderer: Usp.users.fmtDuree },
            { text: 'Adresse IP', dataIndex: 'ip', width: 120 },
            { text: 'Poste', dataIndex: 'poste', width: 150, renderer: function (v) { return v || ''; } },
            { text: 'Lieu', dataIndex: 'lieu', flex: 1, renderer: function (v) { return v || ''; } },
            { text: 'Détails', width: 70, align: 'center', sortable: false, menuDisabled: true, dataIndex: 'login',
              renderer: function () {
                  return '<span class="con-det" title="Menus parcourus et actions de cette session" ' +
                      'style="cursor:pointer;font-size:15px">🔍</span>';
              } }
        ],
        tbar: Usp.users.filtreItems(store, false)
            .concat([{ text: '🔄 Rafraîchir', tooltip: 'Recharger la liste', handler: function () { store.load(); } }])
            .concat(Usp.export.boutons('Historique des connexions')),
        listeners: {
            cellclick: function (g, td, ci, rec, tr, ri, e) {
                if (e.getTarget('.con-det')) { Usp.users.activiteSession(rec); }
            },
            itemdblclick: function (g, rec) { Usp.users.activiteSession(rec); }
        }
    };
};

/* Détail d'une session : menus parcourus et actions effectuées entre connexion et déconnexion. */
Usp.users.activiteSession = function (rec) {
    var store = Ext.create('Ext.data.Store', {
        fields: ['action', 'entite', 'details', 'createdAt'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/users/activite',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' },
            extraParams: {
                login: rec.get('login'),
                debut: rec.get('connexionAt') || '',
                fin: rec.get('deconnexionAt') || ''
            } },
        autoLoad: true
    });
    var libelle = function (a) {
        if (a === 'NAVIGATION') { return '📂 Menu'; }
        if (a === 'CONNEXION') { return '🔑 Connexion'; }
        if (a === 'DECONNEXION') { return '🚪 Déconnexion'; }
        return '⚙️ ' + Ext.String.htmlEncode(a || '');
    };
    Ext.create('Ext.window.Window', {
        title: 'Activité de ' + Ext.String.htmlEncode(rec.get('login')) +
            ' — session du ' + Usp.users.fmtDate(rec.get('connexionAt')),
        width: 620, height: 460, modal: true, layout: 'fit',
        items: [{
            xtype: 'grid', store: store,
            columns: [
                { text: 'Heure', dataIndex: 'createdAt', width: 140, renderer: Usp.users.fmtDate },
                { text: 'Type', dataIndex: 'action', width: 150, renderer: libelle },
                { text: 'Élément', dataIndex: 'entite', width: 110 },
                { text: 'Détails', dataIndex: 'details', flex: 1, renderer: function (v) {
                    return v ? Ext.String.htmlEncode(v) : ''; } }
            ]
        }]
    }).show();
};

/* ---------- Journal d'actions ---------- */
Usp.users.journalPanel = function () {
    var store = Ext.create('Ext.data.Store', {
        fields: ['login', 'action', 'entite', 'entiteId', 'details', 'adresseIp', 'poste', 'createdAt'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/users/journal',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' },
            extraParams: { limit: 300 } },
        autoLoad: true
    });
    return {
        xtype: 'grid', title: '📜 Journal d\'actions', store: store,
        columns: [
            { text: 'Date', dataIndex: 'createdAt', width: 140, renderer: Usp.users.fmtDate },
            { text: 'Utilisateur', dataIndex: 'login', width: 130 },
            { text: 'Action', dataIndex: 'action', width: 150 },
            { text: 'Entité', dataIndex: 'entite', width: 130 },
            { text: 'Réf.', dataIndex: 'entiteId', width: 60 },
            { text: 'Détails', dataIndex: 'details', flex: 1, renderer: function (v) {
                return v ? Ext.String.htmlEncode(v) : ''; } },
            { text: 'Adresse IP', dataIndex: 'adresseIp', width: 120 },
            { text: 'Poste', dataIndex: 'poste', width: 150, renderer: function (v) { return v || ''; } }
        ],
        tbar: Usp.users.filtreItems(store, true)
            .concat([{ text: '🔄 Rafraîchir', tooltip: 'Recharger la liste', handler: function () { store.load(); } }])
            .concat(Usp.export.boutons('Journal d\'actions'))
    };
};
