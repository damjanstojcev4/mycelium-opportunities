package com.mycelium.opportunities.opportunity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "opportunity")
public class Opportunity {
  @Id private UUID id;

  @Column(nullable = false)
  private String title;

  protected Opportunity() {}

  public UUID getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }
}
