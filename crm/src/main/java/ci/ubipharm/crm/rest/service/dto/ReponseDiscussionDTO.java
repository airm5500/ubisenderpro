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

/** Message ecrit dans une discussion, envoye depuis le numero de la discussion. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReponseDiscussionDTO {
    @NotBlank(message = "Le numéro de l'expéditeur manque.")
    private String numeroAgent;
    @NotBlank(message = "Le numéro du client manque.")
    private String numeroClient;
    @NotBlank(message = "Écrivez le message.")
    @Size(max = 4000, message = "Le message ne peut pas dépasser 4000 caractères.")
    private String contenu;
}
