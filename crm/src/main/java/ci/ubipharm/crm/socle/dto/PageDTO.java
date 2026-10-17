package ci.ubipharm.crm.socle.dto;

import java.util.List;

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
public class PageDTO<T> {

    private List<T> elements;
    private long total;
    private int page;
    private int taille;
}
