package no.nav.hjelpemidler.brille.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer
import mu.KotlinLogging

private val LOG = KotlinLogging.logger {}

class WiremockConfig {

    fun wiremockServer(): WireMockServer {
        val wireMockServer = WireMockServer(
            wireMockConfig()
                .port(8089)
                .extensions(ResponseTemplateTransformer(false))
        )

        fun hentStringFraFil(filnavn: String): String {
            LOG.info("WireMockServer.hentStringFraFil($filnavn)")
            return this::class.java.classLoader.getResource("mock/$filnavn").readText()
        }

//        wireMockServer.stubFor(
//            post(urlPathMatching("/api/bestillingsordningsjekker/soknad/sjekk"))
//                .willReturn(
//                    aResponse().withStatus(200)
//                        .withHeader("Content-Type", "application/json").withBody(
//                            """
//                        {
//                            "kanVæreBestilling": true,
//                            "kriterier": {
//                                "alleHovedProdukterPåBestillingsOrdning": true,
//                                "alleTilbehørPåBestillingsOrdning": true,
//                                "brukerHarHjelpemidlerFraFør": true,
//                                "brukerHarInfotrygdVedtakFraFør": true,
//                                "brukerHarHotsakVedtakFraFør": true,
//                            },
//                            "version": "123abc"
//                        }
//                            """.trimIndent()
//                        )
//                )
//        )

        wireMockServer.start()
        println("Wiremock stubs:")
        wireMockServer.listAllStubMappings().mappings.forEach { println(it) }
        return wireMockServer
    }
}
