package com.garmentline.operations.config;

import com.garmentline.operations.supabase.SupabaseAdminClient;
import com.tradex.unionnorth.common.storage.FileStorageService;
import com.tradex.unionnorth.common.storage.StoredFile;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/** Private employee photos use the same managed object-storage platform as LineMatrix. */
@Service
@Profile("payroll")
public class SupabasePayrollStorageService implements FileStorageService {
  private static final int MAX_BYTES = 6 * 1024 * 1024;

  private final SupabaseAdminClient supabase;
  private final String bucket;

  public SupabasePayrollStorageService(
      SupabaseAdminClient supabase,
      @Value("${app.payroll.storage-bucket}") String bucket) {
    this.supabase = supabase;
    this.bucket = bucket;
  }

  @Override
  public StoredFile store(String originalFilename, String contentType, InputStream inputStream)
      throws IOException {
    byte[] bytes = inputStream.readNBytes(MAX_BYTES + 1);
    if (bytes.length > MAX_BYTES) throw new IOException("Employee photo exceeds storage limit");
    String safeName = originalFilename == null ? "file" : originalFilename.replaceAll("[^A-Za-z0-9._-]", "_");
    String key = "employee-photos/" + UUID.randomUUID() + "-" + safeName;
    supabase.uploadObject(bucket, key, bytes, contentType);
    return new StoredFile(key, originalFilename, contentType, bytes.length);
  }

  @Override
  public Optional<Resource> load(String storageKey) {
    if (!validKey(storageKey)) return Optional.empty();
    try {
      byte[] bytes = supabase.downloadObject(bucket, storageKey);
      return bytes == null ? Optional.empty() : Optional.of(new ByteArrayResource(bytes));
    } catch (com.garmentline.operations.support.ApiException exception) {
      if (exception.getStatus().value() == 404) return Optional.empty();
      throw exception;
    }
  }

  @Override
  public void delete(String storageKey) throws IOException {
    if (!validKey(storageKey)) throw new IOException("Invalid storage key");
    supabase.deleteObject(bucket, storageKey);
  }

  private boolean validKey(String key) {
    return key != null && key.matches("employee-photos/[A-Za-z0-9._-]+") && !key.contains("..");
  }
}
