package com.gladunalexander.lsmkv.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gladunalexander.lsmkv.engine.Store;

/**
 * REST front-end for the key-value engine.
 *
 * <ul>
 *   <li>{@code PUT /{key}} with the value in the body — creates or updates, returns 200.</li>
 *   <li>{@code GET /{key}} — returns 200 with the value, or 404 if absent.</li>
 *   <li>{@code DELETE /{key}} — deletes the key (writes a tombstone), returns 200.</li>
 * </ul>
 *
 * Keys must be lowercase ASCII; values must be ASCII.
 */
@RestController
public class KvController {

    private final Store store;

    public KvController(Store store) {
        this.store = store;
    }

    @PutMapping("/{key}")
    public ResponseEntity<String> put(@PathVariable("key") String key, InputStream body) throws IOException {
        if (!isLowercaseAscii(key)) {
            return ResponseEntity.badRequest().body("key must be lowercase ASCII\n");
        }
        // Read the raw request body so we are agnostic to the client's Content-Type.
        String value = new String(body.readAllBytes(), StandardCharsets.US_ASCII);
        if (!isAscii(value)) {
            return ResponseEntity.badRequest().body("value must be ASCII\n");
        }
        store.put(key, value);
        return ResponseEntity.ok("OK\n");
    }

    @GetMapping("/{key}")
    public ResponseEntity<String> get(@PathVariable("key") String key) {
        Optional<String> value = store.get(key);
        return value.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<String> delete(@PathVariable("key") String key) {
        if (!isLowercaseAscii(key)) {
            return ResponseEntity.badRequest().body("key must be lowercase ASCII\n");
        }
        store.delete(key);
        return ResponseEntity.ok("OK\n");
    }

    private static boolean isLowercaseAscii(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c > 0x7F || Character.isUpperCase(c)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAscii(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 0x7F) {
                return false;
            }
        }
        return true;
    }
}
