/*
 * UbiSenderPro - Historique global des envois (tous canaux).
 * Agrège discussions, campagnes et envois de masse WhatsApp Web.
 * Dépend de app.js (objet Usp) et waweb.js (détail d'un envoi de masse).
 */
Ext.define('Usp.history', { singleton: true });

Usp.history._fmtDate = function (v) {
    return v ? Ext.String.htmlEncode(String(v).replace('T', ' ').substring(0, 16)) : '';
};

Usp.history._badgeCanal = function (v) {
    if (v === 'WEB') {
        return '<span style="background:#25d366;color:#fff;border-radius:3px;padding:1px 6px;font-size:11px">Web</span>';
    }
    return '<span style="background:#1976d2;color:#fff;border-radius:3px;padding:1px 6px;font-size:11px">API</span>';
};

Usp.history._libelleType = function (v) {
    switch (v) {
        case 'DISCUSSION': return 'Discussion';
        case 'CAMPAGNE': return 'Campagne';
        case 'ENVOI_MASSE': return 'Envoi de masse';
        default: return v || '';
    }
};

Usp.history._badgeStatut = function (v) {
    var s = (v || '').toUpperCase();
    if (s === 'ECHEC' || s === 'ECHOUE' || s === 'FAILED') {
        return '<span style="color:#c62828;font-weight:bold">' + Ext.String.htmlEncode(v) + '</span>';
    }
    if (s === 'ENVOYE' || s === 'DELIVRE' || s === 'LU' || s === 'SENT') {
        return '<span style="color:#2e7d32">' + Ext.String.htmlEncode(v) + '</span>';
    }
    return Ext.String.htmlEncode(v || '');
};

Usp.history.store = function () {
    return Ext.create('Ext.data.Store', {
        fields: ['type', 'canal', 'sourceId', 'parentId', 'libelle', 'numero', 'nom',
                 'utilisateur', 'apercu', 'statut', 'erreur', 'date'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/historique',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' },
            extraParams: { limit: 300 } },
        autoLoad: true
    });
};

