package no.nav.hjelpemidler.brille.utbetaling

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.DatabaseTransactionContext
import no.nav.hjelpemidler.brille.db.DefaultDatabaseTransactionContext
import no.nav.hjelpemidler.brille.db.TestDatabaseContext
import no.nav.hjelpemidler.brille.db.transaction
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.vedtak.Behandlingsresultat
import no.nav.hjelpemidler.brille.vedtak.KravKilde
import no.nav.hjelpemidler.brille.vedtak.Vedtak
import no.nav.hjelpemidler.brille.vedtak.VedtakDto
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsvurdering
import no.nav.hjelpemidler.brille.virksomhet.Virksomhet
import no.nav.hjelpemidler.database.JdbcOperations
import no.nav.hjelpemidler.nare.regel.Regelevaluering
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource
import no.nav.hjelpemidler.database.transaction as jdbcTransaction

class UtbetalingServicePostgresTest {
    @Test
    fun `ruller tilbake fjerning fra kø når lagring av utbetaling feiler og tillater retry`() = runTest {
        val testVedtak = testVedtak()
        val vedtak =
            transaction(TestDatabaseContext) { ctx ->
                ctx.virksomhetStore.lagreVirksomhet(
                    Virksomhet(
                        orgnr = testVedtak.orgnr,
                        kontonr = "00000000000",
                        fnrInnsender = testVedtak.fnrInnsender,
                        navnInnsender = testVedtak.navnInnsender,
                        aktiv = true,
                    ),
                )
                ctx.vedtakStore.lagreVedtak(testVedtak).also {
                    ctx.vedtakStore.lagreVedtakIKø(it.id, it.opprettet)
                }
            }
        val failingDatabase = FailOnceOnSavingUtbetalingDatabaseContext(
            dataSource = TestDatabaseContext.dataSource,
            failNextSave = AtomicBoolean(true),
        )
        val service = UtbetalingService(failingDatabase, mockk<KafkaService>(relaxed = true))

        try {
            shouldThrow<IllegalStateException> { service.opprettNyUtbetaling(vedtak) }
            countRows("vedtak_ko_v1", "id", vedtak.id) shouldBe 1
            countRows("utbetaling_v1", "vedtak_id", vedtak.id) shouldBe 0

            val retry = service.opprettNyUtbetaling(vedtak)
            retry.status shouldBe UtbetalingStatus.NY
            countRows("vedtak_ko_v1", "id", vedtak.id) shouldBe 0
            countRows("utbetaling_v1", "vedtak_id", vedtak.id) shouldBe 1
        } finally {
            cleanup(vedtakIds = listOf(vedtak.id), orgnrs = listOf(vedtak.orgnr))
        }
    }

    @Test
    fun `ruller tilbake batch og statusendringer ved Kafka-feil og retry sender samme utbetalinger`() = runTest {
        val batchId = "regresjon-${UUID.randomUUID()}"
        val orgnr = uniqueOrgnr()
        val vedtakIds = listOf(uniqueId(), uniqueId())
        val utbetalinger =
            transaction(TestDatabaseContext) { ctx ->
                vedtakIds.mapIndexed { index, vedtakId ->
                    ctx.utbetalingStore.lagreUtbetaling(testUtbetaling(vedtakId, index, batchId, orgnr))
                }
            }
        val failKafkaOnce = AtomicBoolean(true)
        val kafkaService = mockk<KafkaService>()
        every {
            kafkaService.produceEvent<Map<String, Any>>(null, any<Map<String, Any>>())
        } answers {
            if (failKafkaOnce.compareAndSet(true, false)) {
                throw IllegalStateException("syntetisk Kafka-feil")
            }
        }
        val service = UtbetalingService(TestDatabaseContext, kafkaService)
        val batch = UtbetalingsbatchDTO(
            utbetalinger = utbetalinger,
            orgNr = utbetalinger.first().vedtak.orgnr,
            batchId = batchId,
        )

        try {
            shouldThrow<IllegalStateException> {
                service.sendBatchTilUtbetaling(batch, "syntetisk-tss")
            }

            transaction(TestDatabaseContext) { ctx ->
                ctx.utbetalingStore.hentUtbetalingsbatch(batchId) shouldBe null
                ctx.utbetalingStore.hentUtbetalingerMedBatchId(batchId).map { it.status } shouldBe
                    listOf(UtbetalingStatus.NY, UtbetalingStatus.NY)
            }

            service.sendBatchTilUtbetaling(batch, "syntetisk-tss")

            transaction(TestDatabaseContext) { ctx ->
                val persisted = ctx.utbetalingStore.hentUtbetalingerMedBatchId(batchId)
                persisted.size shouldBe 2
                persisted.map { it.vedtakId }.toSet() shouldBe vedtakIds.toSet()
                persisted.map { it.status }.toSet() shouldBe setOf(UtbetalingStatus.TIL_UTBETALING)
                ctx.utbetalingStore.hentUtbetalingsbatch(batchId)?.antallUtbetalinger shouldBe 2
            }
            vedtakIds.forEach { vedtakId ->
                countRows("utbetaling_v1", "vedtak_id", vedtakId) shouldBe 1
            }
            countRows("utbetalingsbatch_v1", "batch_id", batchId) shouldBe 1
        } finally {
            cleanup(vedtakIds = vedtakIds, batchIds = listOf(batchId))
        }
    }

