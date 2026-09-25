package com.tutran.callassistant.ingest.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Khởi tạo client Elasticsearch single-node local từ {@link ElasticsearchProperties}. */
@Configuration
public class ElasticsearchConfiguration {

    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchProperties properties) {
        RestClient restClient = RestClient
                .builder(new HttpHost(properties.host(), properties.port(), "http"))
                .build();
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}
