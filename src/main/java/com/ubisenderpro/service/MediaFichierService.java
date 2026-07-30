package com.ubisenderpro.service;

import com.ubisenderpro.entity.MediaFichier;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Optional;

/**
 * Stockage et lecture des fichiers média importés (en-tête de modèle, pièces jointes).
 */
@Stateless
public class MediaFichierService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    public MediaFichier enregistrer(byte[] contenu, String mimeType, String nomFichier) {
        MediaFichier mf = new MediaFichier();
        mf.setContenu(contenu);
        mf.setMimeType(mimeType);
        mf.setNomFichier(nomFichier);
        mf.setTaille(contenu == null ? 0L : (long) contenu.length);
        em.persist(mf);
        // Génère l'identifiant (IDENTITY) immédiatement : l'appelant construit
        // aussitôt l'URL publique du fichier à partir de cet identifiant. Sans
        // ce flush, l'URL de la pièce jointe pointait vers un identifiant nul.
        em.flush();
        return mf;
    }

    public Optional<MediaFichier> parId(Long id) {
        return Optional.ofNullable(em.find(MediaFichier.class, id));
    }
}
