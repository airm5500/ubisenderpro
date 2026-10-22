package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Filtres de l'historique complet des envois. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EnvoiHistoriqueCriteresDTO {

    private LocalDate du;
    private LocalDate au;
    @Pattern(regexp = "RELANCE|PROMOTION|DISCUSSION|ENVOI_MASSE", message = "Origine inconnue.")
    private String origine;
    @Pattern(regexp = "WHATSAPP|EMAIL|SMS", message = "Canal inconnu.")
    private String canal;
    private String statut;
    private Long utilisateurId;
    private Long clientId;
    /** Numero, adresse e-mail ou nom du client. */
    private String q;

    @Min(value = 0, message = "La page doit être positive.")
    @Builder.Default
    private int page = 0;

    @Min(value = 1, message = "La taille doit être au moins 1.")
    @Max(value = 200, message = "La taille ne peut pas dépasser 200.")
    @Builder.Default
    private int taille = 50;
}
