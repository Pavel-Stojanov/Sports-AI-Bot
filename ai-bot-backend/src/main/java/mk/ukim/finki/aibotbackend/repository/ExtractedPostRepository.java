package mk.ukim.finki.aibotbackend.repository;

import mk.ukim.finki.aibotbackend.model.domain.ExtractedPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ExtractedPostRepository
    extends JpaRepository<ExtractedPost, Long>, JpaSpecificationExecutor<ExtractedPost> {
    // PostFilterDto filtering is implemented with JPA Specifications via the
    // JpaSpecificationExecutor above — see ExtractedPostServiceImpl.findAll.
}
