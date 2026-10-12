-- Week 8: postings carry the full job text (the AI analyzes it) and, when fetched from a job portal, its own id,
-- publication time and remote flag. Nullable: rows imported before have no description.
ALTER TABLE job_posting ADD COLUMN description  TEXT;
ALTER TABLE job_posting ADD COLUMN external_id  VARCHAR(200);
ALTER TABLE job_posting ADD COLUMN published_at TIMESTAMPTZ;
ALTER TABLE job_posting ADD COLUMN remote       BOOLEAN;
