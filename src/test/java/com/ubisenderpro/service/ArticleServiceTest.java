package com.ubisenderpro.service;

import com.ubisenderpro.entity.Article;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Non-régression : auto-création d'un article au catalogue depuis les produits
 * de promotion/disponibilité, et génération d'un CIP7 libre.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ArticleServiceTest {

    @Mock
    private EntityManager em;
    @Mock
    private TypedQuery<Article> query;

    @InjectMocks
    private ArticleService service;

    private void aucunArticleTrouve() {
        when(em.createQuery(anyString(), eq(Article.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.setMaxResults(org.mockito.ArgumentMatchers.anyInt())).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.emptyList());
    }

    @Test
    void resoudreOuCreer_articleAbsent_creeAuCatalogue() {
        aucunArticleTrouve();

        Article a = service.resoudreOuCreer("1234567", "3400900000000", "DOLIPRANE 1000");

        assertEquals("1234567", a.getCip(), "le CIP7 saisi devient le cip de l'article");
        assertEquals("DOLIPRANE 1000", a.getDesignation());
        assertEquals("1234567", a.getPscode(), "pscode dérivé du CIP7 (libre)");
        verify(em).persist(a);
    }

    @Test
    void resoudreOuCreer_sansNom_neCreeRien() {
        aucunArticleTrouve();
        Article a = service.resoudreOuCreer("1234567", null, "   ");
        assertNull(a, "sans nom de produit, aucune création");
    }

    @Test
    void genererCip7Libre_renvoie7Chiffres() {
        aucunArticleTrouve();
        String c = service.genererCip7Libre();
        assertTrue(c.matches("\\d{7}"), "doit être composé de 7 chiffres : " + c);
    }
}
