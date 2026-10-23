package com.mycelium.opportunities;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycelium.opportunities.common.Json;
import com.mycelium.opportunities.source.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class SourceFixtureTest {
  @Test
  void parsesRealIpardHtmlSnapshotWithoutNetwork() throws Exception {
    var http = mock(SafeSourceHttp.class);
    when(http.get("https://www.ipardpa.gov.mk/mk/Home/JavniPovici/2"))
        .thenReturn(Files.readString(Path.of("../../docs/data-sources/ipard-listing.html")));
    when(http.get("https://www.ipardpa.gov.mk/mk/Home/RuralenPovici/11/75"))
        .thenReturn(Files.readString(Path.of("../../docs/data-sources/ipard-detail.html")));
    var result = new IpardConnector(http).fetch(null);
    assertEquals(1, result.records().size());
    var call = result.records().getFirst();
    assertTrue(call.title().contains("04/2026"));
    assertEquals(3, call.documents().size());
    assertNull(call.deadlineAt());
    call.documents().forEach(SafeSourceHttp::validate);
  }

  @Test
  void parsesRealFundingSnapshotWithoutInventingDeadlineTime() throws Exception {
    var result =
        new EuFundingConnector(null, new Json(new ObjectMapper()))
            .parse(Files.readString(Path.of("../../docs/data-sources/funding-sample.json")), 1);
    assertFalse(result.records().isEmpty());
    var call = result.records().getFirst();
    assertNotNull(call.details().get("deadlineDates"));
    assertNull(call.deadlineAt());
    assertEquals("CLOSED", call.status());
  }

  @Test
  void changedIpardMarkupFailsInsteadOfEmptySuccess() throws Exception {
    var http = mock(SafeSourceHttp.class);
    when(http.get(anyString())).thenReturn("<html><body>Maintenance</body></html>");
    assertThrows(IllegalArgumentException.class, () -> new IpardConnector(http).fetch(null));
  }
}
