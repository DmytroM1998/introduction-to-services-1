package com.epam.mf.api_gateway.filter;

import com.epam.mf.api_gateway.tracing.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class TraceIdGatewayFilter extends HttpFilter {

  private final TraceContext traceContext;

  public TraceIdGatewayFilter(TraceContext traceContext) {
    this.traceContext = traceContext;
  }
  
  @Override
  protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    String traceId = traceContext.initTraceId(request.getHeader(TraceContext.HTTP_HEADER_TRACE_ID));
    HttpServletRequest tracedRequest = new TraceIdRequestWrapper(request, traceId);
    try {
      log.info("Handling HTTP {} {}", request.getMethod(), request.getRequestURI());
      chain.doFilter(tracedRequest, response);
    } finally {
      response.setHeader(TraceContext.HTTP_HEADER_TRACE_ID, traceId);
      traceContext.clear();
    }
  }

  private static final class TraceIdRequestWrapper extends HttpServletRequestWrapper {
    private final String traceId;

    private TraceIdRequestWrapper(HttpServletRequest request, String traceId) {
      super(request);
      this.traceId = traceId;
    }

    @Override
    public String getHeader(String name) {
      return isTraceIdHeader(name) ? traceId : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      return isTraceIdHeader(name)
          ? Collections.enumeration(List.of(traceId))
          : super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
      Set<String> names = new LinkedHashSet<>();
      Enumeration<String> originalNames = super.getHeaderNames();
      while (originalNames.hasMoreElements()) {
        String name = originalNames.nextElement();
        if (!isTraceIdHeader(name)) {
          names.add(name);
        }
      }
      names.add(TraceContext.HTTP_HEADER_TRACE_ID);
      return Collections.enumeration(names);
    }

    private boolean isTraceIdHeader(String name) {
      return TraceContext.HTTP_HEADER_TRACE_ID.equalsIgnoreCase(name);
    }
  }
}
