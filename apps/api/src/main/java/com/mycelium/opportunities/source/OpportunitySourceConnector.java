package com.mycelium.opportunities.source;

import java.util.List;

public interface OpportunitySourceConnector {
  String sourceCode();

  FetchResult fetch(String checkpoint) throws Exception;

  record FetchResult(List<SourceRecord> records, String nextCheckpoint, boolean complete) {}
}
