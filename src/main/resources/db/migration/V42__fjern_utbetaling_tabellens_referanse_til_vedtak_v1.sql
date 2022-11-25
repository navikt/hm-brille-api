-- Pga. ny feature hvor vi støtter å slette allerede utbetalte vedtak må vi fjerne en constraint (siden vi har en egen
-- tabell for slettede vedtak)
ALTER TABLE utbetaling_v1 DROP CONSTRAINT IF EXISTS utbetaling_v1_vedtak_id_fkey;
