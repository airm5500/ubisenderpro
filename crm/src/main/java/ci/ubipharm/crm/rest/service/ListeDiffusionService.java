package ci.ubipharm.crm.rest.service;

import java.util.List;

import ci.ubipharm.crm.rest.service.dto.ApercuListeDTO;
import ci.ubipharm.crm.rest.service.dto.ClientsListeDTO;
import ci.ubipharm.crm.rest.service.dto.ListeDiffusionDTO;
import ci.ubipharm.crm.rest.service.dto.ListeDiffusionDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.MembresCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.ValeursCriteresDTO;

/** Listes de diffusion du Marketing : criteres sur la base clients, plus des ajouts, moins des exclusions. */
public interface ListeDiffusionService {

    List<ListeDiffusionDTO> lister(boolean avecArchivees);

    ListeDiffusionDTO consulter(Long id);

    ListeDiffusionDTO creer(ListeDiffusionDemandeDTO demande);

    ListeDiffusionDTO modifier(Long id, ListeDiffusionDemandeDTO demande);

    ListeDiffusionDTO archiver(Long id);

    ListeDiffusionDTO reactiver(Long id);

    /** Destinataires d'une liste enregistree, avec le motif des non joignables. */
    ApercuListeDTO membres(Long id, MembresCriteresDTO criteres);

    /** Clients actifs de la liste dans les agences donnees (null = toutes), desabonnes compris. */
    List<Long> clients(Long id, List<Long> agences);

    /** Definition de la liste sans ses compteurs, lisible hors session (planificateur). */
    ListeDiffusionDTO consulterDefinition(Long id);

    /** Compteurs d'une liste pas encore enregistree, pendant la saisie des criteres. */
    ApercuListeDTO simuler(ListeDiffusionDemandeDTO demande);

    ListeDiffusionDTO changerClients(Long id, ClientsListeDTO demande);

    /** Sous-agences et villes presentes dans la base clients du perimetre. */
    ValeursCriteresDTO valeursCriteres();
}
