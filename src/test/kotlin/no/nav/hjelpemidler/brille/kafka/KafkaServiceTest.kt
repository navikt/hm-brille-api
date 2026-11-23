package no.nav.hjelpemidler.brille.kafka

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.KafkaRapid
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.hjelpemidler.brille.sats.Brilleseddel
import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.vedtak.Behandlingsresultat
import no.nav.hjelpemidler.brille.vedtak.KravDto
import no.nav.hjelpemidler.brille.vedtak.KravKilde
import no.nav.hjelpemidler.brille.vedtak.SlettetAvType
import no.nav.hjelpemidler.brille.vedtak.Vedtak
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsgrunnlag
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsgrunnlagDto
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsgrunnlagExtrasDto
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsvurdering
import no.nav.hjelpemidler.nare.regel.Regelevaluering
import no.nav.hjelpemidler.serialization.jackson.jsonMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class KafkaServiceTest {
    private val kafkaRapid = mockk<KafkaRapid>()
    private val service = KafkaService(kafkaRapid)

    @Test
    fun `statistikk beholder feltnavn og verdier gjennom Rapids og Rivers`() {
        val message = slot<String>()
        every { kafkaRapid.publishWithTimeout(capture(message), any()) } returns Unit
        val slettetAvType = SlettetAvType.entries.first()

        service.vedtakSlettet(42, slettetAvType)

        val legacyMessage = JsonMessage(message.captured, MessageProblems(message.captured))
        val event = jsonMapper.readTree(legacyMessage.toJson())
        event["eventName"].asString() shouldBe "hm-bigquery-sink-hendelse"
        event["schemaId"].asString() shouldBe "slettede_vedtak_v1"
        val payload = event["payload"]
        payload.size() shouldBe 3
        payload["vedtak_id"].asLong() shouldBe 42
        payload["slettet_av_type"].asString() shouldBe slettetAvType.name
        assertDoesNotThrow { LocalDateTime.parse(payload["opprettet"].asString()) }
    }

    @Test
    fun `vedtak fattet`() {
        every {
            kafkaRapid.publishWithTimeout(any(), any(), any())
            kafkaRapid.publishWithTimeout(any(), any())
        }

        val sats = SatsType.SATS_1
        val krav = KravDto(
            VilkårsgrunnlagDto(
                orgnr = "067234162",
                butikkId = null,
                fnrBarn = "07083440346",
                brilleseddel = Brilleseddel(
                    høyreSfære = 1.0,
                    høyreSylinder = 1.0,
                    venstreSfære = 1.0,
                    venstreSylinder = 1.0,
                ),
                bestillingsdato = LocalDate.now(),
                brillepris = BigDecimal.ZERO,
                extras = VilkårsgrunnlagExtrasDto("", ""),
            ),
            "test",
            "test",
            "test",
        )
        val vedtak = Vedtak<Vilkårsgrunnlag>(
            fnrBarn = krav.vilkårsgrunnlag.fnrBarn,
            fnrInnsender = "28102525703",
            navnInnsender = "Kronjuvel Sedat",
            orgnr = krav.vilkårsgrunnlag.orgnr,
            butikkId = null,
            bestillingsdato = krav.vilkårsgrunnlag.bestillingsdato,
            brillepris = krav.vilkårsgrunnlag.brillepris,
            bestillingsreferanse = krav.bestillingsreferanse,
            vilkårsvurdering = Vilkårsvurdering(mockk(relaxed = true), Regelevaluering.ja("test")),
            behandlingsresultat = Behandlingsresultat.INNVILGET,
            sats = sats,
            satsBeløp = sats.beløp(LocalDate.now()),
            satsBeskrivelse = sats.beskrivelse,
            beløp = sats.beløp(LocalDate.now()).toBigDecimal(),
            kilde = KravKilde.KRAV_APP,
            avsendersystemOrgNr = krav.vilkårsgrunnlag.orgnr,
        )

        assertDoesNotThrow {
            service.vedtakFattet(
                krav = krav,
                vedtak = vedtak,
                navnBarn = "Test Barn",
            )
        }
    }
}
