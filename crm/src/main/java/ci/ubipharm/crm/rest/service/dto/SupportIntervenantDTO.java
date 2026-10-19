package ci.ubipharm.crm.rest.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Titulaire du droit Support, a qui un ticket peut etre confie. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupportIntervenantDTO {

    private Long id;
    private String login;
    private String nomComplet;
}
