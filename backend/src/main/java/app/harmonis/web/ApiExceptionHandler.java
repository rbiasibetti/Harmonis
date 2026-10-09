package app.harmonis.web;

import app.harmonis.forms.FormService.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    /** Erros de validacao de uma submissao -> 422 com mapa campo->mensagem. */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> onValidation(ValidationException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("message", "Há campos inválidos.", "errors", ex.getErrors()));
    }
}
