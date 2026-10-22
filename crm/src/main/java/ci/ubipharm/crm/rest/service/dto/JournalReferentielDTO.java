package ci.ubipharm.crm.rest.service.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Listes des filtres du journal : types de donnees, natures et auteurs. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JournalReferentielDTO {
    private List<JournalOptionDTO> types;
    private List<JournalOptionDTO> natures;
    /** code = id de l'utilisateur ; 0 = traitements automatiques. */
    private List<JournalOptionDTO> utilisateurs;
}
