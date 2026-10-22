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

/** Une tentative de connexion d'un utilisateur. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConnexionHistoriqueDTO {
    private Long id;
    private String login;
    private boolean succes;
    private String adresseIp;
    private String navigateur;
    private LocalDateTime le;
}
