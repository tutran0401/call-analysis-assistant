package com.tutran.callassistant.ingest.es;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.tutran.callassistant.application.port.out.SignalingFetchError;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/** Quy exception của client Elasticsearch về {@link SignalingFetchError}. */
final class ElasticsearchErrors {

    private ElasticsearchErrors() {
    }

    static SignalingFetchError classify(Throwable error) {
        if (error instanceof ElasticsearchException es) {
            String type = es.error() == null ? null : es.error().type();
            return "index_not_found_exception".equals(type)
                    ? SignalingFetchError.ES_INDEX_MISSING : SignalingFetchError.ES_ERROR;
        }
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof SocketTimeoutException) {
                return SignalingFetchError.ES_TIMEOUT;
            }
            if (t instanceof ConnectException || t instanceof UnknownHostException) {
                return SignalingFetchError.ES_UNAVAILABLE;
            }
        }
        return SignalingFetchError.ES_ERROR;
    }
}
