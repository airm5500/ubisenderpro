/*
 * UbiSenderPro - Assistant d'import générique (section 25 de la spec).
 * Détection des colonnes, mapping configurable, modèles de mapping sauvegardés,
 * modes d'import, simulation, rapport et téléchargement des lignes rejetées.
 * Dépend de app.js (objet Usp).
 */
Ext.define('Usp.importer', { singleton: true });

Usp.importer.CHAMPS = {
    CLIENTS: [
        ['numero_client', 'Code client *'], ['nom_compte', 'Nom client *'],
        ['entreprise', 'Entreprise'],
        ['contact_principal', 'Contact principal'], ['telephone_principal', 'Téléphone principal'],
        ['telephone_2', 'Téléphone 2'], ['numero_whatsapp', 'Numéro WhatsApp'],
        ['fonction', 'Fonction'], ['agence', 'Agence'], ['region', 'Région'],
        ['email_principal', 'E-mail'], ['segmentation', 'Segmentation'],
        ['adresse', 'Adresse'], ['ville', 'Ville'], ['commune', 'Commune'],
        ['pays', 'Pays'], ['statut', 'Statut'], ['notes', 'Notes'],
        ['consentement_whatsapp', 'Consentement WhatsApp']
    ],
    ARTICLES: [
        ['pscode', 'PS Code *'], ['designation', 'Désignation *'],
        ['prix_vente', 'Prix de vente'], ['prix_vente_public', 'Prix de vente public'],
        ['prix_promotionnel', 'Prix promotionnel'],
        ['quantite_commandee', 'Quantité commandée'], ['quantite_ug', 'Quantité UG'],
        ['nom_promo', 'Nom promo'], ['code_promo', 'Code promo'],
        ['promo_annee', 'Année promo'], ['promo_mois_debut', 'Mois début promo'],
        ['promo_jour_debut', 'Jour début promo'], ['promo_mois_fin', 'Mois fin promo'],
        ['promo_jour_fin', 'Jour fin promo'],
        ['code_barres', 'Code-barres'], ['cip', 'CIP'],
        ['categorie', 'Catégorie'], ['marque', 'Marque'],
        ['stock_disponible', 'Stock disponible'], ['unite', 'Unité'],
        ['image_url', 'Image URL'], ['lien_produit', 'Lien produit']
    ],
    REFERENTIEL: [
        ['code', 'Code'], ['libelle', 'Libellé *']
    ],
    REC_FICHES: [
        ['numero_client', 'Code client *'], ['encours_initial', 'Encours initial'],
        ['segment', 'Segment'], ['profil', 'Profil de paiement'],
        ['responsable', 'Responsable'], ['statut', 'Statut']
    ],
    REC_CREANCES: [
        ['numero_client', 'Code client *'], ['type', 'Type (FACTURE/AVOIR)'],
        ['numero', 'N° pièce'], ['date_emission', 'Date émission'],
        ['date_echeance', 'Date échéance'], ['montant', 'Montant *'], ['statut', 'Statut']
    ],
    REC_PAIEMENTS: [
        ['numero_client', 'Code client *'], ['date_paiement', 'Date de règlement'],
        ['montant', 'Montant *'], ['mode', 'Mode'], ['reference', 'Référence']
    ]
};

