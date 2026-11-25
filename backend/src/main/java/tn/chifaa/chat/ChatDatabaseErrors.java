package tn.chifaa.chat;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ChatController.class)
class ChatDatabaseErrors {
    // Les détails d'erreur PostgreSQL peuvent contenir le texte de la ligne.
    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ProblemDetail unavailable() {
        return ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
