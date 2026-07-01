/*
 * UbiSenderPro - Canal WhatsApp Web (non officiel, via service compagnon Baileys).
 * Deux onglets : Comptes (connexion par QR) et Envoi en masse (5 variantes + pièce jointe).
 * Dépend de app.js (objet global Usp).
 */
Ext.define('Usp.waweb', { singleton: true });

/* Catalogue des variables insérables dans un message (token + description). */
Usp.waweb.VARIABLES = [
    { t: 'NOM', d: 'Nom du contact destinataire' },
    { t: 'TELEPHONE', d: 'Téléphone du contact' },
    { t: 'EMAIL', d: 'E-mail du contact' },
    { t: 'SOCIETE_CLIENT', d: 'Société / compte du client' },
    { t: 'SEGMENTATION', d: 'Segmentation du client' },
    { t: 'VILLE', d: 'Ville du client' },
    { t: 'REGION', d: 'Région du client' },
    { t: 'SOCIETE', d: 'Votre société émettrice (Paramètres → Général)' },
    { t: 'TEL_SOCIETE', d: 'Téléphone(s) société (Paramètres → Général)' },
    { t: 'SITE', d: 'Lien du site société (Paramètres → Général)' },
    { t: 'LIEN_COMMANDE', d: 'Lien de commande société (Paramètres → Général)' }
];

/* Barre de boutons « variables » : insère [TOKEN] dans le champ édité (sinon le champ par défaut). */
Usp.waweb.barreVariables = function (fallbackName) {
    return {
        xtype: 'fieldcontainer', fieldLabel: 'Variables', layout: { type: 'hbox', wrap: true },
        defaults: { margin: '0 4 4 0' },
        items: Usp.waweb.VARIABLES.map(function (v) {
            return { xtype: 'button', text: '[' + v.t + ']', tooltip: v.d,
                     handler: function (b) { Usp.waweb.insertVar(v.t, b, fallbackName); } };
        })
    };
};

/* Insère [token] à la position du curseur dans le dernier champ édité (sinon champ par défaut). */
Usp.waweb.insertVar = function (token, btn, fallbackName) {
    var f = Usp.waweb._lastMsgField;
    if (!f || typeof f.isXType !== 'function' ||
            !(f.isXType('textareafield') || f.isXType('textfield'))) {
        f = btn.up('form').down('[name=' + (fallbackName || 'msg1') + ']');
    }
    if (!f) { return; }
    var ins = '[' + token + ']';
    var el = f.inputEl && f.inputEl.dom;
    var val = f.getValue() || '';
    if (el && typeof el.selectionStart === 'number') {
        var s = el.selectionStart, e = el.selectionEnd;
        f.setValue(val.substring(0, s) + ins + val.substring(e));
        var pos = s + ins.length;
        f.focus();
        Ext.defer(function () { try { el.selectionStart = el.selectionEnd = pos; } catch (ex) {} }, 20);
    } else {
        f.setValue(val + ins);
        f.focus();
    }
};

