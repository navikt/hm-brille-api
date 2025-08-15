package no.nav.hjelpemidler.brille.redis

import io.valkey.JedisPoolConfig
import io.valkey.commands.StringCommands
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.enhetsregisteret.Organisasjonsenhet
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapResultat
import no.nav.hjelpemidler.configuration.Environment
import no.nav.hjelpemidler.serialization.jackson.jsonMapper
import java.time.LocalDate

class RedisClient(private val redisProps: Configuration.RedisProperties = Configuration.redisProperties) {
    private val valkey: StringCommands = if (Environment.current.isLocal) {
        ValkeyMock()
    } else {
        ValkeyPoolExtension(
            io.valkey.JedisPool(
                JedisPoolConfig().let {
                    it.maxTotal = 32
                    it.maxIdle = 32
                    it.minIdle = 16
                    it.testOnCreate = true
                    it.testOnBorrow = true
                    it.testOnReturn = true
                    it
                },
                Configuration.redisProperties.host,
                Configuration.redisProperties.port,
                Configuration.redisProperties.timeout,
                Configuration.redisProperties.username,
                Configuration.redisProperties.password,
                Configuration.redisProperties.ssl,
            ),
        )
    }

    fun erOptiker(fnr: String): Boolean? = valkey.get(erOptikerKey(fnr))?.toBoolean()

    fun setErOptiker(fnr: String, erOptiker: Boolean) {
        valkey.setex(erOptikerKey(fnr), redisProps.hprExpirySeconds, erOptiker.toString())
    }

    fun optikerNavn(fnr: String): String? = valkey.get(optikerNavnKey(fnr))

    fun setOptikerNavn(fnr: String, optikerNavn: String) {
        valkey.setex(optikerNavnKey(fnr), redisProps.hprExpirySeconds, optikerNavn)
    }

    fun medlemskapBarn(fnr: String, bestillingsdato: LocalDate): MedlemskapResultat? =
        valkey.get(medlemskapBarnKey(fnr, bestillingsdato))?.let {
            jsonMapper.readValue(it, MedlemskapResultat::class.java)
        }

    fun setMedlemskapBarn(fnr: String, bestillingsdato: LocalDate, medlemskapResultat: MedlemskapResultat) {
        valkey.setex(
            medlemskapBarnKey(fnr, bestillingsdato),
            redisProps.medlemskapBarnExpirySeconds(),
            jsonMapper.writeValueAsString(medlemskapResultat),
        )
    }

    fun organisasjonsenhet(orgnr: String): Organisasjonsenhet? = valkey.get(orgenhetKey(orgnr))?.let {
        jsonMapper.readValue(it, Organisasjonsenhet::class.java)
    }

    fun setOrganisasjonsenhet(orgnr: String, orgenhet: Organisasjonsenhet) {
        valkey.setex(
            orgenhetKey(orgnr),
            redisProps.orgenhetExpirySeconds,
            jsonMapper.writeValueAsString(orgenhet),
        )
    }
}

private fun erOptikerKey(fnr: String) = "fnr:$fnr:hpr:er.optiker"
private fun optikerNavnKey(fnr: String) = "fnr:$fnr:hpr:optiker.navn"
private fun medlemskapBarnKey(fnr: String, bestillingsdato: LocalDate) =
    "fnr:$fnr:bestillingsdato:$bestillingsdato:medlemskapbarn:resultat"

private fun orgenhetKey(orgnr: String) = "orgnr:$orgnr:orgenhet"
