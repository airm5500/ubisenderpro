package ci.ubipharm.crm.rest.service.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Client saisi a la main ; le numero de compte ne change plus apres la creation. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClientDemandeDTO {

    @Size(max = 50, message = "Le numéro de compte tient en 50 caractères.")
    private String code;

    @NotBlank(message = "Le nom de la pharmacie est obligatoire.")
    @Size(max = 255, message = "Le nom de la pharmacie tient en 255 caractères.")
    private String raisonSociale;

    @NotNull(message = "Choisissez l'agence.")
    private Long agenceId;

    @Size(max = 30, message = "La sous-agence tient en 30 caractères.")
    private String sousAgence;

    private String segmentation;

    @Size(max = 255, message = "Le nom du pharmacien tient en 255 caractères.")
    private String nomPharmacien;

    @Size(max = 100, message = "La ville tient en 100 caractères.")
    private String ville;

    @Size(max = 255, message = "L'adresse tient en 255 caractères.")
    private String adresse;

    @Size(max = 255, message = "L'e-mail tient en 255 caractères.")
    private String email;

    @Size(max = 1000, message = "Le commentaire tient en 1000 caractères.")
    private String commentaire;

    /** Numeros par role (PRINCIPAL = numero des relances WhatsApp, TITULAIRE, ASSISTANT, CHARGE_COMMANDE, COMPTABLE, SECONDAIRE). */
    private Map<String, String> numeros;
}
