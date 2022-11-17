-- Selskapet 891138482 ble fusjonert inn i 929263677. 929263677 har nå inngått avtale og vedtakene flyttes til det nye
-- organisasjonsnummeret hvor utbetaling vil være mulig.
UPDATE vedtak_v1 SET orgnr = '929263677' WHERE id = 9948 AND orgnr = '891138482';
UPDATE vedtak_v1 SET orgnr = '929263677' WHERE id = 9947 AND orgnr = '891138482';
UPDATE vedtak_v1 SET orgnr = '929263677' WHERE id = 9946 AND orgnr = '891138482';
UPDATE vedtak_v1 SET orgnr = '929263677' WHERE id = 9945 AND orgnr = '891138482';
UPDATE vedtak_v1 SET orgnr = '929263677' WHERE id = 8088 AND orgnr = '891138482';

UPDATE virksomhet_v1 SET aktiv = false WHERE orgnr = '891138482';
