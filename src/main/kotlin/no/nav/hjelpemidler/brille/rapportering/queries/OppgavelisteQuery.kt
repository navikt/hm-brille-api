package no.nav.hjelpemidler.brille.rapportering.queries

import no.nav.hjelpemidler.brille.rapportering.KravFilter
import no.nav.hjelpemidler.brille.store.COLUMN_LABEL_TOTAL
import org.intellij.lang.annotations.Language
import java.time.LocalDate

fun kravlinjeQuery(
    kravFilter: KravFilter?,
    tilDato: LocalDate?,
    avstemmingsreferanse: String?,
    referanseFilter: String?,
    paginert: Boolean = false,
): String {
    @Language("PostgreSQL")
    var sql = """
        WITH alle_vedtak AS (
            -- Slå sammen vanlige og slettede vedtak, filtrer/søk...
            SELECT
                COALESCE(v.id, vs.id) AS id,
                -- COALESCE(v.fnr_barn, vs.fnr_barn) AS fnr_barn,
                -- COALESCE(v.fnr_innsender, vs.fnr_innsender) AS fnr_innsender,
                -- COALESCE(v.orgnr, vs.orgnr) AS orgnr,
                COALESCE(v.bestillingsdato, vs.bestillingsdato) AS bestillingsdato,
                -- COALESCE(v.brillepris, vs.brillepris) AS brillepris,
                COALESCE(v.bestillingsreferanse, vs.bestillingsreferanse) AS bestillingsreferanse,
                -- COALESCE(v.vilkarsvurdering, vs.vilkarsvurdering) AS vilkarsvurdering,
                COALESCE(v.behandlingsresultat, vs.behandlingsresultat) AS behandlingsresultat,
                -- COALESCE(v.sats, vs.sats) AS sats,
                -- COALESCE(v.sats_belop, vs.sats_belop) AS sats_belop,
                -- COALESCE(v.sats_beskrivelse, vs.sats_beskrivelse) AS sats_beskrivelse,
                COALESCE(v.belop, vs.belop) AS belop,
                COALESCE(v.opprettet, vs.opprettet) AS opprettet,
                -- COALESCE(v.navn_innsender, vs.navn_innsender) AS navn_innsender,
                -- COALESCE(v.kilde, vs.kilde) AS kilde,
                vs.slettet,
                -- vs.slettet_av_type,
                COALESCE(u1.batch_id, u2.batch_id) AS batch_id,
                COALESCE(u1.utbetalingsdato, u2.utbetalingsdato) AS utbetalingsdato,
                COALESCE(ub1.totalbelop, ub2.totalbelop) AS batch_totalbelop
            FROM vedtak_v1 v
            FULL OUTER JOIN vedtak_slettet_v1 vs ON v.id = vs.id
            LEFT JOIN utbetaling_v1 u1 ON v.id = u1.vedtak_id
            LEFT JOIN utbetaling_v1 u2 ON vs.id = u2.vedtak_id
            LEFT JOIN utbetalingsbatch_v1 ub1 ON u1.batch_id = ub1.batch_id
            LEFT JOIN utbetalingsbatch_v1 ub2 ON u2.batch_id = ub2.batch_id
            
            WHERE
                -- Bare inkluder resultater fra den relevante organisasjonen
                (v.orgnr = :orgNr OR vs.orgnr = :orgNr)
            
                -- Bare inkluder resultater fra slettet-vedtak tabellen som ble utbetalt før de ble slettet:
                AND (vs.id IS NULL OR u2.utbetalingsdato IS NOT NULL)
            
            ORDER BY COALESCE(v.id, vs.id) DESC
        
        ), alle_vedtak_sok_filtrert AS (
            SELECT *
            FROM alle_vedtak v
            WHERE
                TRUE
                -- Søk:
                {{SEARCH_PLACEHOLDER}}
            ORDER BY id DESC
        ), grupperte_resultater AS (
            -- Grupper vedtak sammen basert på avstemmingsreferanse eller dagen de ble
            -- opprettet hvis de ikke har fått avstemmingsreferanse enda. Aggreger sammen
            -- lister med vedtak-ider for hvert resultat slik at vi kan ekspandere til
            -- alle vedtak igjen senere.
            SELECT v.batch_id, v.utbetalingsdato, DATE(v.opprettet), array_agg(v.id) AS vedtak_ids, count(*) over() AS $COLUMN_LABEL_TOTAL
            FROM alle_vedtak_sok_filtrert v
            GROUP BY DATE(v.opprettet), v.batch_id, v.utbetalingsdato
            ORDER BY DATE(v.opprettet) DESC
        ), grupperte_resultater_pagination AS (
            -- Paginer resultatene
            SELECT batch_id, utbetalingsdato, vedtak_ids, $COLUMN_LABEL_TOTAL FROM grupperte_resultater
            ${if (paginert) { "LIMIT :limit OFFSET :offset" } else { "" }}
        )
        -- Ekspander de paginerte resultatene igjen til alle relevante vedtak
        SELECT * ${if (!referanseFilter.isNullOrBlank()) {
        ", (SELECT json_agg(ak) FROM alle_vedtak ak WHERE ak.batch_id IS NOT NULL AND ak.batch_id = v.batch_id) AS potensielt_bortfiltrerte_krav"
    } else { ", NULL AS potensielt_bortfiltrerte_krav" }}
        FROM grupperte_resultater_pagination grp
        LEFT JOIN alle_vedtak_sok_filtrert v ON v.id = ANY(grp.vedtak_ids)
    """

    if (tilDato != null && kravFilter?.equals(KravFilter.EGENDEFINERT) == true) {
        sql = sql.replace(
            "{{SEARCH_PLACEHOLDER}}",
            """
                AND v.opprettet >= :fraDato AND v.opprettet <= :tilDato
                {{SEARCH_PLACEHOLDER}}
            """,
        )
    } else if (tilDato == null && kravFilter?.equals(KravFilter.EGENDEFINERT) == true) {
        sql = sql.replace(
            "{{SEARCH_PLACEHOLDER}}",
            """
                AND v.opprettet >= :fraDato
                {{SEARCH_PLACEHOLDER}}
            """,
        )
    } else if (kravFilter?.equals(KravFilter.HITTILAR) == true) {
        sql = sql.replace(
            "{{SEARCH_PLACEHOLDER}}",
            """
                AND date_part('year', v.opprettet) = date_part('year', CURRENT_DATE)
                {{SEARCH_PLACEHOLDER}}
            """,
        )
    } else if (kravFilter?.equals(KravFilter.SISTE3MND) == true) {
        sql = sql.replace(
            "{{SEARCH_PLACEHOLDER}}",
            """
                AND v.opprettet > CURRENT_DATE - INTERVAL '3 months'
                {{SEARCH_PLACEHOLDER}}
            """,
        )
    }

    if (avstemmingsreferanse != null) {
        sql = sql.replace(
            "{{SEARCH_PLACEHOLDER}}",
            """
                AND v.batch_id = :avstemmingsreferanse
                {{SEARCH_PLACEHOLDER}}
            """,
        )
    }

    if (!referanseFilter.isNullOrBlank()) {
        sql = sql.replace(
            "{{SEARCH_PLACEHOLDER}}",
            """
                AND (
                    CAST(v.id AS TEXT) LIKE :referanseFilter
                    OR v.bestillingsreferanse LIKE :referanseFilter
                    OR v.batch_id LIKE :referanseFilter
                )
            """,
        )
    }

    // Hvis søk-placeholder teksten fortsatt er her, da fjerner vi den bare
    sql = sql.replace("{{SEARCH_PLACEHOLDER}}", "")

    //language=PostgreSQL
    return sql.trimIndent()
}
