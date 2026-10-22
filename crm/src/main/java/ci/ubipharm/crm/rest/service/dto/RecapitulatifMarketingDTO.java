package ci.ubipharm.crm.rest.service.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ce qui partira : destinataires, messages, exclusions par motif et quelques messages tels que recus. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RecapitulatifMarketingDTO {

    private long nbClients;
    private long nbMessages;
    private long nbExclus;
    private String expediteur;
    private String numeroExpediteur;
    private String emailReponse;
    private List<MotifExclusionDTO> exclusions;
    private List<MessageCampagneDTO> exemples;
}
