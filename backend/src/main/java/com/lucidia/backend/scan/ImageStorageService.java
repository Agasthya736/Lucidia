package com.lucidia.backend.scan;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Stores and retrieves scan image slices.
 *
 * <p><b>GCS mode</b> (production): activated when {@code GCS_BUCKET} env var is set.
 * Uses Application Default Credentials (ADC) — no additional config needed on Cloud Run.
 * Object key format: {@code scans/{scanId}/slice_{sliceIndex}.bin}
 *
 * <p><b>Local-disk mode</b> (dev): falls back to local filesystem when {@code GCS_BUCKET} is blank.
 * Configured via {@code lucidia.imaging.storage-path} (default: {@code /data/images}).
 */
@Service
public class ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);
    private static final String CONTENT_TYPE = "application/octet-stream";

    private final String gcsBucket;
    private final Storage gcsStorage;

    /** Non-null only in local-disk mode. */
    private final Path localStorageRoot;

    public ImageStorageService(
            @Value("${lucidia.imaging.gcs-bucket:}") String gcsBucket,
            @Value("${lucidia.imaging.storage-path:/data/images}") String localStoragePath) {

        this.gcsBucket = (gcsBucket != null) ? gcsBucket.trim() : "";

        if (!this.gcsBucket.isEmpty()) {
            // GCS mode
            this.gcsStorage = StorageOptions.getDefaultInstance().getService();
            this.localStorageRoot = null;
            log.info("ImageStorageService initialised in GCS mode (bucket={})", this.gcsBucket);
        } else {
            // Local-disk fallback for development
            this.gcsStorage = null;
            this.localStorageRoot = resolveLocalRoot(localStoragePath);
            log.info("ImageStorageService initialised in local-disk mode (root={})", this.localStorageRoot);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API (identical signature as before)
    // ─────────────────────────────────────────────────────────────────────────

    /** Save the primary image (slice 0 alias). */
    public void save(UUID scanId, byte[] imageBytes) {
        saveSlice(scanId, 0, imageBytes);
    }

    public void saveSlice(UUID scanId, int sliceIndex, byte[] sliceBytes) {
        if (isGcsMode()) {
            BlobId blobId = BlobId.of(gcsBucket, gcsSlicePath(scanId, sliceIndex));
            BlobInfo info = BlobInfo.newBuilder(blobId).setContentType(CONTENT_TYPE).build();
            gcsStorage.create(info, sliceBytes);
        } else {
            try {
                Files.write(localSlicePath(scanId, sliceIndex), sliceBytes);
            } catch (IOException e) {
                throw new RuntimeException("Failed to save slice " + sliceIndex + " for scan " + scanId, e);
            }
        }
    }

    /** Load the primary image (slice 0). */
    public byte[] load(UUID scanId) {
        return loadSlice(scanId, 0);
    }

    public byte[] loadSlice(UUID scanId, int sliceIndex) {
        if (isGcsMode()) {
            byte[] bytes = gcsStorage.readAllBytes(gcsBucket, gcsSlicePath(scanId, sliceIndex));
            if (bytes == null || bytes.length == 0) {
                // Try legacy path (no slice suffix) for old uploads
                if (sliceIndex == 0) {
                    bytes = gcsStorage.readAllBytes(gcsBucket, gcsLegacyPath(scanId));
                }
                if (bytes == null || bytes.length == 0) {
                    throw new java.util.NoSuchElementException(
                            "No stored slice " + sliceIndex + " for scan " + scanId);
                }
            }
            return bytes;
        } else {
            try {
                Path slicePath = localSlicePath(scanId, sliceIndex);
                if (Files.exists(slicePath)) {
                    return Files.readAllBytes(slicePath);
                }
                if (sliceIndex == 0) {
                    Path legacyPath = localLegacyPath(scanId);
                    if (Files.exists(legacyPath)) {
                        return Files.readAllBytes(legacyPath);
                    }
                }
                throw new java.util.NoSuchElementException(
                        "No stored slice " + sliceIndex + " for scan " + scanId);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load slice " + sliceIndex + " for scan " + scanId, e);
            }
        }
    }

    /**
     * Deletes all stored slices for the given scan. Called during account deletion
     * and by the retention scheduler.
     */
    public void deleteAllSlicesForScan(UUID scanId, int sliceCount) {
        if (isGcsMode()) {
            for (int i = 0; i < sliceCount; i++) {
                try {
                    gcsStorage.delete(BlobId.of(gcsBucket, gcsSlicePath(scanId, i)));
                } catch (Exception e) {
                    log.warn("GCS delete failed for scan {} slice {}: {}", scanId, i, e.getMessage());
                }
            }
            // Also attempt legacy path cleanup
            try {
                gcsStorage.delete(BlobId.of(gcsBucket, gcsLegacyPath(scanId)));
            } catch (Exception ignored) {}
        } else {
            for (int i = 0; i < sliceCount; i++) {
                try {
                    Files.deleteIfExists(localSlicePath(scanId, i));
                } catch (IOException e) {
                    log.warn("Local delete failed for scan {} slice {}: {}", scanId, i, e.getMessage());
                }
            }
            try { Files.deleteIfExists(localLegacyPath(scanId)); } catch (IOException ignored) {}
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private boolean isGcsMode() {
        return !gcsBucket.isEmpty();
    }

    private static String gcsSlicePath(UUID scanId, int sliceIndex) {
        return "scans/" + scanId + "/slice_" + sliceIndex + ".bin";
    }

    private static String gcsLegacyPath(UUID scanId) {
        return "scans/" + scanId + "/" + scanId + ".bin";
    }

    private Path localSlicePath(UUID scanId, int sliceIndex) {
        return localStorageRoot.resolve(scanId + "_slice_" + sliceIndex + ".bin");
    }

    private Path localLegacyPath(UUID scanId) {
        return localStorageRoot.resolve(scanId + ".bin");
    }

    private static Path resolveLocalRoot(String storagePath) {
        Path resolved = Paths.get(storagePath);
        try {
            Files.createDirectories(resolved);
            return resolved;
        } catch (Exception e) {
            Path fallback = Paths.get(System.getProperty("user.home"), ".lucidia", "images");
            try {
                Files.createDirectories(fallback);
                return fallback;
            } catch (Exception ex) {
                Path tmp = Paths.get(System.getProperty("java.io.tmpdir"), "lucidia-images");
                try { Files.createDirectories(tmp); } catch (Exception ignored) {}
                return tmp;
            }
        }
    }
}