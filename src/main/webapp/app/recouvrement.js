/*
 * UbiSenderPro - Module RECOUVREMENT (Lot 1 : socle financier).
 * Indépendant du Marketing. Droits via le menu « recouvrement » (RBAC).
 * Dépend de app.js (objet Usp).
 */
Ext.define('Usp.recouvrement', { singleton: true });

Usp.recouvrement.CANAUX = [['WHATSAPP', 'WhatsApp'], ['EMAIL', 'Email']];

/* Formatage monétaire. */
Usp.recouvrement.money = function (v) {
    if (v === null || v === undefined || v === '') { return ''; }
    return Ext.util.Format.number(parseFloat(v), '0,000.00');
};

/* Combo d'un référentiel du module (SEGMENT_COMMERCIAL / PROFIL_PAIEMENT / STATUT_RECOUVREMENT). */
Usp.recouvrement.refCombo = function (type, cfg) {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'code', 'libelle'], autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/recouvrement/referentiels/' + type,
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } }
    });
    return Ext.apply({ xtype: 'combobox', store: store, valueField: 'libelle', displayField: 'libelle',
        queryMode: 'local', forceSelection: false, anchor: '100%' }, cfg || {});
};

/* Télécharge/ouvre le relevé de compte PDF d'un client (avec jeton d'authentification). */
Usp.recouvrement.ouvrirReleve = function (clientId) {
    var xhr = new XMLHttpRequest();
    xhr.open('GET', Usp.apiBase + '/recouvrement/clients/' + clientId + '/releve', true);
    xhr.responseType = 'blob';
    xhr.setRequestHeader('Authorization', 'Bearer ' + (Usp.token || ''));
    xhr.onload = function () {
        if (xhr.status >= 200 && xhr.status < 300) {
            var url = window.URL.createObjectURL(xhr.response);
            window.open(url, '_blank');
            setTimeout(function () { window.URL.revokeObjectURL(url); }, 60000);
        } else { Ext.Msg.alert('Erreur', 'Impossible de générer le relevé de compte.'); }
    };
    xhr.onerror = function () { Ext.Msg.alert('Erreur', 'Impossible de générer le relevé de compte.'); };
    xhr.send();
};

/* Combo des segmentations clients existantes (réutilise la même liste que Comptes clients).
 * La valeur stockée est le libellé, cohérent avec le champ segmentCommercial. */
Usp.recouvrement.segmentationCombo = function (cfg) {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'libelle'], autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/segmentations',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } } });
    return Ext.apply({ xtype: 'combobox', store: store, valueField: 'libelle', displayField: 'libelle',
        queryMode: 'local', forceSelection: false, editable: false, anchor: '100%' }, cfg || {});
};

/* Combo de sélection d'un client (recherche distante sur /clients). */
Usp.recouvrement.clientCombo = function (cfg) {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'nomCompte', 'numeroClient', 'entreprise'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/clients', queryParam: 'q',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') },
            reader: { type: 'json', root: 'data', totalProperty: 'total' } } });
    return Ext.apply({ xtype: 'combobox', store: store, valueField: 'id', displayField: 'nomCompte',
        queryMode: 'remote', minChars: 2, anchor: '100%', emptyText: 'Tapez 2 lettres…',
        listConfig: { getInnerTpl: function () {
            return '<b>{numeroClient}</b> {nomCompte} <span style="color:#999">{entreprise}</span>'; } } }, cfg || {});
};

/* ============================ Panneau principal ============================ */
Usp.recouvrement.panel = function () {
    return {
        xtype: 'tabpanel', title: 'Suivi Relance et Recouvrements', listeners: Usp.tabListeners,
        items: [Usp.recouvrement.dashboardPanel(), Usp.recouvrement.fichesPanel(),
                Usp.recouvrement.assistantPanel(), Usp.recouvrement.campagnesPanel(),
                Usp.recouvrement.modelesPanel(), Usp.recouvrement.historiquePanel(),
                Usp.recouvrement.importPanel(), Usp.recouvrement.referentielsPanel()]
    };
};

Usp.recouvrement.TYPES_MODELE = [
    ['RELANCE_PREVENTIVE', 'Relance préventive'], ['FACTURE_ECHUE', 'Facture échue'],
    ['IMPAYE', 'Impayé'], ['MISE_EN_DEMEURE', 'Mise en demeure'], ['DIVERS', 'Divers']
];

