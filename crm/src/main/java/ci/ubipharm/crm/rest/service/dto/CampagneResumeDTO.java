package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDateTime;

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
public class CampagneResumeDTO {

    private Long id;
    private String nom;
    private String statut;
    private String canal;
    private LocalDateTime lanceLe;
    private long envoyes;
    private long lus;
    private long reponses;
    private long echecs;
}
