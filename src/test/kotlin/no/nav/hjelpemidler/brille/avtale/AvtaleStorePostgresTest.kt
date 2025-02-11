package no.nav.hjelpemidler.brille.avtale

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import no.nav.hjelpemidler.brille.test.AbstractStoreTest
import no.nav.hjelpemidler.brille.virksomhet.Virksomhet
import kotlin.test.Test

class AvtaleStorePostgresTest : AbstractStoreTest() {
    @Test
    fun `lagrer og henter hovedavtale`() = runTest {
        val lagretVirksomhet = transaction {
            virksomhetStore.lagreVirksomhet(
                Virksomhet(
                    orgnr = "986165759",
                    kontonr = "55718628082",
                    epost = "test@test",
                    fnrInnsender = "27121346260",
                    navnInnsender = "",
                    aktiv = true,
                    bruksvilkår = false,
                ),
            )
            avtaleStore.lagreAvtale(
                Avtale(
                    id = 1,
                    orgnr = "986165759",
                    fnrInnsender = "27121346260",
                    aktiv = true,
                    avtaleId = 1,
                ),
            )
        }

        transaction {
            val hentetVirksomhetForOrganisasjon = virksomhetStore.hentVirksomhetForOrganisasjon(lagretVirksomhet.orgnr)
            val hentetVirksomhetForInnsender = virksomhetStore
                .hentVirksomheterForOrganisasjoner(listOf(lagretVirksomhet.orgnr))
                .firstOrNull()
            hentetVirksomhetForOrganisasjon shouldBe hentetVirksomhetForInnsender
        }
    }

    @Test
    fun `lagrer, oppdaterer og henter virksomhet med godtatte bruksvilkår for API`() = runTest {
        val lagretVirksomhet = transaction {
            virksomhetStore.lagreVirksomhet(
                Virksomhet(
                    orgnr = "986165760",
                    kontonr = "55718628082",
                    epost = "test@test",
                    fnrInnsender = "27121346260",
                    navnInnsender = "",
                    aktiv = true,
                    bruksvilkår = true,
                ),
            )
        }

        transaction {
            avtaleStore.lagreAvtale(
                Avtale(
                    id = null,
                    orgnr = "986165760",
                    fnrInnsender = "27121346260",
                    aktiv = true,
                    avtaleId = 1,
                ),
            )

            val bruksvilkårGodtatt = avtaleStore.godtaBruksvilkår(
                BruksvilkårGodtatt(
                    id = null,
                    orgnr = "986165760",
                    fnrInnsender = "27121346260",
                    aktiv = true,
                    bruksvilkårDefinisjonId = 1,
                ),
            )

            avtaleStore.henBruksvilkårOrganisasjon("986165760") shouldBe bruksvilkårGodtatt
        }

        transaction {
            val hentetVirksomhetForOrganisasjon = virksomhetStore.hentVirksomhetForOrganisasjon(lagretVirksomhet.orgnr)
            val hentetVirksomhetForInnsender = virksomhetStore
                .hentVirksomheterForOrganisasjoner(listOf(lagretVirksomhet.orgnr))
                .firstOrNull()
            hentetVirksomhetForOrganisasjon shouldBe hentetVirksomhetForInnsender
            hentetVirksomhetForOrganisasjon?.bruksvilkår shouldBe true
        }
    }
}