/* ---------------------------- Tableau de bord ---------------------------- */
Usp.recouvrement.dashboardPanel = function () {
    var agStore = Ext.create('Ext.data.Store', {
        fields: ['agence', 'encours', 'factures', 'avoirs', 'recouvre', 'solde', 'clients', 'facturesEchues', 'promesses', 'tauxRecouvrement'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/recouvrement/dashboard/par-agence',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } } });
    var kpi = Ext.create('Ext.Component', { html: '<div style="padding:10px;color:#888">Chargement…</div>', height: 90 });
    var chargerKpi = function () {
        Usp.ajax({ url: '/recouvrement/dashboard/groupe', method: 'GET', success: function (resp) {
            var s = Ext.decode(resp.responseText) || {};
            var carte = function (lib, val, couleur) {
                return '<div style="display:inline-block;vertical-align:top;min-width:132px;margin:4px 6px;padding:6px 10px;border:1px solid #e0e0e0;' +
                    'border-radius:8px;background:#fff"><div style="color:#888;font-size:11px;white-space:nowrap">' + lib + '</div>' +
                    '<div style="font-size:16px;font-weight:bold;color:' + (couleur || '#333') + '">' + val + '</div></div>';
            };
            // Les 7 bulles sur une seule ligne (défilement horizontal si nécessaire).
            kpi.update('<div style="padding:6px;white-space:nowrap;overflow-x:auto">' +
                carte('Encours global', Usp.recouvrement.money(s.encours)) +
                carte('Encaissé', Usp.recouvrement.money(s.recouvre), '#2e7d32') +
                carte('Solde restant', Usp.recouvrement.money(s.solde), '#c62828') +
                carte('Taux de recouvrement', (s.tauxRecouvrement || 0) + ' %', '#1976d2') +
                carte('Clients', s.clients) +
                carte('Factures échues', s.facturesEchues, '#ef6c00') +
                carte('Promesses', s.promesses) + '</div>');
        } });
    };
    return {
        title: '📊 Tableau de bord', xtype: 'panel', layout: 'border', bodyPadding: 0,
        items: [
            { region: 'north', xtype: 'container', items: [kpi], height: 100, style: 'background:#f4f6f8;border-bottom:1px solid #ddd' },
            { region: 'center', xtype: 'grid', store: agStore, title: 'Point par agence',
              columns: [
                  { text: 'Agence', dataIndex: 'agence', flex: 1 },
                  { text: 'Encours', dataIndex: 'encours', width: 120, align: 'right', renderer: Usp.recouvrement.money },
                  { text: 'Encaissé', dataIndex: 'recouvre', width: 120, align: 'right', renderer: Usp.recouvrement.money },
                  { text: 'Solde', dataIndex: 'solde', width: 130, align: 'right',
                    renderer: function (v) { return '<span style="color:#c62828;font-weight:bold">' + Usp.recouvrement.money(v) + '</span>'; } },
                  { text: 'Taux', dataIndex: 'tauxRecouvrement', width: 80, align: 'right', renderer: function (v) { return (v || 0) + ' %'; } },
                  { text: 'Clients', dataIndex: 'clients', width: 80, align: 'right' },
                  { text: 'Factures échues', dataIndex: 'facturesEchues', width: 110, align: 'right' },
                  { text: 'Promesses', dataIndex: 'promesses', width: 90, align: 'right' }
              ],
              tbar: [{ text: '🔄 Rafraîchir', handler: function () { chargerKpi(); agStore.load(); } }]
                  .concat(Usp.export.boutons('Recouvrement - par agence')) }
        ],
        listeners: { afterrender: function () { chargerKpi(); agStore.load(); } }
    };
};

/* ---------------------------- Clients & encours ---------------------------- */
Usp.recouvrement.fichesPanel = function () {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'clientId', 'nomCompte', 'numeroClient', 'agence', 'segmentCommercial',
                 'profilPaiement', 'responsable', 'statut', 'canalPrefere',
                 'encoursInitial', 'totalFactures', 'totalPaiements', 'solde'],
        autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/recouvrement/fiches',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } }
    });
    var recharger = function () { store.load(); };

    // Filtres combinés (côté client sur les fiches chargées) : recherche + agence + segment + statut.
    var f = { q: '', agence: '', segment: '', statut: '' };
    var appliquer = function () {
        store.clearFilter(true);
        store.filterBy(function (rec) {
            if (f.q) {
                var q = f.q.toLowerCase();
                var hay = ((rec.get('nomCompte') || '') + ' ' + (rec.get('numeroClient') || '') + ' '
                    + (rec.get('responsable') || '')).toLowerCase();
                if (hay.indexOf(q) < 0) { return false; }
            }
            if (f.agence && rec.get('agence') !== f.agence) { return false; }
            if (f.segment && rec.get('segmentCommercial') !== f.segment) { return false; }
            if (f.statut && rec.get('statut') !== f.statut) { return false; }
            return true;
        });
    };
    store.on('load', appliquer);

    return {
        xtype: 'grid', title: '💼 Clients & encours', store: store,
        columns: [
            { text: 'N° client', dataIndex: 'numeroClient', width: 100 },
            { text: 'Client', dataIndex: 'nomCompte', flex: 1 },
            { text: 'Agence', dataIndex: 'agence', width: 110 },
            { text: 'Segment', dataIndex: 'segmentCommercial', width: 110 },
            { text: 'Profil paiement', dataIndex: 'profilPaiement', width: 120 },
            { text: 'Responsable', dataIndex: 'responsable', width: 120 },
            { text: 'Statut', dataIndex: 'statut', width: 110 },
            { text: 'Encours init.', dataIndex: 'encoursInitial', width: 110, align: 'right', renderer: Usp.recouvrement.money },
            { text: 'Réglé', dataIndex: 'totalPaiements', width: 110, align: 'right', renderer: Usp.recouvrement.money },
            { text: 'Solde', dataIndex: 'solde', width: 120, align: 'right',
              renderer: function (v) {
                  var n = parseFloat(v || 0);
                  return '<span style="color:' + (n > 0 ? '#c62828' : '#2e7d32') + ';font-weight:bold">'
                      + Usp.recouvrement.money(v) + '</span>';
              } },
            { text: 'Actions', width: 400, sortable: false, menuDisabled: true, dataIndex: 'id',
              renderer: function () {
                  return '<span class="rec-mouv" title="Créances / paiements" style="cursor:pointer;color:#1976d2;margin-right:10px">📂 Créances</span>'
                      + '<span class="rec-releve" title="Relevé de compte PDF" style="cursor:pointer;color:#6a1b9a;margin-right:10px">📄 Relevé</span>'
                      + '<span class="rec-send" title="Envoyer une relance" style="cursor:pointer;color:#2e7d32;margin-right:10px">📨 Relancer</span>'
                      + '<span class="rec-edit" title="Modifier la fiche" style="cursor:pointer">✏️ Fiche</span>';
              } }
        ],
        tbar: [
            Usp.permBtn('recouvrement', 'CREER', { text: '➕ Nouvelle fiche', handler: function () { Usp.recouvrement.ficheForm(store, null); } }),
            { text: '🔄 Rafraîchir', handler: recharger }, '-',
            { xtype: 'textfield', emptyText: 'Rechercher client…', width: 180,
              listeners: { change: function (c, v) { f.q = v || ''; appliquer(); }, buffer: 300 } },
            Usp.referentielCombo('AGENCE', { emptyText: 'Agence', width: 140, value: '',
                listeners: { change: function (c, v) { f.agence = v || ''; appliquer(); } } }),
            Usp.recouvrement.segmentationCombo({ emptyText: 'Segment', width: 140, editable: false,
                listeners: { change: function (c, v) { f.segment = v || ''; appliquer(); } } }),
            Usp.recouvrement.refCombo('STATUT_RECOUVREMENT', { emptyText: 'Statut', width: 140, editable: false,
                listeners: { change: function (c, v) { f.statut = v || ''; appliquer(); } } }),
            '->'
        ].concat(Usp.export.boutons('Recouvrement - encours')),
        listeners: {
            cellclick: function (g, td, ci, rec, tr, ri, e) {
                if (e.getTarget('.rec-mouv')) { Usp.recouvrement.mouvementsWindow(rec, store); }
                else if (e.getTarget('.rec-releve')) { Usp.recouvrement.ouvrirReleve(rec.get('clientId')); }
                else if (e.getTarget('.rec-send')) {
                    if (!Usp.can('recouvrement', 'ENVOYER')) { Usp.refusPermission(); return; }
                    Usp.recouvrement.relanceForm(rec);
                }
                else if (e.getTarget('.rec-edit')) { Usp.recouvrement.ficheForm(store, rec); }
            },
            itemdblclick: function (g, rec) { Usp.recouvrement.mouvementsWindow(rec, store); }
        }
    };
};

/* Remplit automatiquement le segment (segmentation client) à la sélection d'un client. */
Usp.recouvrement.autoSegment = function (win, clientId) {
    if (!clientId) { return; }
    var seg = win.down('[name=segmentCommercial]');
    var ent = win.down('#ficheEntreprise');
    Usp.ajax({ url: '/clients/' + clientId, method: 'GET', success: function (resp) {
        var c = Ext.decode(resp.responseText) || {};
        if (ent) { ent.setValue(c.entreprise || '<span style="color:#bbb">—</span>'); }
        if (!seg) { return; }
        if (!c.segmentationId) { seg.setReadOnly(false); return; }
        Usp.ajax({ url: '/segmentations', method: 'GET', success: function (r2) {
            var l = []; try { l = Ext.decode(r2.responseText) || []; } catch (e) {}
            var m = l.filter(function (s) { return String(s.id) === String(c.segmentationId); })[0];
            if (m) {
                // Le segment provient du client : renseigné et verrouillé (non modifiable ici).
                seg.setValue(m.libelle);
                seg.setReadOnly(true);
                if (seg.inputEl) { seg.inputEl.setStyle('background', '#f0f0f0'); }
            }
        } });
    } });
};

/* Formulaire fiche recouvrement (2 colonnes, sans scroll). */
Usp.recouvrement.ficheForm = function (store, rec) {
    var colGauche = [];
    if (rec) {
        colGauche.push({ xtype: 'displayfield', fieldLabel: 'Client', value: rec.get('nomCompte') + ' (' + (rec.get('numeroClient') || '') + ')' });
        colGauche.push({ xtype: 'hiddenfield', name: 'clientId', value: rec.get('clientId') });
    } else {
        colGauche.push(Usp.recouvrement.clientCombo({ name: 'clientId', fieldLabel: 'Client', allowBlank: false,
            listeners: { select: function (cb, r) {
                var a = Ext.isArray(r) ? r[0] : r;
                Usp.recouvrement.autoSegment(cb.up('window'), a ? a.get('id') : cb.getValue());
            } } }));
    }
    colGauche.push(
        { xtype: 'displayfield', itemId: 'ficheEntreprise', fieldLabel: 'Entreprise', value: rec ? '' : '<span style="color:#bbb">—</span>' },
        Usp.recouvrement.segmentationCombo({ name: 'segmentCommercial', fieldLabel: 'Segment' }),
        Usp.recouvrement.refCombo('PROFIL_PAIEMENT', { name: 'profilPaiement', fieldLabel: 'Profil de paiement' }),
        Usp.recouvrement.refCombo('STATUT_RECOUVREMENT', { name: 'statut', fieldLabel: 'Statut' })
    );
    var colDroite = [
        { xtype: 'textfield', name: 'responsable', fieldLabel: 'Responsable' },
        { xtype: 'combobox', name: 'canalPrefere', fieldLabel: 'Canal préféré', queryMode: 'local',
          editable: false, store: Usp.recouvrement.CANAUX, anchor: '100%' },
        { xtype: 'numberfield', name: 'encoursInitial', fieldLabel: 'Encours initial', value: 0, hideTrigger: true },
        { xtype: 'datefield', name: 'dateSituation', fieldLabel: 'Date de situation', format: 'd/m/Y', submitFormat: 'Y-m-d', editable: false }
    ];
    var win = Ext.create('Ext.window.Window', {
        title: rec ? 'Fiche recouvrement — ' + rec.get('nomCompte') : 'Nouvelle fiche recouvrement',
        width: 720, modal: true, bodyPadding: 12,
        items: [{ xtype: 'form', border: false, layout: 'column',
            defaults: { columnWidth: 0.5, xtype: 'container', layout: 'anchor', border: false,
                        defaults: { anchor: '96%', labelWidth: 130 } },
            items: [
                { items: colGauche },
                { items: colDroite },
                { columnWidth: 1, items: [
                    { xtype: 'textareafield', name: 'observations', fieldLabel: 'Observations', height: 60, anchor: '98%', labelWidth: 130 }
                ] }
            ] }],
        buttons: [
            { text: 'Annuler', handler: function () { win.close(); } },
            { text: 'Enregistrer', formBind: true, handler: function (b) {
                var form = b.up('window').down('form').getForm();
                if (!form.isValid()) {
                    Ext.Msg.alert('Champs à compléter', 'Merci de renseigner les champs obligatoires (repérés par *).');
                    return;
                }
                var vals = Usp.compact(form.getValues());
                Usp.ajax({
                    url: rec ? '/recouvrement/fiches/' + rec.get('id') : '/recouvrement/fiches',
                    method: rec ? 'PUT' : 'POST', jsonData: vals,
                    success: function () { win.close(); store.load(); Usp.toastEnregistre('Fiche', !!rec); },
                    failure: function (resp) { Usp.afficherErreurForm(form, resp); }
                });
            } }
        ]
    });
    win.show();
    if (rec) {
        Usp.ajax({ url: '/recouvrement/fiches/' + rec.get('id'), method: 'GET', success: function (resp) {
            win.down('form').getForm().setValues(Ext.decode(resp.responseText));
            Usp.recouvrement.autoSegment(win, rec.get('clientId'));
        } });
    }
};

