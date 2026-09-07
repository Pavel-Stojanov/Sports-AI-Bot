package mk.ukim.finki.aibotbackend.service.domain.impl;

import java.util.Set;
import mk.ukim.finki.aibotbackend.model.exception.InvalidDonationStateException;

import java.util.List;
import java.util.Optional;
import mk.ukim.finki.aibotbackend.model.domain.ExtractedPost;
import mk.ukim.finki.aibotbackend.model.dto.PostFilterDto;
import mk.ukim.finki.aibotbackend.repository.ExtractedPostRepository;
import mk.ukim.finki.aibotbackend.service.domain.ExtractedPostService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
public class ExtractedPostServiceImpl implements ExtractedPostService {
    private final ExtractedPostRepository extractedPostRepository;

    public ExtractedPostServiceImpl(ExtractedPostRepository extractedPostRepository) {
        this.extractedPostRepository = extractedPostRepository;
    }

    @Override
    public Page<ExtractedPost> findAll(PostFilterDto filter, int page, int size) {
        Specification<ExtractedPost> spec = (root, query, cb) -> cb.conjunction();
        if (filter.sessionId() != null) {
            spec = spec.and((root, query, cb) ->
                cb.equal(root.get("session").get("id"), filter.sessionId()));
        }
        if (filter.socialNetwork() != null) {
            spec = spec.and((root, query, cb) ->
                cb.equal(root.get("session").get("socialNetwork"), filter.socialNetwork()));
        }
        if (filter.minMacedonianConfidence() != null) {
            spec = spec.and((root, query, cb) ->
                cb.greaterThanOrEqualTo(root.get("macedonianConfidence"), filter.minMacedonianConfidence()));
        }
        if (filter.donated() != null) {
            spec = spec.and((root, query, cb) -> filter.donated()
                ? cb.isNotNull(root.get("donationBatch"))
                : cb.isNull(root.get("donationBatch")));
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            spec = spec.and((root, query, cb) ->
                cb.like(cb.lower(root.get("content")), "%" + filter.search().toLowerCase() + "%"));
        }
        return extractedPostRepository.findAll(
            spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Override
    public Set<String> findExternalIdsBySessionId(Long sessionId) {
        return extractedPostRepository.findExternalIdsBySessionId(sessionId);
    }

    @Override
    public Optional<ExtractedPost> findById(Long id) {
        return extractedPostRepository.findById(id);
    }

    @Override
    public List<ExtractedPost> findAllById(List<Long> ids) {
        return extractedPostRepository.findAllById(ids);
    }

    @Override
    public List<ExtractedPost> saveAll(List<ExtractedPost> posts) {
        return extractedPostRepository.saveAll(posts);
    }

    @Override
    public Optional<ExtractedPost> deleteById(Long id) {
        Optional<ExtractedPost> post = extractedPostRepository.findById(id);
        post.ifPresent(item -> {
            if (item.getDonationBatch() != null) {
                throw new InvalidDonationStateException(
                    "Posts assigned to a donation batch cannot be deleted.");
            }
            extractedPostRepository.delete(item);
        });
        return post;
    }
}
