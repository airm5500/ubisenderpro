package ci.ubipharm.crm.securite.rest.service.dto;

import java.util.List;

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
public class UtilisateurConnecteDTO {

    private Long id;
    private String login;
    private String nomComplet;
    private List<String> roles;
    private List<String> modules;
    private boolean visionGlobale;
    private List<Long> agenceIds;
}
