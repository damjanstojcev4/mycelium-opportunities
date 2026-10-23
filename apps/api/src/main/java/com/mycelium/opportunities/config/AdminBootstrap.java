package com.mycelium.opportunities.config;

import com.mycelium.opportunities.auth.AccountService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {
  private final AccountService accounts;
  private final JdbcTemplate db;
  private final String email, password;

  public AdminBootstrap(
      AccountService accounts,
      JdbcTemplate db,
      @Value("${app.admin-email}") String email,
      @Value("${app.admin-password}") String password) {
    this.accounts = accounts;
    this.db = db;
    this.email = email;
    this.password = password;
  }

  public void run(ApplicationArguments args) {
    if (!email.isBlank()
        && !password.isBlank()
        && db.queryForObject("select count(*) from app_user where role='ADMIN'", Long.class) == 0)
      accounts.create(email, password, "Administrator", "ADMIN");
  }
}
