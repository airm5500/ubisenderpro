package ci.ubipharm.crm.recouvrement.rest.service.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Modele de message de relance administre par la Direction du Recouvrement. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ModeleMessageDTO {

    private Long id;
    private String nom;
    private String typeCampagne;
    private String contenu;
    private String statut;
    private List<String> variables;
    private String creePar;
    private String modifiePar;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