/* ---------------- Fenêtre des mouvements d'un client ---------------- */
Usp.recouvrement.mouvementsWindow = function (rec, ficheStore) {
    var clientId = rec.get('clientId');
    var base = '/recouvrement/clients/' + clientId;

    var jsonStore = function (suffixe, fields) {
        return Ext.create('Ext.data.Store', { fields: fields, autoLoad: true,
            proxy: { type: 'ajax', url: Usp.apiBase + base + suffixe,
                headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } } });
    };
    var creances = jsonStore('/creances', ['id', 'type', 'numero', 'dateEmission', 'dateEcheance', 'montant', 'statut', 'notes']);
    var paiements = jsonStore('/paiements', ['id', 'datePaiement', 'montant', 'mode', 'reference']);
    var promesses = jsonStore('/promesses', ['id', 'datePromesse', 'montant', 'statut', 'notes']);

    var entete = Ext.create('Ext.Component', { height: 30, html: '' });
    var rafraichirSituation = function () {
        Usp.ajax({ url: base + '/situation', method: 'GET', success: function (resp) {
            var s = Ext.decode(resp.responseText) || {};
            entete.update('<div style="padding:4px 8px">Encours initial : <b>' + Usp.recouvrement.money(s.encoursInitial)
                + '</b> &nbsp;|&nbsp; Factures : <b>' + Usp.recouvrement.money(s.totalFactures)
                + '</b> &nbsp;|&nbsp; Avoirs : <b>' + Usp.recouvrement.money(s.totalAvoirs)
                + '</b> &nbsp;|&nbsp; Réglé : <b>' + Usp.recouvrement.money(s.totalPaiements)
                + '</b> &nbsp;|&nbsp; <span style="font-size:14px">Solde : <b style="color:#c62828">'
                + Usp.recouvrement.money(s.solde) + '</b></span></div>');
        } });
    };
    var toutRafraichir = function () {
        creances.load(); paiements.load(); promesses.load(); rafraichirSituation();
        if (ficheStore) { ficheStore.load(); }
    };

    var fmtDate = function (v) { return v ? String(v).substring(0, 10) : ''; };
    var supprBtn = function (cls) {
        return '<span class="' + cls + '" title="Supprimer" style="cursor:pointer;color:#c62828">🗑️</span>';
    };

    var win = Ext.create('Ext.window.Window', {
        title: 'Créances — ' + rec.get('nomCompte'), width: 820, modal: true,
        height: Math.min(620, Ext.getBody().getViewSize().height - 60), layout: 'border',
        items: [
            { region: 'north', xtype: 'container', items: [entete], style: 'background:#f4f6f8;border-bottom:1px solid #ddd' },
            { region: 'center', xtype: 'tabpanel', items: [
                { title: '🧾 Factures / Avoirs', layout: 'fit', items: [{
                    xtype: 'grid', store: creances,
                    columns: [
                        { text: 'Type', dataIndex: 'type', width: 80 },
                        { text: 'N°', dataIndex: 'numero', width: 110 },
                        { text: 'Émission', dataIndex: 'dateEmission', width: 95, renderer: fmtDate },
                        { text: 'Échéance', dataIndex: 'dateEcheance', width: 95, renderer: fmtDate },
                        { text: 'Montant', dataIndex: 'montant', width: 120, align: 'right', renderer: Usp.recouvrement.money },
                        { text: 'Statut', dataIndex: 'statut', width: 100 },
                        { text: '', width: 40, align: 'center', sortable: false, menuDisabled: true, dataIndex: 'id',
                          renderer: function () { return supprBtn('cre-del'); } }
                    ],
                    tbar: [Usp.permBtn('recouvrement', 'CREER', { text: '➕ Facture / Avoir',
                        handler: function () { Usp.recouvrement.creanceForm(base, creances, toutRafraichir); } })],
                    listeners: { cellclick: function (g, td, ci, r, tr, ri, e) {
                        if (e.getTarget('.cre-del')) { Usp.recouvrement.suppr(base + '/creances/' + r.get('id'), toutRafraichir); } } }
                }] },
                { title: '💵 Règlements', layout: 'fit', items: [{
                    xtype: 'grid', store: paiements,
                    columns: [
                        { text: 'Date', dataIndex: 'datePaiement', width: 100, renderer: fmtDate },
                        { text: 'Montant', dataIndex: 'montant', width: 120, align: 'right', renderer: Usp.recouvrement.money },
                        { text: 'Mode', dataIndex: 'mode', width: 120 },
                        { text: 'Référence', dataIndex: 'reference', flex: 1 },
                        { text: '', width: 40, align: 'center', sortable: false, menuDisabled: true, dataIndex: 'id',
                          renderer: function () { return supprBtn('pai-del'); } }
                    ],
                    tbar: [Usp.permBtn('recouvrement', 'CREER', { text: '➕ Règlement',
                        handler: function () { Usp.recouvrement.paiementForm(base, toutRafraichir); } })],
                    listeners: { cellclick: function (g, td, ci, r, tr, ri, e) {
                        if (e.getTarget('.pai-del')) { Usp.recouvrement.suppr(base + '/paiements/' + r.get('id'), toutRafraichir); } } }
                }] },
                { title: '🤝 Promesses', layout: 'fit', items: [{
                    xtype: 'grid', store: promesses,
                    columns: [
                        { text: 'Date promesse', dataIndex: 'datePromesse', width: 120, renderer: fmtDate },
                        { text: 'Montant', dataIndex: 'montant', width: 120, align: 'right', renderer: Usp.recouvrement.money },
                        { text: 'Statut', dataIndex: 'statut', width: 120 },
                        { text: 'Notes', dataIndex: 'notes', flex: 1 },
                        { text: '', width: 40, align: 'center', sortable: false, menuDisabled: true, dataIndex: 'id',
                          renderer: function () { return supprBtn('pro-del'); } }
                    ],
                    tbar: [Usp.permBtn('recouvrement', 'CREER', { text: '➕ Promesse',
                        handler: function () { Usp.recouvrement.promesseForm(base, toutRafraichir); } })],
                    listeners: { cellclick: function (g, td, ci, r, tr, ri, e) {
                        if (e.getTarget('.pro-del')) { Usp.recouvrement.suppr(base + '/promesses/' + r.get('id'), toutRafraichir); } } }
                }] }
            ] }
        ]
    });
    win.show();
    rafraichirSituation();
};