/* ---------- Comptes WhatsApp Web ---------- */
Usp.waweb.sessionStore = function () {
    return Ext.create('Ext.data.Store', {
        fields: ['id', 'libelle', 'numero', 'statut', 'actif'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/wa-web/sessions',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
        autoLoad: true
    });
};

Usp.waweb.sessionsPanel = function () {
    var store = Usp.waweb.sessionStore();
    var ico = function (act, titre, emoji) {
        return '<span class="wa-act" data-act="' + act + '" title="' + titre +
            '" style="cursor:pointer;font-size:17px;margin:0 5px">' + emoji + '</span>';
    };
    return {
        xtype: 'grid', title: 'Comptes WhatsApp Web', store: store,
        columns: [
            { text: 'Libellé', dataIndex: 'libelle', flex: 1 },
            { text: 'Numéro', dataIndex: 'numero', width: 160 },
            { text: 'Statut', dataIndex: 'statut', width: 120, renderer: function (v) {
                var c = v === 'CONNECTE' ? '#2e7d32' : (v === 'QR' ? '#ef6c00' : '#999');
                return '<span style="color:' + c + '">' + (v || '') + '</span>';
            } },
            { text: 'Actions', width: 160, sortable: false, menuDisabled: true, dataIndex: 'id',
              renderer: function () {
                  return ico('connect', 'Connecter (scanner le QR)', '🔗') +
                         ico('warmup', 'Réchauffeur (warming)', '🔥') +
                         ico('logout', 'Déconnecter', '⏏️') +
                         ico('delete', 'Supprimer', '🗑️');
              } }
        ],
        tbar: [
            { text: 'Nouveau compte', cls: 'usp-btn-pri', handler: function () {
                Ext.Msg.prompt('Nouveau compte', 'Libellé :', function (b, val) {
                    if (b === 'ok' && val) {
                        Usp.ajax({ url: '/wa-web/sessions', method: 'POST', jsonData: { libelle: val },
                            success: function () { store.load(); } });
                    }
                });
            } },
            '->',
            { text: 'Rafraîchir', handler: function () { store.load(); } }
        ],
        listeners: {
            cellclick: function (grid, td, cellIndex, rec, tr, rowIndex, e) {
                var el = e.getTarget('.wa-act'); if (!el) { return; }
                var act = el.getAttribute('data-act');
                if (act === 'connect') {
                    Usp.waweb.connect(rec.get('id'), store);
                } else if (act === 'warmup') {
                    Usp.waweb.warmup(rec.get('id'), rec.get('libelle'));
                } else if (act === 'logout') {
                    Usp.ajax({ url: '/wa-web/sessions/' + rec.get('id') + '/logout', method: 'POST',
                        success: function () { store.load(); } });
                } else if (act === 'delete') {
                    Ext.Msg.confirm('Supprimer', 'Supprimer ce compte ?', function (btn) {
                        if (btn === 'yes') {
                            Usp.ajax({ url: '/wa-web/sessions/' + rec.get('id'), method: 'DELETE',
                                success: function () { store.load(); } });
                        }
                    });
                }
            }
        }
    };
};

/* Fenêtre de connexion : démarre la session puis sonde le statut/QR. */
Usp.waweb.connect = function (id, store) {
    var win = Ext.create('Ext.window.Window', {
        title: 'Connexion WhatsApp Web', width: 360, height: 420, modal: true, bodyPadding: 12,
        layout: 'fit',
        items: [{ xtype: 'component', itemId: 'zone',
            html: '<div style="text-align:center;color:#666">Démarrage…</div>' }],
        listeners: { close: function () { win.polling = false; } }
    });
    win.show();
    var zone = win.down('#zone');
    win.polling = true;

    var render = function (etat) {
        if (!win.polling) { return; }
        var st = etat && etat.status;
        if (st === 'CONNECTE') {
            zone.update('<div style="text-align:center;color:#2e7d32;padding-top:40px">' +
                '<div style="font-size:40px">✔</div><b>Connecté</b><br/>' +
                (etat.me && etat.me.id ? Ext.String.htmlEncode(etat.me.id.split(/[:@]/)[0]) : '') + '</div>');
            win.polling = false;
            if (store) { store.load(); }
            return;
        }
        if (st === 'QR' && etat.qr) {
            zone.update('<div style="text-align:center">' +
                '<div style="margin-bottom:8px;color:#666">Scannez avec WhatsApp → Appareils connectés</div>' +
                '<img src="' + etat.qr + '" style="width:280px;height:280px"/></div>');
        } else {
            zone.update('<div style="text-align:center;color:#666;padding-top:40px">' +
                'Statut : ' + (st || 'CONNEXION') + '…<br/>(préparation du QR)</div>');
        }
        Ext.defer(poll, 2000);
    };

    var poll = function () {
        if (!win.polling) { return; }
        Usp.ajax({ url: '/wa-web/sessions/' + id + '/status', method: 'GET',
            success: function (resp) { render(Ext.decode(resp.responseText)); },
            failure: function () { if (win.polling) { Ext.defer(poll, 3000); } } });
    };

    Usp.ajax({ url: '/wa-web/sessions/' + id + '/start', method: 'POST',
        success: function (resp) { render(Ext.decode(resp.responseText)); },
        failure: function () { zone.update('<div style="color:#a00">Service WhatsApp Web injoignable.</div>'); } });
};

/* ---------- Réchauffeur (warming) ---------- */
Usp.waweb.warmup = function (id, libelle) {
    var win = Ext.create('Ext.window.Window', {
        title: 'Réchauffeur — ' + (libelle || ('compte ' + id)), width: 480, modal: true, bodyPadding: 12,
        items: [{ xtype: 'form', border: false, defaults: { anchor: '100%' }, items: [
            { xtype: 'displayfield', value: '<span style="color:#555">Envoie progressivement de petits messages ' +
              'neutres vers vos numéros pour « chauffer » le compte (8h–20h) et réduire le risque de bannissement ' +
              'avant les gros volumes.</span>' },
            { xtype: 'checkbox', name: 'actif', boxLabel: 'Activer le réchauffeur' },
            { xtype: 'textareafield', name: 'numeros', fieldLabel: 'Numéros (pool)', height: 100,
              emptyText: 'Un numéro par ligne (vos propres numéros de préférence)' },
            { xtype: 'fieldcontainer', fieldLabel: 'Volume/jour', layout: 'hbox', defaults: { width: 110, labelWidth: 55 }, items: [
                { xtype: 'numberfield', name: 'parJourBase', fieldLabel: 'Base', value: 10, minValue: 1 },
                { xtype: 'numberfield', name: 'incrementJour', fieldLabel: '+ /j', value: 10, minValue: 0 },
                { xtype: 'numberfield', name: 'parJourMax', fieldLabel: 'Max', value: 60, minValue: 1 }
            ] },
            { xtype: 'displayfield', value: '<span style="color:#888;font-size:11px">Ex. 10 le 1er jour, +10/jour, plafonné à 60.</span>' }
        ] }],
        buttons: [{ text: 'Enregistrer', handler: function (b) {
            var form = b.up('window').down('form').getForm();
            var v = form.getValues();
            v.actif = form.findField('actif').getValue();
            Usp.ajax({ url: '/wa-web/sessions/' + id + '/warmup', method: 'PUT', jsonData: v,
                success: function () { win.close(); Ext.Msg.alert('OK', 'Réchauffeur enregistré.'); },
                failure: function () { Ext.Msg.alert('Erreur', 'Enregistrement impossible.'); } });
        } }]
    });
    win.show();
    Usp.ajax({ url: '/wa-web/sessions/' + id + '/warmup', method: 'GET', success: function (resp) {
        if (!resp.responseText) { return; }
        try { win.down('form').getForm().setValues(Ext.decode(resp.responseText)); } catch (e) {}
    } });
};

/* ---------- Envoi en masse ---------- */
Usp.waweb.bulkPanel = function () {
    var sessionCombo = Ext.create('Ext.data.Store', {
        fields: ['id', 'libelle'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/wa-web/sessions',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
        autoLoad: true
    });
    var medias = []; // pièces jointes multiples : [{url,type,mime,nom}]
    var modeleStore = Ext.create('Ext.data.Store', {
        fields: ['id', 'nom', 'corps', 'enteteMediaUrl', 'enteteMediaType', 'boutonsJson'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/templates',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
        autoLoad: true,
        listeners: { load: function (s) {
            // Option « aucun modèle » pour pouvoir effacer la sélection.
            if (s.findExact('id', '') === -1) { s.insert(0, { id: '', nom: '— Aucun modèle —', corps: '' }); }
        } }
    });

    return {
        xtype: 'form', title: 'Envoi en masse', autoScroll: true, bodyPadding: 12,
        defaults: { anchor: '100%' },
        items: [
            { xtype: 'combobox', name: 'sessionId', fieldLabel: 'Compte WhatsApp Web', allowBlank: false,
              store: sessionCombo, valueField: 'id', displayField: 'libelle', queryMode: 'local', editable: false },
            { xtype: 'textfield', name: 'nom', fieldLabel: 'Nom de l\'envoi' },
            { xtype: 'combobox', name: 'modeleId', fieldLabel: 'Modèle (option)', store: modeleStore,
              valueField: 'id', displayField: 'nom', queryMode: 'local', editable: false,
              emptyText: 'Pré-remplir la variante 1 depuis un modèle…',
              listeners: { select: function (c, recs) {
                  var rec = recs && recs[0]; if (!rec) { return; }
                  var corps = rec.get('corps') || '';
                  if (!corps && !rec.get('enteteMediaUrl')) { return; } // « — Aucun modèle — »
                  // Corps + liens des boutons (le lien vient après le message).
                  c.up('form').down('[name=msg1]').setValue(corps + Usp.waweb.boutonsTexte(rec.get('boutonsJson')));
                  // Pièce jointe du modèle (média d'en-tête) reprise comme pièce jointe de l'envoi.
                  var url = rec.get('enteteMediaUrl');
                  if (url) {
                      medias.length = 0;
                      medias.push({ url: url, type: (rec.get('enteteMediaType') || 'IMAGE').toLowerCase(),
                          mime: null, nom: 'pièce jointe du modèle' });
                  } else {
                      medias.length = 0;
                  }
                  Usp.waweb.majPjList(c.up('form'), medias);
              } } },
            { xtype: 'displayfield', value: '<b>Variantes du message</b> — pour limiter le marquage « spam », ' +
              'une variante est tirée <b>au hasard</b> pour chaque contact. Une seule suffit ; les autres sont ' +
              'facultatives. Cliquez sur une variable ci-dessous pour l\'insérer.' },
            Usp.waweb.barreVariables('msg1'),
            { xtype: 'textareafield', name: 'msg1', fieldLabel: 'Variante 1', height: 60,
              emptyText: 'Bonjour [NOM], bienvenue chez [SOCIETE].',
              listeners: { focus: function (f) { Usp.waweb._lastMsgField = f; } } },
            { xtype: 'textareafield', name: 'msg2', fieldLabel: 'Variante 2 (option)', height: 45,
              listeners: { focus: function (f) { Usp.waweb._lastMsgField = f; } } },
            { xtype: 'textareafield', name: 'msg3', fieldLabel: 'Variante 3 (option)', height: 45,
              listeners: { focus: function (f) { Usp.waweb._lastMsgField = f; } } },
            { xtype: 'textareafield', name: 'msg4', fieldLabel: 'Variante 4 (option)', height: 45,
              listeners: { focus: function (f) { Usp.waweb._lastMsgField = f; } } },
            { xtype: 'textareafield', name: 'msg5', fieldLabel: 'Variante 5 (option)', height: 45,
              listeners: { focus: function (f) { Usp.waweb._lastMsgField = f; } } },
            { xtype: 'fieldcontainer', fieldLabel: 'Pièces jointes', layout: 'hbox', items: [
                { xtype: 'filefield', buttonOnly: true, hideLabel: true, buttonText: 'Ajouter un fichier…',
                  listeners: { change: function (f) { Usp.waweb.uploadPiece(f, medias); } } },
                { xtype: 'button', text: 'Vider', margin: '0 0 0 6', handler: function (b) {
                    medias.length = 0; Usp.waweb.majPjList(b.up('form'), medias); } },
                { xtype: 'component', itemId: 'pjInfo', margin: '4 0 0 8',
                  html: '<span style="color:#888;font-size:11px">optionnel — plusieurs fichiers possibles (image/vidéo/document)</span>' }
            ] },
            { xtype: 'fieldcontainer', fieldLabel: 'Débit', layout: 'hbox', defaults: { width: 120, labelWidth: 70 }, items: [
                { xtype: 'numberfield', name: 'attenteMin', fieldLabel: 'Attente', value: 4, minValue: 0 },
                { xtype: 'numberfield', name: 'attenteMax', fieldLabel: 'à', value: 8, minValue: 0, labelWidth: 20 },
                { xtype: 'displayfield', value: 's entre messages', width: 110 }
            ] },
            { xtype: 'fieldcontainer', fieldLabel: '', layout: 'hbox', defaults: { width: 120, labelWidth: 70 }, items: [
                { xtype: 'numberfield', name: 'pauseApres', fieldLabel: 'Pause après', value: 10, minValue: 0 },
                { xtype: 'numberfield', name: 'pauseMin', fieldLabel: 'msgs:', value: 10, minValue: 0, labelWidth: 40 },
                { xtype: 'numberfield', name: 'pauseMax', fieldLabel: 'à', value: 20, minValue: 0, labelWidth: 20 }
            ] },
            { xtype: 'fieldcontainer', fieldLabel: 'Envoi', layout: 'hbox', items: [
                { xtype: 'radiogroup', columns: 2, width: 230, items: [
                    { boxLabel: 'Immédiat', name: 'envoiMode', inputValue: 'now', checked: true },
                    { boxLabel: 'Planifié', name: 'envoiMode', inputValue: 'plan' }
                ] },
                { xtype: 'datefield', name: 'dateProg', format: 'Y-m-d', submitFormat: 'Y-m-d',
                  emptyText: 'Date', width: 120, margin: '0 0 0 8' },
                { xtype: 'timefield', name: 'heureProg', format: 'H:i', submitFormat: 'H:i',
                  emptyText: 'Heure', width: 90, margin: '0 0 0 6', increment: 15 }
            ] },
            { xtype: 'fieldcontainer', fieldLabel: 'Plage horaire', layout: 'hbox',
              defaults: { width: 80, labelWidth: 24 }, items: [
                { xtype: 'numberfield', name: 'heureDebut', fieldLabel: 'de', minValue: 0, maxValue: 23, value: 0 },
                { xtype: 'numberfield', name: 'heureFin', fieldLabel: 'à', minValue: 0, maxValue: 23, value: 0 },
                { xtype: 'displayfield', value: 'h (0 / 0 = sans restriction)', width: 180 }
            ] },
            { xtype: 'fieldcontainer', fieldLabel: 'Destinataires', layout: 'hbox', items: [
                { xtype: 'button', text: 'Choisir des clients…', handler: function (b) {
                    Usp.waweb.choisirClients(b.up('form')); } },
                { xtype: 'filefield', buttonOnly: true, hideLabel: true, margin: '0 0 0 6',
                  buttonText: 'Importer un fichier (.csv/.xlsx)…',
                  listeners: { change: function (f) { Usp.waweb.importerFichierBulk(f); } } },
                { xtype: 'component', itemId: 'destCount', margin: '5 0 0 10',
                  html: '<span style="color:#888;font-size:11px">ou saisie manuelle ci-dessous</span>' }
            ] },
            { xtype: 'displayfield',
              value: '<span style="color:#888;font-size:11px">Fichier ponctuel (non enregistré), structure ' +
                     '<b>numero;nomclient</b> — ex. <code>2250707075510;konate mariam</code>. ' +
                     'Les numéros sont vérifiés ; les lignes non conformes sont proposées à la correction.</span>' },
            { xtype: 'textareafield', name: 'destinatairesTexte', height: 110,
              emptyText: 'Une ligne par contact : numero;nom\nEx. 2250700000000;Awa' }
        ],
        bbar: ['->',
            { text: 'Créer / lancer' + Usp.permBadge('waweb', 'ENVOI_MASSE'), formBind: true, handler: function (b) {
                if (!Usp.can('waweb', 'ENVOI_MASSE')) { Usp.refusPermission(); return; }
                var formPanel = b.up('form');
                var f = formPanel.getForm();
                if (!f.isValid()) { return; }
                var v = f.getValues();
                v.mediasJson = medias.length ? Ext.encode(medias) : null;
                var planifie = v.envoiMode === 'plan';
                v.planifier = planifie;
                if (planifie) {
                    if (!v.dateProg) { Ext.Msg.alert('Info', 'Choisissez une date de programmation.'); return; }
                    v.dateProgrammee = v.dateProg + ' ' + (v.heureProg || '09:00');
                }
                formPanel.setLoading('Traitement en cours…');
                var fini = function (msgTitre, msgTexte) {
                    formPanel.setLoading(false);
                    if (Usp.waweb._jobStore) { Usp.waweb._jobStore.load(); }
                    Ext.Msg.show({
                        title: msgTitre,
                        msg: msgTexte + ' &nbsp; Réinitialiser la vue ?',
                        width: 560, minWidth: 560,
                        buttons: Ext.Msg.YESNO, icon: Ext.Msg.QUESTION,
                        fn: function (btn) {
                            if (btn === 'yes') {
                                f.reset();
                                medias.length = 0;
                                Usp.waweb.majPjList(formPanel, medias);
                            }
                        }
                    });
                };
                var echec = function (r, def) { formPanel.setLoading(false); Ext.Msg.alert('Erreur', Usp.waweb.err(r, def)); };
                Usp.ajax({ url: '/wa-bulk', method: 'POST', jsonData: v,
                    success: function (resp) {
                        var job = Ext.decode(resp.responseText);
                        if (planifie) {
                            fini('Planifié', 'Envoi planifié (' + job.total + ' destinataires) pour le ' + v.dateProgrammee + '.');
                        } else {
                            Usp.ajax({ url: '/wa-bulk/' + job.id + '/launch', method: 'POST',
                                success: function () { fini('Lancé', 'Envoi démarré (' + job.total + ' destinataires).'); },
                                failure: function (r) { echec(r, 'Lancement impossible.'); } });
                        }
                    },
                    failure: function (r) { echec(r, 'Création impossible.'); } });
            } }
        ]
    };
};

/* ---------- Historique des envois ---------- */
Usp.waweb.historyPanel = function () {
    var store = Ext.create('Ext.data.Store', {
        fields: ['id', 'nom', 'statut', 'total', 'envoyes', 'echoues', 'derniereErreur', 'createdAt'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/wa-bulk',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
        autoLoad: true
    });
    Usp.waweb._jobStore = store;
    var dateFiltre = { d: null, f: null };
    var appliquerFiltre = function () {
        store.clearFilter(true);
        if (dateFiltre.d || dateFiltre.f) {
            store.filterBy(function (rec) {
                var c = rec.get('createdAt');
                if (!c) { return false; }
                var dt = new Date(String(c).substring(0, 10) + 'T00:00:00');
                if (dateFiltre.d && dt < dateFiltre.d) { return false; }
                if (dateFiltre.f && dt > dateFiltre.f) { return false; }
                return true;
            });
        }
    };
    return {
        xtype: 'grid', title: 'Historique des envois Web', store: store,
        columns: [
            { text: '#', dataIndex: 'id', width: 50 },
            { text: 'Date', dataIndex: 'createdAt', width: 120, renderer: function (v) {
                return v ? Ext.String.htmlEncode(String(v).replace('T', ' ').substring(0, 16)) : ''; } },
            { text: 'Nom', dataIndex: 'nom', flex: 1 },
            { text: 'Statut', dataIndex: 'statut', width: 110 },
            { text: 'Total', dataIndex: 'total', width: 70 },
            { text: 'Envoyés', dataIndex: 'envoyes', width: 90, renderer: function (v) {
                return '<span style="color:#2e7d32;font-weight:bold">' + (v || 0) + '</span>'; } },
            { text: 'Échoués', dataIndex: 'echoues', width: 90, renderer: function (v) {
                return v ? '<span style="color:#c62828;font-weight:bold">' + v + '</span>' : '0'; } },
            { text: 'Motif d\'échec', dataIndex: 'derniereErreur', width: 200, renderer: function (v, m) {
                if (!v) { return ''; }
                m.tdAttr = 'data-qtip="' + Ext.String.htmlEncode(v).replace(/"/g, '&quot;') + '"';
                return '<span style="color:#c62828">' + Ext.String.htmlEncode(v) + '</span>'; } },
            { text: 'Détail', width: 70, align: 'center', sortable: false, menuDisabled: true, dataIndex: 'id',
              renderer: function () {
                  return '<span class="wa-det" data-qtip="Voir le détail (contenu + statuts par destinataire)" ' +
                      'title="Voir le détail (contenu + statuts)" style="cursor:pointer;font-size:16px">🔍</span>';
              } },
            { text: 'Renvoi échecs', width: 110, sortable: false, menuDisabled: true, dataIndex: 'echoues',
              renderer: function (v) {
                  return v > 0 ? '<span class="wa-relancer" title="Renvoyer uniquement les échecs" ' +
                      'style="cursor:pointer;color:#c62828">🔄 ' + v + '</span>' : '';
              } }
        ],
        tbar: [
            { xtype: 'datefield', itemId: 'hDateDebut', emptyText: 'Du', width: 110, format: 'd/m/Y',
              editable: false, listeners: { select: function (f) {
                  dateFiltre.d = Ext.Date.clearTime(f.getValue()); appliquerFiltre(); } } },
            { xtype: 'datefield', itemId: 'hDateFin', emptyText: 'Au', width: 110, format: 'd/m/Y',
              editable: false, listeners: { select: function (f) {
                  var d = f.getValue(); d.setHours(23, 59, 59, 999); dateFiltre.f = d; appliquerFiltre(); } } },
            { text: 'Réinitialiser', handler: function (b) {
                var tb = b.up('toolbar');
                tb.down('#hDateDebut').setValue(''); tb.down('#hDateFin').setValue('');
                dateFiltre.d = null; dateFiltre.f = null; appliquerFiltre();
            } },
            '->',
            { text: 'Rafraîchir', handler: function () { store.load(); } }
        ],
        listeners: {
            cellclick: function (grid, td, cellIndex, rec, tr, rowIndex, e) {
                if (e.getTarget('.wa-relancer')) {
                    if (!Usp.can('waweb', 'RENVOI_ECHECS')) { Usp.refusPermission(); return; }
                    Usp.waweb.relancerEchecs(rec.get('id'), store); return;
                }
                if (e.getTarget('.wa-det')) { Usp.waweb.detailEnvoi(rec.get('id')); }
            },
            itemdblclick: function (g, rec) { Usp.waweb.detailEnvoi(rec.get('id')); },
            afterrender: function () {
                Usp.waweb._histTask = Ext.TaskManager.start({ interval: 8000, run: function () { store.load(); } });
            },
            beforedestroy: function () {
                if (Usp.waweb._histTask) { try { Ext.TaskManager.stop(Usp.waweb._histTask); } catch (e) {} Usp.waweb._histTask = null; }
            }
        }
    };
};

/* Met à jour l'affichage de la liste des pièces jointes de l'envoi de masse. */
Usp.waweb.majPjList = function (formPanel, medias) {
    var info = formPanel.down('#pjInfo');
    if (!info) { return; }
    if (!medias || !medias.length) {
        info.update('<span style="color:#888;font-size:11px">optionnel — plusieurs fichiers possibles (image/vidéo/document)</span>');
        return;
    }
    var noms = medias.map(function (m) { return Ext.String.htmlEncode(m.nom || m.type || 'fichier'); });
    info.update('<span style="color:#2e7d32;font-size:11px">✔ ' + medias.length + ' pièce(s) jointe(s) : ' +
        noms.join(', ') + '</span>');
};

Usp.waweb.uploadPiece = function (f, medias) {
    var file = f.fileInputEl.dom.files[0];
    if (!file) { return; }
    var formPanel = f.up('form');
    var reader = new FileReader();
    reader.onload = function (e) {
        var b64 = e.target.result.split(',')[1];
        var mime = file.type || 'application/octet-stream';
        Usp.ajax({ url: '/media/upload', method: 'POST',
            jsonData: { fichierBase64: b64, mimeType: mime, nomFichier: file.name },
            success: function (resp) {
                var r = Ext.decode(resp.responseText);
                medias.push({ url: r.url, mime: mime, nom: file.name,
                    type: mime.indexOf('image/') === 0 ? 'image'
                        : mime.indexOf('video/') === 0 ? 'video'
                        : mime.indexOf('audio/') === 0 ? 'audio' : 'document' });
                Usp.waweb.majPjList(formPanel, medias);
            },
            failure: function () { Ext.Msg.alert('Erreur', 'Téléversement de la pièce jointe impossible.'); } });
    };
    reader.readAsDataURL(file);
};

/* Convertit des boutons JSON ([{type:'URL',text,url}]) en lignes de lien à ajouter après le message. */
Usp.waweb.boutonsTexte = function (json) {
    if (!json) { return ''; }
    var arr;
    try { arr = Ext.decode(json); } catch (e) { return ''; }
    if (!Ext.isArray(arr)) { return ''; }
    var lignes = [];
    arr.forEach(function (b) {
        if (b && b.url) { lignes.push((b.text ? b.text + ' : ' : '') + b.url); }
    });
    return lignes.length ? '\n\n' + lignes.join('\n') : '';
};

/* Renvoie uniquement les destinataires en échec d'un envoi (sans retoucher les réussis). */
Usp.waweb.relancerEchecs = function (jobId, store) {
    Ext.Msg.confirm('Renvoyer les échecs', 'Renvoyer uniquement les destinataires en échec de cet envoi ?', function (btn) {
        if (btn !== 'yes') { return; }
        Usp.ajax({ url: '/wa-bulk/' + jobId + '/relancer', method: 'POST',
            success: function () { Ext.Msg.alert('OK', 'Renvoi des échecs lancé.'); if (store) { store.load(); } },
            failure: function (r) { Ext.Msg.alert('Erreur', Usp.waweb.err(r, 'Renvoi impossible.')); } });
    });
};

Usp.waweb.err = function (resp, def) {
    try { var r = Ext.decode(resp.responseText); if (r && r.erreur) { return r.erreur; } } catch (e) {}
    return def;
};

/* Combo des sessions (réutilisé par filtre/extraction). */
Usp.waweb.sessionComboStore = function () {
    return Ext.create('Ext.data.Store', {
        fields: ['id', 'libelle'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/wa-web/sessions',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
        autoLoad: true
    });
};

/* Fenêtre d'export : numéros (et nom) prêts à copier / coller dans l'envoi en masse. */
Usp.waweb.exportNumbers = function (lignes) {
    Ext.create('Ext.window.Window', {
        title: 'Numéros (' + lignes.length + ') — copier', width: 420, height: 460, modal: true, layout: 'fit',
        bodyPadding: 6,
        items: [{ xtype: 'textareafield', value: lignes.join('\n'), selectOnFocus: true }]
    }).show();
};

/* ---------- Phase 3 : Filtre de numéros ---------- */
Usp.waweb.filterPanel = function () {
    var resStore = Ext.create('Ext.data.Store', { fields: ['numero', 'format', 'exists'] });
    return {
        xtype: 'panel', title: '🔢 Vérification de numéros', layout: 'border',
        items: [
            { region: 'west', width: 340, xtype: 'form', bodyPadding: 10, border: false,
              defaults: { anchor: '100%' },
              items: [
                { xtype: 'combobox', name: 'sessionId', itemId: 'fSession', fieldLabel: 'Compte', allowBlank: false,
                  store: Usp.waweb.sessionComboStore(), valueField: 'id', displayField: 'libelle',
                  queryMode: 'local', editable: false },
                { xtype: 'fieldcontainer', fieldLabel: 'Importer', layout: 'hbox', items: [
                    { xtype: 'filefield', buttonOnly: true, hideLabel: true, buttonText: 'Charger un fichier (.csv/.xlsx)…',
                      listeners: { change: function (f) { Usp.waweb.chargerNumerosFichier(f); } } },
                    { xtype: 'button', text: '📄 Exemplaire', margin: '0 0 0 8',
                      tooltip: 'Télécharger un modèle CSV (un numéro par ligne)',
                      handler: function () { Usp.telechargerCsv('modele_numeros.csv', 'numero\n2250700000000\n2250500000000\n'); } }
                  ] },
                { xtype: 'textareafield', name: 'numeros', fieldLabel: 'Numéros', height: 300,
                  emptyText: 'Un numéro par ligne (format international)\nou chargez un fichier ci-dessus' }
              ],
              bbar: ['->', { text: 'Vérifier', handler: function (b) {
                  var p = b.up('panel'); var f = p.down('form').getForm();
                  var sid = p.down('#fSession').getValue();
                  if (!sid) { Ext.Msg.alert('Info', 'Choisissez un compte.'); return; }
                  var nums = (f.findField('numeros').getValue() || '').split(/\r?\n/)
                      .map(function (s) { return s.replace(/[^0-9]/g, ''); })
                      .filter(function (s) { return s.length >= 6; });
                  if (!nums.length) { Ext.Msg.alert('Info', 'Saisissez des numéros.'); return; }
                  // Format vérifié côté client (mêmes règles que l'envoi).
                  resStore.loadData(nums.map(function (n) {
                      return { numero: n, format: !Usp.waweb._motifNumero(n), exists: null };
                  }));
                  b.disable();
                  Usp.ajax({ url: '/wa-web/sessions/' + sid + '/check-numbers', method: 'POST',
                      jsonData: { numeros: nums },
                      success: function (resp) {
                          b.enable();
                          var r = Ext.decode(resp.responseText);
                          var presence = {};
                          (r.results || []).forEach(function (x) { presence[String(x.number).replace(/[^0-9]/g, '')] = !!x.exists; });
                          resStore.each(function (rec) {
                              var k = String(rec.get('numero')).replace(/[^0-9]/g, '');
                              rec.set('exists', presence.hasOwnProperty(k) ? presence[k] : false);
                          });
                      },
                      failure: function () { b.enable(); Ext.Msg.alert('Erreur', 'Service WhatsApp Web injoignable ou compte non connecté.'); } });
              } }]
            },
            { region: 'center', xtype: 'grid', store: resStore,
              columns: [
                { text: 'Numéro', dataIndex: 'numero', flex: 1 },
                { text: 'Format', dataIndex: 'format', width: 110, renderer: function (v) {
                    return v ? '<span style="color:#2e7d32">✔ OK</span>' : '<span style="color:#c62828">✖ Non conforme</span>';
                } },
                { text: 'WhatsApp', dataIndex: 'exists', width: 130, renderer: function (v) {
                    if (v === null || v === undefined) { return '<span style="color:#999">—</span>'; }
                    return v ? '<span style="color:#2e7d32">✔ Oui</span>' : '<span style="color:#c62828">✖ Non</span>';
                } }
              ],
              tbar: [
                { text: 'Copier les valides', handler: function () {
                    var out = [];
                    resStore.each(function (r) { if (r.get('exists')) { out.push(r.get('numero')); } });
                    if (!out.length) { Ext.Msg.alert('Info', 'Aucun numéro présent sur WhatsApp.'); return; }
                    Usp.waweb.exportNumbers(out);
                } },
                { text: 'Exporter (CSV)', handler: function () {
                    if (!resStore.getCount()) { Ext.Msg.alert('Info', 'Rien à exporter.'); return; }
                    var lignes = ['numero;format;whatsapp'];
                    resStore.each(function (r) {
                        lignes.push(r.get('numero') + ';' + (r.get('format') ? 'OK' : 'NON') + ';' +
                            (r.get('exists') === null ? '' : (r.get('exists') ? 'OUI' : 'NON')));
                    });
                    var uri = 'data:text/csv;charset=utf-8,' + encodeURIComponent(lignes.join('\n'));
                    var a = document.createElement('a'); a.href = uri; a.download = 'verification_numeros.csv';
                    document.body.appendChild(a); a.click(); document.body.removeChild(a);
                } }
              ]
            }
        ]
    };
};

/* Charge des numéros depuis un fichier CSV/Excel (réutilise l'analyse de l'envoi de masse). */
Usp.waweb.chargerNumerosFichier = function (f) {
    var file = f.fileInputEl.dom.files[0];
    if (!file) { return; }
    var ta = f.up('form').down('[name=numeros]');
    var reader = new FileReader();
    reader.onload = function (e) {
        var b64 = e.target.result.split(',')[1];
        Usp.ajax({ url: '/wa-bulk/preparer-fichier', method: 'POST',
            jsonData: { fichierBase64: b64, nomFichier: file.name },
            success: function (resp) {
                var r = Ext.decode(resp.responseText) || {};
                var nums = (r.valides || []).map(function (v) { return v.numero; })
                    .concat((r.invalides || []).map(function (v) { return v.numero; }));
                ta.setValue(nums.join('\n'));
                Ext.Msg.alert('Fichier chargé', nums.length + ' numéro(s) importé(s). Cliquez sur « Vérifier ».');
            },
            failure: function (resp) { Ext.Msg.alert('Erreur', Usp.waweb.err(resp, 'Lecture du fichier impossible.')); } });
    };
    reader.readAsDataURL(file);
};

/* ---------- Phase 4 : Extraction contacts / groupes ---------- */
Usp.waweb.extractPanel = function () {
    var groupStore = Ext.create('Ext.data.Store', { fields: ['jid', 'nom', 'taille'] });
    var resStore = Ext.create('Ext.data.Store', { fields: ['numero', 'info'] });
    var sessionCombo = { xtype: 'combobox', itemId: 'xSession', fieldLabel: 'Compte', labelWidth: 60, width: 280,
        store: Usp.waweb.sessionComboStore(), valueField: 'id', displayField: 'libelle',
        queryMode: 'local', editable: false };

    var sid = function (c) { var v = c.up('panel').down('#xSession').getValue();
        if (!v) { Ext.Msg.alert('Info', 'Choisissez un compte.'); } return v; };

    return {
        xtype: 'panel', title: 'Extraction', layout: 'border',
        tbar: [ sessionCombo,
            { text: 'Mes contacts', handler: function (b) {
                var id = sid(b); if (!id) { return; } b.disable();
                Usp.ajax({ url: '/wa-web/sessions/' + id + '/contacts', method: 'GET',
                    success: function (resp) { b.enable();
                        var r = Ext.decode(resp.responseText);
                        resStore.loadData((r.contacts || []).map(function (c) {
                            return { numero: c.numero, info: c.nom || '' }; }));
                    },
                    failure: function () { b.enable(); Ext.Msg.alert('Erreur', 'Extraction impossible (compte connecté ?).'); } });
            } },
            { text: 'Mes groupes', handler: function (b) {
                var id = sid(b); if (!id) { return; } b.disable();
                Usp.ajax({ url: '/wa-web/sessions/' + id + '/groups', method: 'GET',
                    success: function (resp) { b.enable();
                        var r = Ext.decode(resp.responseText);
                        groupStore.loadData((r.groups || []).map(function (g) {
                            return { jid: g.jid, nom: g.nom || g.jid, taille: g.taille }; }));
                    },
                    failure: function () { b.enable(); Ext.Msg.alert('Erreur', 'Extraction impossible (compte connecté ?).'); } });
            } }
        ],
        items: [
            { region: 'west', width: 320, xtype: 'grid', title: 'Groupes', store: groupStore,
              columns: [
                { text: 'Groupe', dataIndex: 'nom', flex: 1 },
                { text: 'Membres', dataIndex: 'taille', width: 80 }
              ],
              listeners: { itemclick: function (g, rec) {
                  var id = g.up('panel').down('#xSession').getValue(); if (!id) { return; }
                  Usp.ajax({ url: '/wa-web/sessions/' + id + '/participants?jid=' + encodeURIComponent(rec.get('jid')), method: 'GET',
                      success: function (resp) {
                          var r = Ext.decode(resp.responseText);
                          resStore.loadData((r.participants || []).map(function (p) {
                              return { numero: p.numero, info: p.admin || '' }; }));
                      },
                      failure: function () { Ext.Msg.alert('Erreur', 'Membres indisponibles.'); } });
              } }
            },
            { region: 'center', xtype: 'grid', title: 'Numéros', store: resStore,
              columns: [
                { text: 'Numéro', dataIndex: 'numero', flex: 1 },
                { text: 'Nom / rôle', dataIndex: 'info', width: 200 }
              ],
              tbar: [{ text: 'Exporter (copier)', handler: function () {
                  var out = [];
                  resStore.each(function (r) { out.push(r.get('numero') + (r.get('info') ? ';' + r.get('info') : '')); });
                  if (!out.length) { Ext.Msg.alert('Info', 'Rien à exporter.'); return; }
                  Usp.waweb.exportNumbers(out);
              } }]
            }
        ]
    };
};

/* Sélecteur de clients (recherche + filtre segmentation + cocher) -> remplit les destinataires. */
Usp.waweb.choisirClients = function (formPanel) {
    // Utilise le SCL unifié (tous les clients + filtres agence/région/segmentation + recherche live).
    Usp.clientPicker({
        title: 'Choisir des clients', boutonValider: 'Ajouter aux destinataires', avecListe: true,
        onValider: function (rows) {
            var lignes = [], sansNumero = 0;
            rows.forEach(function (r) {
                if (r.numero) { lignes.push(r.numero + (r.nom ? ';' + r.nom : '')); } else { sansNumero++; }
            });
            if (lignes.length) {
                var champ = formPanel.down('[name=destinatairesTexte]');
                var actuel = champ.getValue();
                champ.setValue((actuel ? actuel.replace(/\s*$/, '') + '\n' : '') + lignes.join('\n'));
            }
            Usp.toast(lignes.length + ' destinataire(s) ajouté(s)'
                + (sansNumero ? ' — ' + sansNumero + ' sans numéro WhatsApp ignoré(s).' : '.'));
        }
    });
};

/* Détail d'un envoi : contenu + statut par destinataire (réussi/échoué). */
/* Ajoute des lignes « numero;nom » à la zone des destinataires de l'envoi de masse. */
Usp.waweb._ajouterDestinataires = function (lignes) {
    var ta = Ext.ComponentQuery.query('textareafield[name=destinatairesTexte]')[0];
    if (!ta) { return; }
    var courant = (ta.getValue() || '').replace(/\s+$/, '');
    var ajout = lignes.join('\n');
    ta.setValue(courant ? courant + '\n' + ajout : ajout);
    var dc = Ext.ComponentQuery.query('#destCount')[0];
    if (dc) {
        dc.update('<span style="color:#2e7d32;font-size:11px">✔ ' + lignes.length +
            ' destinataire(s) ajouté(s)</span>');
    }
};

/* Vérifie côté client un numéro (mêmes règles que le serveur). @return motif ou null si conforme. */
Usp.waweb._motifNumero = function (numero) {
    var brut = String(numero || '').replace(/[^0-9]/g, '');
    if (!brut) { return 'Numéro manquant'; }
    var norm = Usp.normNumero(numero);
    if (norm.length < 8) { return 'Numéro trop court'; }
    if (norm.length > 15) { return 'Numéro trop long'; }
    return null;
};

/* Importe un fichier CSV/Excel ponctuel : ajoute les conformes, réconcilie les autres. */
Usp.waweb.importerFichierBulk = function (f) {
    var file = f.fileInputEl.dom.files[0];
    if (!file) { return; }
    var reader = new FileReader();
    reader.onload = function (e) {
        var b64 = e.target.result.split(',')[1];
        Usp.ajax({
            url: '/wa-bulk/preparer-fichier', method: 'POST',
            jsonData: { fichierBase64: b64, nomFichier: file.name },
            success: function (resp) {
                var r = Ext.decode(resp.responseText) || {};
                var valides = r.valides || [], invalides = r.invalides || [];
                if (valides.length) {
                    Usp.waweb._ajouterDestinataires(valides.map(function (v) {
                        return v.numero + ';' + (v.nom || ''); }));
                }
                if (invalides.length) {
                    Usp.waweb.reconciliation(invalides);
                } else {
                    Ext.Msg.alert('Import', valides.length + ' destinataire(s) conforme(s) ajouté(s). ' +
                        'Aucune ligne à corriger.');
                }
            },
            failure: function (resp) {
                Ext.Msg.alert('Erreur', Usp.waweb.err(resp, 'Lecture du fichier impossible.'));
            }
        });
    };
    reader.readAsDataURL(file);
};

/* Vue de réconciliation : corriger les lignes non conformes puis réintégrer celles qui le deviennent. */
Usp.waweb.reconciliation = function (invalides) {
    var store = Ext.create('Ext.data.Store', {
        fields: ['numero', 'nom', 'raison'],
        data: invalides
    });
    var win = Ext.create('Ext.window.Window', {
        title: 'Destinataires non reconnus (' + invalides.length + ')',
        width: 620, height: 420, modal: true, layout: 'fit', bodyPadding: 0,
        items: [{
            xtype: 'grid', store: store,
            plugins: [Ext.create('Ext.grid.plugin.CellEditing', { clicksToEdit: 1 })],
            tbar: [{ xtype: 'tbtext', text: 'Corrigez les numéros (double-clic), puis réintégrez les conformes :' }],
            columns: [
                { text: 'Numéro', dataIndex: 'numero', width: 180, editor: { xtype: 'textfield' } },
                { text: 'Nom', dataIndex: 'nom', flex: 1, editor: { xtype: 'textfield' } },
                { text: 'Motif', dataIndex: 'raison', width: 160, renderer: function (v) {
                    return '<span style="color:#c62828">' + Ext.String.htmlEncode(v || '') + '</span>'; } }
            ]
        }],
        buttons: [
            { text: 'Exporter (CSV)', handler: function () {
                var lignes = ['numero;nom;motif'];
                store.each(function (rec) {
                    lignes.push((rec.get('numero') || '') + ';' + (rec.get('nom') || '') + ';' + (rec.get('raison') || ''));
                });
                var uri = 'data:text/csv;charset=utf-8,' + encodeURIComponent(lignes.join('\n'));
                var a = document.createElement('a');
                a.href = uri; a.download = 'destinataires_non_reconnus.csv';
                document.body.appendChild(a); a.click(); document.body.removeChild(a);
            } },
            '->',
            { text: 'Réintégrer les conformes', handler: function () {
                var ok = [], aRetirer = [];
                store.each(function (rec) {
                    var motif = Usp.waweb._motifNumero(rec.get('numero'));
                    if (motif) { rec.set('raison', motif); }
                    else { ok.push(Usp.normNumero(rec.get('numero')) + ';' + (rec.get('nom') || '')); aRetirer.push(rec); }
                });
                if (ok.length) { Usp.waweb._ajouterDestinataires(ok); }
                Ext.Array.each(aRetirer, function (rec) { store.remove(rec); });
                if (store.getCount() === 0) {
                    Ext.Msg.alert('Réconciliation', ok.length + ' destinataire(s) réintégré(s). Tout est conforme.');
                    win.close();
                } else {
                    Ext.Msg.alert('Réconciliation', ok.length + ' réintégré(s). ' +
                        store.getCount() + ' ligne(s) restent non conformes.');
                }
            } },
            { text: 'Fermer', handler: function () { win.close(); } }
        ]
    });
    win.show();
};

Usp.waweb.detailEnvoi = function (jobId) {
    var dStore = Ext.create('Ext.data.Store', {
        fields: ['numero', 'nom', 'statut', 'erreur'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/wa-bulk/' + jobId + '/destinataires',
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
        autoLoad: true
    });
    Usp.ajax({ url: '/wa-bulk/' + jobId, method: 'GET', success: function (resp) {
        var j = Ext.decode(resp.responseText) || {};
        var variantes = [j.msg1, j.msg2, j.msg3, j.msg4, j.msg5].filter(function (m) { return m && m.trim(); });
        var pj = '';
        var lstMedias = [];
        if (j.mediasJson) { try { lstMedias = Ext.decode(j.mediasJson) || []; } catch (e) {} }
        if (lstMedias.length) {
            pj = '<div>📎 ' + lstMedias.length + ' pièce(s) jointe(s) : ' +
                lstMedias.map(function (m) { return Ext.String.htmlEncode(m.nom || m.type || ''); }).join(', ') + '</div>';
        } else if (j.mediaUrl) {
            pj = '<div>📎 Pièce jointe : ' + Ext.String.htmlEncode(j.mediaNom || j.mediaType || '') + '</div>';
        }
        var contenu = '<b>' + Ext.String.htmlEncode(j.nom || ('Envoi #' + jobId)) + '</b> — statut : ' + (j.statut || '') +
            '<br/>Total : ' + j.total + ' · <span style="color:#2e7d32">Envoyés : ' + j.envoyes + '</span> · ' +
            '<span style="color:#c62828">Échoués : ' + j.echoues + '</span><hr/>' +
            pj +
            '<div style="color:#555"><b>Variantes :</b></div>' +
            variantes.map(function (m) { return '<div style="margin:2px 0;padding:4px;background:#f4f4f4;border-radius:4px">' +
                Ext.String.htmlEncode(m) + '</div>'; }).join('');
        Ext.create('Ext.window.Window', {
            title: 'Détail de l\'envoi #' + jobId, width: 680, height: 520, modal: true, layout: 'border',
            items: [
                { region: 'north', xtype: 'panel', bodyPadding: 10, autoScroll: true, height: 180, html: contenu },
                { region: 'center', xtype: 'grid', store: dStore, title: 'Destinataires',
                  columns: [
                    { text: 'Numéro', dataIndex: 'numero', width: 150 },
                    { text: 'Nom', dataIndex: 'nom', flex: 1 },
                    { text: 'Statut', dataIndex: 'statut', width: 100, renderer: function (v) {
                        var c = v === 'ENVOYE' ? '#2e7d32' : (v === 'ECHEC' ? '#c62828' : '#999');
                        return '<span style="color:' + c + '">' + (v || '') + '</span>'; } },
                    { text: 'Erreur', dataIndex: 'erreur', flex: 1, renderer: function (v) {
                        return v ? '<span style="color:#c62828">' + Ext.String.htmlEncode(v) + '</span>' : ''; } }
                  ],
                  tbar: [{ text: 'Rafraîchir', handler: function () { dStore.load(); } }]
                }
            ]
        }).show();
    } });
};

Usp.waweb.tabs = function () {
    return {
        xtype: 'tabpanel', title: 'WhatsApp Web', listeners: Usp.tabListeners,
        // « Vérification de numéros » (filterPanel) déplacé vers Comptes clients (#4).
        items: [Usp.waweb.sessionsPanel(), Usp.waweb.bulkPanel(), Usp.waweb.historyPanel(),
                Usp.waweb.extractPanel()]
    };
};
