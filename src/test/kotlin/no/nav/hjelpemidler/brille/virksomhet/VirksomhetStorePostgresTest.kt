package no.nav.hjelpemidler.brille.virksomhet

import io.kotest.matchers.shouldBe
import no.nav.hjelpemidler.brille.test.withMigratedDB
import kotlin.test.Test

internal class VirksomhetStorePostgresTest {
    @Test
    internal fun `lagrer og henter virksomhet`() = withMigratedDB {
        val store = VirksomhetStorePostgres(it)
        val lagretVirksomhet = store.lagreVirksomhet(
            Virksomhet(
                orgnr = "986165754",
                kontonr = "55718628082",
                epost = "test@test",
                fnrInnsender = "27121346260",
                navnInnsender = "",
                aktiv = true,
            )
        )
        val hentetVirksomhetForOrganisasjon = store.hentVirksomhetForOrganisasjon(lagretVirksomhet.orgnr)
        val hentetVirksomhetForInnsender = store.hentVirksomheterForInnsender(lagretVirksomhet.fnrInnsender)
            .firstOrNull()
        hentetVirksomhetForOrganisasjon shouldBe hentetVirksomhetForInnsender
    }
}
