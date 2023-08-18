package no.nav.hjelpemidler.brille.rapportering.queries

import no.nav.hjelpemidler.brille.rapportering.KravFilter
import no.nav.hjelpemidler.brille.store.COLUMN_LABEL_TOTAL
import org.intellij.lang.annotations.Language
import java.time.LocalDate

fun kravlinjeQuery(
    kravFilter: KravFilter?,
    tilDato: LocalDate?,
    referanseFilter: String?,
    paginert: Boolean = false,
): String {
    @Language("PostgreSQL")
    var sql = """
        WITH alle_vedtak AS (
            -- Slå sammen gjeldende og slettede vedtak
            SELECT
                COALESCE(v.id, vs.id) AS id,
                COALESCE(v.fnr_barn, vs.fnr_barn) AS fnr_barn,
                COALESCE(v.fnr_innsender, vs.fnr_innsender) AS fnr_innsender,
                COALESCE(v.orgnr, vs.orgnr) AS orgnr,
                COALESCE(v.bestillingsdato, vs.bestillingsdato) AS bestillingsdato,
                COALESCE(v.brillepris, vs.brillepris) AS brillepris,
                COALESCE(v.bestillingsreferanse, vs.bestillingsreferanse) AS bestillingsreferanse,
                COALESCE(v.vilkarsvurdering, vs.vilkarsvurdering) AS vilkarsvurdering,
                COALESCE(v.behandlingsresultat, vs.behandlingsresultat) AS behandlingsresultat,
                COALESCE(v.sats, vs.sats) AS sats,
                COALESCE(v.sats_belop, vs.sats_belop) AS sats_belop,
                COALESCE(v.sats_beskrivelse, vs.sats_beskrivelse) AS sats_beskrivelse,
                COALESCE(v.belop, vs.belop) AS belop,
                COALESCE(v.opprettet, vs.opprettet) AS opprettet,
                COALESCE(v.navn_innsender, vs.navn_innsender) AS navn_innsender,
                COALESCE(v.kilde, vs.kilde) AS kilde,
                vs.slettet,
                vs.slettet_av_type
            FROM vedtak_v1 v
            FULL OUTER JOIN vedtak_slettet_v1 vs ON v.id = vs.id
            ORDER BY COALESCE(v.id, vs.id) DESC
        ), grupperte_resultater AS (
            -- Grupper vedtak sammen basert på avstemmingsreferanse eller dag de ble
            -- opprettet hvis de ikke har fått avstemmingsreferanse enda. Ekskluder
            -- slettede vedtak som ble slettet før utbetaling.
            SELECT u.batch_id, u.utbetalingsdato, DATE(v.opprettet), array_agg(v.id) AS vedtak_ids, count(*) over() AS $COLUMN_LABEL_TOTAL
            FROM alle_vedtak v
            LEFT JOIN utbetaling_v1 u ON v.id = u.vedtak_id
            WHERE v.orgnr = :orgNr AND (v.slettet IS NULL OR u.id IS NOT NULL)
            GROUP BY DATE(v.opprettet), u.batch_id, u.utbetalingsdato
            ORDER BY DATE(v.opprettet) DESC
        ), grupperte_resultater_pagination AS (
            -- Paginer resultatene og gi oss lister med vedtak-ider for hvert resultat
            SELECT batch_id, utbetalingsdato, vedtak_ids, $COLUMN_LABEL_TOTAL FROM grupperte_resultater
            ${if (paginert) "LIMIT :limit OFFSET :offset" else ""}
        )
        -- Ekspander de paginerte resultatene igjen til alle relevante vedtak
        SELECT * FROM grupperte_resultater_pagination grp
        LEFT JOIN alle_vedtak v ON v.id = ANY(grp.vedtak_ids)
        -- Søk relaterte begrensinger på oppslaget legges på etter denne
        WHERE TRUE
    """.trimIndent()

    if (tilDato != null && kravFilter?.equals(KravFilter.EGENDEFINERT) == true) {
        sql = sql.plus(
            """
                AND v.opprettet >= :fraDato AND v.opprettet <= :tilDato
            """.trimIndent()
        )
    } else if (tilDato == null && kravFilter?.equals(KravFilter.EGENDEFINERT) == true) {
        sql = sql.plus(
            """
                AND v.opprettet >= :fraDato
            """.trimIndent()
        )
    } else if (kravFilter?.equals(KravFilter.HITTILAR) == true) {
        sql = sql.plus(
            """
                AND date_part('year', v.opprettet) = date_part('year', CURRENT_DATE)
            """.trimIndent()
        )
    } else if (kravFilter?.equals(KravFilter.SISTE3MND) == true) {
        sql = sql.plus(
            """
                AND v.opprettet > CURRENT_DATE - INTERVAL '3 months'
            """.trimIndent()
        )
    }

    if (!referanseFilter.isNullOrBlank()) {
        sql = sql.plus(
            """
                AND (
                    CAST(v.id AS TEXT) LIKE :referanseFilter
                    OR v.bestillingsreferanse LIKE :referanseFilter
                    OR grp.batch_id LIKE :referanseFilter
                )
            """.trimIndent()
        )
    }

    //language=PostgreSQL
    return sql.trimIndent()
}
