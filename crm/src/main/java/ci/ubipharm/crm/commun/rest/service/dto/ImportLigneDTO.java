package ci.ubipharm.crm.commun.rest.service.dto;

import java.util.Map;

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
public class ImportLigneDTO {

    private Long id;
    private int numeroLigne;
    private String cle;
    private String action;
    private String message;
    private String resultat;
    private Map<String, String> donnees;
}
