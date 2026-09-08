ALTER TABLE extraction_sessions ADD COLUMN execution_number BIGINT NOT NULL DEFAULT 0;
ALTER TABLE extracted_posts ADD COLUMN donation_status VARCHAR(32);
ALTER TABLE donation_batches ADD COLUMN next_retry_at TIMESTAMP WITH TIME ZONE;

UPDATE extracted_posts SET donation_status = CASE
    WHEN rejection_reason IS NOT NULL THEN 'REJECTED'
    ELSE 'ACCEPTED'
END WHERE vezilka_id IS NOT NULL OR rejection_reason IS NOT NULL;

-- Older code stored no id and no reason for deduplicated items, so a post
-- without a verdict may already be in the corpus. Finished batches stay
-- finished; resending those posts could donate them a second time. A batch
-- the old code left SUBMITTED is still retried, and any deduplicated post in
-- it is sent once more; Vezilka answers deduped again, so nothing is added.
-- A rejected post that came back with an id but no reason cannot be told
-- apart from an accepted one and is labelled ACCEPTED.
