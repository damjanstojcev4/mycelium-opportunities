package com.mycelium.opportunities.source;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class SafeSourceHttp {
  private static final Set<String> HOSTS =
      Set.of(
          "api.ted.europa.eu",
          "ted.europa.eu",
          "api.tech.ec.europa.eu",
          "ec.europa.eu",
          "www.ipardpa.gov.mk",
          "ipardpa.gov.mk",
          "e-nabavki.gov.mk");
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(15))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public static URI validate(String url) {
    URI u = URI.create(url);
    if (!"https".equals(u.getScheme())
        || u.getUserInfo() != null
        || u.getHost() == null
        || !HOSTS.contains(u.getHost())
        || (u.getPort() != -1 && u.getPort() != 443))
      throw new IllegalArgumentException("URL must use HTTPS on a registered official source host");
    return u;
  }

  public String get(String url) throws Exception {
    return request(url, null, null, "text/html");
  }

  public String post(String url, String body, String contentType) throws Exception {
    return request(url, body, contentType, "application/json");
  }

  public synchronized String request(String url, String body, String contentType, String expected)
      throws Exception {
    URI uri = validate(url);
    for (InetAddress address : InetAddress.getAllByName(uri.getHost()))
      if (address.isAnyLocalAddress()
          || address.isLoopbackAddress()
          || address.isLinkLocalAddress()
          || address.isSiteLocalAddress())
        throw new IOException("Source resolved to a private address");
    for (int attempt = 0; attempt < 3; attempt++) {
      Thread.sleep(1000L * (1L << attempt));
      var builder =
          HttpRequest.newBuilder(uri)
              .timeout(Duration.ofSeconds(30))
              .header("User-Agent", "MyceliumOpportunities/0.1 (official opportunity indexing)")
              .header("Accept", expected);
      if (body != null)
        builder.header("Content-Type", contentType).POST(HttpRequest.BodyPublishers.ofString(body));
      HttpResponse<InputStream> response;
      try {
        response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
      } catch (HttpTimeoutException timeout) {
        if (attempt == 2) throw timeout;
        continue;
      }
      try (var input = response.body()) {
        if (response.statusCode() == 429 || response.statusCode() >= 500) {
          if (attempt == 2) throw new IOException("Source HTTP " + response.statusCode());
          String retry = response.headers().firstValue("Retry-After").orElse("0");
          long delay;
          try {
            delay = Long.parseLong(retry);
          } catch (NumberFormatException e) {
            try {
              delay =
                  Math.max(
                      0,
                      Duration.between(
                              Instant.now(),
                              ZonedDateTime.parse(
                                      retry, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                                  .toInstant())
                          .toSeconds());
            } catch (Exception invalid) {
              delay = 0;
            }
          }
          if (delay > 60)
            throw new IOException("Source requests delayed retry: " + delay + " seconds");
          Thread.sleep(delay * 1000);
          continue;
        }
        if (response.statusCode() != 200)
          throw new IOException(
              "Source HTTP " + response.statusCode() + "; redirects are not followed");
        if (!response
            .headers()
            .firstValue("Content-Type")
            .orElse("")
            .toLowerCase(Locale.ROOT)
            .contains(expected)) throw new IOException("Unexpected source content type");
        byte[] bytes = input.readNBytes(5_000_001);
        if (bytes.length > 5_000_000) throw new IOException("Source response exceeds 5 MB");
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
      }
    }
    throw new IOException("Source retries exhausted");
  }
}
