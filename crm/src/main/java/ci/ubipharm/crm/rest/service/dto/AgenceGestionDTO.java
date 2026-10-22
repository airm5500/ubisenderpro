package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Agence vue par la Direction Generale : codes, statut et ce qui y est rattache. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AgenceGestionDTO {

    private Long id;
    private String code;
    /** Code de l'agence dans les fichiers importes (clients, factures), s'il differe. */
    private String codeImport;
    private String libelle;
    private String statut;
    private long nbClients;
    private long nbUtilisateurs;
    private String modifiePar;
    private LocalDateTime modifieLe;
}
