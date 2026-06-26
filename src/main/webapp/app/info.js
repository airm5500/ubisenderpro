/*
 * UbiSenderPro - Module INFORMATIONS CLIENTS & ALERTES OPÉRATIONNELLES.
 * Communications non promotionnelles (livraison, garde, fériés, horaires,
 * alertes, anniversaires). Producteur : alimente l'agrégateur Marketing.
 * Dépend de app.js (objet Usp).
 */
Ext.define('Usp.info', { singleton: true });

Usp.info._stores = [];

Usp.info.TYPES = [
    ['RETARD_LIVRAISON', '🚚 Retard de livraison'], ['MODIFICATION_TOURNEE', '🚚 Modification de tournée'],
    ['ANNULATION_TOURNEE', '🚫 Annulation de tournée'], ['REPRISE_LIVRAISON', '✅ Reprise de livraison'],
    ['INFORMATION_GARDE', '🏥 Information de garde'], ['INFORMATION_JOUR_FERIE', '📅 Jour férié'],
    ['MODIFICATION_HORAIRES', '🕒 Modification des horaires'], ['FERMETURE_AGENCE', '🚪 Fermeture d\'agence'],
    ['INFORMATION_GENERALE', '📢 Information générale'], ['ALERTE_URGENTE', '🚨 Alerte urgente'],
    ['ANNIVERSAIRE_CLIENT', '🎂 Anniversaire client']
];
Usp.info.PRIORITES = [['NORMALE', 'Normale'], ['IMPORTANTE', 'Importante'], ['URGENTE', 'Urgente'], ['CRITIQUE', 'Critique']];
Usp.info.AUDIENCES = [
    ['TOUS_LES_SEGMENTS', 'Tous les segments'], ['DIAMOND', 'Diamond'], ['PLATINIUM', 'Platinium'],
    ['DIAMOND_ET_PLATINIUM', 'Diamond et Platinium'],
    ['AGENCE', 'Clients de l\'agence'], ['REGION', 'Clients de la région'],
    ['TOURNEE', 'Clients de la tournée'], ['LISTE_DE_DIFFUSION', 'Liste de diffusion'],
    ['CONTACTS_MANUELS', 'Contacts sélectionnés']
];
Usp.info.COULEUR_STATUT = {
    BROUILLON: '#777', EN_ATTENTE: '#777', PROGRAMMEE: '#1976d2', EN_COURS: '#ef6c00',
    ENVOYEE: '#2e7d32', ANNULEE: '#8e0000', EXPIREE: '#999', ECHOUEE: '#c62828', ARCHIVEE: '#555'
};
Usp.info.COULEUR_PRIO = { NORMALE: '#555', IMPORTANTE: '#1976d2', URGENTE: '#ef6c00', CRITIQUE: '#c62828' };

Usp.info.fdate = function (v) { return v ? String(v).substring(0, 10) : ''; };
Usp.info.typeLib = function (v) {
    var m = Ext.Array.findBy(Usp.info.TYPES, function (a) { return a[0] === v; });
    return m ? m[1] : (v || '');
};
Usp.info.statutRenderer = function (v) {
    return '<span style="color:' + (Usp.info.COULEUR_STATUT[v] || '#333') + ';font-weight:bold">' + (v || '') + '</span>';
};
Usp.info.prioRenderer = function (v) {
    return '<span style="color:' + (Usp.info.COULEUR_PRIO[v] || '#555') + ';font-weight:bold">' + (v || '') + '</span>';
};
Usp.info.reloadAll = function () { Usp.info._stores.forEach(function (s) { s.load(); }); };

Usp.info.panel = function () {
    Usp.info._stores = [];
    return {
        xtype: 'tabpanel', title: 'Informations Clients', listeners: Usp.tabListeners,
        items: [
            Usp.info.grille({ encours: true }, '📌 Informations en cours'),
            Usp.info.grille({ types: 'RETARD_LIVRAISON,MODIFICATION_TOURNEE,ANNULATION_TOURNEE,REPRISE_LIVRAISON' }, '🚚 Problèmes de livraison'),
            Usp.info.grille({ types: 'INFORMATION_GARDE,INFORMATION_JOUR_FERIE' }, '🏥 Gardes & jours fériés'),
            Usp.info.grille({ type: 'ANNIVERSAIRE_CLIENT' }, '🎂 Anniversaires'),
            Usp.info.grille({ statut: 'PROGRAMMEE' }, '📅 Programmées'),
            Usp.info.grille({ historique: true }, '🗂️ Historique')
        ]
    };
};

