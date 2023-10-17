package no.nav.hjelpemidler.brille.virksomhet

import io.kotest.matchers.shouldBe
import no.nav.hjelpemidler.brille.db.PostgresTestHelper
import no.nav.hjelpemidler.brille.db.PostgresTestHelper.withMigratedDb
import kotlin.test.Test

internal class VirksomhetStorePostgresTest {
    @Test
    internal fun `lagrer og henter virksomhet`() = withMigratedDb {
        with(VirksomhetStorePostgres(PostgresTestHelper.sessionFactory)) {
            val lagretVirksomhet = lagreVirksomhet(
                Virksomhet(
                    orgnr = "986165754",
                    kontonr = "55718628082",
                    epost = "test@test",
                    fnrInnsender = "27121346260",
                    navnInnsender = "",
                    aktiv = true,
                ),
            )
            val hentetVirksomhetForOrganisasjon = hentVirksomhetForOrganisasjon(lagretVirksomhet.orgnr)
            try {
                val hentetVirksomhetForInnsender =
                    hentVirksomheterForOrganisasjoner(listOf(lagretVirksomhet.orgnr))
                        .firstOrNull()
                hentetVirksomhetForOrganisasjon shouldBe hentetVirksomhetForInnsender
            } catch (e: Exception) {
                System.out.println(e.message)
                throw e
            }
        }
    }
}
