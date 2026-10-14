package com.tutran.callassistant.web;

import com.tutran.callassistant.application.RequestIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Gắn {@code request_id} cho mỗi request HTTP (Sprint 3 T5): đặt vào MDC để mọi dòng log trong request
 * mang mã này, và trả về header {@value #HEADER} để client đối chiếu được.
 *
 * <p>Mã luôn do server sinh - không nhận mã từ client, để log không chứa chuỗi tuỳ ý do người ngoài gửi vào.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = RequestIds.newId();
        MDC.put(RequestIds.MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(RequestIds.MDC_KEY);
        }
    }
}
