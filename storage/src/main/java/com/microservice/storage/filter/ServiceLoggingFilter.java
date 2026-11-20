package com.microservice.storage.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ServiceLoggingFilter extends OncePerRequestFilter {

  private static final String SERVICE_MDC_KEY = "service";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    MDC.put(SERVICE_MDC_KEY, "storage");
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(SERVICE_MDC_KEY);
    }
  }
}
