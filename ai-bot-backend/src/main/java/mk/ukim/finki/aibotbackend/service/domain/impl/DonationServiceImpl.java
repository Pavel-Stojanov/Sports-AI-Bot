package mk.ukim.finki.aibotbackend.service.domain.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import mk.ukim.finki.aibotbackend.integration.vezilka.TextDonationRequest;
import mk.ukim.finki.aibotbackend.integration.vezilka.VezilkaClient;
import mk.ukim.finki.aibotbackend.integration.vezilka.DonationReceipt;
import mk.ukim.finki.aibotbackend.model.domain.DonationBatch;
import mk.ukim.finki.aibotbackend.model.domain.ExtractedPost;
import mk.ukim.finki.aibotbackend.model.enums.DonationStatus;
import mk.ukim.finki.aibotbackend.model.exception.DonationBatchNotFoundException;
import mk.ukim.finki.aibotbackend.model.exception.InvalidDonationStateException;
import mk.ukim.finki.aibotbackend.model.exception.PostNotFoundException;
import mk.ukim.finki.aibotbackend.model.exception.VezilkaIntegrationException;
import mk.ukim.finki.aibotbackend.repository.DonationBatchRepository;
import mk.ukim.finki.aibotbackend.service.domain.DonationService;
import mk.ukim.finki.aibotbackend.service.domain.ExtractedPostService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class DonationServiceImpl implements DonationService {
    private final DonationBatchRepository donationBatchRepository;
    private final ExtractedPostService extractedPostService;
    private final VezilkaClient vezilkaClient;

    public DonationServiceImpl(
        DonationBatchRepository donationBatchRepository,
        ExtractedPostService extractedPostService,
        VezilkaClient vezilkaClient
    ) {
        this.donationBatchRepository = donationBatchRepository;
        this.extractedPostService = extractedPostService;
        this.vezilkaClient = vezilkaClient;
    }

    @Override
    public List<DonationBatch> findAll() {
        return donationBatchRepository.findAll();
    }

    @Override
    public Optional<DonationBatch> findById(Long id) {
        return donationBatchRepository.findById(id);
    }

    @Override
    @Transactional
    public DonationBatch createBatch(List<Long> postIds) {
        List<ExtractedPost> posts = extractedPostService.findAllById(postIds);
        for (Long postId : postIds) {
            if (posts.stream().noneMatch(post -> post.getId().equals(postId))) {
                throw new PostNotFoundException(postId);
            }
        }
        DonationBatch batch = donationBatchRepository.save(new DonationBatch(DonationStatus.DRAFT));
        posts.forEach(post -> post.setDonationBatch(batch));
        extractedPostService.saveAll(posts);
        batch.getPosts().addAll(posts);
        return batch;
    }

    @Override
    public DonationBatch approve(Long id) {
        DonationBatch batch = getOrThrow(id);
        if (batch.getStatus() != DonationStatus.DRAFT) {
            throw new InvalidDonationStateException(id, batch.getStatus());
        }
        batch.setStatus(DonationStatus.APPROVED);
        return donationBatchRepository.save(batch);
    }

    @Override
    public DonationBatch submit(Long id) {
        DonationBatch batch = getOrThrow(id);
        if (batch.getStatus() != DonationStatus.APPROVED) {
            throw new InvalidDonationStateException(id, batch.getStatus());
        }
        TextDonationRequest request = new TextDonationRequest(
            "Македонски спортски содржини од gol.mk — пакет %d".formatted(batch.getId()),
            buildContent(batch),
            "https://www.gol.mk/"
        );
        DonationReceipt receipt = vezilkaClient.submitTextDonation(request);
        batch.setVezilkaReference(receipt.reference());
        batch.setSubmittedAt(LocalDateTime.now());
        batch.setStatus(DonationStatus.SUBMITTED);
        return donationBatchRepository.save(batch);
    }

    @Override
    public void refreshSubmittedStatuses() {
        for (DonationBatch batch : donationBatchRepository.findAllByStatus(DonationStatus.SUBMITTED)) {
            try {
                DonationStatus status = vezilkaClient.checkStatus(batch.getVezilkaReference());
                if (status == DonationStatus.ACCEPTED
                    || status == DonationStatus.REJECTED
                    || status == DonationStatus.FAILED) {
                    batch.setStatus(status);
                    donationBatchRepository.save(batch);
                }
            } catch (VezilkaIntegrationException exception) {
                log.warn("Could not refresh status of donation batch {}: {}",
                    batch.getId(), exception.getMessage());
            }
        }
    }

    private String buildContent(DonationBatch batch) {
        return batch.getPosts()
            .stream()
            .map(post -> {
                String text = post.getSummary() != null && !post.getSummary().isBlank()
                    ? post.getSummary()
                    : post.getContent();
                return text + "\n\nИзвор: " + post.getSourceUrl();
            })
            .collect(Collectors.joining("\n\n---\n\n"));
    }

    private DonationBatch getOrThrow(Long id) {
        return donationBatchRepository
            .findById(id)
            .orElseThrow(() -> new DonationBatchNotFoundException(id));
    }
}
