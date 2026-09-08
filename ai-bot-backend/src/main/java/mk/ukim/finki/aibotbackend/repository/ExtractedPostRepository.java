package mk.ukim.finki.aibotbackend.repository;

import java.util.Set;
import mk.ukim.finki.aibotbackend.model.domain.ExtractedPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ExtractedPostRepository
    extends JpaRepository<ExtractedPost, Long>, JpaSpecificationExecutor<ExtractedPost> {
    @Query(
        "select p.externalId from ExtractedPost p where p.session.id = :sessionId and p.externalId is not null")
    Set<String> findExternalIdsBySessionId(Long sessionId);
}