Usp.history.panel = function () {
    var store = Usp.history.store();

    var recharger = function (grid) {
        var tb = grid.down('toolbar');
        var canal = tb.down('#fCanal').getValue() || '';
        var type = tb.down('#fType').getValue() || '';
        var q = tb.down('#fRecherche').getValue() || '';
        var dd = tb.down('#fDateDebut').getValue();
        var df = tb.down('#fDateFin').getValue();
        store.getProxy().extraParams = {
            limit: 300, canal: canal, type: type, q: q,
            dateDebut: dd ? Ext.Date.format(dd, 'Y-m-d') : '',
            dateFin: df ? Ext.Date.format(df, 'Y-m-d') : ''
        };
        store.load();
    };

    return {
        xtype: 'grid', title: 'Historique des envois', store: store,
        columns: [
            { text: 'Date', dataIndex: 'date', width: 130, renderer: Usp.history._fmtDate },
            { text: 'Canal', dataIndex: 'canal', width: 70, align: 'center',
              renderer: Usp.history._badgeCanal },
            { text: 'Type', dataIndex: 'type', width: 120, renderer: Usp.history._libelleType },
            { text: 'Regroupement', dataIndex: 'libelle', width: 160 },
            { text: 'Numéro destinataire', dataIndex: 'numero', width: 150 },
            { text: 'Nom', dataIndex: 'nom', width: 140 },
            { text: 'Utilisateur', dataIndex: 'utilisateur', width: 130,
              renderer: function (v) { return v ? Ext.String.htmlEncode(v) : '<span style="color:#bbb">—</span>'; } },
            { text: 'Aperçu / erreur', flex: 1, dataIndex: 'apercu', sortable: false,
              renderer: function (v, m, rec) {
                  if (rec.get('erreur')) {
                      var err = rec.get('erreur');
                      m.tdAttr = 'data-qtip="' + Ext.String.htmlEncode(err).replace(/"/g, '&quot;') + '"';
                      return '<span style="color:#c62828">' + Ext.String.htmlEncode(err) + '</span>';
                  }
                  if (!v) { return ''; }
                  m.tdAttr = 'data-qtip="' + Ext.String.htmlEncode(v).replace(/"/g, '&quot;') + '"';
                  return '<span style="color:#1976d2;cursor:help">' + Ext.String.htmlEncode(v) + '</span>';
              } },
            { text: 'Statut', dataIndex: 'statut', width: 100, renderer: Usp.history._badgeStatut },
            { text: 'Détail', width: 60, align: 'center', sortable: false, menuDisabled: true, dataIndex: 'sourceId',
              renderer: function () {
                  return '<span class="hist-det" data-qtip="Voir le détail complet de cet envoi" ' +
                      'title="Voir le détail complet de cet envoi" style="cursor:pointer;font-size:15px">🔍</span>';
              } }
        ],
        tbar: [
            { xtype: 'combobox', itemId: 'fCanal', fieldLabel: 'Canal', labelWidth: 40, width: 150,
              emptyText: 'Tous', editable: false, queryMode: 'local',
              store: [['', 'Tous'], ['API', 'API officielle'], ['WEB', 'WhatsApp Web']],
              listeners: { select: function (c) { recharger(c.up('grid')); } } },
            { xtype: 'combobox', itemId: 'fType', fieldLabel: 'Type', labelWidth: 40, width: 190,
              emptyText: 'Tous', editable: false, queryMode: 'local',
              store: [['', 'Tous'], ['DISCUSSION', 'Discussion'],
                      ['CAMPAGNE', 'Campagne'], ['ENVOI_MASSE', 'Envoi de masse']],
              listeners: { select: function (c) { recharger(c.up('grid')); } } },
            { xtype: 'datefield', itemId: 'fDateDebut', emptyText: 'Du', width: 110,
              format: 'd/m/Y', editable: false,
              listeners: { select: function (f) { recharger(f.up('grid')); } } },
            { xtype: 'datefield', itemId: 'fDateFin', emptyText: 'Au', width: 110,
              format: 'd/m/Y', editable: false,
              listeners: { select: function (f) { recharger(f.up('grid')); } } },
            { xtype: 'textfield', itemId: 'fRecherche', emptyText: 'Numéro, nom, contenu…', width: 200,
              listeners: { specialkey: function (f, e) {
                  if (e.getKey() === e.ENTER) { recharger(f.up('grid')); } } } },
            { text: 'Filtrer', handler: function (b) { recharger(b.up('grid')); } },
            { text: 'Réinitialiser', handler: function (b) {
                var g = b.up('grid'), tb = g.down('toolbar');
                tb.down('#fCanal').setValue(''); tb.down('#fType').setValue('');
                tb.down('#fRecherche').setValue('');
                tb.down('#fDateDebut').setValue(''); tb.down('#fDateFin').setValue('');
                recharger(g);
            } },
            '->',
            { text: 'Rafraîchir', handler: function (b) { recharger(b.up('grid')); } }
        ],
        listeners: {
            cellclick: function (grid, td, cellIndex, rec, tr, rowIndex, e) {
                if (e.getTarget('.hist-det')) { Usp.history.detail(rec); }
            },
            itemdblclick: function (g, rec) { Usp.history.detail(rec); }
        }
    };
};

/* Détail d'une ligne : ouvre l'envoi de masse parent, sinon affiche le contenu. */
Usp.history.detail = function (rec) {
    if (rec.get('type') === 'ENVOI_MASSE' && rec.get('parentId') && Usp.waweb && Usp.waweb.detailEnvoi) {
        Usp.waweb.detailEnvoi(rec.get('parentId'));
        return;
    }
    var lignes = [
        ['Canal', rec.get('canal') === 'WEB' ? 'WhatsApp Web' : 'API officielle'],
        ['Type', Usp.history._libelleType(rec.get('type'))],
        ['Regroupement', rec.get('libelle')],
        ['Destinataire', (rec.get('nom') || '') + ' — ' + (rec.get('numero') || '')],
        ['Utilisateur', rec.get('utilisateur') || '—'],
        ['Date', String(rec.get('date') || '').replace('T', ' ').substring(0, 16)],
        ['Statut', rec.get('statut')]
    ];
    var html = '<table style="width:100%;border-collapse:collapse">';
    lignes.forEach(function (l) {
        html += '<tr><td style="color:#888;padding:2px 8px 2px 0;vertical-align:top;white-space:nowrap">' +
            Ext.String.htmlEncode(l[0]) + '</td><td style="padding:2px 0">' +
            Ext.String.htmlEncode(l[1] || '—') + '</td></tr>';
    });
    html += '</table>';
    if (rec.get('apercu')) {
        html += '<div style="margin-top:10px;padding:8px;background:#f5f5f5;border-radius:4px;white-space:pre-wrap">' +
            Ext.String.htmlEncode(rec.get('apercu')) + '</div>';
    }
    if (rec.get('erreur')) {
        html += '<div style="margin-top:10px;color:#c62828"><b>Erreur :</b> ' +
            Ext.String.htmlEncode(rec.get('erreur')) + '</div>';
    }
    Ext.create('Ext.window.Window', {
        title: 'Détail de l\'envoi', width: 520, modal: true, autoScroll: true,
        bodyPadding: 14, maxHeight: 460, html: html
    }).show();
};
