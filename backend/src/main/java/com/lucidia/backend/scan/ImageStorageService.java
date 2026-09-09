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
        saveSlice(scanId, 0, imageBytes);
        try {
            Files.write(resolvePath(scanId), imageBytes);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save primary image for scan " + scanId, e);
        }
    }

    public void saveSlice(UUID scanId, int sliceIndex, byte[] sliceBytes) {
        try {
            Files.write(resolveSlicePath(scanId, sliceIndex), sliceBytes);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save slice " + sliceIndex + " for scan " + scanId, e);
        }
    }

    public byte[] load(UUID scanId) {
        return loadSlice(scanId, 0);
    }

    public byte[] loadSlice(UUID scanId, int sliceIndex) {
        try {
            Path slicePath = resolveSlicePath(scanId, sliceIndex);
            if (Files.exists(slicePath)) {
                return Files.readAllBytes(slicePath);
            }
            // Fallback to legacy single-image path if slice 0
            if (sliceIndex == 0) {
                Path legacyPath = resolvePath(scanId);
                if (Files.exists(legacyPath)) {
                    return Files.readAllBytes(legacyPath);
                }
            }
            throw new java.util.NoSuchElementException("No stored slice " + sliceIndex + " for scan " + scanId);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load slice " + sliceIndex + " for scan " + scanId, e);
        }
    }

    private Path resolvePath(UUID scanId) {
        return storageRoot.resolve(scanId.toString() + ".bin");
    }

    private Path resolveSlicePath(UUID scanId, int sliceIndex) {
        return storageRoot.resolve(scanId.toString() + "_slice_" + sliceIndex + ".bin");
    }
}