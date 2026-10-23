package com.mycelium.opportunities.auth;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(-110)
public class AuthRateLimit extends OncePerRequestFilter {
  private record Window(long start, int count) {}

  private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

  protected void doFilterInternal(HttpServletRequest q, HttpServletResponse s, FilterChain chain)
      throws ServletException, IOException {
    if (q.getMethod().equals("POST")
        && (q.getRequestURI().endsWith("/auth/login")
            || q.getRequestURI().endsWith("/auth/register"))) {
      long now = System.currentTimeMillis();
      windows.entrySet().removeIf(e -> now - e.getValue().start() > 600000);
      Window w =
          windows.compute(
              q.getRemoteAddr(),
              (k, v) -> v == null ? new Window(now, 1) : new Window(v.start(), v.count() + 1));
      if (w.count() > 20) {
        s.setHeader("Retry-After", "600");
        s.sendError(429, "Please try again later");
        return;
      }
    }
    chain.doFilter(q, s);
  }
}
