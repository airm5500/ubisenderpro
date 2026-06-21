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
        return mf;
    }

    public Optional<MediaFichier> parId(Long id) {
        return Optional.ofNullable(em.find(MediaFichier.class, id));
    }
}
