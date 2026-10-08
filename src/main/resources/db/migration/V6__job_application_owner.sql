-- Applications created before user accounts existed have no owner. Only dev/test data exists at this point,
-- so it is removed instead of being assigned to an invented user.
DELETE FROM status_history;
DELETE FROM job_application;

ALTER TABLE job_application ADD COLUMN owner_id BIGINT NOT NULL REFERENCES app_user(id);

-- The duplicate rules from V2 apply per user: two people may track the same job and talk to the same recruiter.
-- owner_id comes first, so these indexes also serve "all applications of this owner".
DROP INDEX ux_job_application_job_url;
DROP INDEX ux_job_application_company_position;
DROP INDEX ux_job_application_active_recruiter;

CREATE UNIQUE INDEX ux_job_application_job_url
    ON job_application (owner_id, job_url);
CREATE UNIQUE INDEX ux_job_application_company_position
    ON job_application (owner_id, company_id, lower(position));
CREATE UNIQUE INDEX ux_job_application_active_recruiter
    ON job_application (owner_id, recruiter_id)
    WHERE status IN ('APPLIED', 'INTERVIEW', 'OFFER');
