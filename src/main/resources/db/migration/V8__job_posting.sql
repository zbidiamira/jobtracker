-- Week 6: job postings imported by the CSV batch job (postingImportJob). Shared by all users, like company.
-- The url identifies a posting: the importer filters known urls, the unique constraint is the safety net.
CREATE TABLE job_posting (
    id           BIGSERIAL    PRIMARY KEY,
    title        VARCHAR(200) NOT NULL,
    company_name VARCHAR(200) NOT NULL,
    city         VARCHAR(100),
    url          VARCHAR(500) NOT NULL,
    source       VARCHAR(255) NOT NULL,
    imported_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ux_job_posting_url UNIQUE (url)
);
