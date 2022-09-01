package no.nav.hjelpemidler.brille.altinn

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

data class Rettighet(
    @JsonProperty("ServiceCode")
    val serviceCode: String,
    @JsonProperty("ServiceEditionCode")
    val serviceEditionCode: Int,
) {
    override fun toString(): String = "[$serviceCode,$serviceEditionCode]"

    companion object {
        /**
         * Avtale om direkte oppgjør av briller for barn - serviceCode 5849 editionCode 1
         */
        val OPPGJØRSAVTALE: Rettighet = Rettighet(serviceCode = "5849", serviceEditionCode = 1)

        /**
         * Utbetalingsrapport - brillestøtte - serviceCode 5850 editionCode 1
         */
        val UTBETALINGSRAPPORT: Rettighet = Rettighet(serviceCode = "5850", serviceEditionCode = 1)
    }
}

data class Rettigheter(
    @JsonProperty("Rights")
    val rettigheter: List<Rettighet>,
) {
    @JsonIgnore
    fun harRettighet(rettighet: Rettighet): Boolean =
        rettigheter.contains(rettighet)

    companion object {
        val INGEN: Rettigheter = Rettigheter(rettigheter = emptyList())
    }
}
