/*
 * UbiSmartCRM Pro - Centre de support.
 * Tout utilisateur : « Me contacter » + ses tickets.
 * Rôles ADMIN / SUPPORT : tous les tickets, diagnostic (bugs capturés),
 * santé de l'application, demandes reçues, FAQ.
 * Dépend de app.js (objet Usp).
 */
Ext.define('Usp.support', { singleton: true });

Usp.support.estEquipe = function () {
    var roles = (Usp.user && Usp.user.roles) || [];
    return roles.indexOf('ADMIN') !== -1 || roles.indexOf('SUPPORT') !== -1;
};

Usp.support.COULEUR_STATUT = {
    NOUVEAU: '#1976d2', OUVERT: '#1976d2', AFFECTE: '#6a1b9a', EN_COURS: '#ef6c00',
    EN_ATTENTE_CLIENT: '#f9a825', RESOLU: '#2e7d32', CLOTURE: '#777', ANNULE: '#c62828'
};
Usp.support.STATUTS = ['NOUVEAU', 'OUVERT', 'AFFECTE', 'EN_COURS', 'EN_ATTENTE_CLIENT', 'RESOLU', 'CLOTURE', 'ANNULE'];
Usp.support.statutRenderer = function (v) {
    var c = Usp.support.COULEUR_STATUT[v] || '#333';
    return '<span style="color:' + c + ';font-weight:bold">' + (v || '') + '</span>';
};
Usp.support.fdate = function (v) { return v ? String(v).replace('T', ' ').substring(0, 16) : ''; };

Usp.support.panel = function () {
    var tabs = [Usp.support.contactPanel(), Usp.support.ticketsPanel(true)];
    if (Usp.support.estEquipe()) {
        tabs.push(Usp.support.ticketsPanel(false));
        tabs.push(Usp.support.demandesPanel());
        tabs.push(Usp.support.diagnosticPanel());
        tabs.push(Usp.support.santePanel());
    }
    tabs.push(Usp.support.faqPanel());
    return { xtype: 'tabpanel', title: 'Centre de support', listeners: Usp.tabListeners, items: tabs };
};

/* ------------------------------ Me contacter ------------------------------ */
Usp.support.contactPanel = function () {
    return {
        title: '📨 Me contacter', xtype: 'form', bodyPadding: 16, autoScroll: true,
        defaults: { anchor: '60%', labelWidth: 140 },
        items: [
            { xtype: 'displayfield', value: '<b>Contacter le support éditeur.</b> Votre demande est ' +
                'archivée et transmise par e-mail à l\'équipe support.' },
            { xtype: 'textfield', name: 'nom', fieldLabel: 'Votre nom',
              value: (Usp.user && Usp.user.nomComplet) || '' },
            { xtype: 'textfield', name: 'societe', fieldLabel: 'Société', value: Usp.societeParDefaut || '' },
            { xtype: 'textfield', name: 'email', fieldLabel: 'E-mail de réponse', vtype: 'email',
              value: (Usp.user && Usp.user.email) || '' },
            { xtype: 'textfield', name: 'telephone', fieldLabel: 'Téléphone' },
            { xtype: 'textfield', name: 'objet', fieldLabel: 'Objet *', allowBlank: false },
            { xtype: 'textareafield', name: 'corps', fieldLabel: 'Votre demande *', height: 140, allowBlank: false },
            Usp.support.pieceField()
        ],
        bbar: ['->', {
            text: '📨 Envoyer au support', cls: 'usp-btn-pri', handler: function (b) {
                var form = b.up('form').getForm();
                if (!form.isValid()) {
                    Ext.Msg.alert('Champs à compléter', 'Renseignez au moins l\'objet et la description.');
                    return;
                }
                b.disable();
                Usp.ajax({ url: '/support/demandes', method: 'POST', jsonData: Usp.compact(form.getValues()),
                    success: function (resp) {
                        b.enable();
                        var r = Ext.decode(resp.responseText) || {};
                        form.reset();
                        if (r.statut === 'ENVOYEE') {
                            Ext.Msg.alert('Demande envoyée', 'Votre demande a été transmise au support. ' +
                                'Un accusé vous parviendra par e-mail si une adresse de réponse est renseignée.');
                        } else {
                            Ext.Msg.alert('Demande archivée', 'Votre demande est enregistrée. ' +
                                (r.erreur || ''));
                        }
                    },
                    failure: function (resp) { b.enable(); Usp.afficherErreurForm(form, resp); } });
            } }]
    };
};

