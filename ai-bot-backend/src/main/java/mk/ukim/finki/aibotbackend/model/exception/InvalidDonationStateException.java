package mk.ukim.finki.aibotbackend.model.exception;

import mk.ukim.finki.aibotbackend.model.enums.DonationStatus;

public class InvalidDonationStateException extends RuntimeException {
    public InvalidDonationStateException(String message) {
        super(message);
    }

    public InvalidDonationStateException(Long id, DonationStatus status) {
        super("The donation batch with id %d cannot perform this operation in status %s.".formatted(id, status));
    }
}
