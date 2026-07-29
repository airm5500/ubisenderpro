package com.ubisenderpro.service;

import com.ubisenderpro.entity.PromotionProduit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Liste des produits inseree dans le message d'une promotion.
 *
 * <p>Regle metier : quelques produits sont listes dans le message ; au-dela du
 * seuil, le message renvoie vers le fichier Excel joint.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ListeProduitsPromoTest {

    @Mock
    private PromotionProduitService promotionProduitService;

    @InjectMocks
    private EnvoiProposeService service;

    private PromotionProduit produit(String nom, String cip7, Integer qteMin, Integer ug, BigDecimal taux) {
        PromotionProduit p = new PromotionProduit();
        p.setNomProduit(nom);
        p.setCip7(cip7);
        p.setQuantiteMinimale(qteMin);
        p.setQuantiteUg(ug);
        p.setTauxUg(taux);
        p.setActif(true);
        return p;
    }

    @Test
    void produitAvecQuantiteEtUnitesGratuites() {
        when(promotionProduitService.lister(any())).thenReturn(
                Collections.singletonList(produit("EFFERALGAN", "3257001", 10, 2, null)));

        String out = service.listeProduitsPromo(1L);

        assertTrue(out.contains("EFFERALGAN"), out);
        assertTrue(out.contains("10"), "La quantite minimale doit apparaitre : " + out);
        assertTrue(out.contains("2"), "Les unites gratuites doivent apparaitre : " + out);
    }

    @Test
    void nomAbsentReplieSurLeCip7() {
        when(promotionProduitService.lister(any())).thenReturn(
                Collections.singletonList(produit("", "3257001", null, null, null)));
        assertTrue(service.listeProduitsPromo(1L).contains("3257001"));
    }

    @Test
    void tauxUtiliseQuandAucuneQuantiteGratuite() {
        when(promotionProduitService.lister(any())).thenReturn(
                Collections.singletonList(produit("DOLIPRANE", "1", 5, null, new BigDecimal("7.50"))));
        String out = service.listeProduitsPromo(1L);
        assertTrue(out.contains("7.5"), "Le taux doit apparaitre sans zero inutile : " + out);
    }

    @Test
    void produitInactifIgnore() {
        PromotionProduit inactif = produit("RETIRE", "9", 1, 1, null);
        inactif.setActif(false);
        when(promotionProduitService.lister(any())).thenReturn(
                Arrays.asList(produit("GARDE", "1", 1, 1, null), inactif));

        String out = service.listeProduitsPromo(1L);
        assertTrue(out.contains("GARDE"), out);
        assertFalse(out.contains("RETIRE"), "Un produit inactif ne doit pas etre annonce : " + out);
    }

    @Test
    void plusieursProduitsSurDesLignesDistinctes() {
        when(promotionProduitService.lister(any())).thenReturn(Arrays.asList(
                produit("A", "1", 10, 2, null), produit("B", "2", 5, 1, null)));

        String out = service.listeProduitsPromo(1L);
        assertTrue(out.contains("A"), out);
        assertTrue(out.contains("B"), out);
        assertTrue(out.contains("\n"), "Chaque produit sur sa propre ligne : " + out);
    }

    @Test
    void aucunProduitDonneUneChaineVide() {
        when(promotionProduitService.lister(any())).thenReturn(Collections.emptyList());
        assertEquals("", service.listeProduitsPromo(1L));
    }
}
