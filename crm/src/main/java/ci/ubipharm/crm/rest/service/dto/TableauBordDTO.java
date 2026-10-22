package ci.ubipharm.crm.rest.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tableau de bord du recouvrement, adapte au perimetre de l'utilisateur. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TableauBordDTO {

    private LocalDate du;
    private LocalDate au;
    /** AGENT (son portefeuille), AGENCE (ses agences) ou GLOBAL. */
    private String vue;
    private IndicateursDTO total;

    private int joursEcheanceProche;
    private long nbEcheanceProche;
    private BigDecimal soldeEcheanceProche;
    private long nbPromessesEnCours;
    private BigDecimal montantPromis;
    private long nbPromessesNonTenues;
    private long nbLitiges;
    private long nbContentieux;

    private long campagnesAValider;
    private long campagnesProgrammees;
    private long campagnesEnCours;
    private long campagnesTerminees;
    private long campagnesAnnulees;

    private List<RepartitionDTO> parSousType;
    private List<RepartitionDTO> balanceAgee;
    private List<RepartitionDTO> echecs;
    private List<RepartitionDTO> annulations;
    private List<IndicateursDTO> parAgent;
    private List<IndicateursDTO> parAgence;
}