Usp.importer.show = function (type, url, onDone) {
    var champs = Usp.importer.CHAMPS[type] || [];
    var colStore = Ext.create('Ext.data.Store', { fields: ['col'], data: [] });
    var fileData = { base64: null, nom: null };

    // Combos de mapping : un par champ logique.
    var mappingItems = champs.map(function (c) {
        return {
            xtype: 'combobox', name: 'map_' + c[0], fieldLabel: c[1],
            store: colStore, valueField: 'col', displayField: 'col',
            queryMode: 'local', editable: true, forceSelection: false, anchor: '100%'
        };
    });

    // Modèles de mapping sauvegardés.
    var mappingStore = Ext.create('Ext.data.Store', {
        fields: ['id', 'nom', 'mappingJson', 'separateur'],
        proxy: { type: 'ajax', url: Usp.apiBase + '/imports/mappings?type=' + type,
            headers: { 'Authorization': 'Bearer ' + (Usp.token || '') }, reader: { type: 'json' } },
        autoLoad: true
    });

    var detecter = function (win) {
        var sep = win.down('[name=separateur]').getValue() || ';';
        var nom = (fileData.nom || '').toLowerCase();
        if (nom.match(/\.xlsx?$/)) {
            Ext.Msg.alert('Info', 'Détection automatique des colonnes disponible pour CSV. ' +
                'Pour Excel, saisissez les noms de colonnes dans les listes de mapping.');
            return;
        }
        if (!fileData.text) { Ext.Msg.alert('Info', 'Choisissez d\'abord un fichier CSV.'); return; }
        var firstLine = fileData.text.split(/\r?\n/)[0] || '';
        var cols = firstLine.split(sep).map(function (s) { return s.trim().replace(/^"|"$/g, ''); });
        colStore.loadData(cols.map(function (c) { return { col: c }; }));
        // Pré-mapping automatique par nom identique.
        champs.forEach(function (c) {
            var field = win.down('[name=map_' + c[0] + ']');
            var found = Ext.Array.findBy(cols, function (col) { return col.toLowerCase() === c[0].toLowerCase(); });
            if (found) { field.setValue(found); }
        });
        Ext.Msg.alert('Colonnes détectées', cols.length + ' colonnes : ' + Ext.String.htmlEncode(cols.join(', ')));
    };

    var win = Ext.create('Ext.window.Window', {
        title: 'Assistant d\'import — ' + type, width: 640,
        height: Math.min(720, Ext.getBody().getViewSize().height - 40),
        maxHeight: Ext.getBody().getViewSize().height - 40, modal: true,
        layout: 'fit',
        items: [{
            xtype: 'form', border: false, autoScroll: true, bodyPadding: 12,
            defaults: { anchor: '100%' },
            items: [
                { xtype: 'filefield', name: 'fichier', fieldLabel: 'Fichier', buttonText: 'Parcourir...',
                  listeners: { change: function (f) {
                      var file = f.fileInputEl.dom.files[0];
                      if (!file) { return; }
                      fileData.nom = file.name;
                      var reader = new FileReader();
                      reader.onload = function (e) {
                          fileData.base64 = e.target.result.split(',')[1];
                          var txtReader = new FileReader();
                          txtReader.onload = function (ev) { fileData.text = ev.target.result; };
                          txtReader.readAsText(file);
                      };
                      reader.readAsDataURL(file);
                  } } },
                { xtype: 'fieldcontainer', layout: 'hbox', items: [
                    { xtype: 'textfield', name: 'separateur', fieldLabel: 'Séparateur', value: ';', width: 160 },
                    { xtype: 'button', text: 'Détecter les colonnes', margin: '0 0 0 10',
                      handler: function (b) { detecter(b.up('window')); } }
                ] },
                { xtype: 'combobox', fieldLabel: 'Modèle de mapping', store: mappingStore,
                  valueField: 'id', displayField: 'nom', queryMode: 'local', editable: false,
                  emptyText: 'Aucun (mapping manuel)', name: 'mappingId',
                  listeners: { select: function (cb, recs) {
                      var rec = recs[0]; if (!rec) { return; }
                      try {
                          var m = Ext.decode(rec.get('mappingJson'));
                          Ext.Object.each(m, function (k, v) {
                              var field = win.down('[name=map_' + k + ']');
                              if (field) { field.setValue(v); }
                          });
                      } catch (e) { }
                  } } },
                { xtype: 'fieldset', title: 'Correspondance des colonnes', collapsible: true,
                  defaults: { labelWidth: 160, anchor: '100%' }, items: mappingItems },
                { xtype: 'combobox', name: 'mode', fieldLabel: 'En cas de doublon', value: 'AJOUT_MAJ',
                  store: [['AJOUT_MAJ', 'Ajouter et mettre à jour'], ['IGNORER', 'Ignorer les doublons']],
                  queryMode: 'local', editable: false },
                { xtype: 'checkbox', name: 'simulation', boxLabel: 'Simulation (aucun enregistrement)' }
            ]
        }],
        buttons: [
            { text: '📄 Exporter un exemplaire', tooltip: 'Télécharger un modèle CSV avec les colonnes attendues',
              handler: function (b) { Usp.importer.exempleCsv(type, champs, b.up('window').down('[name=separateur]').getValue()); } },
            { text: 'Enregistrer ce mapping', handler: function (b) { Usp.importer.saveMapping(b.up('window'), type, champs, mappingStore); } },
            '->',
            { text: 'Lancer l\'import', handler: function (b) { Usp.importer.run(b.up('window'), type, url, champs, fileData, onDone); } }
        ]
    });
    win.show();
};

