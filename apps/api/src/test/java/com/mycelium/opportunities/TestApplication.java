package com.mycelium.opportunities;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.testcontainers.containers.PostgreSQLContainer;

/** Disposable local acceptance-test database; never connects to the user's database. */
public class TestApplication {
  public static void main(String[] args) {
    var database = new PostgreSQLContainer<>("postgres:17-alpine");
    database.start();
    Runtime.getRuntime().addShutdownHook(new Thread(database::stop));
    var app = new SpringApplication(Application.class);
    app.setDefaultProperties(
        Map.of(
            "spring.datasource.url",
            database.getJdbcUrl(),
            "spring.datasource.username",
            database.getUsername(),
            "spring.datasource.password",
            database.getPassword(),
            "app.notifications-enabled",
            "false"));
    // Explicit system properties override application.yml's default placeholders.
    System.setProperty("spring.datasource.url", database.getJdbcUrl());
    System.setProperty("spring.datasource.username", database.getUsername());
    System.setProperty("spring.datasource.password", database.getPassword());
    app.run(args);
  }
}