/* Champ pièce jointe simple (upload -> /media/upload, id stocké dans « pieces »). */
Usp.support.pieceField = function () {
    return { xtype: 'fieldcontainer', fieldLabel: 'Pièce jointe', layout: 'hbox', items: [
        { xtype: 'hiddenfield', name: 'pieces' },
        { xtype: 'filefield', flex: 1, buttonText: 'Choisir un fichier…', msgTarget: 'side',
          emptyText: 'Capture d\'écran, PDF… (optionnel)',
          listeners: { change: function (f) {
              var file = f.fileInputEl.dom.files[0]; if (!file) { return; }
              var statut = f.up('fieldcontainer').down('#pieceEtat');
              statut.update('<span style="color:#888">Téléversement…</span>');
              var reader = new FileReader();
              reader.onload = function (e) {
                  var data = String(e.target.result || '');
                  var b64 = data.indexOf(',') >= 0 ? data.substring(data.indexOf(',') + 1) : data;
                  Usp.ajax({ url: '/media/upload', method: 'POST',
                      jsonData: { fichierBase64: b64, mimeType: file.type || 'application/octet-stream', nomFichier: file.name },
                      success: function (resp) {
                          var r = Ext.decode(resp.responseText) || {};
                          f.up('form').getForm().findField('pieces').setValue(String(r.id || ''));
                          statut.update('<span style="color:#2e7d32">📎 ' + Ext.String.htmlEncode(file.name) + '</span>');
                      },
                      failure: function () { statut.update('<span style="color:#c62828">Échec du téléversement.</span>'); } });
              };
              reader.readAsDataURL(file);
          } } },
        { xtype: 'component', itemId: 'pieceEtat', margin: '4 0 0 8', width: 200, html: '' }
    ] };
};

/* -------------------------------- Tickets -------------------------------- */
Usp.support.ticketsPanel = function (mine) {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'numero', 'sujet', 'type', 'priorite', 'statut', 'module',
                 'utilisateur', 'affecteA', 'createdAt', 'updatedAt'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/support/tickets',
            extraParams: { mine: mine ? 'true' : 'false' },
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
        autoLoad: true
    });
    var cols = [
        { text: 'N°', dataIndex: 'numero', width: 120 },
        { text: 'Sujet', dataIndex: 'sujet', flex: 1 },
        { text: 'Type', dataIndex: 'type', width: 90 },
        { text: 'Priorité', dataIndex: 'priorite', width: 90, renderer: function (v) {
            var c = v === 'HAUTE' ? '#c62828' : (v === 'BASSE' ? '#777' : '#333');
            return '<span style="color:' + c + '">' + (v || '') + '</span>'; } },
        { text: 'Statut', dataIndex: 'statut', width: 140, renderer: Usp.support.statutRenderer },
        { text: 'Ouvert le', dataIndex: 'createdAt', width: 130, renderer: Usp.support.fdate }
    ];
    if (!mine) {
        cols.splice(5, 0, { text: 'Par', dataIndex: 'utilisateur', width: 100 },
            { text: 'Affecté à', dataIndex: 'affecteA', width: 100 });
    }
    return {
        xtype: 'grid', title: mine ? '🎫 Mes tickets' : '🎟️ Tous les tickets', store: store, columns: cols,
        tbar: [
            { text: '➕ Nouveau ticket', cls: 'usp-btn-pri', handler: function () { Usp.support.ticketForm(store); } },
            { text: '🔄 Rafraîchir', handler: function () { store.load(); } }, '-',
            { xtype: 'combobox', emptyText: 'Statut', width: 170, editable: false, queryMode: 'local',
              store: [''].concat(Usp.support.STATUTS),
              listeners: { change: function (c, v) {
                  store.getProxy().extraParams.statut = v || ''; store.load(); } } },
            { xtype: 'textfield', emptyText: 'Rechercher (n°, sujet)…', width: 200,
              listeners: { change: { buffer: 350, fn: function (f, v) {
                  store.getProxy().extraParams.q = v || ''; store.load(); } } } }
        ],
        listeners: { itemdblclick: function (g, rec) { Usp.support.ticketDetail(rec.get('id'), store); } }
    };
};

