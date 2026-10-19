package ci.ubipharm.crm.rest.service;

import java.util.List;
import java.util.Map;

import ci.ubipharm.crm.rest.service.dto.FichierDTO;
import ci.ubipharm.crm.rest.service.dto.PromotionCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.PromotionDTO;
import ci.ubipharm.crm.rest.service.dto.PromotionDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.PromotionProduitDTO;
import ci.ubipharm.crm.rest.service.dto.PromotionProduitDemandeDTO;

/** Promotions du Marketing et leurs produits ; rien n'est supprime : une promotion s'archive, un produit se retire. */
public interface PromotionService {

    List<PromotionDTO> lister(PromotionCriteresDTO criteres);

    PromotionDTO consulter(Long id);

    PromotionDTO creer(PromotionDemandeDTO demande);

    PromotionDTO modifier(Long id, PromotionDemandeDTO demande);

    PromotionDTO activer(Long id);

    PromotionDTO archiver(Long id);

    PromotionDTO reactiver(Long id);

    List<PromotionProduitDTO> produits(Long id, boolean avecRetires);

    PromotionProduitDTO ajouterProduit(Long id, PromotionProduitDemandeDTO demande);

    PromotionProduitDTO modifierProduit(Long id, Long ligneId, PromotionProduitDemandeDTO demande);

    PromotionProduitDTO retirerProduit(Long id, Long ligneId);

    /** Produits actifs de la promotion, au format de l'import : le fichier se reimporte tel quel. */
    FichierDTO exporterProduits(Long id);

    /** Refuse une promotion qui n'est pas active ou dont la periode est depassee : une campagne ne cite que des promotions en cours. */
    void verifierCitables(List<Long> ids);

    /** Valeurs des variables de promotion (source calcul.promotion_* et calcul.produits_promotion) pour une campagne. */
    Map<String, String> valeursCampagne(List<Long> ids);

    /** Classeur Excel des produits des promotions citees par une campagne, joint a ses messages. */
    FichierDTO classeurCampagne(List<Long> ids);

    /** Passe en terminee les promotions actives dont la periode est depassee ; renvoie leur nombre. */
    int terminerPromotionsEchues();
}