Usp.info.grille = function (filtre, libelleTab) {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'code', 'type', 'titre', 'message', 'priorite', 'societe', 'agence', 'region', 'tournee',
                 'audience', 'segmentationId', 'listeId', 'contactIds', 'canal', 'dateEnvoi', 'dateFinValidite',
                 'statut', 'responsable', 'dateLivraison', 'creneau', 'heureInitiale', 'nouvelleHeure',
                 'causeInterne', 'causeCommunicable', 'dateResolution', 'jourFerie', 'dateGarde',
                 'heureLimiteCommande', 'consignesLivraison', 'pharmacienGarde', 'telephonePharmacien'],
        autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/infos', extraParams: filtre,
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } }
    });
    Usp.info._stores.push(store);
    var historique = !!filtre.historique;
    return {
        xtype: 'grid', title: libelleTab, store: store,
        columns: [
            { text: 'Code', dataIndex: 'code', width: 120 },
            { text: 'Type', dataIndex: 'type', width: 180, renderer: Usp.info.typeLib },
            { text: 'Titre', dataIndex: 'titre', flex: 1 },
            { text: 'Priorité', dataIndex: 'priorite', width: 100, renderer: Usp.info.prioRenderer },
            { text: 'Agence', dataIndex: 'agence', width: 110 },
            { text: 'Envoi', dataIndex: 'dateEnvoi', width: 100, renderer: Usp.info.fdate },
            { text: 'Statut', dataIndex: 'statut', width: 110, renderer: Usp.info.statutRenderer },
            { text: 'Actions', width: 230, sortable: false, menuDisabled: true, dataIndex: 'id',
              renderer: function (v, m, rec) {
                  var s = rec.get('statut');
                  var h = '<span class="ie-edit" title="Voir / modifier" style="cursor:pointer;margin:0 4px">✏️</span>' +
                          '<span class="ie-dup" title="Dupliquer" style="cursor:pointer;margin:0 4px">📑</span>';
                  if (s !== 'PROGRAMMEE' && s !== 'ENVOYEE' && s !== 'ANNULEE' && s !== 'ARCHIVEE') {
                      h += '<span class="ie-prog" title="Programmer" style="cursor:pointer;margin:0 4px;color:#1976d2">📅</span>';
                  }
                  if (s !== 'ANNULEE' && s !== 'ARCHIVEE' && s !== 'ENVOYEE') {
                      h += '<span class="ie-cancel" title="Annuler" style="cursor:pointer;margin:0 4px;color:#c62828">⛔</span>';
                  }
                  if (s !== 'ARCHIVEE') {
                      h += '<span class="ie-archive" title="Archiver" style="cursor:pointer;margin:0 4px;color:#777">🗄️</span>';
                  }
                  return h;
              } }
        ],
        tbar: historique ? [{ text: '🔄 Rafraîchir', handler: function () { store.load(); } }]
                .concat(Usp.export.boutons('Informations historique'))
            : [
                { text: '➕ Nouvelle information', handler: function () { Usp.info.form(store, null, filtre.type); } },
                { text: '🔄 Rafraîchir', handler: function () { store.load(); } }
            ].concat(Usp.export.boutons('Informations')),
        listeners: {
            itemdblclick: function (g, rec) { Usp.info.form(store, rec); },
            cellclick: function (g, td, ci, rec, tr, ri, e) {
                if (e.getTarget('.ie-edit')) { Usp.info.form(store, rec); }
                else if (e.getTarget('.ie-dup')) { Usp.info.action(rec, 'dupliquer', 'Dupliquer cette information ?'); }
                else if (e.getTarget('.ie-prog')) { Usp.info.action(rec, 'programmer', 'Programmer « ' + Ext.String.htmlEncode(rec.get('titre')) + ' » ?'); }
                else if (e.getTarget('.ie-cancel')) { Usp.info.action(rec, 'annuler', 'Annuler « ' + Ext.String.htmlEncode(rec.get('titre')) + ' » ?'); }
                else if (e.getTarget('.ie-archive')) { Usp.info.action(rec, 'archiver', 'Archiver « ' + Ext.String.htmlEncode(rec.get('titre')) + ' » ?'); }
            }
        }
    };
};

