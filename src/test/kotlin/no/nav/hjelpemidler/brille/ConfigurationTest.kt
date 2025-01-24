package no.nav.hjelpemidler.brille

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.io.FileTemplateLoader
import io.kotest.matchers.maps.shouldHaveKeys
import no.nav.hjelpemidler.configuration.Environment
import no.nav.hjelpemidler.configuration.environmentVariablesIn
import no.nav.hjelpemidler.serialization.jackson.readValue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class ConfigurationTest {
    private val miljøvariablerIKode = environmentVariablesIn(Configuration, includeExternal = false)

    @Test
    fun `Alle miljøvariabler i kode er definert i manifest for dev`() {
        val miljøvariablerIManifest = readManifest(Environment.Tier.DEV).miljøvariabler
        miljøvariablerIManifest.shouldHaveKeys(*miljøvariablerIKode.toTypedArray())
    }

    @Test
    fun `Alle miljøvariabler i kode er definert i manifest for prod`() {
        val miljøvariablerIManifest = readManifest(Environment.Tier.PROD).miljøvariabler
        miljøvariablerIManifest.shouldHaveKeys(*miljøvariablerIKode.toTypedArray())
    }

    private fun readManifest(tier: Environment.Tier): JsonNode {
        val contextPath = Path.of(".nais", tier.toString().lowercase(), "config.json")
        val context = jsonMapper.readValue<Map<String, Any?>>(contextPath)
        val manifest = handlebars.compile("nais-deploy").apply(context)
        return yamlMapper.readTree(manifest)
    }

    private val yamlMapper: YAMLMapper = YAMLMapper.builder().addModule(kotlinModule()).build()
    private val handlebars: Handlebars = Handlebars(FileTemplateLoader("./.nais", ".yaml"))
}

private val JsonNode.miljøvariabler: Map<String, String>
    get() = at("/spec/env")
        .associate { it["name"].textValue() to it["value"].textValue() }
