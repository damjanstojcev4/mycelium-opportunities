package com.mycelium.opportunities.auth;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService implements UserDetailsService {
  private final JdbcTemplate db;
  private final PasswordEncoder passwords;

  public AccountService(JdbcTemplate db, PasswordEncoder passwords) {
    this.db = db;
    this.passwords = passwords;
  }

  public UserDetails loadUserByUsername(String email) {
    var rows =
        db.queryForList(
            "select email,password_hash,role from app_user where email=?", normalize(email));
    if (rows.isEmpty()) throw new UsernameNotFoundException("Invalid credentials");
    var u = rows.getFirst();
    return User.withUsername((String) u.get("email"))
        .password((String) u.get("password_hash"))
        .roles((String) u.get("role"))
        .build();
  }

  public Map<String, Object> me(String email) {
    var rows =
        db.queryForList(
            "select id,email,full_name,role,timezone from app_user where email=?",
            normalize(email));
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    return rows.getFirst();
  }

  public UUID id(String email) {
    return (UUID) me(email).get("id");
  }

  @Transactional
  public void register(String email, String password, String name) {
    create(email, password, name, "USER");
  }

  public void create(String email, String password, String name, String role) {
    if (password.length() < 12 || password.length() > 72)
      throw new IllegalArgumentException("Password must be 12–72 characters");
    UUID id = UUID.randomUUID();
    db.update(
        "insert into app_user(id,email,password_hash,full_name,role) values(?,?,?,?,?)",
        id,
        normalize(email),
        passwords.encode(password),
        name,
        role);
    db.update("insert into notification_preference(user_id) values(?)", id);
  }

  private String normalize(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }
}
