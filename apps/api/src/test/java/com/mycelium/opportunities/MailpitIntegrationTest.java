package com.mycelium.opportunities;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class MailpitIntegrationTest {
  @Container
  static GenericContainer<?> mailpit =
      new GenericContainer<>("axllent/mailpit:v1.27").withExposedPorts(1025, 8025);

  @Test
  void localSmtpAcceptsMessage() throws Exception {
    var sender = new JavaMailSenderImpl();
    sender.setHost(mailpit.getHost());
    sender.setPort(mailpit.getMappedPort(1025));
    var message = new SimpleMailMessage();
    message.setFrom("test@example.test");
    message.setTo("recipient@example.test");
    message.setSubject("Mycelium SMTP acceptance fixture");
    message.setText("Disposable test message; never delivered outside Mailpit.");
    sender.send(message);
    var request =
        java.net.http.HttpRequest.newBuilder(
                java.net.URI.create(
                    "http://"
                        + mailpit.getHost()
                        + ":"
                        + mailpit.getMappedPort(8025)
                        + "/api/v1/messages"))
            .build();
    var response =
        java.net.http.HttpClient.newHttpClient()
            .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("Mycelium SMTP acceptance fixture"));
  }
}
