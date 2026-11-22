package com.doqsa.backend.adapter.out.storage.local;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.io.*;
import java.net.URLConnection;

/**
 * Upload/download endpoints that stand in for presigned URLs while the local storage plugin is active.
 * Lives in the plugin package so removing the plugin removes these endpoints too.
 */
@RestController @Profile("!prod") @RequiredArgsConstructor
@ConditionalOnProperty(name = LocalStorageConfiguration.PROVIDER_PROPERTY,
        havingValue = LocalStorageConfiguration.PROVIDER, matchIfMissing = true)
public class LocalImageController {
    private final LocalImageStorage storage;
    @PutMapping("/api/dev/uploads/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void upload(@PathVariable String token, HttpServletRequest request) throws IOException {
        storage.upload(token, request.getInputStream());
    }
    @GetMapping("/api/dev/images/{key}")
    public ResponseEntity<byte[]> image(@PathVariable String key) throws IOException {
        if (!storage.exists(key)) return ResponseEntity.notFound().build();
        var bytes = storage.read(key);
        var type = URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(bytes));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(type == null ? "application/octet-stream" : type)).body(bytes);
    }
}
