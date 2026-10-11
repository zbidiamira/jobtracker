-- Week 6: when the "no answer for 14 days" reminder was sent; NULL = not yet. Set once, so it is sent only once.
ALTER TABLE job_application ADD COLUMN reminded_at TIMESTAMPTZ;

-- the daily reminder query: APPLIED, not reminded yet, unchanged since a cutoff
CREATE INDEX idx_job_application_reminder_due
    ON job_application (updated_at)
    WHERE status = 'APPLIED' AND reminded_at IS NULL;
