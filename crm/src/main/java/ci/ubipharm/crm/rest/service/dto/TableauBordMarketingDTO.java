package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tableau de bord du marketing : campagnes, messages par canal, desabonnements, promotions. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TableauBordMarketingDTO {

    private LocalDate du;
    private LocalDate au;

    private long campagnesAValider;
    private long campagnesProgrammees;
    private long campagnesEnCours;
    private long campagnesTerminees;
    private long campagnesAnnulees;

    private long messagesTentes;
    private long messagesEnvoyes;
    private long messagesDistribues;
    private long messagesLus;
    private long messagesEchecs;
    private long clientsTouches;
    private long reponses;
    private long clientsAyantRepondu;
    private long desabonnements;
    private Double tauxReussite;
    private Double tauxLecture;
    private Double tauxReponse;

    private long promotionsEnCours;
    private long listesActives;
    private long clientsJoignables;

    /** Par canal : code = WHATSAPP, EMAIL ou SMS ; nombre = envoyes, montant = lus (WhatsApp) ou null. */
    private List<RepartitionDTO> parCanal;
    private List<RepartitionDTO> echecs;
    /** Campagnes lancees dans la periode, les plus recentes d'abord. */
    private List<CampagneResumeDTO> campagnes;
}
