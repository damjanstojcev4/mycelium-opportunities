package com.mycelium.opportunities.company;

import com.mycelium.opportunities.auth.AccountService;
import com.mycelium.opportunities.matching.MatchingService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies")
public class CompanyController {
  private final CompanyService companies;
  private final AccountService accounts;
  private final MatchingService matches;

  public CompanyController(
      CompanyService companies, AccountService accounts, MatchingService matches) {
    this.companies = companies;
    this.accounts = accounts;
    this.matches = matches;
  }

  @GetMapping
  public Object list(Principal p) {
    return companies.list(accounts.id(p.getName()));
  }

  @PostMapping
  public Object create(Principal p, @Valid @RequestBody CompanyService.Profile body) {
    return Map.of("id", companies.save(null, accounts.id(p.getName()), body));
  }

  @PatchMapping("/{id}")
  public Object update(
      Principal p, @PathVariable UUID id, @Valid @RequestBody CompanyService.Profile body) {
    return Map.of("id", companies.save(id, accounts.id(p.getName()), body));
  }

  @GetMapping("/{id}/matches")
  public Object matches(
      Principal p, @PathVariable UUID id, @RequestParam(defaultValue = "0") int page) {
    companies.requireOwner(id, accounts.id(p.getName()));
    return matches.list(id, page);
  }
}
