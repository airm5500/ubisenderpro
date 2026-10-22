package ci.ubipharm.crm.rest.service;

import java.util.List;

import ci.ubipharm.crm.rest.service.dto.DroitDTO;
import ci.ubipharm.crm.rest.service.dto.RoleDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.RoleGestionDTO;

/** Roles et droits : chaque role porte la liste des ecrans et actions qu'il permet. */
public interface RoleService {

    List<RoleGestionDTO> lister();

    RoleGestionDTO consulter(Long id);

    /** Catalogue des droits, dans l'ordre du menu. */
    List<DroitDTO> droits();

    RoleGestionDTO creer(RoleDemandeDTO demande);

    /** Les comptes qui ont ce role devront se reconnecter si ses droits changent. */
    RoleGestionDTO modifier(Long id, RoleDemandeDTO demande);

    /** Refuse tant que des comptes actifs ont ce role ; le role Administrateur reste actif. */
    RoleGestionDTO desactiver(Long id);

    RoleGestionDTO reactiver(Long id);
}
