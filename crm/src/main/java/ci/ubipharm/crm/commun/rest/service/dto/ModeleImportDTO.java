package ci.ubipharm.crm.commun.rest.service.dto;

import java.time.LocalDateTime;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Correspondance de colonnes enregistree pour etre reutilisee a chaque import du meme fichier. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ModeleImportDTO {

    private Long id;
    private String type;

    @NotBlank(message = "Le nom du modele est obligatoire.")
    @Size(max = 100, message = "Le nom ne peut pas depasser 100 caracteres.")
    private String nom;

    @NotEmpty(message = "La correspondance est vide.")
    private Map<String, String> correspondance;

    private String auteur;
    private LocalDateTime modifieLe;
}
