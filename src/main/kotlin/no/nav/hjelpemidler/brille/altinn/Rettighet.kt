package no.nav.hjelpemidler.brille.altinn

import com.fasterxml.jackson.annotation.JsonProperty

data class Rettighet(
    @JsonProperty("ServiceCode")
    val serviceCode: String,
    @JsonProperty("ServiceEditionCode")
    val serviceEditionCode: Int,
) {
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
) : List<Rettighet> by rettigheter {
    val harRettighetOppgjørsavtale: Boolean
        get() = any {
            it == Rettighet.OPPGJØRSAVTALE
        }

    val harRettighetUtbetalingsrapport: Boolean
        get() = any {
            it == Rettighet.UTBETALINGSRAPPORT
        }

    companion object {
        val INGEN: Rettigheter = Rettigheter(rettigheter = emptyList())
    }
}
