package no.nav.hjelpemidler.brille.avtale

import io.kotest.matchers.shouldBe
import no.nav.hjelpemidler.brille.avtale.Avtale
import no.nav.hjelpemidler.brille.avtale.AvtaleStorePostgres
import no.nav.hjelpemidler.brille.db.PostgresTestHelper
import no.nav.hjelpemidler.brille.db.PostgresTestHelper.withMigratedDb
import no.nav.hjelpemidler.brille.virksomhet.Virksomhet
import no.nav.hjelpemidler.brille.virksomhet.VirksomhetStorePostgres
import kotlin.test.Test

internal class AvtaleStorePostgresTest {
    @Test
    internal fun `lagrer og henter hovedavtale`() = withMigratedDb {

        with(VirksomhetStorePostgres(PostgresTestHelper.sessionFactory)) {
            val lagretVirksomhet = lagreVirksomhet(
                Virksomhet(
                    orgnr = "986165754",
                    kontonr = "55718628082",
                    epost = "test@test",
                    fnrInnsender = "27121346260",
                    navnInnsender = "",
                    aktiv = true,
                    utvidetAvtale = false
                )
            )

            with(AvtaleStorePostgres(PostgresTestHelper.sessionFactory)) {

                lagreAvtale(
                    Avtale(
                        id = 1,
                        orgnr = "986165754",
                        fnrInnsender = "27121346260",
                        aktiv = true,
                        avtaleId = 1,
                    )
                )
            }

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

    @Test
    internal fun `lagrer og henter virksomhet med utvidet avtale`() = withMigratedDb {

        with(VirksomhetStorePostgres(PostgresTestHelper.sessionFactory)) {
            val lagretVirksomhet = lagreVirksomhet(
                Virksomhet(
                    orgnr = "986165755",
                    kontonr = "55718628082",
                    epost = "test@test",
                    fnrInnsender = "27121346260",
                    navnInnsender = "",
                    aktiv = true,
                    utvidetAvtale = true
                )
            )

            with(AvtaleStorePostgres(PostgresTestHelper.sessionFactory)) {

                lagreAvtale(
                    Avtale(
                        id = 1,
                        orgnr = "986165755",
                        fnrInnsender = "27121346260",
                        aktiv = true,
                        avtaleId = 2,
                    )
                )

                lagreAvtale(
                    Avtale(
                        id = 1,
                        orgnr = "986165755",
                        fnrInnsender = "27121346260",
                        aktiv = true,
                        avtaleId = 2,
                    )
                )
            }

            val hentetVirksomhetForOrganisasjon = hentVirksomhetForOrganisasjon(lagretVirksomhet.orgnr)
            try {
                val hentetVirksomhetForInnsender =
                    hentVirksomheterForOrganisasjoner(listOf(lagretVirksomhet.orgnr))
                        .firstOrNull()
                hentetVirksomhetForOrganisasjon shouldBe hentetVirksomhetForInnsender
                hentetVirksomhetForOrganisasjon?.utvidetAvtale shouldBe true
            } catch (e: Exception) {
                System.out.println(e.message)
                throw e
            }
        }
    }
}
