package mk.ukim.finki.aibotbackend.model.domain;

import jakarta.persistence.CascadeType;
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
import mk.ukim.finki.aibotbackend.model.enums.SessionStatus;
import mk.ukim.finki.aibotbackend.model.enums.SocialNetwork;

/**
 * One run of the bot: which network it works against, what it should
 * look for (the {@link ExtractionTarget}s) and how far it has come.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "extraction_sessions")
public class ExtractionSession extends BaseAuditableEntity {
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SocialNetwork socialNetwork;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SessionStatus status;

    private String description;

    private LocalDateTime startedAt;

    @Column(nullable = false)
    private long executionNumber;

    private LocalDateTime finishedAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExtractionTarget> targets = new ArrayList<>();

    public ExtractionSession(SocialNetwork socialNetwork, String description) {
        this.socialNetwork = socialNetwork;
        this.description = description;
        this.status = SessionStatus.CREATED;
    }
}
