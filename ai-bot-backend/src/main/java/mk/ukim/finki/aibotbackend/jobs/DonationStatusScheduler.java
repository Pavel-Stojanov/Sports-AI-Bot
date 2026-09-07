package mk.ukim.finki.aibotbackend.jobs;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import mk.ukim.finki.aibotbackend.service.domain.DonationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically asks doniraj.vezilka.ai what happened to the SUBMITTED
 * donation batches. The heavy lifting is in
 * {@code DonationService.refreshSubmittedStatuses()}, which reads each donated
 * post back and settles the batch.
 */
@Component
@Slf4j
public class DonationStatusScheduler {
    private final DonationService donationService;

    public DonationStatusScheduler(DonationService donationService) {
        this.donationService = donationService;
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void refreshSubmittedDonationStatuses() {
        log.info("Refreshing statuses of submitted donation batches...");
        donationService.refreshSubmittedStatuses();
        log.info("Statuses of submitted donation batches refreshed.");
    }
}
