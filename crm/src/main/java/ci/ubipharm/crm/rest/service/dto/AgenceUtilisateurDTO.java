package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDate;

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
public class AgenceUtilisateurDTO {

    private Long agenceId;
    private String libelle;
    private LocalDate depuis;
}
