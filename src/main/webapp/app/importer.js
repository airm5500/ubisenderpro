/*
 * UbiSenderPro - Assistant d'import générique (section 25 de la spec).
 * Détection des colonnes, mapping configurable, modèles de mapping sauvegardés,
 * modes d'import, simulation, rapport et téléchargement des lignes rejetées.
 * Dépend de app.js (objet Usp).
 */
Ext.define('Usp.importer', { singleton: true });

Usp.importer.CHAMPS = {
    CLIENTS: [
        ['numero_client', 'Numéro client *'], ['nom_compte', 'Nom du compte *'],
        ['contact_principal', 'Contact principal'], ['telephone_principal', 'Téléphone principal'],
        ['telephone_2', 'Téléphone 2'], ['numero_whatsapp', 'Numéro WhatsApp'],
        ['fonction', 'Fonction'], ['agence', 'Agence'], ['region', 'Région'],
        ['email_principal', 'E-mail'], ['segmentation', 'Segmentation'],
        ['adresse', 'Adresse'], ['ville', 'Ville'], ['commune', 'Commune'],
        ['pays', 'Pays'], ['statut', 'Statut'], ['notes', 'Notes'],
        ['consentement_whatsapp', 'Consentement WhatsApp']
    ],
    ARTICLES: [
        ['code_article', 'Code article *'], ['designation', 'Désignation *'],
        ['prix_vente', 'Prix de vente *'], ['code_barres', 'Code-barres'], ['cip', 'CIP'],
        ['categorie', 'Catégorie'], ['marque', 'Marque'], ['prix_promotionnel', 'Prix promotionnel'],
        ['stock_disponible', 'Stock disponible'], ['unite', 'Unité'],
        ['image_url', 'Image URL'], ['lien_produit', 'Lien produit']
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
        title: 'Assistant d\'import — ' + type, width: 560, height: 560, modal: true,
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
                  defaults: { labelWidth: 160 }, items: mappingItems },
                { xtype: 'combobox', name: 'mode', fieldLabel: 'En cas de doublon', value: 'AJOUT_MAJ',
                  store: [['AJOUT_MAJ', 'Ajouter et mettre à jour'], ['IGNORER', 'Ignorer les doublons']],
                  queryMode: 'local', editable: false },
                { xtype: 'checkbox', name: 'simulation', boxLabel: 'Simulation (aucun enregistrement)' }
            ]
        }],
        buttons: [
            { text: 'Enregistrer ce mapping', handler: function (b) { Usp.importer.saveMapping(b.up('window'), type, champs, mappingStore); } },
            '->',
            { text: 'Lancer l\'import', handler: function (b) { Usp.importer.run(b.up('window'), type, url, champs, fileData, onDone); } }
        ]
    });
    win.show();
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
        fichierBase64: fileData.base64
    };
    Usp.ajax({
        url: url, method: 'POST', jsonData: payload,
        success: function (resp) {
            var r = Ext.decode(resp.responseText);
            win.close();
            Usp.importer.rapport(r);
            if (onDone) { onDone(); }
        },
        failure: function () { Ext.Msg.alert('Erreur', 'Import en échec.'); }
    });
};

Usp.importer.rapport = function (r) {
    var html =
        'Lignes lues : ' + r.lignesLues + '<br/>' +
        'Comptes/articles créés : ' + r.comptesCrees + '<br/>' +
        'Mis à jour : ' + r.comptesMisAJour + '<br/>' +
        'Contacts créés : ' + (r.contactsCrees || 0) + '<br/>' +
        'Contacts WhatsApp : ' + (r.contactsWhatsapp || 0) + '<br/>' +
        'Lignes ignorées : ' + (r.lignesIgnorees || 0) + '<br/>' +
        '<b>Lignes rejetées : ' + r.lignesRejetees + '</b>';

    Ext.create('Ext.window.Window', {
        title: 'Rapport d\'import', width: 420, modal: true, bodyPadding: 14, html: html,
        buttons: [
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
