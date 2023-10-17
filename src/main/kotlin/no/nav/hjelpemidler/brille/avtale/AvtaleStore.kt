package no.nav.hjelpemidler.brille.avtale

import kotliquery.Row
import kotliquery.Session
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.store.Store
import no.nav.hjelpemidler.brille.store.TransactionalStore
import no.nav.hjelpemidler.brille.store.query
import no.nav.hjelpemidler.brille.store.update
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
    fun oppdaterBruksvilkår(bruksvilkårGodtatt: BruksvilkårGodtatt)
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
    val epostKontaktperson: String,
    val aktiv: Boolean,
    val bruksvilkårDefinisjonId: Int,
    val opprettet: LocalDateTime = LocalDateTime.now(),
    val oppdatert: LocalDateTime = opprettet,
)

class AvtaleStorePostgres(private val sessionFactory: () -> Session) : AvtaleStore,
    TransactionalStore(sessionFactory) {

    override fun lagreAvtale(avtale: Avtale): Avtale = session {
        @Language("PostgreSQL")
        val sql = """
            INSERT INTO avtale_v1 (    orgnr,
                                       fnr_innsender,
                                       aktiv,
                                       avtale_id,
                                       opprettet,
                                       oppdatert)
            VALUES (:orgnr, :fnr_innsender, :aktiv, :avtale_id, :opprettet, :oppdatert)
            RETURNING id
        """.trimIndent()
        val id = it.query(
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
        avtale.copy(id = id?.toInt())
    }

    override fun godtaBruksvilkår(bruksvilkårGodtatt: BruksvilkårGodtatt): BruksvilkårGodtatt = session {
        @Language("PostgreSQL")
        val sql = """
            INSERT INTO bruksvilkar_v1 (    orgnr,
                                       fnr_innsender,
                                       epost_kontaktperson,
                                       aktiv,
                                       bruksvilkardefinisjon_id,
                                       opprettet,
                                       oppdatert)
            VALUES (:orgnr, :fnr_innsender, :epost_kontaktperson, :aktiv, :bruksvilkardefinisjon_id, :opprettet, :oppdatert)
            RETURNING id
        """.trimIndent()
        val id = it.query(
            sql,
            mapOf(
                "orgnr" to bruksvilkårGodtatt.orgnr,
                "fnr_innsender" to bruksvilkårGodtatt.fnrInnsender,
                "epost_kontaktperson" to bruksvilkårGodtatt.epostKontaktperson,
                "aktiv" to bruksvilkårGodtatt.aktiv,
                "bruksvilkardefinisjon_id" to bruksvilkårGodtatt.bruksvilkårDefinisjonId,
                "opprettet" to bruksvilkårGodtatt.opprettet,
                "oppdatert" to bruksvilkårGodtatt.oppdatert,
            ),
        ) { row: Row ->
            row.long("id")
        }
        bruksvilkårGodtatt.copy(id = id?.toInt())
    }

    override fun henBruksvilkårOrganisasjon(orgnr: String): BruksvilkårGodtatt? = session {
        @Language("PostgreSQL")
        val sql = """
            SELECT b.orgnr, b.epost_kontaktperson, b.aktiv, b, b.opprettet, b.oppdatert, b.fnr_innsender, b.fnr_oppdatert_av, b.bruksvilkardefinisjon_id
            FROM bruksvilkar_v1 b
            WHERE b.orgnr = :orgnr AND b.bruksvilkardefinisjon_id = 1
        """.trimIndent()
        it.query(sql, mapOf("orgnr" to orgnr), ::mapper)
    }

    override fun oppdaterBruksvilkår(bruksvilkårGodtatt: BruksvilkårGodtatt) = session {
        @Language("PostgreSQL")
        val sql = """
            UPDATE bruksvilkar_v1
            SET epost_kontaktperson = :epost_kontaktperson, fnr_oppdatert_av = :fnr_oppdatert_av, oppdatert = :oppdatert
            WHERE orgnr = :orgnr
        """.trimIndent()
        it.update(
            sql,
            mapOf(
                "epost_kontaktperson" to bruksvilkårGodtatt.epostKontaktperson,
                "fnr_oppdatert_av" to bruksvilkårGodtatt.fnrOppdatertAv,
                "oppdatert" to bruksvilkårGodtatt.oppdatert,
                "orgnr" to bruksvilkårGodtatt.orgnr,
            ),
        ).validate()
    }

    private fun mapper(row: Row): BruksvilkårGodtatt = BruksvilkårGodtatt(
        orgnr = row.string("orgnr"),
        epostKontaktperson = row.string("epost_kontaktperson"),
        fnrInnsender = row.string("fnr_innsender"),
        fnrOppdatertAv = row.stringOrNull("fnr_oppdatert_av"),
        aktiv = row.boolean("aktiv"),
        bruksvilkårDefinisjonId = row.int("bruksvilkardefinisjon_id"),
        opprettet = row.localDateTime("opprettet"),
        oppdatert = row.localDateTime("oppdatert"),
    )
}
