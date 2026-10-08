-- Since Week 5 the history is written by a Kafka listener. Kafka delivers at least once, so the same event can
-- arrive twice; the event id makes the insert idempotent.
ALTER TABLE status_history ADD COLUMN event_id UUID;

-- rows written before events existed get an id of their own
UPDATE status_history SET event_id = gen_random_uuid() WHERE event_id IS NULL;

ALTER TABLE status_history ALTER COLUMN event_id SET NOT NULL;
ALTER TABLE status_history ADD CONSTRAINT ux_status_history_event_id UNIQUE (event_id);