Usp.support.ticketForm = function (store) {
    var win = Ext.create('Ext.window.Window', {
        title: 'Nouveau ticket', width: 620, modal: true, bodyPadding: 12,
        items: [{ xtype: 'form', border: false, defaults: { anchor: '100%', labelWidth: 120 }, items: [
            { xtype: 'textfield', name: 'sujet', fieldLabel: 'Sujet *', allowBlank: false },
            { xtype: 'combobox', name: 'type', fieldLabel: 'Type', value: 'INCIDENT', editable: false,
              queryMode: 'local', store: [['INCIDENT', 'Incident'], ['QUESTION', 'Question'],
                  ['DEMANDE', 'Demande d\'évolution'], ['BUG', 'Bug']] },
            { xtype: 'combobox', name: 'priorite', fieldLabel: 'Priorité', value: 'NORMALE', editable: false,
              queryMode: 'local', store: ['BASSE', 'NORMALE', 'HAUTE'] },
            { xtype: 'combobox', name: 'module', fieldLabel: 'Module concerné', queryMode: 'local',
              editable: true, store: ['Tableau de bord', 'Discussions', 'Comptes clients', 'Catalogue',
                  'Promotions', 'Marketing', 'Campagnes', 'WhatsApp Web', 'CRM', 'Recouvrement', 'Paramètres', 'Autre'] },
            { xtype: 'textareafield', name: 'description', fieldLabel: 'Description *', height: 120, allowBlank: false },
            Usp.support.pieceField()
        ] }],
        buttons: [
            { text: 'Annuler', handler: function () { win.close(); } },
            { text: 'Créer le ticket', formBind: true, handler: function (b) {
                var form = b.up('window').down('form').getForm();
                if (!form.isValid()) {
                    Ext.Msg.alert('Champs à compléter', 'Sujet et description sont obligatoires.');
                    return;
                }
                Usp.ajax({ url: '/support/tickets', method: 'POST', jsonData: Usp.compact(form.getValues()),
                    success: function (resp) {
                        var t = Ext.decode(resp.responseText) || {};
                        win.close(); store.load();
                        Usp.toast('Ticket ' + (t.numero || '') + ' créé.');
                    },
                    failure: function (resp) { Usp.afficherErreurForm(form, resp); } });
            } }
        ]
    });
    win.show();
};

