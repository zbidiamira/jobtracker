-- timeline of status changes per application, written in the same transaction as the status update
CREATE TABLE application_status_change (
                                           id                  BIGSERIAL    PRIMARY KEY,
                                           job_application_id  BIGINT       NOT NULL REFERENCES job_application(id),
                                           from_status         VARCHAR(20)  NOT NULL,
                                           to_status           VARCHAR(20)  NOT NULL,
                                           changed_at          TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_status_change_application ON application_status_change(job_application_id, changed_at);
