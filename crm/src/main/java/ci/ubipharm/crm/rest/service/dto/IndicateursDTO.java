package ci.ubipharm.crm.rest.service.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Indicateurs du recouvrement pour un perimetre (tout, une agence ou un agent) : stock du jour et activite de la periode. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IndicateursDTO {

    private Long id;
    private String libelle;
    private String detail;

    private long nbClients;
    private long nbFactures;
    private BigDecimal solde;
    private long nbEchues;
    private BigDecimal soldeEchu;

    private long messagesTentes;
    private long messagesEnvoyes;
    private long messagesDistribues;
    private long messagesLus;
    private long messagesEchecs;
    private long facturesRelancees;
    private long clientsRelances;
    private long reponses;
    private long clientsAyantRepondu;
    private long promesses;
    private BigDecimal montantEncaisse;
    private long facturesSoldees;
    private long facturesConverties;

    /** Pourcentages a une decimale ; null quand il n'y a rien a mesurer. */
    private Double tauxReussite;
    private Double tauxEchec;
    private Double tauxReponse;
    private Double tauxConversion;
}
