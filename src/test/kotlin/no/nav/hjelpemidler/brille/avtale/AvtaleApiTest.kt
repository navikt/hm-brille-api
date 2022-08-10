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
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretService
import no.nav.hjelpemidler.brille.enhetsregisteret.Næringskode
import no.nav.hjelpemidler.brille.enhetsregisteret.Organisasjonsenhet
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.test.TestRouting
import no.nav.hjelpemidler.brille.virksomhet.Virksomhet
import no.nav.hjelpemidler.brille.virksomhet.VirksomhetStore
import kotlin.test.BeforeTest
import kotlin.test.Test

internal class AvtaleApiTest {
    private val virksomhetStore = mockk<VirksomhetStore>()
    private val altinnService = mockk<AltinnService>()
    private val enhetsregisteretService = mockk<EnhetsregisteretService>()
    private val kafkaService = mockk<KafkaService>()

    private val routing = TestRouting {
        authenticate("test") {
            avtaleApi(AvtaleService(virksomhetStore, altinnService, enhetsregisteretService, kafkaService))
        }
    }

    private val fnrInnsender = routing.principal.getFnr()
    private val avgiver = Avgiver(
        navn = "Brillesjø AS",
        orgnr = "456313701",
        parentOrgnr = null,
        harRolle = true
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
        kontonr = virksomhet.kontonr,
        epost = "test@test"
    )
    private val oppdaterAvtale = OppdaterAvtale(
        kontonr = virksomhet.kontonr,
        epost = opprettAvtale.epost
    )

    @BeforeTest
    internal fun setUp() {
        every {
            virksomhetStore.hentVirksomheterForOrganisasjoner(listOf(virksomhet.orgnr))
        } returns listOf(virksomhet)
        every {
            virksomhetStore.hentVirksomhetForOrganisasjon(virksomhet.orgnr)
        } returns virksomhet
        coEvery {
            altinnService.hentAvgivereHovedadministrator(fnrInnsender)
        } returns listOf(avgiver)
        coEvery {
            altinnService.hentAvgivereMedRolle(fnrInnsender, any())
        } returns listOf(avgiver)
        coEvery {
            enhetsregisteretService.hentOrganisasjonsenhet(avgiver.orgnr)
        } returns Organisasjonsenhet(
            orgnr = avgiver.orgnr,
            overordnetEnhet = null,
            navn = avgiver.navn,
            forretningsadresse = null,
            beliggenhetsadresse = null,
            naeringskode1 = Næringskode("", Næringskode.BUTIKKHANDEL_MED_OPTISKE_ARTIKLER),
            naeringskode2 = null,
            naeringskode3 = null
        )
        every {
            virksomhetStore.lagreVirksomhet(any())
        } returnsArgument 0
        every {
            virksomhetStore.oppdaterVirksomhet(any())
        } returnsArgument 0
        every {
            kafkaService.avtaleOpprettet(any())
        } returns Unit
        every {
            kafkaService.avtaleOppdatert(any())
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
            setBody(oppdaterAvtale)
        }
        response.status shouldBe HttpStatusCode.OK
    }

    @Test
    internal fun `redigerer avtale uten tilgang`() = routing.test {
        erIkkeHovedadministratorFor(avgiver.orgnr)
        val response = client.put("/avtale/virksomheter/${avgiver.orgnr}") {
            setBody(oppdaterAvtale)
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
