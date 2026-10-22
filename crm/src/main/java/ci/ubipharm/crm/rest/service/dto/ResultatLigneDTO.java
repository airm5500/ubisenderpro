package ci.ubipharm.crm.rest.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Verdict sur une ligne : CREATION, MISE_A_JOUR (avec ses ecarts), IDENTIQUE ou REJET (avec ses motifs). */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResultatLigneDTO {

    private String action;
    private String cle;
    private String message;
}
