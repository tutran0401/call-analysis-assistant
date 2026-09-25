package com.tutran.callassistant.ingest.es;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình Elasticsearch, gom về một chỗ.
 *
 * <p>Trước đây host/port/tên index được đọc rải rác bằng ba kiểu khác nhau ({@code @Value} trong
 * config, {@code Environment.getProperty} trong fetcher, {@code @Value} trong CLI), mỗi chỗ tự khai
 * một giá trị mặc định - tức là cùng một thiết lập có ba nguồn chân lý và có thể lệch nhau. Giờ chỉ
 * còn một record được bind từ {@code application.yml}.
 */
@ConfigurationProperties(prefix = "elasticsearch")
public record ElasticsearchProperties(
        @DefaultValue("localhost") String host,
        @DefaultValue("9200") int port,
        @DefaultValue("call-signaling-events") String signalingIndex
) {
    public static ElasticsearchProperties defaults() {
        return new ElasticsearchProperties("localhost", 9200, "call-signaling-events");
    }
}
