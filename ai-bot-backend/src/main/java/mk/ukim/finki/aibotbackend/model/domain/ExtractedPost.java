package mk.ukim.finki.aibotbackend.model.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mk.ukim.finki.aibotbackend.model.enums.DonationStatus;

/**
 * A piece of content the bot extracted from the social network.
 * Once it is part of a {@link DonationBatch} it is on its way to
 * doniraj.vezilka.ai.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "extracted_posts")
public class ExtractedPost extends BaseAuditableEntity {
    @ManyToOne
    @JoinColumn(name = "session_id", nullable = false)
    private ExtractionSession session;

    /**
     * The identifier of the post within the social network itself, when available.
     */
    private String externalId;

    private String authorHandle;

    @Column(columnDefinition = "text")
    private String content;

    /**
     * Generated summary for display. Donations use the extracted content.
     */
    @Column(columnDefinition = "text")
    private String summary;

    @Column(length = 2048)
    private String sourceUrl;

    private LocalDateTime postedAt;

    /**
     * Confidence (0.0 - 1.0) that {@code content} is written in Macedonian,
     * produced by the {@code LanguageDetector}.
     */
    private Double macedonianConfidence;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MediaItem> mediaItems = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "donation_batch_id")
    private DonationBatch donationBatch;

    /**
     * Vezilka's identifier of this post as a donation, from the per-item result.
     * Set once the batch is submitted, also for rejected items.
     */
    private String vezilkaId;

    /**
     * Why Vezilka rejected this post, e.g. {@code not_macedonian} or
     * {@code text_too_short}. Null when the post was accepted.
     */
    private String rejectionReason;

    @Enumerated(EnumType.STRING)
    private DonationStatus donationStatus;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    public ExtractedPost(
        ExtractionSession session,
        String externalId,
        String authorHandle,
        String content,
        String sourceUrl,
        LocalDateTime postedAt,
        Double macedonianConfidence
    ) {
        this.session = session;
        this.externalId = externalId;
        this.authorHandle = authorHandle;
        this.content = content;
        this.sourceUrl = sourceUrl;
        this.postedAt = postedAt;
        this.macedonianConfidence = macedonianConfidence;
    }
}
