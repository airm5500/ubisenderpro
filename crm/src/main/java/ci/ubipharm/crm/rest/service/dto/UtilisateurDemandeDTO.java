package ci.ubipharm.crm.rest.service.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Creation ou modification d'un compte. Le login n'est lu qu'a la creation. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UtilisateurDemandeDTO {

    @Pattern(regexp = "[a-zA-Z0-9._-]{3,50}", message = "Identifiant : 3 à 50 lettres, chiffres, points ou tirets, sans espace ni accent.")
    private String login;

    @NotBlank(message = "Saisissez le nom complet.")
    @Size(max = 255, message = "Le nom ne peut pas dépasser 255 caractères.")
    private String nomComplet;

    @Email(message = "Adresse e-mail invalide.")
    @Size(max = 150, message = "L'adresse e-mail est trop longue.")
    private String email;

    @Size(max = 30, message = "Le numéro est trop long.")
    private String numeroWhatsapp;

    @NotEmpty(message = "Donnez au moins un rôle.")
    @Valid
    @Builder.Default
    private List<HabilitationDTO> habilitations = new ArrayList<>();

    @Builder.Default
    private List<Long> agenceIds = new ArrayList<>();
}
