package no.nav.hjelpemidler.brille.kafka

import no.nav.hjelpemidler.brille.Configuration
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer

class AivenKafkaConfiguration {

    private val javaKeystore = "jks"
    private val pkcs12 = "PKCS12"

    private val kafkaProperties = Configuration.kafkaProperties

    private fun commonConfigs(): Map<String, Any?> {
        val props: MutableMap<String, Any?> = HashMap()
        props[CommonClientConfigs.CLIENT_ID_CONFIG] = kafkaProperties.clientId
        if (kafkaProperties.truststorePath != null) {
            props[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = SecurityProtocol.SSL.name
            props[SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG] = ""
            props[SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG] = javaKeystore
            props[SslConfigs.SSL_KEYSTORE_TYPE_CONFIG] = pkcs12
            props[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] = kafkaProperties.truststorePath
            props[SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] = kafkaProperties.truststorePassword
            props[SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] = kafkaProperties.keystorePath
            props[SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG] = kafkaProperties.keystorePassword
        }
        return props
    }

    private fun producerConfigs(): Map<String, Any?> {
        val props: MutableMap<String, Any?> = HashMap()
        props[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaProperties.bootstrapServers
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        props.putAll(commonConfigs())
        return props
    }

    private fun consumerConfigs(): Map<String, Any?> {
        val props: MutableMap<String, Any?> = HashMap()
        props[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaProperties.bootstrapServers
        props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        props.putAll(commonConfigs())
        return props
    }

    fun aivenKafkaProducer(): KafkaProducer<String, String> {
        return KafkaProducer(producerConfigs())
    }

    fun aivenKafkaConsumer(): KafkaConsumer<String, String> {
        return KafkaConsumer(consumerConfigs())
    }
}
