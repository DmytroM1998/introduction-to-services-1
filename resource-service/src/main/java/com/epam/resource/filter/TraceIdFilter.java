package com.epam.resource.filter;

import com.epam.resource.tracing.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class TraceIdFilter extends OncePerRequestFilter {
  
  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain) throws ServletException, IOException {
    String traceId = TraceContext.initTraceId(req.getHeader(TraceContext.HTTP_HEADER_TRACE_ID));
    try {
      log.info("Handling HTTP {} {}", req.getMethod(), req.getRequestURI());
      chain.doFilter(req, resp);
    } finally {
      resp.setHeader(TraceContext.HTTP_HEADER_TRACE_ID, traceId);
      TraceContext.clear();
    }
  }
  
}
