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
public class DelegationDTO {

    private Long id;
    private Long titulaireId;
    private String titulaireNom;
    private String titulaireNumero;
    private Long remplacantId;
    private String remplacantNom;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private String motif;
    private String statut;
    private boolean enCours;
    private String autorisePar;
}
