package com.tutran.callassistant.ingest.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import com.tutran.callassistant.application.port.out.AnalysisResultArchive;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Khởi tạo client Elasticsearch single-node local từ {@link ElasticsearchProperties}, có timeout cho mọi lời gọi. */
@Configuration
public class ElasticsearchConfiguration {

    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchProperties properties) {
        RestClient restClient = RestClient
                .builder(new HttpHost(properties.host(), properties.port(), "http"))
                .setRequestConfigCallback(config -> config
                        .setConnectTimeout(properties.connectTimeoutMs())
                        .setConnectionRequestTimeout(properties.connectTimeoutMs())
                        .setSocketTimeout(properties.socketTimeoutMs()))
                .build();
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }

    /**
     * Nơi lưu kết quả phân tích (Sprint 3 T9): {@code call-analysis.results.archive=es} ghi vào index
     * {@value ElasticsearchResultArchive#INDEX}; mặc định {@code none} (CLI, test) không ghi gì.
     */
    @Bean
    public AnalysisResultArchive analysisResultArchive(ElasticsearchClient client,
                                                       @Value("${call-analysis.results.archive:none}") String mode) {
        return "es".equalsIgnoreCase(mode.trim()) ? new ElasticsearchResultArchive(client) : AnalysisResultArchive.NONE;
    }
}
