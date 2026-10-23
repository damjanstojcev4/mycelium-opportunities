package com.mycelium.opportunities.opportunity;

import com.mycelium.opportunities.auth.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.security.Principal;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class SavedController {
  private final SavedService saved;
  private final AccountService accounts;

  public SavedController(SavedService saved, AccountService accounts) {
    this.saved = saved;
    this.accounts = accounts;
  }

  public record Bookmark(@NotNull UUID opportunityId) {}

  public record State(@NotNull String status) {}

  @GetMapping("/saved-opportunities")
  public Object list(
      Principal p,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "") String type) {
    return saved.list(accounts.id(p.getName()), page, type);
  }

  @PostMapping("/saved-opportunities")
  public void save(Principal p, @Valid @RequestBody Bookmark body) {
    saved.save(accounts.id(p.getName()), body.opportunityId());
  }

  @DeleteMapping("/saved-opportunities/{id}")
  public void remove(Principal p, @PathVariable UUID id) {
    saved.remove(accounts.id(p.getName()), id);
  }

  @PatchMapping("/saved-opportunities/{id}/status")
  public void status(Principal p, @PathVariable UUID id, @Valid @RequestBody State body) {
    saved.status(accounts.id(p.getName()), id, body.status());
  }

  @GetMapping("/dashboard")
  public Object dashboard(Principal p) {
    return saved.dashboard(accounts.id(p.getName()));
  }
}
