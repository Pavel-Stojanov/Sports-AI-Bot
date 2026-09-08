ALTER TABLE donation_batches ADD COLUMN attempt_count INT NOT NULL DEFAULT 0;
ALTER TABLE donation_batches ADD COLUMN last_error VARCHAR(1000);
