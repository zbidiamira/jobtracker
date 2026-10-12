-- Week 7: the user's own skills; the AI posting analyzer compares postings against them.
CREATE TABLE skill (
    id      BIGSERIAL    PRIMARY KEY,
    user_id BIGINT       NOT NULL REFERENCES app_user (id),
    name    VARCHAR(100) NOT NULL,
    years   INT          NOT NULL CHECK (years >= 0),
    level   VARCHAR(20)  NOT NULL
);

-- one entry per skill and user ("Java" and "java" are the same skill); user_id first: "all skills of a user"
CREATE UNIQUE INDEX ux_skill_user_name ON skill (user_id, lower(name));
