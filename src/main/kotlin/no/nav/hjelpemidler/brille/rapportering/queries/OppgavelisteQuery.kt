package no.nav.hjelpemidler.brille.rapportering.queries

import no.nav.hjelpemidler.brille.rapportering.KravFilter
import no.nav.hjelpemidler.brille.store.COLUMN_LABEL_TOTAL
import java.time.LocalDate

fun kravlinjeQuery(
    kravFilter: KravFilter? = null,
    tilDato: LocalDate? = null
): String {
    var sql = """
                SELECT id, bestillingsdato, behandlingsresultat, opprettet, belop, bestillingsreferanse, count(*) over() AS $COLUMN_LABEL_TOTAL
            FROM vedtak_v1
            WHERE orgnr = :orgNr 
                       """
    if (tilDato != null && kravFilter?.equals(KravFilter.EGENDEFINERT) == true) {
        sql = sql.plus(" AND opprettet >= :fraDato AND opprettet <= :tilDato ")
    } else if (tilDato == null && kravFilter?.equals(KravFilter.EGENDEFINERT) == true) {
        sql = sql.plus(" AND opprettet >= :fraDato ")
    } else if (kravFilter?.equals(KravFilter.HITTILAR) == true) {
        sql = sql.plus(" AND date_part('year', opprettet) = date_part('year', CURRENT_DATE)")
    } else if (kravFilter?.equals(KravFilter.SISTE3MND) == true) {
        sql = sql.plus(" AND opprettet >  CURRENT_DATE - INTERVAL '3 months'")
    }

    //language=PostgreSQL
    return sql.trimIndent()
}
