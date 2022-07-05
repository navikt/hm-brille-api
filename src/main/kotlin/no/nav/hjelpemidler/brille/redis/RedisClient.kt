package no.nav.hjelpemidler.brille.redis

import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.Profile
import no.nav.hjelpemidler.brille.jsonMapper
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapResultat
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPooled
import redis.clients.jedis.commands.StringCommands

class RedisClient(val redisProps: Configuration.RedisProperties = Configuration.redisProperties) {
    private val jedis: StringCommands = when (Configuration.profile) {
        Profile.LOCAL -> Jedis()
        else -> JedisPooled(
            GenericObjectPoolConfig(),
            redisProps.host,
            redisProps.port,
            2 * 1000, // 2 seconds
            redisProps.password
        )
    }

    fun erOptiker(fnr: String): Boolean? = jedis.get("fnr:$fnr:hpr:er.optiker")?.toBoolean()

    fun setErOptiker(fnr: String, erOptiker: Boolean): String =
        jedis.setex("fnr:$fnr:hpr:er.optiker", redisProps.hprExpirySeconds, erOptiker.toString())

    fun medlemskapBarn(fnr: String): MedlemskapResultat? = jedis.get("fnr:$fnr:medlemskapbarn:resultat").let {
        jsonMapper.readValue(it, MedlemskapResultat::class.java)
    }

    fun setMedlemskapBarn(fnr: String, medlemskapResultat: MedlemskapResultat): String = jedis.setex(
        "fnr:$fnr:medlemskapbarn:resultat",
        redisProps.medlemskapBarnExpirySeconds,
        jsonMapper.writeValueAsString(medlemskapResultat)
    )
}
