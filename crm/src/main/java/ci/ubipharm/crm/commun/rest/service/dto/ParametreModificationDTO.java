package ci.ubipharm.crm.commun.rest.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Nouvelle valeur d'un parametre ; vide = parametre vide (s'il n'est pas obligatoire). */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ParametreModificationDTO {

    @NotBlank(message = "Paramètre inconnu.")
    private String cle;

    @Size(max = 2000, message = "Une valeur tient en 2000 caractères.")
    private String valeur;
}
