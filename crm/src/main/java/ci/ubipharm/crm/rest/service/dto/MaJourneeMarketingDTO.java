package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Ma journee Marketing : reponses du jour aux campagnes, promotions qui commencent ou finissent, evenements a venir. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaJourneeMarketingDTO {

    /** Jours a venir couverts par les promotions et les evenements (parametre marketing.journee_horizon_jours). */
    private int horizonJours;
    /** Null quand l'utilisateur ne voit pas les campagnes Marketing. */
    private Long nbReponsesDuJour;
    private List<Reponse> reponsesDuJour;
    private List<Promotion> promotions;
    private List<EvenementDTO> evenements;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Reponse {
        private Long clientId;
        private String raisonSociale;
        private String numeroClient;
        private String contenu;
        private LocalDateTime recuLe;
        private Long campagneId;
        private String campagneNom;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Promotion {
        private Long id;
        private String nom;
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private String statut;
        /** COMMENCE ou FINIT dans l'horizon. */
        private String moment;
    }
}
