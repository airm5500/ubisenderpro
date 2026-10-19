package ci.ubipharm.crm.rest.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Valeur proposee dans un filtre du journal. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JournalOptionDTO {
    private String code;
    private String libelle;
}
