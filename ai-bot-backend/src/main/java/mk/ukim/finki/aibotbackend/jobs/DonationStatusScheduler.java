package mk.ukim.finki.aibotbackend.jobs;

import lombok.extern.slf4j.Slf4j;
import mk.ukim.finki.aibotbackend.service.domain.DonationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Retries pending items after their API retry delay. Existing verdicts are final.
 * Runs without a transaction so that every batch commits on its own.
 */
@Component
@Slf4j
public class DonationStatusScheduler {
    private final DonationService donationService;

    public DonationStatusScheduler(DonationService donationService) {
        this.donationService = donationService;
    }

    @Scheduled(fixedDelayString = "${vezilka.retry-check-interval-ms:60000}")
    public void refreshSubmittedDonationStatuses() {
        log.info("Refreshing statuses of submitted donation batches...");
        donationService.refreshSubmittedStatuses();
        log.info("Statuses of submitted donation batches refreshed.");
    }
}
