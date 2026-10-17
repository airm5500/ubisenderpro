package ci.ubipharm.crm.commun.rest.service.dto;

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
public class FichierDTO {

    private String nom;
    private String typeContenu;
    private byte[] contenu;
}
