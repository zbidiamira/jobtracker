CREATE TABLE recruiter (
                           id          BIGSERIAL    PRIMARY KEY,
                           name        VARCHAR(200) NOT NULL,
                           email       VARCHAR(300) NOT NULL,
                           company_id  BIGINT       REFERENCES company(id)   -- NULL = agency recruiter
);

CREATE UNIQUE INDEX ux_recruiter_email ON recruiter (lower(email));

ALTER TABLE job_application ADD COLUMN recruiter_id BIGINT REFERENCES recruiter(id);

CREATE INDEX idx_job_application_recruiter ON job_application(recruiter_id);

-- never apply for the same job twice
CREATE UNIQUE INDEX ux_job_application_job_url
    ON job_application (job_url);
CREATE UNIQUE INDEX ux_job_application_company_position
    ON job_application (company_id, lower(position));

-- only one active application (CV with the recruiter) per recruiter at a time
CREATE UNIQUE INDEX ux_job_application_active_recruiter
    ON job_application (recruiter_id)
    WHERE status IN ('APPLIED', 'INTERVIEW', 'OFFER');
