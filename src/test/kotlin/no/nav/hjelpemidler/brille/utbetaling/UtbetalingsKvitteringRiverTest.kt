package no.nav.hjelpemidler.brille.utbetaling

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.hjelpemidler.brille.db.MockDatabaseContext
import no.nav.hjelpemidler.brille.internal.MetricsConfig
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.vedtak.Behandlingsresultat
import no.nav.hjelpemidler.brille.vedtak.VedtakDto
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class UtbetalingsKvitteringRiverTest {
    @ParameterizedTest
    @ValueSource(strings = ["AKSEPTERT", "AKSEPTERT_MED_FEIL"])
    fun `oppdaterer bare ventende utbetaling for godkjent kvitteringsstatus`(kvitteringsstatus: String) {
        val fixture = fixture()
        try {
            fixture.rapid.sendTestMessage(kvitteringsmelding(fixture.batchId, kvitteringsstatus))
            fixture.utbetalinger.map { it.status } shouldBe listOf(
                UtbetalingStatus.UTBETALT,
                UtbetalingStatus.NY,
                UtbetalingStatus.REKJOR,
                UtbetalingStatus.UTBETALT,
            )
            verify(exactly = 1) {
                fixture.databaseContext.utbetalingStore.oppdaterStatusOgUtbetalingsdato(any())
            }
            fixture.registry.get("utbetaling_kvittering_mottat")
                .tag("status", kvitteringsstatus)
                .counter()
                .count() shouldBe 1.0
        } finally {
            fixture.registry.close()
        }
    }

    @Test
    fun `gjentatt kvittering oppdaterer ikke allerede utbetalt rad`() {
        val fixture = fixture()
        try {
            repeat(2) {
                fixture.rapid.sendTestMessage(kvitteringsmelding(fixture.batchId, "AKSEPTERT"))
            }

            fixture.utbetalinger.first().status shouldBe UtbetalingStatus.UTBETALT
            verify(exactly = 1) {
                fixture.databaseContext.utbetalingStore.oppdaterStatusOgUtbetalingsdato(any())
            }
        } finally {
            fixture.registry.close()
        }
    }

    @Test
    fun `ukjent batch og ugyldige kvitteringer lar utbetaling stå urørt`() {
        val fixture = fixture()
        try {
            fixture.rapid.sendTestMessage(kvitteringsmelding("ukjent-batch", "AKSEPTERT"))
            fixture.rapid.sendTestMessage(
                kvitteringsmelding(fixture.batchId, "AKSEPTERT", eventName = "annet-event"),
            )
            shouldThrow<IllegalStateException> {
                fixture.rapid.sendTestMessage(
                    """
                    {
                      "eventName": "hm-oppdragHarUtbetaltKrav",
                      "batchId": "${fixture.batchId}"
                    }
                    """.trimIndent(),
                )
            }

            fixture.utbetalinger.first().status shouldBe UtbetalingStatus.TIL_UTBETALING
            verify(exactly = 0) {
                fixture.databaseContext.utbetalingStore.oppdaterStatusOgUtbetalingsdato(any())
            }
        } finally {
            fixture.registry.close()
        }
    }

    private fun fixture(): RiverFixture {
        val batchId = "regresjon-kvittering"
        val utbetalinger = mutableListOf(
            utbetaling(1, batchId, UtbetalingStatus.TIL_UTBETALING),
            utbetaling(2, batchId, UtbetalingStatus.NY),
            utbetaling(3, batchId, UtbetalingStatus.REKJOR),
            utbetaling(4, batchId, UtbetalingStatus.UTBETALT),
        )
        val databaseContext = MockDatabaseContext()
        val service = UtbetalingService(databaseContext, mockk<KafkaService>(relaxed = true))
        every {
            databaseContext.utbetalingStore.hentUtbetalingerMedBatchId(any())
        } answers {
            utbetalinger.filter { it.batchId == firstArg() }
        }
        every {
            databaseContext.utbetalingStore.oppdaterStatusOgUtbetalingsdato(any())
        } answers {
            val oppdatert = firstArg<Utbetaling>()
            val index = utbetalinger.indexOfFirst { it.id == oppdatert.id }
            utbetalinger[index] = oppdatert
            oppdatert
        }

        val rapid = TestRapid()
        val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        UtbetalingsKvitteringRiver(
            rapid,
            service,
            MetricsConfig(registry = registry, meterbinders = emptyList()),
        )
        return RiverFixture(batchId, utbetalinger, databaseContext, rapid, registry)
    }

    private data class RiverFixture(
        val batchId: String,
        val utbetalinger: MutableList<Utbetaling>,
        val databaseContext: MockDatabaseContext,
        val rapid: TestRapid,
        val registry: PrometheusMeterRegistry,
    )

    private fun utbetaling(id: Long, batchId: String, status: UtbetalingStatus) = Utbetaling(
        id = id,
        vedtakId = id,
        vedtak = VedtakDto(
            id = id,
            orgnr = "000000001",
            bestillingsdato = LocalDate.of(2020, 1, 1),
            brillepris = BigDecimal.ONE,
            bestillingsreferanse = "test-$id",
            behandlingsresultat = Behandlingsresultat.INNVILGET,
            sats = SatsType.SATS_1,
            satsBeløp = 1,
            satsBeskrivelse = "syntetisk test",
            beløp = BigDecimal.ONE,
            opprettet = LocalDateTime.of(2020, 1, 1, 0, 0),
        ),
        referanse = "test-$id",
        utbetalingsdato = null,
        status = status,
        batchDato = LocalDate.of(2020, 1, 1),
        batchId = batchId,
    )

    private fun kvitteringsmelding(
        batchId: String,
        status: String,
        eventName: String = "hm-oppdragHarUtbetaltKrav",
    ) = """
        {
          "eventName": "$eventName",
          "eventId": "test-event-id",
          "opprettet": "2020-01-01T00:00:00",
          "tssId": "test-tss",
          "avstemmingsnøkkel": "test-avstemming",
          "status": "$status",
          "feilkode_oppdrag": "",
          "beskrivelse": "syntetisk test",
          "originalXml": "",
          "batchId": "$batchId"
        }
    """.trimIndent()
}
