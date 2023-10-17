package no.nav.hjelpemidler.brille

import io.getunleash.DefaultUnleash
import io.getunleash.Unleash
import io.getunleash.strategy.Strategy
import io.getunleash.util.UnleashConfig

object UnleashKlient {
    private val unleash: Unleash

    init {
        val miljø = Configuration.cluster
        unleash = when (miljø) {
            Configuration.Cluster.`PROD-GCP`, Configuration.Cluster.`DEV-GCP`, Configuration.Cluster.LOCAL -> DefaultUnleash(
                UnleashConfig.builder()
                    .appName("hm-brille-api")
                    .instanceId("hm-brille.api" + "_" + miljø.name)
                    .unleashAPI("https://unleash.nais.io/api/")
                    .build(),
                ClusterStrategy(miljø),
            )
        }
    }

    fun isEnabled(toggleKey: String) = unleash.isEnabled(toggleKey, false)
}

object UnleashToggleKeys

class ClusterStrategy(val miljø: Configuration.Cluster) : Strategy {
    override fun getName() = "byCluster"

    override fun isEnabled(parameters: MutableMap<String, String>): Boolean {
        val clustersParameter = parameters["cluster"] ?: return false
        val alleClustere = clustersParameter.split(",").map { it.trim() }.map { it.lowercase() }.toList()
        return alleClustere.contains(miljø.name.lowercase())
    }
}
