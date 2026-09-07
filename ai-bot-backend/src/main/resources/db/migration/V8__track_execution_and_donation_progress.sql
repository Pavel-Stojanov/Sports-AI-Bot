ALTER TABLE extraction_sessions ADD COLUMN execution_number BIGINT NOT NULL DEFAULT 0;
ALTER TABLE extracted_posts ADD COLUMN donation_status VARCHAR(32);
ALTER TABLE donation_batches ADD COLUMN next_retry_at TIMESTAMP WITH TIME ZONE;

UPDATE extracted_posts SET donation_status = CASE
    WHEN rejection_reason IS NOT NULL THEN 'REJECTED'
    ELSE 'ACCEPTED'
END WHERE vezilka_id IS NOT NULL OR rejection_reason IS NOT NULL;

-- Older refresh logic could finish a batch while some posts were still unsent.
UPDATE donation_batches b SET status = 'SUBMITTED'
WHERE b.status IN ('ACCEPTED', 'REJECTED')
  AND EXISTS (
    SELECT 1 FROM extracted_posts p
    WHERE p.donation_batch_id = b.id AND p.donation_status IS NULL
  );
