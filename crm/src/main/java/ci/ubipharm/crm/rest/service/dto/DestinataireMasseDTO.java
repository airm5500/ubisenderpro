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

/** Un destinataire d'un envoi en masse. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DestinataireMasseDTO {
    private Long id;
    private String saisi;
    private String numero;
    private String nom;
    private Long clientId;
    private String statut;
    private String motif;
    private LocalDateTime envoyeLe;
}
