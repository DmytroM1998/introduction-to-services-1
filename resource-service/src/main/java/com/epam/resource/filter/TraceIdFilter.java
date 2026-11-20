package com.epam.resource.filter;

import com.epam.resource.tracing.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TraceIdFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain) throws ServletException, IOException {
    String traceId = TraceContext.initTraceId(req.getHeader(TraceContext.HTTP_HEADER_TRACE_ID));
    try {
      chain.doFilter(req, resp);
    } finally {
      resp.setHeader(TraceContext.HTTP_HEADER_TRACE_ID, traceId);
      TraceContext.clear();
    }
  }
  
}
