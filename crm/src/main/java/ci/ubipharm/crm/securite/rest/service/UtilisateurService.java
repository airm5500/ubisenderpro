package ci.ubipharm.crm.securite.rest.service;

import ci.ubipharm.crm.securite.rest.service.dto.ChangementMotDePasseDTO;
import ci.ubipharm.crm.securite.rest.service.dto.CompteProvisoireDTO;
import ci.ubipharm.crm.securite.rest.service.dto.ReferentielHabilitationDTO;
import ci.ubipharm.crm.securite.rest.service.dto.UtilisateurCriteresDTO;
import ci.ubipharm.crm.securite.rest.service.dto.UtilisateurDTO;
import ci.ubipharm.crm.securite.rest.service.dto.UtilisateurDemandeDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;

public interface UtilisateurService {

    PageDTO<UtilisateurDTO> rechercher(UtilisateurCriteresDTO criteres);

    UtilisateurDTO consulter(Long id);

    ReferentielHabilitationDTO referentiel();

    /** Cree le compte avec un mot de passe provisoire, montre une seule fois. */
    CompteProvisoireDTO creer(UtilisateurDemandeDTO demande);

    /** Remplace les informations, les habilitations et les agences ; le login ne change pas. */
    UtilisateurDTO modifier(Long id, UtilisateurDemandeDTO demande);

    UtilisateurDTO desactiver(Long id);

    /** Reactive le compte et remet a zero les echecs de connexion. */
    UtilisateurDTO reactiver(Long id);

    CompteProvisoireDTO reinitialiserMotDePasse(Long id);

    /** Mot de passe choisi par l'utilisateur connecte. */
    void changerMonMotDePasse(ChangementMotDePasseDTO changement);
}
