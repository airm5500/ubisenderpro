package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InformationDemandeDTO {

    @NotBlank(message = "Saisissez le titre de l'information.")
    @Size(max = 200, message = "Le titre ne peut pas dépasser 200 caractères.")
    private String titre;

    @NotBlank(message = "Choisissez le type d'information.")
    private String typeInformation;

    private String priorite;

    @Size(max = 2000, message = "Le message ne peut pas dépasser 2000 caractères.")
    private String message;

    private Long agenceId;

    @Size(max = 150, message = "La tournée ne peut pas dépasser 150 caractères.")
    private String tournee;

    @Size(max = 150, message = "Le signataire ne peut pas dépasser 150 caractères.")
    private String signataire;

    private LocalDate finValidite;
    private LocalDate dateLivraison;

    @Size(max = 100, message = "Le créneau ne peut pas dépasser 100 caractères.")
    private String creneau;

    @Size(max = 20, message = "L'heure initiale ne peut pas dépasser 20 caractères.")
    private String heureInitiale;

    @Size(max = 20, message = "La nouvelle heure ne peut pas dépasser 20 caractères.")
    private String nouvelleHeure;

    @Size(max = 255, message = "La cause communiquée ne peut pas dépasser 255 caractères.")
    private String causeCommunicable;

    @Size(max = 255, message = "La cause interne ne peut pas dépasser 255 caractères.")
    private String causeInterne;

    private LocalDate dateResolution;

    @Size(max = 150, message = "Le jour férié ne peut pas dépasser 150 caractères.")
    private String jourFerie;

    private LocalDate dateGarde;

    @Size(max = 20, message = "L'heure limite ne peut pas dépasser 20 caractères.")
    private String heureLimiteCommande;

    @Size(max = 500, message = "Les consignes ne peuvent pas dépasser 500 caractères.")
    private String consignesLivraison;

    @Size(max = 150, message = "Le pharmacien de garde ne peut pas dépasser 150 caractères.")
    private String pharmacienGarde;

    @Size(max = 40, message = "Le téléphone ne peut pas dépasser 40 caractères.")
    private String telephonePharmacien;
}
