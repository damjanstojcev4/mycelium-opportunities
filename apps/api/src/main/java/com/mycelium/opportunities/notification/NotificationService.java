package com.mycelium.opportunities.notification;

import com.mycelium.opportunities.common.Json;
import java.util.*;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
  private final JdbcTemplate db;
  private final JavaMailSender mail;
  private final Json json;
  private final boolean enabled;
  private final String from, url;
  private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

  public NotificationService(
      JdbcTemplate db,
      JavaMailSender mail,
      Json json,
      @Value("${app.notifications-enabled}") boolean enabled,
      @Value("${app.mail-from}") String from,
      @Value("${app.public-url}") String url) {
    this.db = db;
    this.mail = mail;
    this.json = json;
    this.enabled = enabled;
    this.from = from;
    this.url = url;
  }

  public Object preferences(UUID user) {
    var result = db.queryForMap("select * from notification_preference where user_id=?", user);
    result.put("reminder_days", json.read(result.get("reminder_days").toString()));
    return result;
  }

  public void preferences(
      UUID user, boolean digest, boolean reminders, boolean changes, List<Integer> days) {
    if (days == null
        || days.size() > 5
        || days.stream().anyMatch(d -> d == null || d < 1 || d > 30))
      throw new IllegalArgumentException("Choose up to five reminders between 1 and 30 days");
    db.update(
        "update notification_preference set digest=?,reminders=?,changes=?,reminder_days=?::jsonb where user_id=?",
        digest,
        reminders,
        changes,
        json.write(days),
        user);
  }

  @Scheduled(cron = "0 0 7 * * *", zone = "Europe/Skopje")
  public void prepare() {
    if (!enabled) return;
    for (var p : db.queryForList("select * from notification_preference")) {
      UUID user = (UUID) p.get("user_id");
      if (Boolean.TRUE.equals(p.get("digest"))) {
        var rows =
            db.queryForList(
                "select distinct o.id,o.title from company_match m join company c on c.id=m.company_id join opportunity o on o.id=m.opportunity_id where c.owner_user_id=? and m.relevance_score>=30 and o.review_status='APPROVED' and o.created_at>now()-interval '1 day' and o.published_at>now()-interval '7 days' and (o.deadline_at is null or o.deadline_at>now()) limit 20",
                user);
        if (!rows.isEmpty()) {
          StringBuilder body =
              new StringBuilder(
                  "New relevant opportunities. Eligibility must be checked with the official source.\n\n");
          rows.forEach(
              r ->
                  body.append(r.get("title"))
                      .append("\n")
                      .append(url)
                      .append("/opportunities/")
                      .append(r.get("id"))
                      .append("\n\n"));
          queue(
              user,
              null,
              "DIGEST",
              user + ":digest:" + java.time.LocalDate.now(java.time.ZoneId.of("Europe/Skopje")),
              "Your opportunity digest",
              body.toString());
        }
      }
      if (Boolean.TRUE.equals(p.get("reminders")))
        for (var day : json.read(p.get("reminder_days").toString())) {
          int days = day.asInt();
          var rows =
              db.queryForList(
                  "select o.id,o.title,o.deadline_at from saved_opportunity s join opportunity o on o.id=s.opportunity_id where s.user_id=? and s.status in ('SAVED','REVIEWING','PLANNING_TO_APPLY') and o.review_status='APPROVED' and o.status not in ('CANCELLED','CLOSED','AWARDED') and o.deadline_at>=now()+ (? * interval '1 day') and o.deadline_at<now()+ (? * interval '1 day')",
                  user,
                  days,
                  days + 1);
          for (var row : rows)
            queue(
                user,
                (UUID) row.get("id"),
                "DEADLINE",
                user + ":" + row.get("id") + ":" + row.get("deadline_at") + ":" + days,
                "Upcoming deadline: " + row.get("title"),
                "Source deadline: "
                    + row.get("deadline_at")
                    + "\nVerify the official announcement: "
                    + url
                    + "/opportunities/"
                    + row.get("id"));
        }
      if (Boolean.TRUE.equals(p.get("changes"))) {
        var rows =
            db.queryForList(
                "select o.id,o.title,o.content_hash from saved_opportunity s join opportunity o on o.id=s.opportunity_id join opportunity_revision r on r.opportunity_id=o.id and r.content_hash=o.content_hash where s.user_id=? and o.review_status='APPROVED' and r.created_at>s.created_at and not exists(select 1 from notification n where n.deduplication_key=s.user_id::text||':'||o.id::text||':'||o.content_hash) and (r.changed_fields @> '[\"deadlineAt\"]'::jsonb or (r.changed_fields @> '[\"status\"]'::jsonb and o.status='CANCELLED')) order by o.updated_at desc limit 20",
                user);
        for (var row : rows)
          queue(
              user,
              (UUID) row.get("id"),
              "CHANGE",
              user + ":" + row.get("id") + ":" + row.get("content_hash"),
              "Saved opportunity changed",
              row.get("title")
                  + "\nReview the confirmed update: "
                  + url
                  + "/opportunities/"
                  + row.get("id"));
      }
    }
  }

  private void queue(
      UUID user, UUID opportunity, String type, String key, String subject, String body) {
    db.update(
        "insert into notification(id,user_id,opportunity_id,type,deduplication_key,subject,body) values(?,?,?,?,?,?,?) on conflict do nothing",
        UUID.randomUUID(),
        user,
        opportunity,
        type,
        key,
        subject,
        body);
  }

  @Scheduled(fixedDelay = 60000)
  public void deliver() {
    if (!enabled) return;
    for (var n :
        db.queryForList(
            "select n.*,u.email from notification n join app_user u on u.id=n.user_id where n.status in ('QUEUED','RETRY') and n.next_attempt_at<=now() and n.attempts<5 order by created_at limit 20")) {
      if (db.update(
              "update notification set status='SENDING',attempts=attempts+1 where id=? and status in ('QUEUED','RETRY')",
              n.get("id"))
          == 0) continue;
      try {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo((String) n.get("email"));
        message.setSubject((String) n.get("subject"));
        message.setText((String) n.get("body"));
        mail.send(message);
        db.update(
            "update notification set status='SENT',sent_at=now(),last_error=null where id=?",
            n.get("id"));
      } catch (Exception e) {
        log.warn("Email delivery failed for notification {}", n.get("id"), e);
        db.update(
            "update notification set status=case when attempts>=5 then 'FAILED' else 'RETRY' end,next_attempt_at=now()+interval '15 minutes',last_error='SMTP delivery failed' where id=?",
            n.get("id"));
      }
    }
  }
}
