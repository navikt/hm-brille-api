-- Selskapet 889234962 ble fusjonert inn i 928512428. 928512428 har nå inngått avtale og vedtakene flyttes til det nye
-- organisasjonsnummeret hvor utbetaling vil være mulig.
UPDATE vedtak_v1 SET orgnr = '928512428' WHERE id = 136 AND orgnr = '889234962';
UPDATE vedtak_v1 SET orgnr = '928512428' WHERE id = 139 AND orgnr = '889234962';
UPDATE vedtak_v1 SET orgnr = '928512428' WHERE id = 2532 AND orgnr = '889234962';
UPDATE vedtak_v1 SET orgnr = '928512428' WHERE id = 7793 AND orgnr = '889234962';
UPDATE vedtak_v1 SET orgnr = '928512428' WHERE id = 8375 AND orgnr = '889234962';