/* Génère et télécharge un exemplaire CSV : en-tête = colonnes attendues
 * (avec * sur les obligatoires) + une ligne d'exemple vide, pour guider la saisie. */
Usp.importer.exempleCsv = function (type, champs, sep) {
    sep = sep || ';';
    var entetes = champs.map(function (c) { return c[0]; });
    var libelles = champs.map(function (c) { return c[1]; });
    // 1re ligne : noms de colonnes techniques ; 2e ligne (commentaire) : libellés.
    var contenu = entetes.join(sep) + '\n' + libelles.join(sep) + '\n';
    var uri = 'data:text/csv;charset=utf-8,﻿' + encodeURIComponent(contenu);
    var a = document.createElement('a');
    a.href = uri; a.download = 'modele_import_' + String(type).toLowerCase() + '.csv';
    document.body.appendChild(a); a.click(); document.body.removeChild(a);
};

Usp.importer.collecterMapping = function (win, champs) {
    var mapping = {};
    champs.forEach(function (c) {
        var v = win.down('[name=map_' + c[0] + ']').getValue();
        if (v) { mapping[c[0]] = v; }
    });
    return mapping;
};

Usp.importer.saveMapping = function (win, type, champs, mappingStore) {
    Ext.Msg.prompt('Enregistrer le mapping', 'Nom du modèle :', function (btn, nom) {
        if (btn !== 'ok' || !nom) { return; }
        Usp.ajax({
            url: '/imports/mappings', method: 'POST',
            jsonData: {
                nom: nom, typeImport: type,
                mappingJson: Ext.encode(Usp.importer.collecterMapping(win, champs)),
                separateur: win.down('[name=separateur]').getValue()
            },
            success: function () { mappingStore.load(); Ext.Msg.alert('OK', 'Mapping enregistré.'); },
            failure: function () { Ext.Msg.alert('Erreur', 'Enregistrement du mapping impossible.'); }
        });
    });
};

Usp.importer.run = function (win, type, url, champs, fileData, onDone) {
    if (!fileData.base64) { Ext.Msg.alert('Erreur', 'Sélectionnez un fichier.'); return; }
    var payload = {
        nomFichier: fileData.nom,
        separateur: win.down('[name=separateur]').getValue(),
        mapping: Usp.importer.collecterMapping(win, champs),
        mappingId: win.down('[name=mappingId]').getValue() || null,
        mode: win.down('[name=mode]').getValue(),
        simulation: win.down('[name=simulation]').getValue(),
        creerSegmentation: true,
        fichierBase64: fileData.base64,
        correctionsNumero: {}
    };
    win.close();
    Usp.importer.executer(payload, url, onDone);
};

/* Lance l'import (ou la simulation) avec un payload, puis affiche le rapport. */
Usp.importer.executer = function (payload, url, onDone) {
    Usp.ajax({
        url: url, method: 'POST', jsonData: payload,
        success: function (resp) {
            Usp.importer.rapport(Ext.decode(resp.responseText), payload, url, onDone);
            if (onDone) { onDone(); }
        },
        failure: function () { Ext.Msg.alert('Erreur', 'Import en échec.'); }
    });
};

