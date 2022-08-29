ALTER TABLE utbetaling_v1 ADD COLUMN batch_dato DATE NOT NULL;
ALTER TABLE utbetaling_v1 ADD COLUMN batch_id VARCHAR(139) NOT NULL;

CREATE INDEX IF NOT EXISTS utbetaling_v1_batchdato_idx ON utbetaling_v1(batch_dato);
CREATE INDEX IF NOT EXISTS utbetaling_v1_batchid_idx ON utbetaling_v1(batch_id);
