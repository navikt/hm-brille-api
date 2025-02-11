package no.nav.hjelpemidler.brille.innsender

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import no.nav.hjelpemidler.brille.test.AbstractStoreTest
import kotlin.test.Test

class InnsenderStorePostgresTest : AbstractStoreTest() {
    @Test
    fun `lagrer og henter innsender`() = runTest {
        val lagretInnsender = transaction {
            innsenderStore.lagreInnsender(Innsender("27121346260", true))
        }
        val hentetInnsender = transaction {
            innsenderStore.hentInnsender(lagretInnsender.fnrInnsender)
        }
        hentetInnsender.shouldNotBeNull()
        hentetInnsender.fnrInnsender shouldBe lagretInnsender.fnrInnsender
        hentetInnsender.godtatt.shouldBeTrue()
    }
}
