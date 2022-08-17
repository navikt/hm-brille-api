
INSERT INTO vedtak_slettet_v1
    SELECT id, fnr_barn, fnr_innsender, orgnr, bestillingsdato, brillepris, bestillingsreferanse, vilkarsvurdering, behandlingsresultat, sats, sats_belop, sats_beskrivelse, belop
    FROM vedtak_v1
    WHERE id in(750);

DELETE FROM vedtak_v1 WHERE id in(750);

