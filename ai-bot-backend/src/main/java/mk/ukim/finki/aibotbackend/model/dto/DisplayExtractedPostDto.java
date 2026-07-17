package mk.ukim.finki.aibotbackend.model.dto;

import java.time.LocalDateTime;
import java.util.List;
import mk.ukim.finki.aibotbackend.model.domain.ExtractedPost;
import mk.ukim.finki.aibotbackend.model.enums.SocialNetwork;

public record DisplayExtractedPostDto(
    Long id,
    Long sessionId,
    SocialNetwork socialNetwork,
    String externalId,
    String authorHandle,
    String content,
    String summary,
    String sourceUrl,
    LocalDateTime postedAt,
    Double macedonianConfidence,
    List<DisplayMediaItemDto> mediaItems,
    Long donationBatchId
) {
    public static DisplayExtractedPostDto from(ExtractedPost post) {
        return new DisplayExtractedPostDto(
            post.getId(),
            post.getSession().getId(),
            post.getSession().getSocialNetwork(),
            post.getExternalId(),
            post.getAuthorHandle(),
            post.getContent(),
            post.getSummary(),
            post.getSourceUrl(),
            post.getPostedAt(),
            post.getMacedonianConfidence(),
            DisplayMediaItemDto.from(post.getMediaItems()),
            post.getDonationBatch() == null ? null : post.getDonationBatch().getId()
        );
    }

    public static List<DisplayExtractedPostDto> from(List<ExtractedPost> posts) {
        return posts
            .stream()
            .map(DisplayExtractedPostDto::from)
            .toList();
    }
}
