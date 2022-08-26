package no.nav.hjelpemidler.brille.innsender

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.hjelpemidler.brille.db.PostgresTestHelper
import no.nav.hjelpemidler.brille.db.PostgresTestHelper.withMigratedDb
import kotlin.test.Test

internal class InnsenderStorePostgresTest {
    @Test
    internal fun `lagrer og henter innsender`() = withMigratedDb {
        with(InnsenderStorePostgres(PostgresTestHelper.sessionFactory)) {
            val lagretInnsender = lagreInnsender(Innsender("27121346260", true))
            val hentetInnsender = hentInnsender(lagretInnsender.fnrInnsender)
            hentetInnsender.shouldNotBeNull()
            hentetInnsender.fnrInnsender shouldBe lagretInnsender.fnrInnsender
            hentetInnsender.godtatt.shouldBeTrue()
        }
    }
}
