CREATE TABLE company (
                         id          BIGSERIAL    PRIMARY KEY,
                         name        VARCHAR(200) NOT NULL,
                         city        VARCHAR(100),
                         website     VARCHAR(300)
);

CREATE TABLE job_application (
                                 id          BIGSERIAL    PRIMARY KEY,
                                 company_id  BIGINT       NOT NULL REFERENCES company(id),
                                 position    VARCHAR(200) NOT NULL,
                                 status      VARCHAR(20)  NOT NULL,
                                 job_url     VARCHAR(500),
                                 version     BIGINT       NOT NULL DEFAULT 0,
                                 created_at  TIMESTAMPTZ,
                                 updated_at  TIMESTAMPTZ
);

CREATE INDEX idx_job_application_company ON job_application(company_id);
CREATE INDEX idx_job_application_status  ON job_application(status);