Usp.importer.rapport = function (r, payload, url, onDone) {
    var html =
        (payload && payload.simulation ? '<b style="color:#1976d2">Simulation (aucun enregistrement)</b><br/><br/>' : '') +
        'Lignes lues : ' + r.lignesLues + '<br/>' +
        'Comptes/articles créés : ' + r.comptesCrees + '<br/>' +
        'Mis à jour : ' + r.comptesMisAJour + '<br/>' +
        'Contacts créés : ' + (r.contactsCrees || 0) + '<br/>' +
        'Contacts WhatsApp : ' + (r.contactsWhatsapp || 0) + '<br/>' +
        'Lignes ignorées : ' + (r.lignesIgnorees || 0) + '<br/>' +
        '<b>Lignes rejetées : ' + r.lignesRejetees + '</b>';

    var invalides = r.lignesInvalidesNumero || [];
    var items = [{ xtype: 'component', padding: 14, html: html }];
    var store = null;

    if (invalides.length) {
        store = Ext.create('Ext.data.Store', {
            fields: ['ligne', 'nom', 'numero', 'raison'], data: invalides
        });
        items.push({
            xtype: 'grid', flex: 1, store: store, title: invalides.length + ' numéro(s) WhatsApp non conforme(s)',
            plugins: [Ext.create('Ext.grid.plugin.CellEditing', { clicksToEdit: 1 })],
            tbar: [{ xtype: 'tbtext',
                text: 'Corrigez les numéros (double-clic) puis réimportez les lignes corrigées :' }],
            columns: [
                { text: 'Ligne', dataIndex: 'ligne', width: 60 },
                { text: 'Nom', dataIndex: 'nom', flex: 1 },
                { text: 'Numéro', dataIndex: 'numero', width: 180, editor: { xtype: 'textfield' } },
                { text: 'Motif', dataIndex: 'raison', width: 160, renderer: function (v) {
                    return '<span style="color:#c62828">' + Ext.String.htmlEncode(v || '') + '</span>'; } }
            ]
        });
    }

    Ext.create('Ext.window.Window', {
        title: 'Rapport d\'import', width: invalides.length ? 640 : 420,
        height: invalides.length ? 460 : undefined,
        modal: true, layout: invalides.length ? 'vbox' : 'auto',
        defaults: invalides.length ? { width: '100%' } : undefined,
        bodyPadding: invalides.length ? 0 : 0, items: items,
        buttons: [
            { text: 'Exporter les non conformes (CSV)', hidden: !invalides.length, handler: function () {
                var lignes = ['ligne;nom;numero;motif'];
                store.each(function (rec) {
                    lignes.push(rec.get('ligne') + ';' + (rec.get('nom') || '') + ';' +
                        (rec.get('numero') || '') + ';' + (rec.get('raison') || ''));
                });
                var uri = 'data:text/csv;charset=utf-8,' + encodeURIComponent(lignes.join('\n'));
                var a = document.createElement('a'); a.href = uri; a.download = 'import_numeros_non_conformes.csv';
                document.body.appendChild(a); a.click(); document.body.removeChild(a);
            } },
            { text: 'Réimporter les lignes corrigées', hidden: !(invalides.length && payload && url),
              handler: function (b) {
                var corrections = {};
                store.each(function (rec) {
                    var n = (rec.get('numero') || '').trim();
                    if (n) { corrections[String(rec.get('ligne'))] = n; }
                });
                var p = Ext.apply({}, payload);
                p.simulation = false;
                p.correctionsNumero = corrections;
                b.up('window').close();
                Usp.importer.executer(p, url, onDone);
            } },
            { text: 'Télécharger les rejets',
              hidden: !(r.importId && (r.lignesRejetees > 0 || r.lignesIgnorees > 0)),
              handler: function () { Usp.importer.downloadRejets(r.importId); } },
            { text: 'Fermer', handler: function (b) { b.up('window').close(); } }
        ]
    }).show();
};

/* Téléchargement du CSV des rejets (avec le jeton, via blob). */
Usp.importer.downloadRejets = function (importId) {
    var xhr = new XMLHttpRequest();
    xhr.open('GET', Usp.apiBase + '/imports/' + importId + '/errors', true);
    xhr.setRequestHeader('Authorization', 'Bearer ' + (Usp.token || ''));
    xhr.responseType = 'blob';
    xhr.onload = function () {
        if (xhr.status === 200) {
            var blobUrl = window.URL.createObjectURL(xhr.response);
            var a = document.createElement('a');
            a.href = blobUrl;
            a.download = 'import_' + importId + '_rejets.csv';
            document.body.appendChild(a); a.click(); document.body.removeChild(a);
            window.URL.revokeObjectURL(blobUrl);
        } else {
            Ext.Msg.alert('Erreur', 'Téléchargement impossible.');
        }
    };
    xhr.send();
};
