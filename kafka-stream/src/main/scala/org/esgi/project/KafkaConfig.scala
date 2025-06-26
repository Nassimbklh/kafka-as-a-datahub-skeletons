package org.esgi.project

import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.streams.StreamsConfig
import java.util.Properties

trait KafkaConfig {

    def applicationName: String

    // auto loader from properties file in project
    def buildAdminProperties: Properties = {
      val properties = new Properties()
      properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
      properties.put(AdminClientConfig.CLIENT_DNS_LOOKUP_CONFIG, "use_all_dns_ips")
      properties.put(AdminClientConfig.CLIENT_ID_CONFIG, applicationName)
      properties
    }

    def buildStreamsProperties: Properties = {
      val properties = new Properties()
      properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
      properties.put(StreamsConfig.CLIENT_ID_CONFIG, applicationName)
      properties.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationName)
      properties.put(StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, "0")
      properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
      properties.put(StreamsConfig.REPLICATION_FACTOR_CONFIG, "-1")
      properties.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1")
      properties
    }

    def buildProducerProperties: Properties = {
      val properties = new Properties()
      properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
      properties.put(ProducerConfig.CLIENT_DNS_LOOKUP_CONFIG, "use_all_dns_ips")
      properties.put(ProducerConfig.CLIENT_ID_CONFIG, applicationName)
      properties.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1")
      properties
    }

    def buildConsumerProperties: Properties = {
      val properties = new Properties()
      properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
      properties.put(ConsumerConfig.CLIENT_DNS_LOOKUP_CONFIG, "use_all_dns_ips")
      properties.put(ConsumerConfig.CLIENT_ID_CONFIG, applicationName)
      properties.put(ConsumerConfig.GROUP_ID_CONFIG, applicationName)
      properties
    }

}
