package ci.ubipharm.crm.rest.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Callback /message : reponse d'un client recue sur le numero d'un agent. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EvenementMessageDTO {

    private String sessionId;
    private String from;
    private String name;
    private String type;
    private String text;
    private String id;
}
