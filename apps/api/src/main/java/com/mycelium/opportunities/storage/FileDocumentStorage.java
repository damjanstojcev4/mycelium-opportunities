package com.mycelium.opportunities.storage;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FileDocumentStorage implements DocumentStorage {
  private final Path root;

  public FileDocumentStorage(@Value("${app.storage-root}") String root) {
    this.root = Path.of(root).toAbsolutePath().normalize();
  }

  public String put(byte[] content) throws IOException {
    if (content.length > 5_000_000) throw new IOException("Document too large");
    Files.createDirectories(root);
    String key = UUID.randomUUID().toString();
    Files.write(path(key), content, StandardOpenOption.CREATE_NEW);
    return key;
  }

  public byte[] get(String key) throws IOException {
    return Files.readAllBytes(path(key));
  }

  private Path path(String key) {
    UUID.fromString(key);
    Path path = root.resolve(key).normalize();
    if (!path.startsWith(root)) throw new IllegalArgumentException("Invalid document key");
    return path;
  }
}
