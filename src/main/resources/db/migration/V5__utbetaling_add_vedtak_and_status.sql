ALTER TABLE utbetaling_v1 ADD COLUMN vedtak JSONB NOT NULL;
ALTER TABLE utbetaling_v1 ADD COLUMN status VARCHAR(64) NOT NULL;

CREATE INDEX IF NOT EXISTS utbetaling_v1_status_idx ON utbetaling_v1(status);
