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

/** Destinataires retenus et exclus d'un envoi en masse, avant ou apres sa creation. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EnvoiMasseApercuDTO {
    private EnvoiMasseDTO envoi;
    private int nbRetenus;
    private List<DestinataireMasseDTO> exclus;
    private String exemple;
}
