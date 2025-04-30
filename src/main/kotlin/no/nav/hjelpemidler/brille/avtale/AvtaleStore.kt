package no.nav.hjelpemidler.brille.avtale

import io.github.oshai.kotlinlogging.KotlinLogging
import kotliquery.Row
import no.nav.hjelpemidler.database.JdbcOperations
import no.nav.hjelpemidler.database.Store
import org.intellij.lang.annotations.Language
import java.time.LocalDateTime

enum class AVTALETYPE(val avtaleId: Int) {
    OPPGJORSAVTALE(1),
    ;

    companion object {
        fun fromInt(value: Int) = entries.first { it.avtaleId == value }
    }
}

enum class BRUKSVILKÅRTYPE(val bruksvilkårId: Int) {
    BRUKSVILKÅR_API(1),
    ;

    companion object {
        fun fromInt(value: Int) = entries.first { it.bruksvilkårId == value }
    }
}

private val log = KotlinLogging.logger {}

interface AvtaleStore : Store {
    fun lagreAvtale(avtale: Avtale): Avtale
    fun godtaBruksvilkår(bruksvilkårGodtatt: BruksvilkårGodtatt): BruksvilkårGodtatt
    fun henBruksvilkårOrganisasjon(orgnr: String): BruksvilkårGodtatt?
    fun deaktiverVirksomhet(orgnr: String)
}

data class Avtale(
    val id: Int? = null,
    val orgnr: String,
    val fnrInnsender: String,
    val aktiv: Boolean,
    val avtaleId: Int,
    val opprettet: LocalDateTime = LocalDateTime.now(),
    val oppdatert: LocalDateTime = opprettet,
)

data class BruksvilkårGodtatt(
    val id: Int? = null,
    val orgnr: String,
    val fnrInnsender: String,
    val fnrOppdatertAv: String? = null,
    val aktiv: Boolean,
    val bruksvilkårDefinisjonId: Int,
    val opprettet: LocalDateTime = LocalDateTime.now(),
    val oppdatert: LocalDateTime = opprettet,
)

class AvtaleStorePostgres(private val tx: JdbcOperations) : AvtaleStore {
    override fun lagreAvtale(avtale: Avtale): Avtale {
        @Language("PostgreSQL")
        val sql = """
            INSERT INTO avtale_v1 (orgnr,
                                   fnr_innsender,
                                   aktiv,
                                   avtale_id,
                                   opprettet,
                                   oppdatert)
            VALUES (:orgnr, :fnr_innsender, :aktiv, :avtale_id, :opprettet, :oppdatert)
            RETURNING id
        """.trimIndent()
        val id = tx.single(
            sql,
            mapOf(
                "orgnr" to avtale.orgnr,
                "fnr_innsender" to avtale.fnrInnsender,
                "aktiv" to avtale.aktiv,
                "avtale_id" to avtale.avtaleId,
                "opprettet" to avtale.opprettet,
                "oppdatert" to avtale.oppdatert,
            ),
        ) { row: Row ->
            row.long("id")
        }
        return avtale.copy(id = id.toInt())
    }

    override fun godtaBruksvilkår(bruksvilkårGodtatt: BruksvilkårGodtatt): BruksvilkårGodtatt {
        @Language("PostgreSQL")
        val sql = """
            INSERT INTO bruksvilkar_v1 (orgnr,
                                        fnr_innsender,
                                        aktiv,
                                        bruksvilkardefinisjon_id,
                                        opprettet,
                                        oppdatert)
            VALUES (:orgnr, :fnr_innsender, :aktiv, :bruksvilkardefinisjon_id, :opprettet, :oppdatert)
            ON CONFLICT DO UPDATE SET oppdatert = NOW() -- "returning" fungerer ikke uten en oppdatering...
            RETURNING id, opprettet, oppdatert
        """.trimIndent()
        return tx.single(
            sql,
            mapOf(
                "orgnr" to bruksvilkårGodtatt.orgnr,
                "fnr_innsender" to bruksvilkårGodtatt.fnrInnsender,
                "aktiv" to bruksvilkårGodtatt.aktiv,
                "bruksvilkardefinisjon_id" to bruksvilkårGodtatt.bruksvilkårDefinisjonId,
                "opprettet" to bruksvilkårGodtatt.opprettet,
                "oppdatert" to bruksvilkårGodtatt.oppdatert,
            ),
        ) { row: Row ->
            bruksvilkårGodtatt.copy(
                id = row.long("id").toInt(),
                opprettet = row.localDateTime("opprettet"),
                oppdatert = row.localDateTime("oppdatert"),
            )
        }
    }

    override fun deaktiverVirksomhet(orgnr: String) {
        @Language("PostgreSQL")
        val sql = """
            UPDATE virksomhet_v1
            SET aktiv = FALSE
            WHERE orgnr = :orgnr
        """.trimIndent()
        tx.update(
            sql,
            mapOf("orgnr" to orgnr),
        ).expect(1)
    }

    override fun henBruksvilkårOrganisasjon(orgnr: String): BruksvilkårGodtatt? {
        @Language("PostgreSQL")
        val sql = """
            SELECT b.id, b.orgnr, b.aktiv, b, b.opprettet, b.oppdatert, b.fnr_innsender, b.fnr_oppdatert_av, b.bruksvilkardefinisjon_id
            FROM bruksvilkar_v1 b
            WHERE b.orgnr = :orgnr AND b.bruksvilkardefinisjon_id = 1
        """.trimIndent()
        return tx.singleOrNull(sql, mapOf("orgnr" to orgnr), ::mapper)
    }

    private fun mapper(row: Row): BruksvilkårGodtatt = BruksvilkårGodtatt(
        id = row.int("id"),
        orgnr = row.string("orgnr"),
        fnrInnsender = row.string("fnr_innsender"),
        fnrOppdatertAv = row.stringOrNull("fnr_oppdatert_av"),
        aktiv = row.boolean("aktiv"),
        bruksvilkårDefinisjonId = row.int("bruksvilkardefinisjon_id"),
        opprettet = row.localDateTime("opprettet"),
        oppdatert = row.localDateTime("oppdatert"),
    )
}