/* Détail d'un ticket : infos + conversation + actions équipe. */
Usp.support.ticketDetail = function (id, gridStore) {
    var equipe = Usp.support.estEquipe();
    var convo = Ext.create('Ext.Component', { autoScroll: true, flex: 1,
        style: 'background:#f7f9fb;border:1px solid #e4e9ef;border-radius:6px;padding:8px',
        html: '<div style="color:#888">Chargement…</div>' });
    var entete = Ext.create('Ext.Component', { height: 66, html: '' });

    var chargerEntete = function () {
        Usp.ajax({ url: '/support/tickets/' + id, method: 'GET', success: function (resp) {
            var t = Ext.decode(resp.responseText) || {};
            win.setTitle('🎫 ' + (t.numero || '') + ' — ' + Ext.String.htmlEncode(t.sujet || ''));
            entete.update('<div style="padding:4px 2px;font-size:12px;line-height:1.7">' +
                'Statut : ' + Usp.support.statutRenderer(t.statut) +
                ' &nbsp;|&nbsp; Priorité : <b>' + (t.priorite || '') + '</b>' +
                ' &nbsp;|&nbsp; Type : ' + (t.type || '') +
                (t.module ? ' &nbsp;|&nbsp; Module : ' + Ext.String.htmlEncode(t.module) : '') +
                '<br>Ouvert par <b>' + Ext.String.htmlEncode(t.utilisateur || '—') + '</b> le ' +
                Usp.support.fdate(t.createdAt) +
                (t.affecteA ? ' &nbsp;|&nbsp; Affecté à <b>' + Ext.String.htmlEncode(t.affecteA) + '</b>' : '') +
                '</div>');
            win.ticketStatut = t.statut;
        } });
    };
    var chargerMessages = function () {
        Usp.ajax({ url: '/support/tickets/' + id + '/messages', method: 'GET', success: function (resp) {
            var l = []; try { l = Ext.decode(resp.responseText) || []; } catch (e) {}
            var html = l.map(function (m) {
                if (m.direction === 'SYSTEME') {
                    return '<div style="text-align:center;color:#888;font-size:11px;margin:6px 0">— ' +
                        Ext.String.htmlEncode(m.corps) + ' (' + Usp.support.fdate(m.createdAt) + ') —</div>';
                }
                var interne = m.direction === 'INTERNE';
                return '<div style="margin:6px 0;display:flex;justify-content:' + (interne ? 'flex-start' : 'flex-end') + '">' +
                    '<div style="max-width:75%;padding:8px 10px;border-radius:10px;background:' +
                    (interne ? '#e8f0fe' : '#dcf8c6') + '">' +
                    '<div style="font-size:10px;color:#666;margin-bottom:2px"><b>' +
                    Ext.String.htmlEncode(m.auteur || (interne ? 'Support' : 'Vous')) + '</b> · ' +
                    Usp.support.fdate(m.createdAt) + '</div>' +
                    '<div style="white-space:pre-wrap;font-size:12px">' + Ext.String.htmlEncode(m.corps) + '</div>' +
                    '</div></div>';
            }).join('') || '<div style="color:#888">Aucun message.</div>';
            convo.update(html);
            var el = convo.getEl(); if (el) { el.dom.scrollTop = el.dom.scrollHeight; }
        } });
    };
    var toutCharger = function () { chargerEntete(); chargerMessages(); if (gridStore) { gridStore.load(); } };

    var actionsEquipe = !equipe ? [] : [
        { xtype: 'combobox', itemId: 'cbStatut', emptyText: 'Changer le statut…', width: 190, editable: false,
          queryMode: 'local', store: Usp.support.STATUTS,
          listeners: { select: function (c) {
              Usp.ajax({ url: '/support/tickets/' + id + '/statut', method: 'PUT',
                  jsonData: { statut: c.getValue() },
                  success: function () { c.clearValue(); toutCharger(); Usp.toast('Statut mis à jour.'); },
                  failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
          } } },
        { text: '👤 M\'affecter', handler: function () {
            Usp.ajax({ url: '/support/tickets/' + id + '/affecter', method: 'PUT',
                jsonData: { affecteA: (Usp.user && Usp.user.login) || '' },
                success: function () { toutCharger(); Usp.toast('Ticket affecté.'); },
                failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
        } }
    ];

    var win = Ext.create('Ext.window.Window', {
        title: 'Ticket', width: 680, height: Math.min(620, Ext.getBody().getViewSize().height - 40),
        modal: true, layout: { type: 'vbox', align: 'stretch' }, bodyPadding: 10,
        tbar: [{ text: '🔄 Rafraîchir', handler: toutCharger }].concat(actionsEquipe),
        items: [
            entete, convo,
            { xtype: 'fieldcontainer', layout: 'hbox', margin: '8 0 0 0', items: [
                { xtype: 'textareafield', itemId: 'reponse', flex: 1, height: 54, emptyText: 'Votre message…' },
                { xtype: 'button', text: 'Envoyer', cls: 'usp-btn-pri', margin: '0 0 0 8', height: 54,
                  handler: function (b) {
                      var ta = b.up('window').down('#reponse');
                      var corps = (ta.getValue() || '').trim();
                      if (!corps) { return; }
                      Usp.ajax({ url: '/support/tickets/' + id + '/messages', method: 'POST',
                          jsonData: { corps: corps },
                          success: function () { ta.setValue(''); chargerMessages(); },
                          failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
                  } }
            ] }
        ]
    });
    win.show();
    toutCharger();
};

/* --------------------------- Demandes reçues (équipe) --------------------------- */
Usp.support.demandesPanel = function () {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'nom', 'societe', 'email', 'telephone', 'objet', 'corps', 'statut', 'erreur', 'creePar', 'createdAt'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/support/demandes',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
        autoLoad: true
    });
    return {
        xtype: 'grid', title: '📥 Demandes reçues', store: store,
        columns: [
            { text: 'Date', dataIndex: 'createdAt', width: 130, renderer: Usp.support.fdate },
            { text: 'Objet', dataIndex: 'objet', flex: 1 },
            { text: 'De', dataIndex: 'nom', width: 130 },
            { text: 'E-mail', dataIndex: 'email', width: 170 },
            { text: 'Utilisateur', dataIndex: 'creePar', width: 100 },
            { text: 'Statut', dataIndex: 'statut', width: 90, renderer: function (v) {
                var c = v === 'ENVOYEE' ? '#2e7d32' : (v === 'ECHOUEE' ? '#c62828' : '#777');
                return '<span style="color:' + c + ';font-weight:bold">' + (v || '') + '</span>'; } }
        ],
        tbar: [{ text: '🔄 Rafraîchir', handler: function () { store.load(); } }],
        listeners: { itemdblclick: function (g, rec) {
            Ext.Msg.show({ title: Ext.String.htmlEncode(rec.get('objet')), width: 560, buttons: Ext.Msg.OK,
                msg: '<div style="max-height:340px;overflow:auto;white-space:pre-wrap">' +
                     '<b>De :</b> ' + Ext.String.htmlEncode(rec.get('nom') || '—') +
                     ' (' + Ext.String.htmlEncode(rec.get('email') || '—') + ')\n' +
                     '<b>Tél :</b> ' + Ext.String.htmlEncode(rec.get('telephone') || '—') + '\n\n' +
                     Ext.String.htmlEncode(rec.get('corps') || '') + '</div>' });
        } }
    };
};

/* ----------------------------- Diagnostic & bugs ----------------------------- */
Usp.support.diagnosticPanel = function () {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'module', 'type', 'niveau', 'signature', 'messageCourt', 'occurrences',
                 'utilisateur', 'urlOuEcran', 'payloadJson', 'ticketId', 'createdAt', 'lastSeenAt'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/support/events',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
        autoLoad: true
    });
    var nivCouleur = { FATAL: '#8e0000', ERROR: '#c62828', WARN: '#ef6c00', INFO: '#1976d2' };
    return {
        xtype: 'grid', title: '🐞 Diagnostic & bugs', store: store,
        columns: [
            { text: 'Dernière vue', dataIndex: 'lastSeenAt', width: 130, renderer: Usp.support.fdate },
            { text: 'Niveau', dataIndex: 'niveau', width: 70, renderer: function (v) {
                return '<span style="color:' + (nivCouleur[v] || '#333') + ';font-weight:bold">' + (v || '') + '</span>'; } },
            { text: 'Type', dataIndex: 'type', width: 120 },
            { text: 'Module', dataIndex: 'module', width: 130 },
            { text: 'Message', dataIndex: 'messageCourt', flex: 1, renderer: function (v, m) {
                m.tdAttr = 'data-qtip="' + Ext.String.htmlEncode(v || '').replace(/"/g, '&quot;') + '"';
                return Ext.String.htmlEncode(v || ''); } },
            { text: 'Occ.', dataIndex: 'occurrences', width: 60, align: 'right', renderer: function (v) {
                return v > 1 ? '<b style="color:#c62828">' + v + '</b>' : v; } },
            { text: 'Ticket', dataIndex: 'ticketId', width: 70, align: 'center',
              renderer: function (v) { return v ? '🎫 #' + v : ''; } },
            { text: 'Actions', width: 110, align: 'center', sortable: false, menuDisabled: true, dataIndex: 'id',
              renderer: function (v, m, rec) {
                  var h = '<span class="ev-voir" title="Voir le détail" style="cursor:pointer;margin:0 4px">👁️</span>';
                  if (!rec.get('ticketId')) {
                      h += '<span class="ev-ticket" title="Créer un ticket depuis cet événement" style="cursor:pointer;margin:0 4px">🎫➕</span>';
                  }
                  return h;
              } }
        ],
        tbar: [
            { text: '🔄 Rafraîchir', handler: function () { store.load(); } }, '-',
            { xtype: 'combobox', emptyText: 'Niveau', width: 120, editable: false, queryMode: 'local',
              store: ['', 'FATAL', 'ERROR', 'WARN', 'INFO'],
              listeners: { change: function (c, v) { store.getProxy().extraParams = { niveau: v || '' }; store.load(); } } },
            { xtype: 'textfield', emptyText: 'Rechercher…', width: 200,
              listeners: { change: { buffer: 350, fn: function (f, v) {
                  var p = store.getProxy().extraParams || {}; p.q = v || '';
                  store.getProxy().extraParams = p; store.load(); } } } },
            '->',
            { text: '🧹 Purger (rétention)', tooltip: 'Supprime les événements plus vieux que la rétention configurée',
              handler: function () {
                  Usp.ajax({ url: '/support/events/purge', method: 'DELETE',
                      success: function (resp) { store.load();
                          Usp.toast(((Ext.decode(resp.responseText) || {}).purges || 0) + ' événement(s) purgé(s).'); } });
              } }
        ],
        listeners: {
            cellclick: function (g, td, ci, rec, tr, ri, e) {
                if (e.getTarget('.ev-voir')) {
                    Ext.Msg.show({ title: 'Événement #' + rec.get('id') + ' — ' + rec.get('type'), width: 640, buttons: Ext.Msg.OK,
                        msg: '<div style="max-height:380px;overflow:auto">' +
                             '<b>Signature :</b> ' + rec.get('signature') +
                             ' &nbsp; <b>Occurrences :</b> ' + rec.get('occurrences') + '<br>' +
                             '<b>1re vue :</b> ' + Usp.support.fdate(rec.get('createdAt')) +
                             ' &nbsp; <b>Dernière :</b> ' + Usp.support.fdate(rec.get('lastSeenAt')) + '<br>' +
                             '<b>Écran/URL :</b> ' + Ext.String.htmlEncode(rec.get('urlOuEcran') || '—') + '<br><hr>' +
                             '<pre style="white-space:pre-wrap;font-size:11px">' +
                             Ext.String.htmlEncode(rec.get('messageCourt') || '') + '\n\n' +
                             Ext.String.htmlEncode(rec.get('payloadJson') || '') + '</pre></div>' });
                } else if (e.getTarget('.ev-ticket')) {
                    Ext.Msg.confirm('Créer un ticket', 'Créer un ticket BUG depuis cet événement ?', function (btn) {
                        if (btn !== 'yes') { return; }
                        Usp.ajax({ url: '/support/events/' + rec.get('id') + '/ticket', method: 'POST',
                            success: function (resp) {
                                var t = Ext.decode(resp.responseText) || {};
                                store.load(); Usp.toast('Ticket ' + (t.numero || '') + ' créé et lié.');
                            },
                            failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
                    });
                }
            }
        }
    };
};

/* --------------------------------- Santé --------------------------------- */
Usp.support.santePanel = function () {
    var charger = function (panel) {
        var body = panel.down('#santeBody');
        body.update('<div style="padding:20px;color:#888">Analyse en cours…</div>');
        Usp.ajax({ url: '/support/sante', method: 'GET',
            success: function (resp) {
                var s = Ext.decode(resp.responseText) || {};
                var carte = function (titre, ok, lignes) {
                    return '<div style="display:inline-block;vertical-align:top;width:250px;margin:8px;padding:12px 14px;' +
                        'background:#fff;border-radius:10px;border-left:5px solid ' + (ok ? '#2e7d32' : '#c62828') + ';' +
                        'box-shadow:0 1px 4px rgba(0,0,0,.08)">' +
                        '<div style="font-weight:bold;margin-bottom:6px">' + (ok ? '🟢' : '🔴') + ' ' + titre + '</div>' +
                        '<div style="font-size:12px;color:#444;line-height:1.7">' + lignes + '</div></div>';
                };
                var b = s.base || {}, wa = s.whatsappApi || {}, ww = s.whatsappWeb || {},
                    mail = s.email || {}, jvm = s.serveur || {}, c = s.compteurs || {};
                body.update('<div style="padding:6px">' +
                    carte('Base de données', b.ok, b.ok ? 'Latence : <b>' + b.latenceMs + ' ms</b>'
                        : Ext.String.htmlEncode(b.erreur || 'Injoignable')) +
                    carte('WhatsApp API', wa.ok, 'Comptes actifs : <b>' + (wa.comptesActifs || 0) + '</b>') +
                    carte('WhatsApp Web', ww.ok, 'Sessions : <b>' + (ww.sessions || 0) + '</b> — connectées : <b>' + (ww.connectees || 0) + '</b>') +
                    carte('E-mail (SMTP)', mail.ok, mail.configure ? 'Configuré' : 'Non configuré (Paramètres)') +
                    carte('Serveur', jvm.ok, 'Démarré depuis : <b>' + Math.floor((jvm.uptimeMinutes || 0) / 60) + ' h ' +
                        ((jvm.uptimeMinutes || 0) % 60) + ' min</b><br>Mémoire : <b>' + (jvm.memoireUtiliseeMo || 0) +
                        ' / ' + (jvm.memoireMaxMo || 0) + ' Mo</b>') +
                    carte('Incidents', (c.erreurs24h || 0) === 0,
                        'Erreurs 24 h : <b>' + (c.erreurs24h || 0) + '</b> — 7 j : <b>' + (c.erreurs7j || 0) + '</b><br>' +
                        'Avertissements 24 h : <b>' + (c.avertissements24h || 0) + '</b><br>' +
                        'Tickets ouverts : <b>' + (c.ticketsOuverts || 0) + '</b>') +
                    '</div>');
            },
            failure: function () { body.update('<div style="padding:20px;color:#c62828">Sondes indisponibles.</div>'); } });
    };
    return {
        xtype: 'panel', title: '❤️ Santé', autoScroll: true, bodyStyle: 'background:#eef1f5',
        tbar: [{ text: '🔄 Relancer les sondes', handler: function (b) { charger(b.up('panel')); } }],
        items: [{ xtype: 'component', itemId: 'santeBody', html: '' }],
        listeners: { afterrender: function (p) { charger(p); } }
    };
};

/* ---------------------------------- FAQ ---------------------------------- */
Usp.support.faqPanel = function () {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'declencheurs', 'reponse', 'ordre'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/support/faq',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
        autoLoad: true
    });
    return {
        xtype: 'grid', title: '📚 FAQ', store: store,
        viewConfig: { emptyText: '<div style="padding:14px;color:#888">Aucune entrée. La FAQ se gère dans ' +
            'Paramètres → Bot (elle est partagée avec l\'assistant).</div>' },
        columns: [
            { text: 'Question / mots-clés', dataIndex: 'declencheurs', flex: 1 },
            { text: 'Réponse', dataIndex: 'reponse', flex: 2, renderer: function (v, m) {
                m.tdAttr = 'data-qtip="' + Ext.String.htmlEncode(v || '').replace(/"/g, '&quot;') + '"';
                return Ext.String.htmlEncode(v || ''); } }
        ],
        tbar: [{ text: '🔄 Rafraîchir', handler: function () { store.load(); } },
            '->', { xtype: 'tbtext', text: 'FAQ partagée avec le bot (édition : Paramètres → Bot)' }]
    };
};
