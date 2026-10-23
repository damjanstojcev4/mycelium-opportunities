package com.mycelium.opportunities.notification;

import com.mycelium.opportunities.auth.AccountService;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications/preferences")
public class NotificationController {
  private final AccountService accounts;
  private final NotificationService notifications;

  public NotificationController(AccountService accounts, NotificationService notifications) {
    this.accounts = accounts;
    this.notifications = notifications;
  }

  public record Preferences(
      boolean digest, boolean reminders, boolean changes, List<Integer> reminderDays) {}

  @GetMapping
  public Object get(Principal p) {
    return notifications.preferences(accounts.id(p.getName()));
  }

  @PutMapping
  public void put(Principal p, @RequestBody Preferences b) {
    notifications.preferences(
        accounts.id(p.getName()), b.digest(), b.reminders(), b.changes(), b.reminderDays());
  }
}
