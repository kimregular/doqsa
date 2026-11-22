package com.doqsa.backend.adapter.out.storage;

import com.doqsa.backend.application.model.OcrModels.Upload;
import com.doqsa.backend.application.port.out.ImageStorage;
import com.doqsa.backend.application.service.OcrExceptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component @Profile("!prod")
@ConditionalOnProperty(name = "doqsa.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalImageStorage implements ImageStorage {
    private final Path root;
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private record Ticket(String key, Instant expiresAt) { }

    public LocalImageStorage(@Value("${doqsa.storage.local-directory:./data/images}") String directory) throws IOException {
        root = Path.of(directory).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override public Upload issueUpload(String fileName, String contentType) {
        return reissueUpload(UUID.randomUUID().toString(), contentType);
    }
    @Override public Upload reissueUpload(String key, String contentType) {
        path(key);
        var now = Instant.now();
        tickets.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        var token = UUID.randomUUID().toString();
        var expiresAt = now.plus(Duration.ofMinutes(10));
        tickets.put(token, new Ticket(key, expiresAt));
        return new Upload(key, "/api/dev/uploads/" + token, expiresAt);
    }
    @Override public boolean exists(String key) { return Files.isRegularFile(path(key)); }
    @Override public byte[] read(String key) {
        try { return Files.readAllBytes(path(key)); }
        catch (IOException exception) { throw new OcrExceptions.Retryable("Local image read failed", exception); }
    }
    @Override public String readUrl(String key) { path(key); return "/api/dev/images/" + key; }

    public void upload(String token, InputStream input) throws IOException {
        var ticket = tickets.get(token);
        if (ticket == null || !ticket.expiresAt().isAfter(Instant.now())) throw new OcrExceptions.NotFound();
        // Local development endpoint has a bounded body; production uses S3 directly.
        byte[] bytes = input.readNBytes(10 * 1024 * 1024 + 1);
        if (bytes.length == 0 || bytes.length > 10 * 1024 * 1024) throw new IllegalArgumentException("Image must be between 1 byte and 10 MiB");
        Path temporary = Files.createTempFile(root, "upload-", ".tmp");
        try {
            Files.write(temporary, bytes);
            Files.move(temporary, path(ticket.key()), StandardCopyOption.REPLACE_EXISTING);
            tickets.remove(token, ticket);
        } finally { Files.deleteIfExists(temporary); }
    }

    private Path path(String key) {
        if (key == null || !key.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException("Invalid image key");
        return root.resolve(key);
    }
}
