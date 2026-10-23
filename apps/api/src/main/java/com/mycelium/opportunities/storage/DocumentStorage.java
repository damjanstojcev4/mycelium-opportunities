package com.mycelium.opportunities.storage;

import java.io.IOException;

public interface DocumentStorage {
  String put(byte[] content) throws IOException;

  byte[] get(String key) throws IOException;
}
