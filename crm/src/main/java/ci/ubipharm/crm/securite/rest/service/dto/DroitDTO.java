package ci.ubipharm.crm.securite.rest.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ecran ou action qu'un role peut permettre, coche dans l'ecran Roles et droits. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DroitDTO {

    private String code;
    private String libelle;
    private String description;
    /** Rubrique du menu (Créances, Relances, Échanges, Administration). */
    private String groupe;
    /** Module auquel le droit s'applique ; null = administration, valable quel que soit le module. */
    private String module;
    private String moduleLibelle;
}
