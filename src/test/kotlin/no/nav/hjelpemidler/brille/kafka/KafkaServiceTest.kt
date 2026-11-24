package no.nav.hjelpemidler.brille.kafka

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.KafkaRapid
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.hjelpemidler.brille.db.MockDatabaseContext
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapResultat
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapResultatResultat
import no.nav.hjelpemidler.brille.pdl.PdlOppslagPerson
import no.nav.hjelpemidler.brille.pdl.generated.hentperson.Adressebeskyttelse
import no.nav.hjelpemidler.brille.pdl.generated.hentperson.Foedselsdato
import no.nav.hjelpemidler.brille.pdl.generated.hentperson.Navn
import no.nav.hjelpemidler.brille.pdl.generated.hentperson.Person
import no.nav.hjelpemidler.brille.sats.Brilleseddel
import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.utbetaling.Utbetaling
import no.nav.hjelpemidler.brille.utbetaling.UtbetalingService
import no.nav.hjelpemidler.brille.utbetaling.UtbetalingsbatchDTO
import no.nav.hjelpemidler.brille.vedtak.Behandlingsresultat
import no.nav.hjelpemidler.brille.vedtak.KravDto
import no.nav.hjelpemidler.brille.vedtak.KravKilde
import no.nav.hjelpemidler.brille.vedtak.SlettetAvType
import no.nav.hjelpemidler.brille.vedtak.Vedtak
import no.nav.hjelpemidler.brille.vedtak.VedtakDto
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsgrunnlag
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsgrunnlagDto
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsgrunnlagExtrasDto
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsvurdering
import no.nav.hjelpemidler.nare.regel.Regelevaluering
import no.nav.hjelpemidler.serialization.jackson.jsonMapper
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class KafkaServiceTest {
    private val kafkaRapid = mockk<KafkaRapid>()
    private val publishedEvents = mutableListOf<PublishedEvent>()
    private val service = KafkaService(kafkaRapid)

    init {
        every {
            kafkaRapid.publishWithTimeout(any(), any(), any())
        } answers {
            publishedEvents += PublishedEvent(firstArg(), secondArg())
            Unit
        }
        every {
            kafkaRapid.publishWithTimeout(any(), any())
        } answers {
            publishedEvents += PublishedEvent(null, firstArg())
            Unit
        }
    }

    @Test
    fun `statistikk beholder feltnavn og verdier gjennom Rapids og Rivers`() {
        val slettetAvType = SlettetAvType.entries.first()

        service.vedtakSlettet(42, slettetAvType)

        val event = parseLegacy(publishedEvents.single().message)
        event["eventName"].asString() shouldBe "hm-bigquery-sink-hendelse"
        event["schemaId"].asString() shouldBe "slettede_vedtak_v1"
        val payload = event["payload"]
        payload.size() shouldBe 3
        payload["vedtak_id"].asLong() shouldBe 42
        payload["slettet_av_type"].asString() shouldBe slettetAvType.name
        LocalDateTime.parse(payload["opprettet"].asString())
    }

    @Test
    fun `vedtak fattet publiserer journalføring og statistikk med kontraktsfeltene`() {
        val (krav, vedtak) = vedtakFixture()

        service.vedtakFattet(krav, vedtak, "Test Barn")
        publishedEvents.size shouldBe 2

        val journalføringMelding = publishedEvents.single {
            jsonMapper.readTree(it.message)["eventName"].asString() == "hm-barnebrillevedtak-opprettet"
        }
        val journalføring = parseLegacy(journalføringMelding.message)
        journalføringMelding.key shouldBe "00000000001"
        journalføring["eventName"].asString() shouldBe "hm-barnebrillevedtak-opprettet"
        journalføring["sakId"].asString() shouldBe vedtak.id.toString()
        journalføring["fnr"].asString() shouldBe "00000000001"
        journalføring["brukersNavn"].asString() shouldBe "Test Barn"
        journalføring["bestillingsdato"].asString() shouldBe "2024-01-01"
        journalføring["beløp"].isNumber() shouldBe true
        journalføring["beløp"].decimalValue() shouldBe BigDecimal("725.45")
        journalføring["satsBeløp"].isNumber() shouldBe true
        journalføring["satsBeløp"].asInt() shouldBe 800
        journalføring["kilde"].asString() shouldBe KravKilde.KRAV_APP.name
        LocalDateTime.parse(journalføring["opprettetDato"].asString())

        val statistikkMelding = publishedEvents.single {
            jsonMapper.readTree(it.message)["schemaId"]?.asString() == "vedtak_v1"
        }
        val statistikk = parseLegacy(statistikkMelding.message)
        statistikkMelding.key shouldBe "00000000001"
        statistikk["eventName"].asString() shouldBe "hm-bigquery-sink-hendelse"
        statistikk["schemaId"].asString() shouldBe "vedtak_v1"
        val payload = statistikk["payload"]
        payload["vedtak_id"].asLong() shouldBe vedtak.id
        payload["barnets_alder"].asInt() shouldBe 7
        payload["bestillingsdato"].asString() shouldBe "2024-01-01"
        payload["belop"].isNumber() shouldBe true
        payload["belop"].decimalValue() shouldBe BigDecimal("725.45")
        payload["sats_belop"].isNumber() shouldBe true
        payload["sats_belop"].asInt() shouldBe 800
        payload["brillepris"].decimalValue() shouldBe BigDecimal("900.12")
        payload["kilde"].asString() shouldBe KravKilde.KRAV_APP.name
    }

    @Test
    fun `utbetalingsbatch publiseres som JSON-objekt med linje-ID-er også ved rekjøring`() = runTest {
        val orgNr = "123456789"
        val batchId = "123456789-20240101"
        val batch = UtbetalingsbatchDTO(
            utbetalinger = listOf(
                utbetalingFixture(vedtakId = 101L, orgNr = orgNr, batchId = batchId, beløp = "123.45"),
                utbetalingFixture(vedtakId = 102L, orgNr = orgNr, batchId = batchId, beløp = "678.90"),
            ),
        )
        val forventetSum = batch.utbetalinger.sumOf { it.vedtak.beløp }
        val utbetalingService = UtbetalingService(MockDatabaseContext(), service)
        val datoFørKall = LocalDate.now()

        utbetalingService.sendBatchTilUtbetaling(batch, "TSS-TEST")
        utbetalingService.rekjorBatchTilUtbetaling(batch, "TSS-TEST")

        val datoEtterKall = LocalDate.now()
        val gyldigeDatoer = setOf(datoFørKall, datoEtterKall)
        publishedEvents.size shouldBe 2
        val meldinger = publishedEvents.toList()
        meldinger.forEach { published ->
            published.key shouldBe null
            val melding = jsonMapper.readTree(published.message)
            melding.isObject() shouldBe true
            melding["eventName"].asString() shouldBe "hm-barnebrille-utbetaling"
            melding["batchId"].asString() shouldBe batchId
            melding["orgNr"].asString() shouldBe orgNr
            melding["tssIdent"].asString() shouldBe "TSS-TEST"
            val utbetaling = melding["Utbetaling"]
            utbetaling.isObject() shouldBe true
            utbetaling["mottaker"].asString() shouldBe orgNr
            utbetaling["fagområde"].asString() shouldBe "BARNBRIL"
            val linjer = utbetaling["linjer"].toList()
            linjer.size shouldBe 2
            linjer.map { it["delytelseId"].asLong() } shouldBe listOf(101L, 102L)
            linjer.map { it["sats"].decimalValue().setScale(2) } shouldBe listOf(BigDecimal("123.45"), BigDecimal("678.90"))
            linjer.forEach { linje ->
                val fom = LocalDate.parse(linje["fom"].asString())
                val tom = LocalDate.parse(linje["tom"].asString())
                fom shouldBe tom
                (fom in gyldigeDatoer) shouldBe true
            }
            linjer.sumOf { it["sats"].decimalValue() } shouldBe forventetSum
        }
        forventetSum shouldBe BigDecimal("802.35")
    }

    private fun vedtakFixture(): Pair<KravDto, Vedtak<Vilkårsgrunnlag>> {
        val dato = LocalDate.of(2024, 1, 1)
        val brilleseddel = Brilleseddel(
            høyreSfære = 1.0,
            høyreSylinder = 1.0,
            venstreSfære = 1.0,
            venstreSylinder = 1.0,
        )
        val person = Person(
            navn = listOf(Navn(fornavn = "Test", etternavn = "Barn")),
            foedselsdato = listOf(Foedselsdato(foedselsdato = LocalDate.of(2016, 3, 5))),
            adressebeskyttelse = emptyList<Adressebeskyttelse>(),
        )
        val grunnlag = Vilkårsgrunnlag(
            vedtakBarn = emptyList(),
            pdlOppslagBarn = PdlOppslagPerson(person, jsonMapper.nullNode()),
            medlemskapResultat = MedlemskapResultat(MedlemskapResultatResultat.JA, emptyList()),
            brilleseddel = brilleseddel,
            bestillingsdato = dato,
            mottaksdato = dato,
            dagensDato = dato,
        )
        val krav = KravDto(
            vilkårsgrunnlag = VilkårsgrunnlagDto(
                orgnr = "123456789",
                fnrBarn = "00000000001",
                brilleseddel = brilleseddel,
                bestillingsdato = dato,
                brillepris = BigDecimal("900.12"),
                extras = VilkårsgrunnlagExtrasDto("Test Optiker", "test-referanse"),
            ),
            bestillingsreferanse = "test-referanse",
            orgAdresse = "Testgata 1",
            orgNavn = "Test Optiker",
        )
        val sats = SatsType.SATS_1
        val vedtak = Vedtak(
            id = 321L,
            fnrBarn = "00000000001",
            fnrInnsender = "00000000002",
            navnInnsender = "Test Innsender",
            orgnr = "123456789",
            butikkId = null,
            bestillingsdato = dato,
            brillepris = BigDecimal("900.12"),
            bestillingsreferanse = "test-referanse",
            vilkårsvurdering = Vilkårsvurdering(grunnlag, Regelevaluering.ja("test fixture")),
            behandlingsresultat = Behandlingsresultat.INNVILGET,
            sats = sats,
            satsBeløp = sats.beløp(dato),
            satsBeskrivelse = sats.beskrivelse,
            beløp = BigDecimal("725.45"),
            opprettet = LocalDateTime.of(2024, 2, 3, 4, 5, 6),
            kilde = KravKilde.KRAV_APP,
        )
        return krav to vedtak
    }

    private fun utbetalingFixture(vedtakId: Long, orgNr: String, batchId: String, beløp: String) = Utbetaling(
        vedtakId = vedtakId,
        vedtak = VedtakDto(
            id = vedtakId,
            orgnr = orgNr,
            bestillingsdato = LocalDate.of(2024, 1, 1),
            brillepris = BigDecimal(beløp),
            bestillingsreferanse = "referanse-$vedtakId",
            behandlingsresultat = Behandlingsresultat.INNVILGET,
            sats = SatsType.SATS_1,
            satsBeløp = 800,
            satsBeskrivelse = SatsType.SATS_1.beskrivelse,
            beløp = BigDecimal(beløp),
            opprettet = LocalDateTime.of(2024, 1, 1, 12, 0),
        ),
        referanse = "referanse-$vedtakId",
        utbetalingsdato = null,
        batchDato = LocalDate.of(2024, 1, 1),
        batchId = batchId,
    )

    private fun parseLegacy(message: String) =
        jsonMapper.readTree(JsonMessage(message, MessageProblems(message)).toJson())

    private data class PublishedEvent(val key: String?, val message: String)
}
