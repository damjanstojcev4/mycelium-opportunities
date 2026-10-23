package com.mycelium.opportunities.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
  private final AccountService accounts;

  public AuthController(AccountService accounts) {
    this.accounts = accounts;
  }

  public record Registration(
      @Email @NotBlank @Size(max = 254) String email,
      @NotBlank @Size(min = 12, max = 72) String password,
      @NotBlank @Size(max = 160) String fullName) {}

  @GetMapping("/auth/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @PostMapping("/auth/register")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public void register(@Valid @RequestBody Registration body) {
    accounts.register(body.email(), body.password(), body.fullName());
  }

  @GetMapping("/me")
  public Map<String, Object> me(Principal user) {
    return accounts.me(user.getName());
  }
}