    @Test
    fun `lagrer utbetaltstatus og utbetalingsdato`() = runTest {
        val vedtakId = uniqueId()
        val batchId = "regresjon-${UUID.randomUUID()}"
        transaction(TestDatabaseContext) { ctx ->
            ctx.utbetalingStore.lagreUtbetaling(
                testUtbetaling(vedtakId, 0, batchId, uniqueOrgnr()).copy(status = UtbetalingStatus.TIL_UTBETALING),
            )
        }
        val service = UtbetalingService(TestDatabaseContext, mockk<KafkaService>(relaxed = true))

        try {
            val tilUtbetaling = transaction(TestDatabaseContext) { ctx ->
                ctx.utbetalingStore.hentUtbetalingForVedtak(vedtakId)
            }.shouldNotBeNull()
            service.settTilUtbetalt(tilUtbetaling)

            val persisted = transaction(TestDatabaseContext) { ctx ->
                ctx.utbetalingStore.hentUtbetalingForVedtak(vedtakId)
            }.shouldNotBeNull()
            persisted.status shouldBe UtbetalingStatus.UTBETALT
            persisted.utbetalingsdato shouldBe LocalDate.now()

            shouldThrow<UtbetalingsException> {
                service.settTilUtbetalt(tilUtbetaling.copy(status = UtbetalingStatus.NY))
            }
        } finally {
            cleanup(vedtakIds = listOf(vedtakId), batchIds = listOf(batchId))
        }
    }

    private fun testVedtak(): Vedtak<String> {
        val sats = SatsType.SATS_1
        return Vedtak(
            fnrBarn = "00000000001",
            fnrInnsender = "00000000002",
            navnInnsender = "Test Innsender",
            orgnr = uniqueOrgnr(),
            butikkId = null,
            bestillingsdato = LocalDate.of(2024, 1, 2),
            brillepris = BigDecimal.ONE,
            bestillingsreferanse = "regresjon-${UUID.randomUUID().toString().take(8)}",
            vilkårsvurdering = Vilkårsvurdering("syntetisk test", Regelevaluering.ja("test")),
            behandlingsresultat = Behandlingsresultat.INNVILGET,
            sats = sats,
            satsBeløp = sats.beløp(LocalDate.of(2024, 1, 2)),
            satsBeskrivelse = sats.beskrivelse,
            beløp = BigDecimal.ONE,
            opprettet = LocalDateTime.of(2024, 1, 2, 0, 0),
            kilde = KravKilde.KRAV_APP,
        )
    }

    private fun testUtbetaling(vedtakId: Long, index: Int, batchId: String, orgnr: String): Utbetaling {
        val vedtak = VedtakDto(
            id = vedtakId,
            orgnr = orgnr,
            bestillingsdato = LocalDate.of(2024, 1, 2),
            brillepris = BigDecimal.ONE,
            bestillingsreferanse = "regresjon-$index",
            behandlingsresultat = Behandlingsresultat.INNVILGET,
            sats = SatsType.SATS_1,
            satsBeløp = 1,
            satsBeskrivelse = "syntetisk test",
            beløp = BigDecimal.ONE,
            opprettet = LocalDateTime.of(2024, 1, 2, 0, 0),
        )
        return Utbetaling(
            vedtakId = vedtakId,
            vedtak = vedtak,
            referanse = "regresjon-$index",
            utbetalingsdato = null,
            batchDato = LocalDate.of(2024, 1, 2),
            batchId = batchId,
        )
    }

    private fun uniqueId(): Long = (UUID.randomUUID().leastSignificantBits and Long.MAX_VALUE).coerceAtLeast(1)

    private fun uniqueOrgnr(): String = "9" + UUID.randomUUID().toString().filter(Char::isDigit).take(8)

    private suspend fun countRows(table: String, column: String, value: Any): Long =
        jdbcTransaction(TestDatabaseContext.dataSource) { tx ->
            tx.single("SELECT COUNT(*) AS count FROM $table WHERE $column = :value", mapOf("value" to value)) { row ->
                row.long("count")
            }
        }

    private suspend fun cleanup(
        vedtakIds: List<Long>,
        batchIds: List<String> = emptyList(),
        orgnrs: List<String> = emptyList(),
    ) {
        jdbcTransaction(TestDatabaseContext.dataSource) { tx ->
            batchIds.forEach { batchId ->
                tx.update("DELETE FROM utbetalingsbatch_v1 WHERE batch_id = :batch_id", mapOf("batch_id" to batchId))
            }
            vedtakIds.forEach { vedtakId ->
                tx.update("DELETE FROM utbetaling_v1 WHERE vedtak_id = :vedtak_id", mapOf("vedtak_id" to vedtakId))
                tx.update("DELETE FROM vedtak_ko_v1 WHERE id = :id", mapOf("id" to vedtakId))
                tx.update("DELETE FROM vedtak_v1 WHERE id = :id", mapOf("id" to vedtakId))
            }
            orgnrs.forEach { orgnr ->
                tx.update("DELETE FROM virksomhet_v1 WHERE orgnr = :orgnr", mapOf("orgnr" to orgnr))
            }
        }
    }

    private class FailOnceOnSavingUtbetalingDatabaseContext(
        override val dataSource: DataSource,
        private val failNextSave: AtomicBoolean,
    ) : DatabaseContext() {
        override fun databaseTransactionContext(tx: JdbcOperations): DatabaseTransactionContext {
            val delegate = DefaultDatabaseTransactionContext(tx)
            return object : DatabaseTransactionContext by delegate {
                override val utbetalingStore: UtbetalingStore =
                    object : UtbetalingStore by delegate.utbetalingStore {
                        override fun lagreUtbetaling(utbetaling: Utbetaling): Utbetaling {
                            if (failNextSave.compareAndSet(true, false)) {
                                throw IllegalStateException("syntetisk lagreUtbetaling-feil")
                            }
                            return delegate.utbetalingStore.lagreUtbetaling(utbetaling)
                        }
                    }
            }
        }

        override fun close() {
            // TestDatabaseContext owns the shared datasource.
        }
    }
}