Usp.info.action = function (rec, action, message) {
    Ext.Msg.confirm('Confirmation', message, function (btn) {
        if (btn !== 'yes') { return; }
        Usp.ajax({ url: '/infos/' + rec.get('id') + '/' + action, method: 'POST',
            success: function () { Usp.info.reloadAll(); Usp.toast('Opération effectuée avec succès.'); },
            failure: function (resp) {
                var m = 'Opération impossible.';
                try { m = Ext.decode(resp.responseText).erreur || m; } catch (e) {}
                Ext.Msg.alert('Erreur', m);
            } });
    });
};

Usp.info.form = function (store, rec, typeParDefaut) {
    var g = function (n) { return rec ? rec.get(n) : null; };
    var dParse = function (n) {
        var v = g(n);
        return v ? Ext.Date.parse(String(v).substring(0, 10), 'Y-m-d') : null;
    };
    var win = Ext.create('Ext.window.Window', {
        title: rec ? 'Information — ' + Ext.String.htmlEncode(rec.get('titre')) : 'Nouvelle information',
        width: 720, height: Ext.getBody().getViewSize().height - 60, maxHeight: 760, modal: true, layout: 'fit',
        items: [{ xtype: 'form', border: false, bodyPadding: 12, autoScroll: true, defaults: { anchor: '100%', labelWidth: 170 }, items: [
            { xtype: 'textfield', name: 'code', fieldLabel: 'Code', allowBlank: false, value: g('code') || '' },
            { xtype: 'combobox', name: 'type', fieldLabel: 'Type', editable: false, queryMode: 'local', allowBlank: false,
              store: Usp.info.TYPES, value: g('type') || typeParDefaut || 'INFORMATION_GENERALE' },
            { xtype: 'textfield', name: 'titre', fieldLabel: 'Titre', allowBlank: false, value: g('titre') || '' },
            { xtype: 'textarea', name: 'message', fieldLabel: 'Message (info générale / alerte…)', height: 70, value: g('message') || '',
              emptyText: 'Texte libre injecté dans le modèle ({{message}})' },
            { xtype: 'combobox', name: 'priorite', fieldLabel: 'Priorité', editable: false, queryMode: 'local',
              store: Usp.info.PRIORITES, value: g('priorite') || 'NORMALE' },
            { xtype: 'combobox', name: 'audience', fieldLabel: 'Audience', editable: false, queryMode: 'local',
              store: Usp.info.AUDIENCES, value: g('audience') || 'TOUS_LES_SEGMENTS' },
            { xtype: 'combobox', name: 'canal', fieldLabel: 'Canal', editable: false, queryMode: 'local',
              store: [['WEB', 'WhatsApp Web'], ['API', 'API WhatsApp']], value: g('canal') || 'WEB' },
            { xtype: 'textfield', name: 'societe', fieldLabel: 'Société', value: g('societe') || '' },
            { xtype: 'textfield', name: 'agence', fieldLabel: 'Agence', value: g('agence') || '',
              emptyText: 'Sert au texte et au ciblage « Clients de l\'agence »' },
            { xtype: 'textfield', name: 'region', fieldLabel: 'Région', value: g('region') || '',
              emptyText: 'Sert au ciblage « Clients de la région »' },
            { xtype: 'textfield', name: 'tournee', fieldLabel: 'Tournée', value: g('tournee') || '',
              emptyText: 'Sert au ciblage « Clients de la tournée »' },
            { xtype: 'combobox', name: 'listeId', fieldLabel: 'Liste de diffusion', queryMode: 'local',
              valueField: 'id', displayField: 'nom', value: g('listeId'), emptyText: '(si audience = Liste de diffusion)',
              store: Ext.create('Ext.data.Store', { fields: ['id', 'nom'], autoLoad: true,
                  proxy: { type: 'ajax', url: Usp.apiBase + '/lists',
                      headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } } }) },
            { xtype: 'tagfield', name: 'contactIds', fieldLabel: 'Contacts sélectionnés', queryMode: 'remote',
              queryParam: 'q', minChars: 2, valueField: 'id', displayField: 'nom',
              emptyText: '(si audience = Contacts sélectionnés) — tapez 2 lettres',
              value: g('contactIds') ? String(g('contactIds')).split(',') : [],
              listConfig: { getInnerTpl: function () { return '{nom} <span style="color:#999">{client}</span>'; } },
              store: Ext.create('Ext.data.Store', { fields: ['id', 'nom', 'client', 'numero'],
                  proxy: { type: 'ajax', url: Usp.apiBase + '/contacts/selection', queryParam: 'q',
                      headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } } }) },
            { xtype: 'textfield', name: 'responsable', fieldLabel: 'Direction / signataire', value: g('responsable') || '' },
            { xtype: 'datefield', name: 'dateEnvoi', fieldLabel: 'Date d\'envoi', format: 'd/m/Y', submitFormat: 'Y-m-d\\TH:i:s', editable: false, value: dParse('dateEnvoi') },
            { xtype: 'datefield', name: 'dateFinValidite', fieldLabel: 'Fin de validité', format: 'd/m/Y', submitFormat: 'Y-m-d\\TH:i:s', editable: false, value: dParse('dateFinValidite') },
            { xtype: 'fieldset', title: 'Détails livraison (selon le type)', collapsible: true, collapsed: true, defaults: { anchor: '100%', labelWidth: 170 }, items: [
                { xtype: 'datefield', name: 'dateLivraison', fieldLabel: 'Date de livraison', format: 'd/m/Y', submitFormat: 'Y-m-d', editable: false, value: dParse('dateLivraison') },
                { xtype: 'textfield', name: 'creneau', fieldLabel: 'Créneau', value: g('creneau') || '' },
                { xtype: 'textfield', name: 'heureInitiale', fieldLabel: 'Heure initiale', value: g('heureInitiale') || '' },
                { xtype: 'textfield', name: 'nouvelleHeure', fieldLabel: 'Nouvelle heure estimée', value: g('nouvelleHeure') || '' },
                { xtype: 'textfield', name: 'causeCommunicable', fieldLabel: 'Cause communicable', value: g('causeCommunicable') || '' },
                { xtype: 'textfield', name: 'causeInterne', fieldLabel: 'Cause interne (non envoyée)', value: g('causeInterne') || '' },
                { xtype: 'datefield', name: 'dateResolution', fieldLabel: 'Date estimée de résolution', format: 'd/m/Y', submitFormat: 'Y-m-d', editable: false, value: dParse('dateResolution') }
            ] },
            { xtype: 'fieldset', title: 'Détails garde / jour férié (selon le type)', collapsible: true, collapsed: true, defaults: { anchor: '100%', labelWidth: 170 }, items: [
                { xtype: 'textfield', name: 'jourFerie', fieldLabel: 'Jour férié / événement', value: g('jourFerie') || '' },
                { xtype: 'datefield', name: 'dateGarde', fieldLabel: 'Date de la garde', format: 'd/m/Y', submitFormat: 'Y-m-d', editable: false, value: dParse('dateGarde') },
                { xtype: 'textfield', name: 'heureLimiteCommande', fieldLabel: 'Heure limite de commande', value: g('heureLimiteCommande') || '' },
                { xtype: 'textfield', name: 'consignesLivraison', fieldLabel: 'Consignes de livraison', value: g('consignesLivraison') || '' },
                { xtype: 'textfield', name: 'pharmacienGarde', fieldLabel: 'Pharmacien de garde', value: g('pharmacienGarde') || '' },
                { xtype: 'textfield', name: 'telephonePharmacien', fieldLabel: 'Téléphone du pharmacien', value: g('telephonePharmacien') || '' }
            ] }
        ] }],
        buttons: [
            { text: 'Annuler', handler: function () { win.close(); } },
            { text: 'Enregistrer', handler: function (b) {
                var form = b.up('window').down('form').getForm();
                if (!form.isValid()) { return; }
                var v = form.getValues();
                // listeId : null si non choisi (évite l'échec de désérialisation Long).
                if (v.listeId === '' || v.listeId == null) { delete v.listeId; } else { v.listeId = Number(v.listeId); }
                Usp.ajax({ url: rec ? '/infos/' + rec.get('id') : '/infos', method: rec ? 'PUT' : 'POST', jsonData: v,
                    success: function () { win.close(); Usp.info.reloadAll(); Usp.toastEnregistre('Information « ' + v.titre + ' »', !!rec); },
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
