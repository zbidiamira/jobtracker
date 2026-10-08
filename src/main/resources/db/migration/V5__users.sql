CREATE TABLE app_user (
                          id             BIGSERIAL    PRIMARY KEY,
                          email          VARCHAR(254) NOT NULL,
                          password_hash  VARCHAR(100) NOT NULL,   -- BCrypt, never the raw password
                          role           VARCHAR(20)  NOT NULL CHECK (role IN ('USER', 'ADMIN'))
);

-- one account per email, regardless of upper/lower case
CREATE UNIQUE INDEX ux_app_user_email ON app_user (lower(email));
