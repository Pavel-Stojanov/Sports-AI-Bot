package mk.ukim.finki.aibotbackend.web.handler;

import mk.ukim.finki.aibotbackend.model.exception.InvalidDonationStateException;

import mk.ukim.finki.aibotbackend.model.exception.PostNotFoundException;
import mk.ukim.finki.aibotbackend.web.controller.ExtractedPostController;
import mk.ukim.finki.aibotbackend.web.dto.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ExtractedPostController.class)
public class ExtractedPostControllerExceptionHandler {
    @ExceptionHandler(InvalidDonationStateException.class)
    public ResponseEntity<ApiError> handleInvalidState(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiError.of(HttpStatus.CONFLICT, exception.getMessage()));
    }

    @ExceptionHandler(PostNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(PostNotFoundException exception) {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiError.of(HttpStatus.NOT_FOUND, exception.getMessage()));
    }
}
