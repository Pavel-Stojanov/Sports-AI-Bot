package mk.ukim.finki.aibotbackend.web.handler;

import org.springframework.dao.OptimisticLockingFailureException;

import mk.ukim.finki.aibotbackend.model.exception.DonationBatchNotFoundException;
import mk.ukim.finki.aibotbackend.model.exception.InvalidDonationStateException;
import mk.ukim.finki.aibotbackend.model.exception.PostNotFoundException;
import mk.ukim.finki.aibotbackend.model.exception.VezilkaIntegrationException;
import mk.ukim.finki.aibotbackend.web.controller.DonationController;
import mk.ukim.finki.aibotbackend.web.dto.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = DonationController.class)
public class DonationControllerExceptionHandler {
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleConcurrentChange(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiError.of(HttpStatus.CONFLICT, "These posts changed in another request. Refresh and try again."));
    }

    @ExceptionHandler({DonationBatchNotFoundException.class, PostNotFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(RuntimeException exception) {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiError.of(HttpStatus.NOT_FOUND, exception.getMessage()));
    }

    @ExceptionHandler(InvalidDonationStateException.class)
    public ResponseEntity<ApiError> handleInvalidState(InvalidDonationStateException exception) {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ApiError.of(HttpStatus.CONFLICT, exception.getMessage()));
    }

    @ExceptionHandler(VezilkaIntegrationException.class)
    public ResponseEntity<ApiError> handleVezilkaFailure(VezilkaIntegrationException exception) {
        return ResponseEntity
            .status(HttpStatus.BAD_GATEWAY)
            .body(ApiError.of(HttpStatus.BAD_GATEWAY, exception.getMessage()));
    }
}
