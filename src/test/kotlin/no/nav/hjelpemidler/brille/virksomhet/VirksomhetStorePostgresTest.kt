package no.nav.hjelpemidler.brille.virksomhet

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import no.nav.hjelpemidler.brille.test.AbstractStoreTest
import kotlin.test.Test

class VirksomhetStorePostgresTest : AbstractStoreTest() {
    @Test
    fun `lagrer og henter virksomhet`() = runTest {
        transaction {
            val lagretVirksomhet = virksomhetStore.lagreVirksomhet(
                Virksomhet(
                    orgnr = "986165754",
                    kontonr = "55718628082",
                    epost = "test@test",
                    fnrInnsender = "27121346260",
                    navnInnsender = "",
                    aktiv = true,
                ),
            )
            val hentetVirksomhetForOrganisasjon = virksomhetStore.hentVirksomhetForOrganisasjon(lagretVirksomhet.orgnr)
            val hentetVirksomhetForInnsender = virksomhetStore
                .hentVirksomheterForOrganisasjoner(listOf(lagretVirksomhet.orgnr))
                .firstOrNull()
            hentetVirksomhetForOrganisasjon shouldBe hentetVirksomhetForInnsender
        }
    }
}
