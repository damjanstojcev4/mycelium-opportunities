package com.mycelium.opportunities;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.mycelium.opportunities.company.CompanyService;
import com.mycelium.opportunities.ingestion.RecordWriter;
import com.mycelium.opportunities.opportunity.SavedService;
import com.mycelium.opportunities.source.SourceRecord;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(properties = "app.notifications-enabled=true")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class DatabaseIntegrationTest {
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired JdbcTemplate db;
  @Autowired RecordWriter writer;
  @Autowired MockMvc mvc;
  @Autowired CompanyService companies;
  @Autowired SavedService saved;
  @Autowired com.mycelium.opportunities.ingestion.IngestionService ingestion;
  @Autowired com.mycelium.opportunities.notification.NotificationService notifications;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  com.mycelium.opportunities.source.TedConnector ted;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  org.springframework.mail.javamail.JavaMailSender mail;

  @Test
  void flywayAndIdempotentRevisions() {
    UUID source = UUID.fromString("00000000-0000-0000-0000-000000000002");
    String external = UUID.randomUUID().toString();
    var r = record(external, "Fixture title");
    assertEquals("INSERTED", writer.write(source, r));
    assertEquals("UNCHANGED", writer.write(source, r));
    assertEquals("UPDATED", writer.write(source, record(external, "Changed fixture title")));
    assertEquals(
        1,
        db.queryForObject(
            "select count(*) from opportunity where external_id=?", Integer.class, external));
    assertEquals(
        2,
        db.queryForObject(
            "select count(*) from opportunity_revision r join opportunity o on r.opportunity_id=o.id where o.external_id=?",
            Integer.class,
            external));
  }

  @Test
  void unauthenticatedAndCsrfBlocked() throws Exception {
    mvc.perform(get("/api/v1/companies")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/sources").with(user("user").roles("USER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void ownershipIsEnforced() {
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> companies.requireOwner(UUID.randomUUID(), UUID.randomUUID()));
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> saved.status(UUID.randomUUID(), UUID.randomUUID(), "APPLIED"));
  }

  @Test
  void oneUserCannotReadOrEditAnotherCompany() throws Exception {
    String first = "owner-" + UUID.randomUUID() + "@example.test",
        second = "other-" + UUID.randomUUID() + "@example.test";
    UUID owner = UUID.randomUUID(), other = UUID.randomUUID(), company = UUID.randomUUID();
    db.update(
        "insert into app_user(id,email,password_hash,full_name,role) values(?,?,?,'Owner','USER'),(?,?,?,'Other','USER')",
        owner,
        first,
        "unused",
        other,
        second,
        "unused");
    db.update(
        "insert into company(id,owner_user_id,name,country_code) values(?,?,'Private company','MK')",
        company,
        owner);
    mvc.perform(get("/api/v1/companies/" + company + "/matches").with(user(second)))
        .andExpect(status().isNotFound());
    mvc.perform(
            patch("/api/v1/companies/" + company)
                .with(user(second))
                .with(csrf())
                .contentType("application/json")
                .content("{\"name\":\"Stolen\",\"countryCode\":\"MK\"}"))
        .andExpect(status().isNotFound());
    assertEquals(
        "Private company",
        db.queryForObject("select name from company where id=?", String.class, company));
  }

  @Test
  void savedItemsCannotBeChangedByAnotherOwner() throws Exception {
    UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
    String email = "saved-other-" + other + "@example.test";
    db.update(
        "insert into app_user(id,email,password_hash,full_name,role) values(?,?,?,'Owner','USER'),(?,?,?,'Other','USER')",
        owner,
        owner + "@example.test",
        "unused",
        other,
        email,
        "unused");
    String external = UUID.randomUUID().toString();
    writer.write(
        UUID.fromString("00000000-0000-0000-0000-000000000002"), record(external, "Saved fixture"));
    UUID opportunity =
        db.queryForObject("select id from opportunity where external_id=?", UUID.class, external);
    UUID savedId = UUID.randomUUID();
    db.update(
        "insert into saved_opportunity(id,user_id,opportunity_id) values(?,?,?)",
        savedId,
        owner,
        opportunity);
    mvc.perform(delete("/api/v1/saved-opportunities/" + savedId).with(user(email)).with(csrf()))
        .andExpect(status().isNotFound());
    mvc.perform(
            patch("/api/v1/saved-opportunities/" + savedId + "/status")
                .with(user(email))
                .with(csrf())
                .contentType("application/json")
                .content("{\"status\":\"WON\"}"))
        .andExpect(status().isNotFound());
    assertEquals(
        "SAVED",
        db.queryForObject(
            "select status from saved_opportunity where id=?", String.class, savedId));
  }

  @Test
  void pendingRecordsStayPrivate() throws Exception {
    String external = UUID.randomUUID().toString();
    writer.write(
        UUID.fromString("00000000-0000-0000-0000-000000000002"),
        record(external, "Private fixture"));
    UUID id =
        db.queryForObject("select id from opportunity where external_id=?", UUID.class, external);
    mvc.perform(get("/api/v1/opportunities/" + id)).andExpect(status().isNotFound());
  }

  @Test
  void failedSourcePreservesDataAndCanRecover() throws Exception {
    UUID source = UUID.fromString("00000000-0000-0000-0000-000000000002");
    String external = UUID.randomUUID().toString();
    var fixture = record(external, "Recovery fixture");
    writer.write(source, fixture);
    long count = db.queryForObject("select count(*) from opportunity", Long.class);
    org.mockito.Mockito.when(ted.sourceCode()).thenReturn("TED");
    org.mockito.Mockito.when(ted.fetch(null)).thenThrow(new java.io.IOException("Upstream 403"));
    db.update(
        "update source set enabled=true,access_status='VERIFIED',checkpoint=null where id=?",
        source);
    try {
      assertEquals("FAILED", ingestion.sync(source).get("status"));
      assertEquals(count, db.queryForObject("select count(*) from opportunity", Long.class));
      org.mockito.Mockito.doReturn(
              new com.mycelium.opportunities.source.OpportunitySourceConnector.FetchResult(
                  List.of(fixture), null, true))
          .when(ted)
          .fetch(null);
      assertEquals("SUCCESS", ingestion.sync(source).get("status"));
      assertEquals(count, db.queryForObject("select count(*) from opportunity", Long.class));
    } finally {
      db.update("update source set enabled=false where id=?", source);
    }
  }

  @Test
  void notificationRemindersDeduplicateAndSendOnce() {
    UUID user = UUID.randomUUID();
    db.update(
        "insert into app_user(id,email,password_hash,full_name,role) values(?,?,'unused','Email fixture','USER')",
        user,
        user + "@example.test");
    db.update("insert into notification_preference(user_id,reminders) values(?,true)", user);
    String external = UUID.randomUUID().toString();
    writer.write(
        UUID.fromString("00000000-0000-0000-0000-000000000002"),
        record(external, "Reminder fixture"));
    UUID opportunity =
        db.queryForObject("select id from opportunity where external_id=?", UUID.class, external);
    db.update(
        "update opportunity set review_status='APPROVED',deadline_at=now()+interval '7 days 12 hours',status='OPEN' where id=?",
        opportunity);
    db.update(
        "insert into saved_opportunity(id,user_id,opportunity_id) values(?,?,?)",
        UUID.randomUUID(),
        user,
        opportunity);
    notifications.prepare();
    notifications.prepare();
    assertEquals(
        1,
        db.queryForObject(
            "select count(*) from notification where user_id=?", Integer.class, user));
    notifications.deliver();
    notifications.deliver();
    assertEquals(
        "SENT",
        db.queryForObject("select status from notification where user_id=?", String.class, user));
    org.mockito.Mockito.verify(mail)
        .send(org.mockito.ArgumentMatchers.any(org.springframework.mail.SimpleMailMessage.class));
  }

  @Test
  void publishedSearchUsesFullTextAndFilters() throws Exception {
    String external = UUID.randomUUID().toString();
    writer.write(
        UUID.fromString("00000000-0000-0000-0000-000000000002"),
        record(external, "Уникатен софтвер"));
    db.update("update opportunity set review_status='APPROVED' where external_id=?", external);
    mvc.perform(
            get("/api/v1/opportunities")
                .param("q", "Уникатен")
                .param("type", "TENDER")
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].title").value("Уникатен софтвер"));
    mvc.perform(get("/api/v1/opportunities").param("q", "Уникатен").param("type", "GRANT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0));
  }

  private SourceRecord record(String id, String title) {
    return new SourceRecord(
        id,
        "TENDER",
        title,
        null,
        "Test fixture",
        null,
        null,
        null,
        null,
        null,
        null,
        "UNKNOWN",
        "https://ted.europa.eu/en/notice/-/detail/" + id,
        "en",
        Map.of(),
        List.of(),
        Map.of());
  }
}
