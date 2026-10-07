-- V3 created the history as application_status_change; the agreed name is status_history.
-- A new script instead of editing V3, so databases that already ran V3 are migrated too.
ALTER TABLE application_status_change RENAME TO status_history;
ALTER INDEX idx_status_change_application RENAME TO idx_status_history_application;
ALTER SEQUENCE application_status_change_id_seq RENAME TO status_history_id_seq;
