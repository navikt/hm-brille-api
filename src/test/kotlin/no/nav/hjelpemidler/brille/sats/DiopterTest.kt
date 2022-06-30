package no.nav.hjelpemidler.brille.sats

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.shouldBe
import no.nav.hjelpemidler.brille.jsonMapper
import kotlin.test.Test

internal class DiopterTest {
    @Test
    internal fun `to tall før komma`() {
        val diopter = Diopter.parse("00,25D")
        diopter shouldBe "0.25"
    }

    @Test
    internal fun `ett tall før komma`() {
        val diopter = Diopter.parse("0,25D")
        diopter shouldBe "0.25"
    }

    @Test
    internal fun `uten komma`() {
        val diopter = Diopter.parse("25D")
        diopter shouldBe "25"
    }

    @Test
    internal fun `uten suffiks`() {
        val diopter = Diopter.parse("5.75")
        diopter shouldBe "5.75"
    }

    @Test
    internal fun `fra json`() {
        var dto = jsonMapper.readValue<TestDto>("""{"diopter":"00,25D"}""")
        dto.diopter shouldBe "0.25"
        dto = jsonMapper.readValue("""{"diopter":0.25}""")
        dto.diopter shouldBe "0.25"
    }

    @Test
    internal fun `til json`() {
        val json = jsonMapper.writeValueAsString(TestDto(Diopter.parse("00,25D")))
        json shouldBe """{"diopter":"00,25D"}"""
    }

    private infix fun Diopter.shouldBe(expected: String) =
        this.toDecimal() shouldBe expected.toBigDecimal()

    private data class TestDto(val diopter: Diopter)
}
