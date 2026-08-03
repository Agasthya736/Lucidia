package com.lucidia.backend.scan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ImageStorageService {

    private final Path storageRoot;

    public ImageStorageService(@Value("${lucidia.imaging.storage-path}") String storagePath) {
        this.storageRoot = Paths.get(storagePath);
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize image storage directory: " + storagePath, e);
        }
    }

    public void save(UUID scanId, byte[] imageBytes) {
        try {
            Files.write(resolvePath(scanId), imageBytes);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save image for scan " + scanId, e);
        }
    }

    public byte[] load(UUID scanId) {
        try {
            Path path = resolvePath(scanId);
            if (!Files.exists(path)) {
                throw new java.util.NoSuchElementException("No stored image for scan " + scanId);
            }
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load image for scan " + scanId, e);
        }
    }

    private Path resolvePath(UUID scanId) {
        // Stored as raw bytes keyed by scan id; content-type is inferred
        // separately from the original filename extension at serve time.
        return storageRoot.resolve(scanId.toString() + ".bin");
    }
}