package com.mycelium.opportunities;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycelium.opportunities.common.Json;
import com.mycelium.opportunities.source.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "liveSources", matches = "true")
class LiveSourceTest {
  @Test
  void ted() throws Exception {
    var result =
        new TedConnector(
                new SafeSourceHttp(),
                new Json(new ObjectMapper()),
                "buyer-country=MKD SORT BY publication-date DESC")
            .fetch(null);
    assertFalse(result.records().isEmpty());
  }

  @Test
  void funding() throws Exception {
    var result =
        new EuFundingConnector(new SafeSourceHttp(), new Json(new ObjectMapper())).fetch(null);
    assertFalse(result.records().isEmpty());
  }
}
