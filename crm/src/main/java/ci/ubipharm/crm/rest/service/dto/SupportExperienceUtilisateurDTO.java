package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Experience vecue par un utilisateur sur la periode. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupportExperienceUtilisateurDTO {

    private String utilisateur;
    private long appels;
    private long moyenneAppelMs;
    private double part3s;
    private long echecs;
    private long ecrans;
    private long moyenneEcranMs;
    private LocalDateTime derniereActivite;
}
