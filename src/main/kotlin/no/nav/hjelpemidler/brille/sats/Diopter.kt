package no.nav.hjelpemidler.brille.sats

import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale

@JsonDeserialize(using = Diopter.Deserializer::class)
data class Diopter(private val value: BigDecimal) : Comparable<Diopter> {
    private constructor(text: String) : this(BigDecimal(text))

    @JsonValue
    override fun toString(): String = formatter.format(value) + SUFFIX

    fun toDecimal(): BigDecimal = value

    override fun compareTo(other: Diopter): Int = this.value.compareTo(other.value)

    companion object {
        const val SUFFIX = "D"

        val ZERO = Diopter(BigDecimal.ZERO)
        val ONE = Diopter(BigDecimal.ONE)
        val MIN = Diopter("-99.99")
        val MAX = Diopter("+99.99")

        val INGEN = ZERO..ZERO

        private val regex = "^\\d{0,2}([.,]\\d{0,2})?D?\$".toRegex()
        private val formatter: NumberFormat = DecimalFormat("00.00", DecimalFormatSymbols(Locale("nb")))

        fun parse(text: String): Diopter {
            require(text.matches(regex)) {
                "'$text' er ugyldig"
            }
            return Diopter(text.replace(",", ".").removeSuffix(SUFFIX))
        }
    }

    object Deserializer : JsonDeserializer<Diopter?>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): Diopter? =
            parser.valueAsString?.tilDiopter()
    }
}

fun String.tilDiopter(): Diopter = Diopter.parse(this)
infix fun String.til(endInclusive: String): ClosedRange<Diopter> = this.tilDiopter()..endInclusive.tilDiopter()
fun String.ellerMer(): ClosedRange<Diopter> = this.tilDiopter()..Diopter.MAX
fun tilOgMed(endInclusive: String): ClosedRange<Diopter> = Diopter.MIN..endInclusive.tilDiopter()
fun ClosedRange<Diopter>.visning(): String = "≥ $start ≤ $endInclusive"
