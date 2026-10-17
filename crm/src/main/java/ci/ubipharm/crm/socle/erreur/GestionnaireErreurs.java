package ci.ubipharm.crm.socle.erreur;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import ci.ubipharm.crm.socle.dto.ChampEnErreurDTO;
import ci.ubipharm.crm.socle.dto.ErreurDTO;

/**
 * Traduit toutes les exceptions en reponses sans detail technique.
 * Le detail ne part que dans les journaux, sous une reference montree a l'utilisateur.
 */
@RestControllerAdvice
public class GestionnaireErreurs {

    private static final Logger LOG = LoggerFactory.getLogger(GestionnaireErreurs.class);

    @ExceptionHandler(ErreurMetierException.class)
    public ResponseEntity<ErreurDTO> erreurMetier(ErreurMetierException e) {
        return reponse(HttpStatus.BAD_REQUEST, e.getMessage(), null);
    }

    @ExceptionHandler({RessourceIntrouvableException.class, NoResourceFoundException.class})
    public ResponseEntity<ErreurDTO> introuvable(Exception e) {
        return reponse(HttpStatus.NOT_FOUND, "Element introuvable.", null);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErreurDTO> saisieInvalide(BindException e) {
        List<ChampEnErreurDTO> champs = e.getFieldErrors().stream()
                .map(f -> ChampEnErreurDTO.builder().champ(f.getField()).message(f.getDefaultMessage()).build())
                .toList();
        return reponse(HttpStatus.BAD_REQUEST, "Certaines saisies sont invalides.", champs);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErreurDTO> parametreInvalide(MethodArgumentTypeMismatchException e) {
        ChampEnErreurDTO champ = ChampEnErreurDTO.builder().champ(e.getName()).message("Valeur invalide.").build();
        return reponse(HttpStatus.BAD_REQUEST, "Certaines saisies sont invalides.", List.of(champ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErreurDTO> requeteIllisible(HttpMessageNotReadableException e) {
        return reponse(HttpStatus.BAD_REQUEST, "La requete est illisible.", null);
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    public ResponseEntity<ErreurDTO> fichierAbsent(Exception e) {
        return reponse(HttpStatus.BAD_REQUEST, "Joignez un fichier Excel ou CSV.", null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErreurDTO> fichierTropGros(MaxUploadSizeExceededException e) {
        return reponse(HttpStatus.BAD_REQUEST, "Le fichier depasse 10 Mo : decoupez-le en plusieurs imports.", null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErreurDTO> nonAuthentifie(AuthenticationException e) {
        return reponse(HttpStatus.UNAUTHORIZED, "Identifiant ou mot de passe incorrect.", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErreurDTO> accesRefuse(AccessDeniedException e) {
        return reponse(HttpStatus.FORBIDDEN, "Vous n'avez pas les droits pour cette action.", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErreurDTO> erreurInattendue(Exception e) {
        String reference = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LOG.error("Erreur inattendue, reference {}", reference, e);
        ErreurDTO corps = ErreurDTO.builder()
                .statut(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("Une erreur est survenue. Communiquez la reference " + reference + " a l'administrateur.")
                .reference(reference)
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(corps);
    }

    private ResponseEntity<ErreurDTO> reponse(HttpStatus statut, String message, List<ChampEnErreurDTO> champs) {
        ErreurDTO corps = ErreurDTO.builder().statut(statut.value()).message(message).champs(champs).build();
        return ResponseEntity.status(statut).body(corps);
    }
}
