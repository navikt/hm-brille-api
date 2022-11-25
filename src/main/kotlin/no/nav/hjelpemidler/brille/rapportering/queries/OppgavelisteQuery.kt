package no.nav.hjelpemidler.brille.rapportering.queries

import no.nav.hjelpemidler.brille.rapportering.KravFilter
import no.nav.hjelpemidler.brille.store.COLUMN_LABEL_TOTAL
import java.time.LocalDate

fun kravlinjeQuery(
    kravFilter: KravFilter?,
    tilDato: LocalDate?,
    referanseFilter: String?,
): String {
    var sql = """
        SELECT
            COALESCE(v.id, vs.id) AS id,
            COALESCE(v.bestillingsdato, vs.bestillingsdato) AS bestillingsdato,
            COALESCE(v.behandlingsresultat, vs.behandlingsresultat) AS behandlingsresultat,
            COALESCE(v.opprettet, vs.opprettet) AS opprettet,
            COALESCE(v.belop, vs.belop) AS belop,
            COALESCE(v.bestillingsreferanse, vs.bestillingsreferanse) AS bestillingsreferanse,
            COALESCE(u1.utbetalingsdato, u2.utbetalingsdato) AS utbetalingsdato,
            COALESCE(u1.batch_id, u2.batch_id) AS batch_id,
            vs.slettet,
            count(*) over() AS $COLUMN_LABEL_TOTAL
        FROM vedtak_v1 v
        FULL OUTER JOIN vedtak_slettet_v1 vs ON v.id = vs.id
        LEFT JOIN utbetaling_v1 u1 ON v.id = u1.vedtak_id
        LEFT JOIN utbetaling_v1 u2 ON vs.id = u2.vedtak_id
        WHERE
            (v.orgnr = :orgNr OR vs.orgnr = :orgNr)
            -- Bare inkluder resultater fra slettet-vedtak tabellen som ble utbetalt før de ble slettet:
            AND (vs.id IS NULL OR u2.utbetalingsdato IS NOT NULL)
    """

    if (tilDato != null && kravFilter?.equals(KravFilter.EGENDEFINERT) == true) {
        sql = sql.plus("""
            AND (
                (v.id IS NULL OR (v.opprettet >= :fraDato AND v.opprettet <= :tilDato))
                AND (vs.id IS NULL OR (vs.opprettet >= :fraDato AND vs.opprettet <= :tilDato))
            )
        """.trimMargin())
    } else if (tilDato == null && kravFilter?.equals(KravFilter.EGENDEFINERT) == true) {
        sql = sql.plus("""
            AND (
                (v.id IS NULL OR (v.opprettet >= :fraDato))
                AND (vs.id IS NULL OR (vs.opprettet >= :fraDato))
            )
        """.trimIndent())
    } else if (kravFilter?.equals(KravFilter.HITTILAR) == true) {
        sql = sql.plus("""
            AND (
                (v.id IS NULL OR (date_part('year', v.opprettet) = date_part('year', CURRENT_DATE)))
                AND (vs.id IS NULL OR (date_part('year', vs.opprettet) = date_part('year', CURRENT_DATE)))
            )
        """.trimIndent())
    } else if (kravFilter?.equals(KravFilter.SISTE3MND) == true) {
        sql = sql.plus("""
            AND (
                (v.id IS NULL OR (v.opprettet >  CURRENT_DATE - INTERVAL '3 months'))
                AND (vs.id IS NULL OR (vs.opprettet >  CURRENT_DATE - INTERVAL '3 months'))
            )
        """.trimIndent())
    }

    if (!referanseFilter.isNullOrBlank()) {
        sql = sql.plus(
            """
             AND (
                CAST(v.id AS TEXT) LIKE :referanseFilter
                OR v.bestillingsreferanse LIKE :referanseFilter
                OR u1.batch_id LIKE :referanseFilter
                OR u2.batch_id LIKE :referanseFilter
              )
            """.trimIndent()
        )
    }

    //language=PostgreSQL
    return sql.trimIndent()
}
