package no.nav.hjelpemidler.brille.redis

import no.nav.hjelpemidler.brille.Configuration
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import redis.clients.jedis.JedisPooled

class RedisClient(val redisProps: Configuration.RedisProperties = Configuration.redisProperties) {
    val jedis = JedisPooled(
        GenericObjectPoolConfig(),
        redisProps.host,
        redisProps.port,
        2 * 1000, // 2 seconds
        redisProps.password
    )

    fun erOptiker(fnr: String): Boolean? = jedis.get("fnr:$fnr:hpr:er.optiker")?.toBoolean()

    fun setErOptiker(fnr: String, erOptiker: Boolean): String = jedis.setex("fnr:$fnr:hpr:er.optiker", redisProps.hprExpirySeconds, erOptiker.toString())

    fun medlemskapBarn(fnr: String): String? = jedis.get("fnr:$fnr:medlemskapbarn:resultat")

    fun setMedlemskapBarn(fnr: String, resultat: String): String = jedis.setex("fnr:$fnr:medlemskapbarn:resultat", redisProps.medlemskapBarnExpirySeconds, resultat)
}
