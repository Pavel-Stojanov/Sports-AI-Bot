package mk.ukim.finki.aibotbackend.model.dto;

import java.time.Instant;

import java.time.LocalDateTime;
import java.util.List;
import mk.ukim.finki.aibotbackend.model.domain.DonationBatch;
import mk.ukim.finki.aibotbackend.model.domain.ExtractedPost;
import mk.ukim.finki.aibotbackend.model.enums.DonationStatus;

public record DisplayDonationBatchDto(
    Long id,
    DonationStatus status,
    String vezilkaReference,
    LocalDateTime submittedAt,
    LocalDateTime createdAt,
    List<Long> postIds,
    long acceptedCount,
    long rejectedCount,
    long pendingCount,
    Instant nextRetryAt
) {
    public static DisplayDonationBatchDto from(DonationBatch batch) {
        return new DisplayDonationBatchDto(
            batch.getId(),
            batch.getStatus(),
            batch.getVezilkaReference(),
            batch.getSubmittedAt(),
            batch.getCreatedAt(),
            batch.getPosts()
                .stream()
                .map(ExtractedPost::getId)
                .toList(),
            batch.getPosts().stream().filter(post -> post.getDonationStatus() == DonationStatus.ACCEPTED).count(),
            batch.getPosts().stream().filter(post -> post.getDonationStatus() == DonationStatus.REJECTED).count(),
            batch.getPosts().stream().filter(post -> post.getDonationStatus() == null).count(),
            batch.getNextRetryAt()
        );
    }

    public static List<DisplayDonationBatchDto> from(List<DonationBatch> batches) {
        return batches
            .stream()
            .map(DisplayDonationBatchDto::from)
            .toList();
    }
}
