package ci.ubipharm.crm.rest.service;

import java.util.List;

import ci.ubipharm.crm.rest.service.dto.SupportIntervenantDTO;
import ci.ubipharm.crm.rest.service.dto.SupportMessageDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.SupportTicketDTO;
import ci.ubipharm.crm.rest.service.dto.SupportTicketDemandeDTO;

/** Tickets d'assistance : ouverts par tout utilisateur, traites par les titulaires du droit Support ; jamais supprimes. */
public interface SupportTicketService {

    /** Tickets ouverts par l'utilisateur connecte, du plus recent au plus ancien. */
    List<SupportTicketDTO> mesTickets(String statut);

    /** Tous les tickets (droit Support), filtres par statut et par intervenant. */
    List<SupportTicketDTO> tous(String statut, Long assigneId);

    /** Ticket et sa conversation : reserve a son auteur et aux titulaires du droit Support. */
    SupportTicketDTO consulter(Long id);

    SupportTicketDTO ouvrir(SupportTicketDemandeDTO demande);

    /** Ajoute un message ; un ticket ferme n'en recoit plus, un ticket resolu relance par son auteur est rouvert. */
    SupportTicketDTO repondre(Long id, SupportMessageDemandeDTO demande);

    SupportTicketDTO changerStatut(Long id, String statut);

    /** Confie le ticket a un titulaire du droit Support, ou le retire (utilisateurId vide). */
    SupportTicketDTO affecter(Long id, Long utilisateurId);

    /** Utilisateurs actifs titulaires du droit Support. */
    List<SupportIntervenantDTO> intervenants();
}
