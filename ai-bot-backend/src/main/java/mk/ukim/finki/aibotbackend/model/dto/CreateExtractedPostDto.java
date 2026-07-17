package mk.ukim.finki.aibotbackend.model.dto;

import java.time.LocalDateTime;
import java.util.List;
import mk.ukim.finki.aibotbackend.model.domain.ExtractedPost;
import mk.ukim.finki.aibotbackend.model.domain.ExtractionSession;

/**
 * A post as produced by the {@code ContentExtractor}, before it is
 * persisted and attached to a session.
 */
public record CreateExtractedPostDto(
    String externalId,
    String authorHandle,
    String content,
    String summary,
    String sourceUrl,
    LocalDateTime postedAt,
    Double macedonianConfidence,
    List<CreateMediaItemDto> mediaItems
) {
    public CreateExtractedPostDto withMacedonianConfidence(Double confidence) {
        return new CreateExtractedPostDto(
            externalId,
            authorHandle,
            content,
            summary,
            sourceUrl,
            postedAt,
            confidence,
            mediaItems
        );
    }

    public ExtractedPost toExtractedPost(ExtractionSession session) {
        ExtractedPost post = new ExtractedPost(
            session,
            externalId,
            authorHandle,
            content,
            sourceUrl,
            postedAt,
            macedonianConfidence
        );
        post.setSummary(summary);
        if (mediaItems != null) {
            mediaItems
                .stream()
                .map(mediaItem -> mediaItem.toMediaItem(post))
                .forEach(post.getMediaItems()::add);
        }
        return post;
    }
}
