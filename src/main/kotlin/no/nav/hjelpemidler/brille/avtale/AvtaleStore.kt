package no.nav.hjelpemidler.brille.avtale

import kotliquery.Session
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.store.Store
import no.nav.hjelpemidler.brille.store.TransactionalStore
import no.nav.hjelpemidler.brille.store.update
import org.intellij.lang.annotations.Language
import java.time.LocalDateTime

enum class AVTALETYPE(val avtaleId: Int) {
    OPPGJORSAVTALE(1),
    UTVIDET_AVTALE(2),
    ;

    companion object {
        fun fromInt(value: Int) = AVTALETYPE.values().first { it.avtaleId == value }
    }
}

enum class BILAGSTYPE(val bilagsdefinisjonId: Int) {
    BILAG_1_PERSONOPPLYSNINGER(1),
    BILAG_2_TEKNISK(2),
    BILAG_3_VARSLING_FEIL(3),
    BILAG_4_ENDRINGSLOGG(4),
    ;

    companion object {
        fun fromInt(value: Int) = AVTALETYPE.values().first { it.avtaleId == value }
    }
}

private val log = KotlinLogging.logger {}

interface AvtaleStore : Store {
    fun lagreAvtale(avtale: Avtale): Avtale
    fun lagreBilag(bilag: Bilag): Bilag
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

data class Bilag(
    val id: Int? = null,
    val orgnr: String,
    val fnrInnsender: String,
    val avtaleId: Int,
    val bilagsdefinisjonId: Int,
    val aktiv: Boolean,
    val opprettet: LocalDateTime = LocalDateTime.now(),
    val oppdatert: LocalDateTime = opprettet,
)

class AvtaleStorePostgres(private val sessionFactory: () -> Session) : AvtaleStore,
    TransactionalStore(sessionFactory) {

    override fun lagreAvtale(avtale: Avtale): Avtale = session {
        @Language("PostgreSQL")
        val sql = """
            INSERT INTO avtale_v1 (orgnr,
                                       fnr_innsender,
                                       aktiv,
                                       avtale_id,
                                       opprettet,
                                       oppdatert)
            VALUES (:orgnr, :fnr_innsender, :aktiv, :avtale_id, :opprettet, :oppdatert)
        """.trimIndent()
        val result = it.update(
            sql,
            mapOf(
                "orgnr" to avtale.orgnr,
                "fnr_innsender" to avtale.fnrInnsender,
                "aktiv" to avtale.aktiv,
                "avtale_id" to avtale.avtaleId,
                "opprettet" to avtale.opprettet,
                "oppdatert" to avtale.oppdatert,
            ),
        )
        result.validate()
        avtale.copy(id = result.generatedId?.toInt())
    }

    override fun lagreBilag(bilag: Bilag): Bilag = session {
        @Language("PostgreSQL")
        val sql = """
            INSERT INTO bilag_v1 (orgnr,
                                       fnr_innsender,
                                       aktiv,
                                       avtale_id,
                                       bilagsdefinisjon_id,
                                       opprettet,
                                       oppdatert)
            VALUES (:orgnr, :fnr_innsender, :aktiv, :avtale_id, :bilagsdefinisjon_id, :opprettet, :oppdatert)
        """.trimIndent()
        val result = it.update(
            sql,
            mapOf(
                "orgnr" to bilag.orgnr,
                "fnr_innsender" to bilag.fnrInnsender,
                "aktiv" to bilag.aktiv,
                "avtale_id" to bilag.avtaleId,
                "bilagsdefinisjon_id" to bilag.bilagsdefinisjonId,
                "opprettet" to bilag.opprettet,
                "oppdatert" to bilag.oppdatert,
            ),
        )
        result.validate()
        bilag.copy(id = result.generatedId?.toInt())
    }
}
