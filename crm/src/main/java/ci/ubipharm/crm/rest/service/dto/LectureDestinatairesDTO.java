package ci.ubipharm.crm.rest.service.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Fichier de destinataires lu (rien n'est enregistre) : lignes reconnues et lignes a corriger. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LectureDestinatairesDTO {
    private List<DestinataireMasseDTO> valides;
    private List<DestinataireMasseDTO> invalides;
}
