package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Question / reponse de la FAQ. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupportFaqDTO {

    private Long id;
    private String categorie;
    private String question;
    private String reponse;
    private int ordre;
    /** ACTIF ou ARCHIVE. */
    private String statut;
    private String modifiePar;
    private LocalDateTime modifieLe;
}
