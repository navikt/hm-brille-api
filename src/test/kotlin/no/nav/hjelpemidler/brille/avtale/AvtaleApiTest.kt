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
import no.nav.hjelpemidler.brille.db.MockDatabaseContext
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretService
import no.nav.hjelpemidler.brille.enhetsregisteret.Næringskode
import no.nav.hjelpemidler.brille.enhetsregisteret.Organisasjonsenhet
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.test.TestRouting
import no.nav.hjelpemidler.brille.virksomhet.Virksomhet
import kotlin.test.BeforeTest
import kotlin.test.Test

class AvtaleApiTest {
    private val altinnService = mockk<AltinnService>()
    private val enhetsregisteretService = mockk<EnhetsregisteretService>()
    private val kafkaService = mockk<KafkaService>()

    private val routing = TestRouting {
        val databaseContext = MockDatabaseContext()

        authenticate("test") {
            avtaleApi(AvtaleService(databaseContext, altinnService, enhetsregisteretService, kafkaService))
        }
    }

    private val fnrInnsender = routing.principal.fnr
    private val avgiver = Avgiver(
        navn = "Brillesjø AS",
        orgnr = "456313701",
        parentOrgnr = null,
    )
    private val virksomhet = Virksomhet(
        orgnr = avgiver.orgnr,
        kontonr = "94154381079",
        epost = "test@test",
        fnrInnsender = fnrInnsender,
        navnInnsender = "Dag Ledersen",
        aktiv = true,
        bruksvilkår = false,
    )
    private val opprettAvtale = OpprettAvtale(
        orgnr = avgiver.orgnr,
        kontonr = virksomhet.kontonr,
        epost = "test@test",
    )
    private val oppdaterAvtale = OppdaterAvtale(
        kontonr = virksomhet.kontonr,
        epost = opprettAvtale.epost,
    )

    @BeforeTest
    fun setUp() {
        coEvery {
            altinnService.hentAvgivere(fnrInnsender, Avgiver.Tjeneste.OPPGJØRSAVTALE)
        } returns listOf(avgiver)
        coEvery {
            enhetsregisteretService.hentOrganisasjonsenhet(avgiver.orgnr)
        } returns Organisasjonsenhet(
            orgnr = avgiver.orgnr,
            overordnetEnhet = null,
            navn = avgiver.navn,
            forretningsadresse = null,
            beliggenhetsadresse = null,
            naeringskode1 = Næringskode("", Næringskode.BUTIKKHANDEL_MED_OPTISKE_ARTIKLER.first()),
            naeringskode2 = null,
            naeringskode3 = null,
        )
        coEvery {
            enhetsregisteretService.hentOrganisasjonsenheter(setOf(avgiver.orgnr))
        } returns mapOf(
            avgiver.orgnr to Organisasjonsenhet(
                orgnr = avgiver.orgnr,
                overordnetEnhet = null,
                navn = avgiver.navn,
                forretningsadresse = null,
                beliggenhetsadresse = null,
                naeringskode1 = Næringskode("", Næringskode.BUTIKKHANDEL_MED_OPTISKE_ARTIKLER.first()),
                naeringskode2 = null,
                naeringskode3 = null,
            ),
        )
        every {
            kafkaService.avtaleOpprettet(any())
        } returns Unit
        every {
            kafkaService.avtaleOppdatert(any())
        } returns Unit
    }

    @Test
    fun `henter virksomheter med avtale`() = routing.test {
        val response = client.get("/avtale/virksomheter")
        response.status shouldBe HttpStatusCode.OK
    }

    @Test
    fun `henter virksomhet med avtale`() = routing.test {
        val response = client.get("/avtale/virksomheter/${virksomhet.orgnr}")
        response.status shouldBe HttpStatusCode.OK
    }

    @Test
    fun `oppretter ny avtale`() = routing.test {
        harRettighetOppgjørsavtale(opprettAvtale.orgnr)
        val response = client.post("/avtale/virksomheter") {
            setBody(opprettAvtale)
        }
        response.status shouldBe HttpStatusCode.Created
    }

    @Test
    fun `oppretter ny avtale uten tilgang`() = routing.test {
        harIkkeRettighetOppgjørsavtale(opprettAvtale.orgnr)
        val response = client.post("/avtale/virksomheter") {
            setBody(opprettAvtale)
        }
        response.status shouldBe HttpStatusCode.Forbidden
    }

    @Test
    fun `redigerer avtale`() = routing.test {
        harRettighetOppgjørsavtale(avgiver.orgnr)
        val response = client.put("/avtale/virksomheter/${avgiver.orgnr}") {
            setBody(oppdaterAvtale)
        }
        response.status shouldBe HttpStatusCode.OK
    }

    @Test
    fun `redigerer avtale uten tilgang`() = routing.test {
        harIkkeRettighetOppgjørsavtale(avgiver.orgnr)
        val response = client.put("/avtale/virksomheter/${avgiver.orgnr}") {
            setBody(oppdaterAvtale)
        }
        response.status shouldBe HttpStatusCode.Forbidden
    }

    private fun harRettighetOppgjørsavtale(orgnr: String) = coEvery {
        altinnService.harTilgangTilOppgjørsavtale(fnrInnsender, orgnr)
    } returns true

    private fun harIkkeRettighetOppgjørsavtale(orgnr: String) = coEvery {
        altinnService.harTilgangTilOppgjørsavtale(fnrInnsender, orgnr)
    } returns false
}
