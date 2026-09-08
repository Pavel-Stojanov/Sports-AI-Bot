package mk.ukim.finki.aibotbackend.service.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import mk.ukim.finki.aibotbackend.model.domain.ExtractedPost;
import mk.ukim.finki.aibotbackend.model.dto.PostFilterDto;
import org.springframework.data.domain.Page;

/**
 * Domain service for extracted posts.
 */
public interface ExtractedPostService {
    /**
     * Paged browsing of extracted posts. Every filter field may be
     * {@code null} — see {@link PostFilterDto}.
     */
    Page<ExtractedPost> findAll(PostFilterDto filter, int page, int size);

    Optional<ExtractedPost> findById(Long id);

    Set<String> findExternalIdsBySessionId(Long sessionId);

    List<ExtractedPost> findAllById(List<Long> ids);

    /**
     * Persists the posts a bot run produced.
     */
    List<ExtractedPost> saveAll(List<ExtractedPost> posts);

    Optional<ExtractedPost> deleteById(Long id);
}
