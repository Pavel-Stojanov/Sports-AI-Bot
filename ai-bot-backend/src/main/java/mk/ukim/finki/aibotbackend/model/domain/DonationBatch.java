package mk.ukim.finki.aibotbackend.model.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mk.ukim.finki.aibotbackend.model.enums.DonationStatus;

/**
 * A set of {@link ExtractedPost}s that is reviewed, approved and then
 * submitted as one donation to doniraj.vezilka.ai.
 * {@code vezilkaReference} is the identifier Vezilka returns for the
 * submitted donation.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "donation_batches")
public class DonationBatch extends BaseAuditableEntity {
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DonationStatus status;

    private String vezilkaReference;

    private LocalDateTime submittedAt;

    private Instant nextRetryAt;

    @OneToMany(mappedBy = "donationBatch")
    private List<ExtractedPost> posts = new ArrayList<>();

    public DonationBatch(DonationStatus status) {
        this.status = status;
    }
}
