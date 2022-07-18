package no.nav.hjelpemidler.brille.avtale

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import no.nav.hjelpemidler.brille.altinn.AltinnService
import no.nav.hjelpemidler.brille.altinn.Avgiver
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.test.TestRouting
import no.nav.hjelpemidler.brille.virksomhet.Virksomhet
import no.nav.hjelpemidler.brille.virksomhet.VirksomhetStore
import kotlin.test.BeforeTest
import kotlin.test.Test

internal class AvtaleApiTest {
    private val virksomhetStore = mockk<VirksomhetStore>()
    private val altinnService = mockk<AltinnService>()
    private val kafkaService = mockk<KafkaService>()

    private val routing = TestRouting {
        authenticate("test") {
            avtaleApi(AvtaleService(virksomhetStore, altinnService, kafkaService))
        }
    }

    private val fnrInnsender = routing.principal.getFnr()
    private val avgiver = Avgiver(
        navn = "Brillesjø AS",
        orgnr = "456313701",
        parentOrgnr = null,
        hovedadministrator = true
    )
    private val virksomhet = Virksomhet(
        orgnr = avgiver.orgnr,
        kontonr = "94154381079",
        epost = "test@test",
        fnrInnsender = fnrInnsender,
        navnInnsender = "Dag Ledersen",
        aktiv = true
    )
    private val opprettAvtale = OpprettAvtale(
        orgnr = avgiver.orgnr,
        navn = avgiver.navn,
        kontonr = virksomhet.kontonr
    )
    private val redigerAvtale = RedigerAvtale(
        navn = avgiver.navn,
        kontonr = virksomhet.kontonr
    )

    @BeforeTest
    internal fun setUp() {
        every {
            virksomhetStore.hentVirksomheterForInnsender(fnrInnsender)
        } returns listOf(virksomhet)
        every {
            virksomhetStore.hentVirksomhetForOrganisasjon(virksomhet.orgnr)
        } returns virksomhet
        coEvery {
            altinnService.hentAvgivereHovedadministrator(fnrInnsender)
        } returns listOf(avgiver)
        every {
            virksomhetStore.lagreVirksomhet(any())
        } returnsArgument 0
        every {
            virksomhetStore.oppdaterKontonummer(avgiver.orgnr, redigerAvtale.kontonr)
        } returns Unit
        every {
            kafkaService.avtaleOpprettet(opprettAvtale.orgnr, opprettAvtale.navn, any())
        } returns Unit
    }

    @Test
    internal fun `henter virksomheter med avtale`() = routing.test {
        val response = client.get("/avtale/virksomheter")
        response.status shouldBe HttpStatusCode.OK
    }

    @Test
    internal fun `henter virksomhet med avtale`() = routing.test {
        val response = client.get("/avtale/virksomheter/${virksomhet.orgnr}")
        response.status shouldBe HttpStatusCode.OK
    }

    @Test
    internal fun `oppretter ny avtale`() = routing.test {
        erHovedadministratorFor(opprettAvtale.orgnr)
        val response = client.post("/avtale/virksomheter") {
            setBody(opprettAvtale)
        }
        response.status shouldBe HttpStatusCode.Created
    }

    @Test
    internal fun `oppretter ny avtale uten tilgang`() = routing.test {
        erIkkeHovedadministratorFor(opprettAvtale.orgnr)
        val response = client.post("/avtale/virksomheter") {
            setBody(opprettAvtale)
        }
        response.status shouldBe HttpStatusCode.Forbidden
    }

    @Test
    internal fun `redigerer avtale`() = routing.test {
        erHovedadministratorFor(avgiver.orgnr)
        val response = client.put("/avtale/virksomheter/${avgiver.orgnr}") {
            setBody(redigerAvtale)
        }
        response.status shouldBe HttpStatusCode.OK
    }

    @Test
    internal fun `redigerer avtale uten tilgang`() = routing.test {
        erIkkeHovedadministratorFor(avgiver.orgnr)
        val response = client.put("/avtale/virksomheter/${avgiver.orgnr}") {
            setBody(redigerAvtale)
        }
        response.status shouldBe HttpStatusCode.Forbidden
    }

    private fun erHovedadministratorFor(orgnr: String) = coEvery {
        altinnService.erHovedadministratorFor(fnrInnsender, orgnr)
    } returns true

    private fun erIkkeHovedadministratorFor(orgnr: String) = coEvery {
        altinnService.erHovedadministratorFor(fnrInnsender, orgnr)
    } returns false
}