Usp.recouvrement.suppr = function (url, cb) {
    Ext.Msg.confirm('Supprimer', 'Confirmer la suppression ?', function (b) {
        if (b !== 'yes') { return; }
        Usp.ajax({ url: url, method: 'DELETE', success: cb,
            failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
    });
};

Usp.recouvrement.creanceForm = function (base, store, cb) {
    var win = Ext.create('Ext.window.Window', {
        title: 'Facture / Avoir', width: 480, modal: true, bodyPadding: 12,
        items: [{ xtype: 'form', border: false, defaults: { anchor: '100%', labelWidth: 130 }, items: [
            { xtype: 'combobox', name: 'type', fieldLabel: 'Type', value: 'FACTURE', queryMode: 'local',
              editable: false, store: [['FACTURE', 'Facture'], ['AVOIR', 'Avoir']] },
            { xtype: 'textfield', name: 'numero', fieldLabel: 'N° pièce' },
            { xtype: 'datefield', name: 'dateEmission', fieldLabel: 'Date émission', format: 'd/m/Y', submitFormat: 'Y-m-d', editable: false },
            { xtype: 'datefield', name: 'dateEcheance', fieldLabel: 'Date échéance', format: 'd/m/Y', submitFormat: 'Y-m-d', editable: false },
            { xtype: 'numberfield', name: 'montant', fieldLabel: 'Montant', allowBlank: false, hideTrigger: true },
            { xtype: 'textfield', name: 'statut', fieldLabel: 'Statut' },
            { xtype: 'textfield', name: 'notes', fieldLabel: 'Notes' }
        ] }],
        buttons: [{ text: 'Enregistrer', formBind: true, handler: function (b) {
            var f = b.up('window').down('form').getForm();
            if (!f.isValid()) { return; }
            Usp.ajax({ url: base + '/creances', method: 'POST', jsonData: Usp.compact(f.getValues()),
                success: function () { win.close(); cb(); },
                failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
        } }]
    });
    win.show();
};

Usp.recouvrement.paiementForm = function (base, cb) {
    var win = Ext.create('Ext.window.Window', {
        title: 'Règlement', width: 460, modal: true, bodyPadding: 12,
        items: [{ xtype: 'form', border: false, defaults: { anchor: '100%', labelWidth: 130 }, items: [
            { xtype: 'datefield', name: 'datePaiement', fieldLabel: 'Date', format: 'd/m/Y', submitFormat: 'Y-m-d', editable: false, value: new Date() },
            { xtype: 'numberfield', name: 'montant', fieldLabel: 'Montant', allowBlank: false, hideTrigger: true },
            { xtype: 'textfield', name: 'mode', fieldLabel: 'Mode (espèces, virement…)' },
            { xtype: 'textfield', name: 'reference', fieldLabel: 'Référence' }
        ] }],
        buttons: [{ text: 'Enregistrer', formBind: true, handler: function (b) {
            var f = b.up('window').down('form').getForm();
            if (!f.isValid()) { return; }
            Usp.ajax({ url: base + '/paiements', method: 'POST', jsonData: Usp.compact(f.getValues()),
                success: function () { win.close(); cb(); },
                failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
        } }]
    });
    win.show();
};

Usp.recouvrement.promesseForm = function (base, cb) {
    var win = Ext.create('Ext.window.Window', {
        title: 'Promesse de paiement', width: 460, modal: true, bodyPadding: 12,
        items: [{ xtype: 'form', border: false, defaults: { anchor: '100%', labelWidth: 130 }, items: [
            { xtype: 'datefield', name: 'datePromesse', fieldLabel: 'Date promesse', format: 'd/m/Y', submitFormat: 'Y-m-d', editable: false },
            { xtype: 'numberfield', name: 'montant', fieldLabel: 'Montant', allowBlank: false, hideTrigger: true },
            { xtype: 'combobox', name: 'statut', fieldLabel: 'Statut', value: 'EN_ATTENTE', queryMode: 'local', editable: false,
              store: [['EN_ATTENTE', 'En attente'], ['TENUE', 'Tenue'], ['NON_TENUE', 'Non tenue']] },
            { xtype: 'textfield', name: 'notes', fieldLabel: 'Notes' }
        ] }],
        buttons: [{ text: 'Enregistrer', formBind: true, handler: function (b) {
            var f = b.up('window').down('form').getForm();
            if (!f.isValid()) { return; }
            Usp.ajax({ url: base + '/promesses', method: 'POST', jsonData: Usp.compact(f.getValues()),
                success: function () { win.close(); cb(); },
                failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
        } }]
    });
    win.show();
};

/* ---------------------------- Référentiels ---------------------------- */
Usp.recouvrement.refGrid = function (type, titre) {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'code', 'libelle', 'actif'], autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/recouvrement/referentiels/' + type,
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } } });
    var form = function (rec) {
        var win = Ext.create('Ext.window.Window', {
            title: (rec ? 'Modifier' : 'Ajouter') + ' — ' + titre, width: 420, modal: true, bodyPadding: 12,
            items: [{ xtype: 'form', border: false, defaults: { anchor: '100%' }, items: [
                { xtype: 'textfield', name: 'code', fieldLabel: 'Code', emptyText: 'Laisser vide = généré' },
                { xtype: 'textfield', name: 'libelle', fieldLabel: 'Libellé', allowBlank: false }
            ] }],
            buttons: [{ text: 'Enregistrer', formBind: true, handler: function (b) {
                var f = b.up('window').down('form').getForm();
                if (!f.isValid()) { return; }
                Usp.ajax({ url: '/recouvrement/referentiels/' + type + (rec ? '/' + rec.get('id') : ''),
                    method: rec ? 'PUT' : 'POST', jsonData: Usp.compact(f.getValues()),
                    success: function () { win.close(); store.load(); },
                    failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
            } }]
        });
        win.show();
        if (rec) {
            // Repartir de la donnée fraîche du store (évite la valeur périmée après modif).
            var frais = store.getById(rec.get('id')) || rec;
            win.down('form').getForm().setValues(frais.getData());
        }
    };
    var basculerActif = function (rec) {
        Usp.ajax({ url: '/recouvrement/referentiels/' + type + '/' + rec.get('id') + '/actif?actif=' + (!rec.get('actif')),
            method: 'PUT', success: function () { store.load(); },
            failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
    };
    return {
        xtype: 'grid', title: titre, store: store, flex: 1,
        columns: [
            { text: 'Code', dataIndex: 'code', width: 160 },
            { text: 'Libellé', dataIndex: 'libelle', flex: 1 },
            { text: 'Actif', dataIndex: 'actif', width: 70, align: 'center',
              renderer: function (v) { return v ? '✅' : '—'; } },
            { text: 'Actions', width: 200, sortable: false, menuDisabled: true, dataIndex: 'id',
              renderer: function (v, m, rec) {
                  return '<span class="rr-edit" title="Modifier" style="cursor:pointer;margin-right:12px">✏️ Modifier</span>' +
                      '<span class="rr-act" title="Activer / Désactiver" style="cursor:pointer;color:' +
                      (rec.get('actif') ? '#c62828' : '#2e7d32') + '">' +
                      (rec.get('actif') ? '⛔ Désactiver' : '✅ Activer') + '</span>';
              } }
        ],
        tbar: [
            Usp.permBtn('recouvrement', 'GERER_REFERENTIELS', { text: '➕ Ajouter', handler: function () { form(null); } }),
            '->',
            Usp.permBtn('recouvrement', 'IMPORTER', { text: '📥 Importer (CSV code;libellé)',
                handler: function () { Usp.recouvrement.importRef(type, store); } }),
            { text: '🔄', tooltip: 'Rafraîchir', handler: function () { store.load(); } }
        ],
        listeners: {
            cellclick: function (g, td, ci, rec, tr, ri, e) {
                if (e.getTarget('.rr-edit')) {
                    if (!Usp.can('recouvrement', 'GERER_REFERENTIELS')) { Usp.refusPermission(); return; }
                    form(rec);
                } else if (e.getTarget('.rr-act')) {
                    if (!Usp.can('recouvrement', 'GERER_REFERENTIELS')) { Usp.refusPermission(); return; }
                    basculerActif(rec);
                }
            }
        }
    };
};

Usp.recouvrement.importRef = function (type, store) {
    var win = Ext.create('Ext.window.Window', {
        title: 'Importer — ' + type, width: 520, modal: true, bodyPadding: 12,
        items: [{ xtype: 'form', border: false, defaults: { anchor: '100%' }, items: [
            { xtype: 'displayfield', value: '<span style="color:#888">Une valeur par ligne, format ' +
                '<b>code;libellé</b> (séparateur <b>;</b>, sans id). Ex. <code>30J;Paiement 30 jours</code>. En-tête ignoré.</span>' },
            { xtype: 'textareafield', name: 'contenu', height: 170, emptyText: '30J;Paiement 30 jours\nCPT;Comptant' },
            { xtype: 'filefield', fieldLabel: 'ou fichier .csv', msgTarget: 'side',
              listeners: { change: function (f) {
                  var file = f.fileInputEl.dom.files[0]; if (!file) { return; }
                  var reader = new FileReader();
                  reader.onload = function (e) { f.up('form').down('[name=contenu]').setValue(e.target.result); };
                  reader.readAsText(file);
              } } }
        ] }],
        buttons: [{ text: 'Importer', handler: function (b) {
            var contenu = b.up('window').down('[name=contenu]').getValue();
            if (!contenu || !contenu.trim()) { Ext.Msg.alert('Info', 'Aucune donnée.'); return; }
            Usp.ajax({ url: '/recouvrement/referentiels/' + type + '/import', method: 'POST', jsonData: { contenu: contenu },
                success: function (resp) { win.close(); store.load();
                    Usp.toast(((Ext.decode(resp.responseText) || {}).crees || 0) + ' valeur(s) importée(s).'); },
                failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
        } }]
    });
    win.show();
};

Usp.recouvrement.referentielsPanel = function () {
    return {
        title: '⚙️ Référentiels relances', xtype: 'panel', layout: 'fit', bodyPadding: 6,
        items: [{ xtype: 'tabpanel', items: [
            // Les segments commerciaux réutilisent la segmentation clients existante
            // (plus de référentiel dédié). On ne paramètre ici que profils et statuts.
            Usp.recouvrement.refGrid('PROFIL_PAIEMENT', 'Profils de paiement'),
            Usp.recouvrement.refGrid('STATUT_RECOUVREMENT', 'Statuts de recouvrement')
        ] }]
    };
};

/* ---------------------------- Pièces jointes (relances) ---------------------------- */
/* Items de formulaire pour joindre un document (upload) et/ou un relevé de compte PDF.
 * cfg.releve : afficher la case « relevé auto » (true par défaut). */
Usp.recouvrement.pieceItems = function (cfg) {
    cfg = cfg || {};
    var items = [
        { xtype: 'hiddenfield', name: 'pieceMediaId' },
        { xtype: 'fieldcontainer', fieldLabel: 'Pièce jointe', layout: 'hbox', items: [
            { xtype: 'filefield', flex: 1, buttonText: 'Choisir un fichier…', buttonOnly: false, msgTarget: 'side',
              emptyText: 'PDF ou image (optionnel)',
              listeners: { change: function (f) {
                  var file = f.fileInputEl.dom.files[0]; if (!file) { return; }
                  var statut = f.up('fieldcontainer').down('#pieceStatut');
                  statut.update('<span style="color:#888">Téléversement…</span>');
                  var reader = new FileReader();
                  reader.onload = function (e) {
                      var data = String(e.target.result || '');
                      var b64 = data.indexOf(',') >= 0 ? data.substring(data.indexOf(',') + 1) : data;
                      Usp.ajax({ url: '/media/upload', method: 'POST',
                          jsonData: { fichierBase64: b64, mimeType: file.type || 'application/octet-stream', nomFichier: file.name },
                          success: function (resp) {
                              var r = Ext.decode(resp.responseText) || {};
                              f.up('form').getForm().findField('pieceMediaId').setValue(r.id);
                              statut.update('<span style="color:#2e7d32">📎 ' + Ext.String.htmlEncode(file.name) + '</span>');
                          },
                          failure: function (resp) {
                              statut.update('<span style="color:#c62828">Échec du téléversement.</span>');
                              Ext.Msg.alert('Erreur', Usp.erreurServeur(resp));
                          } });
                  };
                  reader.readAsDataURL(file);
              } } },
            { xtype: 'component', itemId: 'pieceStatut', margin: '4 0 0 8', width: 160, html: '' }
        ] }
    ];
    if (cfg.releve !== false) {
        items.push({ xtype: 'checkbox', name: 'releveAuto', boxLabel: 'Joindre un relevé de compte (PDF) généré automatiquement',
            hideLabel: true, margin: '0 0 0 0' });
    }
    return items;
};

/* Complète l'objet de données envoyé au serveur avec les champs de pièce jointe. */
Usp.recouvrement.appliquerPiece = function (form, data) {
    var media = form.findField('pieceMediaId');
    var releve = form.findField('releveAuto');
    if (media && media.getValue()) { data.pieceMediaId = media.getValue(); }
    if (releve) { data.releveAuto = !!releve.getValue(); }
    return data;
};

/* ---------------------------- Envoyer une relance ---------------------------- */
Usp.recouvrement.relanceForm = function (rec) {
    var modeleStore = Ext.create('Ext.data.Store', {
        fields: ['id', 'nom', 'type', 'canal'], autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/recouvrement/modeles',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } } });
    var win = Ext.create('Ext.window.Window', {
        title: 'Relancer — ' + rec.get('nomCompte'), width: 480, modal: true, bodyPadding: 12,
        items: [{ xtype: 'form', border: false, defaults: { anchor: '100%', labelWidth: 120 }, items: [
            { xtype: 'displayfield', fieldLabel: 'Solde', value: Usp.recouvrement.money(rec.get('solde')) },
            { xtype: 'combobox', name: 'modeleId', fieldLabel: 'Modèle', allowBlank: false, store: modeleStore,
              valueField: 'id', displayField: 'nom', queryMode: 'local', editable: false, emptyText: 'Choisir un modèle…' },
            { xtype: 'combobox', name: 'canal', fieldLabel: 'Canal', queryMode: 'local', editable: false,
              store: Usp.recouvrement.CANAUX, value: rec.get('canalPrefere') || 'WHATSAPP' }
        ].concat(Usp.recouvrement.pieceItems()) }],
        buttons: [{ text: '📨 Envoyer', formBind: true, handler: function (b) {
            var f = b.up('window').down('form').getForm();
            if (!f.isValid()) { return; }
            var v = f.getValues();
            v.clientId = rec.get('clientId');
            Usp.recouvrement.appliquerPiece(f, v);
            Usp.ajax({ url: '/recouvrement/envois', method: 'POST', jsonData: v,
                success: function (resp) {
                    var e = Ext.decode(resp.responseText) || {};
                    win.close();
                    if (e.statut === 'ENVOYE') { Usp.toast('Relance envoyée (' + e.canal + ').'); }
                    else { Ext.Msg.alert('Envoi en échec', e.erreur || 'Échec de l\'envoi.'); }
                },
                failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
        } }]
    });
    win.show();
};

/* ---------------------------- Assistant de relance ---------------------------- */
Usp.recouvrement.MOTIFS = {
    RELANCE_PREVENTIVE: 'Relance préventive', FACTURE_ECHUE: 'Facture échue',
    DEUXIEME_RELANCE: 'Deuxième relance', PROMESSE_NON_TENUE: 'Promesse non tenue',
    PAIEMENT_PARTIEL: 'Paiement partiel', CLIENT_CRITIQUE: 'Client critique'
};
Usp.recouvrement._assistantStores = [];
Usp.recouvrement.rechargerAssistant = function () {
    Usp.recouvrement._assistantStores.forEach(function (s) { s.load(); });
};

/* Une grille de propositions pour un statut donné (sous-onglet de l'assistant). */
Usp.recouvrement.assistantGrid = function (statut, titre) {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'clientId', 'nomCompte', 'motif', 'priorite', 'joursRetard', 'montant',
                 'canalRecommande', 'modeleId', 'modeleNom'],
        autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/recouvrement/propositions',
            extraParams: { statut: statut },
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } } });
    Usp.recouvrement._assistantStores.push(store);
    var prioColor = { CRITIQUE: '#c62828', HAUTE: '#ef6c00', NORMALE: '#555' };
    var enAttente = (statut === 'PROPOSEE');
    var cols = [
        { text: 'Client', dataIndex: 'nomCompte', flex: 1 },
        { text: 'Motif', dataIndex: 'motif', width: 160, renderer: function (v) { return Usp.recouvrement.MOTIFS[v] || v; } },
        { text: 'Priorité', dataIndex: 'priorite', width: 90, renderer: function (v) {
            return '<span style="color:' + (prioColor[v] || '#555') + ';font-weight:bold">' + (v || '') + '</span>'; } },
        { text: 'Retard (j)', dataIndex: 'joursRetard', width: 80, align: 'right' },
        { text: 'Montant dû', dataIndex: 'montant', width: 120, align: 'right', renderer: Usp.recouvrement.money },
        { text: 'Canal', dataIndex: 'canalRecommande', width: 90 },
        { text: 'Modèle conseillé', dataIndex: 'modeleNom', width: 160 }
    ];
    if (enAttente) {
        cols.push({ text: 'Actions', width: 200, sortable: false, menuDisabled: true, dataIndex: 'id',
            renderer: function () {
                return '<span class="prop-ok" title="Valider et envoyer" style="cursor:pointer;color:#2e7d32;margin-right:12px">✅ Valider</span>'
                    + '<span class="prop-no" title="Rejeter" style="cursor:pointer;color:#c62828">✖ Rejeter</span>';
            } });
    }
    var tbar = [];
    if (enAttente) {
        tbar.push(Usp.permBtn('recouvrement', 'CREER', { text: '🔍 Analyser maintenant', handler: function () {
            Usp.ajax({ url: '/recouvrement/propositions/generer', method: 'POST',
                success: function (resp) { Usp.recouvrement.rechargerAssistant();
                    Usp.toast(((Ext.decode(resp.responseText) || {}).crees || 0) + ' proposition(s) générée(s).'); },
                failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
        } }));
    }
    tbar.push({ text: '🔄 Rafraîchir', handler: function () { store.load(); } });
    return {
        xtype: 'grid', title: titre, store: store, columns: cols, tbar: tbar,
        listeners: {
            cellclick: function (g, td, ci, rec, tr, ri, e) {
                if (e.getTarget('.prop-ok')) {
                    if (!Usp.can('recouvrement', 'ENVOYER')) { Usp.refusPermission(); return; }
                    Usp.recouvrement.validerProposition(rec, store);
                } else if (e.getTarget('.prop-no')) {
                    Usp.ajax({ url: '/recouvrement/propositions/' + rec.get('id') + '/rejeter', method: 'POST',
                        success: function () { Usp.recouvrement.rechargerAssistant(); },
                        failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
                }
            }
        }
    };
};

/* Assistant présenté en sous-onglets par statut. */
Usp.recouvrement.assistantPanel = function () {
    Usp.recouvrement._assistantStores = [];
    return {
        xtype: 'tabpanel', title: '🤖 Assistant', listeners: Usp.tabListeners,
        items: [
            Usp.recouvrement.assistantGrid('PROPOSEE', 'En attente'),
            Usp.recouvrement.assistantGrid('VALIDEE', 'Validées'),
            Usp.recouvrement.assistantGrid('REJETEE', 'Rejetées')
        ]
    };
};

Usp.recouvrement.validerProposition = function (rec, store) {
    var modeleStore = Ext.create('Ext.data.Store', {
        fields: ['id', 'nom'], autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/recouvrement/modeles',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } } });
    var win = Ext.create('Ext.window.Window', {
        title: 'Valider la relance — ' + rec.get('nomCompte'), width: 480, modal: true, bodyPadding: 12,
        items: [{ xtype: 'form', border: false, defaults: { anchor: '100%', labelWidth: 120 }, items: [
            { xtype: 'displayfield', fieldLabel: 'Motif', value: Usp.recouvrement.MOTIFS[rec.get('motif')] || rec.get('motif') },
            { xtype: 'combobox', name: 'modeleId', fieldLabel: 'Modèle', allowBlank: false, store: modeleStore,
              valueField: 'id', displayField: 'nom', queryMode: 'local', editable: false,
              value: rec.get('modeleId') || null, emptyText: 'Choisir un modèle…' },
            { xtype: 'combobox', name: 'canal', fieldLabel: 'Canal', queryMode: 'local', editable: false,
              store: Usp.recouvrement.CANAUX, value: rec.get('canalRecommande') || 'WHATSAPP' }
        ].concat(Usp.recouvrement.pieceItems()) }],
        buttons: [{ text: '✅ Valider et envoyer', formBind: true, handler: function (b) {
            var f = b.up('window').down('form').getForm();
            if (!f.isValid()) { return; }
            Usp.ajax({ url: '/recouvrement/propositions/' + rec.get('id') + '/valider', method: 'POST',
                jsonData: Usp.recouvrement.appliquerPiece(f, f.getValues()),
                success: function () { win.close(); Usp.recouvrement.rechargerAssistant(); Usp.toast('Relance envoyée.'); },
                failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
        } }]
    });
    win.show();
};

/* ---------------------------- Campagnes ciblées ---------------------------- */
Usp.recouvrement.campagnesPanel = function () {
    var modeleStore = Ext.create('Ext.data.Store', {
        fields: ['id', 'nom'], autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/recouvrement/modeles',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } } });
    var filtre = function (form) {
        var v = form.getValues();
        return { agence: v.agence, responsable: v.responsable, segment: v.segment, profil: v.profil,
                 montantMin: v.montantMin, joursMin: v.joursMin };
    };
    return {
        title: '📣 Campagnes', xtype: 'form', bodyPadding: 14, autoScroll: true,
        defaults: { anchor: '60%', labelWidth: 170 },
        items: [
            { xtype: 'displayfield', value: '<b>Cibler les clients à relancer</b> selon les critères, puis envoyer un modèle.' },
            Usp.referentielCombo('AGENCE', { name: 'agence', fieldLabel: 'Agence' }),
            { xtype: 'textfield', name: 'responsable', fieldLabel: 'Responsable recouvrement' },
            // Composant Audience : une ou plusieurs segmentations client (SMC).
            Usp.multiPicker({ name: 'segment', fieldLabel: 'Segments (un ou plusieurs)',
                url: '/segmentations', valueField: 'libelle', displayField: 'libelle' }),
            Usp.recouvrement.refCombo('PROFIL_PAIEMENT', { name: 'profil', fieldLabel: 'Profil de paiement' }),
            { xtype: 'numberfield', name: 'montantMin', fieldLabel: 'Montant dû minimum', hideTrigger: true, minValue: 0 },
            { xtype: 'numberfield', name: 'joursMin', fieldLabel: 'Ancienneté min. (jours retard)', hideTrigger: true, minValue: 0 },
            { xtype: 'combobox', name: 'modeleId', fieldLabel: 'Modèle de relance', store: modeleStore,
              valueField: 'id', displayField: 'nom', queryMode: 'local', editable: false, emptyText: 'Choisir…' },
            { xtype: 'combobox', name: 'canal', fieldLabel: 'Canal', queryMode: 'local', editable: false,
              store: Usp.recouvrement.CANAUX, value: 'WHATSAPP' }
        ].concat(Usp.recouvrement.pieceItems())
         .concat([{ xtype: 'component', itemId: 'apercu', margin: '6 0 0 0', html: '' }]),
        bbar: [
            { text: '🧹 Réinitialiser l\'écran', tooltip: 'Vider les données saisies dans les champs',
              handler: function (b) {
                  var fp = b.up('form');
                  fp.getForm().reset();
                  var ap = fp.down('#apercu'); if (ap) { ap.update(''); }
              } },
            '->',
            { text: '👁️ Aperçu', handler: function (b) {
                var form = b.up('form').getForm();
                Usp.ajax({ url: '/recouvrement/campagnes/preview', method: 'POST', jsonData: filtre(form),
                    success: function (resp) {
                        var r = Ext.decode(resp.responseText) || {};
                        b.up('form').down('#apercu').update('<span style="color:#1976d2"><b>' + (r.count || 0)
                            + '</b> client(s) ciblé(s). ' + (!r.count ? '<span style="color:#888">(Aucune fiche recouvrement ne correspond aux critères — créez des fiches ou élargissez les filtres.)</span>' : '') + '</span>');
                    },
                    failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
            } },
            Usp.permBtn('recouvrement', 'ENVOYER', { text: '📨 Envoyer la campagne', handler: function (b) {
                var form = b.up('form').getForm();
                var data = filtre(form);
                data.modeleId = form.findField('modeleId').getValue();
                data.canal = form.findField('canal').getValue();
                Usp.recouvrement.appliquerPiece(form, data);
                if (!data.modeleId) { Ext.Msg.alert('Modèle', 'Choisissez un modèle de relance.'); return; }
                Ext.Msg.confirm('Envoyer', 'Lancer la campagne de relance vers les clients ciblés ?', function (btn) {
                    if (btn !== 'yes') { return; }
                    Usp.ajax({ url: '/recouvrement/campagnes/envoyer', method: 'POST', jsonData: data,
                        success: function (resp) {
                            var r = Ext.decode(resp.responseText) || {};
                            Ext.Msg.alert('Campagne terminée', (r.envoyes || 0) + ' envoi(s) réussi(s), '
                                + (r.echecs || 0) + ' échec(s) sur ' + (r.cibles || 0) + ' cible(s).');
                        },
                        failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
                });
            } })
        ]
    };
};

/* ---------------------------- Modèles de relance ---------------------------- */
Usp.recouvrement.modelesPanel = function () {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'code', 'nom', 'type', 'canal', 'sujet', 'corps', 'nomModeleWhatsapp', 'paramsCorps', 'actif'],
        autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/recouvrement/modeles',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } } });
    var typeLib = function (v) {
        var m = Ext.Array.findBy(Usp.recouvrement.TYPES_MODELE, function (a) { return a[0] === v; });
        return m ? m[1] : (v || '');
    };
    // Active/désactive le bouton « Modèles standard » selon qu'il en manque ou non.
    var majBoutonStandard = function (grid) {
        var btn = grid.down('#btnModStd'); if (!btn) { return; }
        var noms = {};
        store.each(function (r) { noms[(r.get('nom') || '').toLowerCase()] = true; });
        var manquants = Usp.recouvrement.MODELES_STANDARD.filter(function (m) { return !noms[m.nom.toLowerCase()]; });
        if (manquants.length) {
            btn.enable(); btn.setTooltip('Générer les ' + manquants.length + ' modèle(s) standard manquant(s)');
        } else {
            btn.disable(); btn.setTooltip('Tous les modèles standard sont déjà générés');
        }
    };
    return {
        xtype: 'grid', title: '✉️ Modèles', store: store,
        columns: [
            { text: 'Code', dataIndex: 'code', width: 130 },
            { text: 'Nom', dataIndex: 'nom', flex: 1 },
            { text: 'Type', dataIndex: 'type', width: 150, renderer: typeLib },
            { text: 'Canal', dataIndex: 'canal', width: 90 },
            { text: 'Actif', dataIndex: 'actif', width: 60, align: 'center', renderer: function (v) { return v ? '✔' : '✖'; } },
            { text: 'Actions', width: 130, align: 'center', sortable: false, menuDisabled: true, dataIndex: 'id',
              renderer: function () {
                  return '<span class="mod-edit" title="Modifier" style="cursor:pointer;margin:0 6px">✏️</span>' +
                      '<span class="mod-del" title="Supprimer" style="cursor:pointer;color:#c62828;margin:0 6px">🗑️</span>';
              } }
        ],
        tbar: [
            Usp.permBtn('recouvrement', 'CREER', { text: '➕ Nouveau modèle', handler: function () { Usp.recouvrement.modeleForm(store, null); } }),
            Usp.permBtn('recouvrement', 'CREER', { itemId: 'btnModStd', text: '📋 Modèles standard',
                tooltip: 'Générer les modèles de relance standard manquants',
                handler: function (b) { Usp.recouvrement.creerModelesStandard(store, b.up('grid')); } }),
            { text: '🔄 Rafraîchir', handler: function () { store.load(); } }
        ],
        listeners: {
            afterrender: function (g) { store.on('load', function () { majBoutonStandard(g); }); majBoutonStandard(g); },
            cellclick: function (g, td, ci, rec, tr, ri, e) {
                if (e.getTarget('.mod-edit')) { Usp.recouvrement.modeleForm(store, rec); return; }
                if (e.getTarget('.mod-del')) {
                    if (!Usp.can('recouvrement', 'SUPPRIMER')) { Usp.refusPermission(); return; }
                    Ext.Msg.confirm('Supprimer', 'Supprimer le modèle « ' + Ext.String.htmlEncode(rec.get('nom')) + ' » ?',
                        function (btn) {
                            if (btn !== 'yes') { return; }
                            Usp.ajax({ url: '/recouvrement/modeles/' + rec.get('id'), method: 'DELETE',
                                success: function () { store.load(); Usp.toast('Modèle supprimé.'); },
                                failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
                        });
                    return;
                }
            },
            itemdblclick: function (g, rec) { Usp.recouvrement.modeleForm(store, rec); }
        }
    };
};

/* Jeu de modèles de relance standard (créés s'ils n'existent pas déjà, par nom). */
Usp.recouvrement.MODELES_STANDARD = [
    { nom: 'Relance préventive', type: 'RELANCE_PREVENTIVE', canal: 'TOUS',
      corps: 'Bonjour {nom_client},\nVotre facture {numero_facture} arrive à échéance le {date_echeance} '
        + '(montant : {montant_du}). Merci de prévoir son règlement. Cordialement.' },
    { nom: 'Facture échue', type: 'FACTURE_ECHUE', canal: 'TOUS',
      corps: 'Bonjour {nom_client},\nLa facture {numero_facture} d\'un montant de {montant_du} est échue depuis '
        + '{jours_retard} jour(s). Merci de régulariser votre situation dans les meilleurs délais.' },
    { nom: 'Impayé', type: 'IMPAYE', canal: 'TOUS',
      corps: 'Bonjour {nom_client},\nMalgré nos relances, votre solde de {solde} reste impayé. '
        + 'Nous vous invitons à procéder au règlement sans délai afin d\'éviter toute suspension.' },
    { nom: 'Mise en demeure', type: 'MISE_EN_DEMEURE', canal: 'EMAIL',
      sujet: 'Mise en demeure — solde impayé', corps: 'Madame, Monsieur ({nom_societe}),\n'
        + 'Sauf règlement de la somme de {solde} sous huitaine, nous serons contraints d\'engager '
        + 'une procédure de recouvrement. Cette lettre vaut mise en demeure.' }
];
Usp.recouvrement.creerModelesStandard = function (store) {
    var existants = {};
    store.each(function (r) { existants[(r.get('nom') || '').toLowerCase()] = true; });
    var aCreer = Usp.recouvrement.MODELES_STANDARD.filter(function (m) { return !existants[m.nom.toLowerCase()]; });
    if (!aCreer.length) { Usp.toast('Les modèles standard existent déjà.'); return; }
    // Confirmation avant génération groupée.
    Ext.Msg.confirm('Modèles standard',
        'Générer ' + aCreer.length + ' modèle(s) de relance standard d\'un coup ?',
        function (btn) {
            if (btn !== 'yes') { return; }
            var reste = aCreer.length, crees = 0;
            aCreer.forEach(function (m) {
                var data = Ext.apply({ actif: true }, m);
                Usp.ajax({ url: '/recouvrement/modeles', method: 'POST', jsonData: data,
                    success: function () { crees++; if (--reste === 0) { store.load(); Usp.toast(crees + ' modèle(s) standard créé(s).'); } },
                    failure: function () { if (--reste === 0) { store.load(); Usp.toast(crees + ' modèle(s) standard créé(s).'); } } });
            });
        });
};

Usp.recouvrement.modeleForm = function (store, rec) {
    var win = Ext.create('Ext.window.Window', {
        title: rec ? 'Modifier le modèle' : 'Nouveau modèle de relance', width: 780, modal: true, bodyPadding: 12,
        items: [{ xtype: 'form', border: false, layout: 'column',
            defaults: { columnWidth: 0.5, xtype: 'container', layout: 'anchor', border: false,
                        defaults: { anchor: '96%', labelWidth: 130 } },
            items: [
                { items: [
                    { xtype: 'textfield', name: 'nom', fieldLabel: 'Nom', allowBlank: false },
                    { xtype: 'combobox', name: 'type', fieldLabel: 'Type', queryMode: 'local', editable: false,
                      store: Usp.recouvrement.TYPES_MODELE, value: 'DIVERS' },
                    { xtype: 'combobox', name: 'canal', fieldLabel: 'Canal', queryMode: 'local', editable: false,
                      store: [['TOUS', 'Tous'], ['WHATSAPP', 'WhatsApp'], ['EMAIL', 'Email']], value: 'TOUS' },
                    { xtype: 'checkbox', name: 'actif', fieldLabel: 'Actif', checked: true }
                ] },
                { items: [
                    { xtype: 'textfield', name: 'sujet', fieldLabel: 'Sujet (Email)' },
                    { xtype: 'textfield', name: 'nomModeleWhatsapp', fieldLabel: 'Nom modèle Meta', emptyText: 'canal WhatsApp API (hors 24h)' },
                    { xtype: 'textfield', name: 'paramsCorps', fieldLabel: 'Paramètres {{1}},{{2}}', emptyText: 'ex. nom_client,montant_du' }
                ] },
                { columnWidth: 1, items: [
                    { xtype: 'textareafield', name: 'corps', fieldLabel: 'Corps du message', height: 130, allowBlank: false, anchor: '98%', labelWidth: 130 },
                    { xtype: 'displayfield', anchor: '98%', value: '<span style="color:#666">Variables : {nom_client}, {nom_societe}, ' +
                        '{solde}, {montant_du}, {jours_retard}, {numero_facture}, {date_echeance}.</span>' }
                ] }
            ] }],
        buttons: [{ text: 'Enregistrer', formBind: true, handler: function (b) {
            var f = b.up('window').down('form').getForm();
            if (!f.isValid()) { return; }
            var v = f.getValues();
            v.actif = f.findField('actif').getValue();
            Usp.ajax({ url: rec ? '/recouvrement/modeles/' + rec.get('id') : '/recouvrement/modeles',
                method: rec ? 'PUT' : 'POST', jsonData: v,
                success: function () { win.close(); store.load(); Usp.toastEnregistre('Modèle', !!rec); },
                failure: function (resp) { Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
        } }]
    });
    win.show();
    if (rec) { win.down('form').getForm().setValues(rec.getData()); }
};

/* ---------------------------- Historique des envois ---------------------------- */
Usp.recouvrement.historiquePanel = function () {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'clientId', 'canal', 'destinataire', 'sujet', 'message', 'statut', 'erreur', 'creePar', 'createdAt', 'pieceJointe'],
        autoLoad: true,
        proxy: { type: 'ajax', url: Usp.apiBase + '/recouvrement/envois',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } } });
    return {
        xtype: 'grid', title: '🗂️ Historique', store: store,
        columns: [
            { text: 'Date', dataIndex: 'createdAt', width: 140, renderer: function (v) { return v ? String(v).replace('T', ' ').substring(0, 16) : ''; } },
            { text: 'Canal', dataIndex: 'canal', width: 90 },
            { text: 'Destinataire', dataIndex: 'destinataire', width: 160 },
            { text: 'Message', dataIndex: 'message', flex: 1, renderer: function (v) { return Ext.String.htmlEncode((v || '').substring(0, 120)); } },
            { text: 'Pièce jointe', dataIndex: 'pieceJointe', width: 150, renderer: function (v) {
                return v ? '📎 ' + Ext.String.htmlEncode(v) : ''; } },
            { text: 'Statut', dataIndex: 'statut', width: 90, renderer: function (v) {
                return '<span style="color:' + (v === 'ENVOYE' ? '#2e7d32' : '#c62828') + ';font-weight:bold">' + (v || '') + '</span>'; } },
            { text: 'Erreur', dataIndex: 'erreur', width: 200 },
            { text: 'Par', dataIndex: 'creePar', width: 110 }
        ],
        tbar: [{ text: '🔄 Rafraîchir', handler: function () { store.load(); } }, '-']
            .concat(Usp.grilleFiltre(store, {
                champs: ['destinataire', 'message', 'creePar'], periode: true, dateChamp: 'createdAt',
                selects: [
                    { field: 'canal', label: 'Canal', width: 110, options: [{ v: 'WHATSAPP', t: 'WhatsApp' }, { v: 'EMAIL', t: 'Email' }] },
                    { field: 'statut', label: 'Statut', width: 110, options: [{ v: 'ENVOYE', t: 'Envoyé' }, { v: 'ECHOUE', t: 'Échoué' }] }
                ]
            }))
            .concat(Usp.export.boutons('Recouvrement - historique'))
    };
};

/* ---------------------------- Import CSV ---------------------------- */
Usp.recouvrement.importPanel = function () {
    var majApercu = function (fs, type) {
        var contenu = fs.down('#ta_' + type).getValue() || '';
        var cmp = fs.down('#ap_' + type);
        var lignes = contenu.split(/\r?\n/).filter(function (l) { return l.trim() !== ''; });
        // 1ʳᵉ ligne = en-tête, non comptée.
        var nb = Math.max(0, lignes.length - 1);
        cmp.update(nb ? '<span style="color:#1976d2"><b>' + nb + '</b> ligne(s) de données détectée(s) (hors en-tête).</span>'
            : '<span style="color:#888">Aucune donnée pour le moment.</span>');
    };
    var bloc = function (titre, type, exemple) {
        return {
            xtype: 'fieldset', title: titre, margin: '0 0 10 0', defaults: { anchor: '100%' }, items: [
                { xtype: 'textareafield', itemId: 'ta_' + type, height: 110, emptyText: exemple,
                  listeners: { change: function (f) { majApercu(f.up('fieldset'), type); }, buffer: 300 } },
                { xtype: 'component', itemId: 'ap_' + type, margin: '2 0 4 0',
                  html: '<span style="color:#888">Aucune donnée pour le moment.</span>' },
                { xtype: 'fieldcontainer', layout: 'hbox', items: [
                    { xtype: 'filefield', flex: 1, buttonText: 'Fichier .csv…', buttonOnly: false, msgTarget: 'side',
                      listeners: { change: function (f) {
                          var file = f.fileInputEl.dom.files[0]; if (!file) { return; }
                          var reader = new FileReader();
                          reader.onload = function (e) {
                              f.up('fieldset').down('#ta_' + type).setValue(e.target.result);
                              majApercu(f.up('fieldset'), type);
                          };
                          reader.readAsText(file);
                      } } },
                    Usp.permBtn('recouvrement', 'IMPORTER', { xtype: 'button', text: '📥 Importer', margin: '0 0 0 8',
                      handler: function (b) {
                          var fs = b.up('fieldset');
                          var contenu = fs.down('#ta_' + type).getValue();
                          if (!contenu || !contenu.trim()) { Ext.Msg.alert('Info', 'Aucune donnée.'); return; }
                          b.disable();
                          Usp.ajax({ url: '/recouvrement/import/' + type, method: 'POST', jsonData: { contenu: contenu },
                              success: function (resp) {
                                  b.enable();
                                  var r = Ext.decode(resp.responseText) || {};
                                  Ext.Msg.show({ title: 'Rapport d\'import', buttons: Ext.Msg.OK, width: 380,
                                      msg: '<div style="font-family:sans-serif">✅ Créés : <b style="color:#2e7d32">' + (r.crees || 0)
                                          + '</b><br>♻️ Mis à jour : <b>' + (r.misAJour || 0)
                                          + '</b><br>⏭️ Ignorés (client introuvable) : <b>' + (r.ignores || 0) + '</b></div>' });
                              },
                              failure: function (resp) { b.enable(); Ext.Msg.alert('Erreur', Usp.erreurServeur(resp)); } });
                      } })
                ] }
            ]
        };
    };
    return {
        title: '📥 Import', xtype: 'panel', autoScroll: true, bodyPadding: 12,
        items: [
            { xtype: 'displayfield', value: '<span style="color:#666">Séparateur <b>;</b> (point-virgule) ou tabulation. ' +
                '1ʳᵉ ligne = en-tête (noms de colonnes). Rattachement par <b>numero_client</b>.</span>' },
            bloc('Fiches (initialisation)', 'fiches', 'numero_client;encours_initial;segment;profil;responsable;statut\nC001;150000;Diamond;Paiement 30 jours;Awa;Sous surveillance'),
            bloc('Créances (factures / avoirs)', 'creances', 'numero_client;type;numero;date_emission;date_echeance;montant;statut\nC001;FACTURE;FA-2026-001;2026-06-01;2026-07-01;250000;ECHUE'),
            bloc('Règlements', 'paiements', 'numero_client;date_paiement;montant;mode;reference\nC001;2026-06-15;100000;Virement;VIR-123')
        ]
    };
};
