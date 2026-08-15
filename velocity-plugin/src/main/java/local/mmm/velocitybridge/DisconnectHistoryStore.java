package local.mmm.velocitybridge;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class DisconnectHistoryStore {

    static final Duration RETENTION = Duration.ofDays(7);

    private final Path file;
    private final Gson gson = new Gson();

    DisconnectHistoryStore(Path file) {
        this.file = file;
    }

    Map<UUID, Instant> load(Instant now) throws IOException {
        if (Files.notExists(file)) {
            return Map.of();
        }

        try {
            JsonObject savedHistory = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            Map<UUID, Instant> loadedHistory = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : savedHistory.entrySet()) {
                Instant disconnectAt = Instant.ofEpochMilli(entry.getValue().getAsLong());
                if (!disconnectAt.isBefore(now.minus(RETENTION))) {
                    loadedHistory.put(UUID.fromString(entry.getKey()), disconnectAt);
                }
            }
            return loadedHistory;
        } catch (RuntimeException exception) {
            throw new IOException("无法解析离线历史文件", exception);
        }
    }

    void save(Map<UUID, Instant> history, Instant now) throws IOException {
        Instant oldestAllowed = now.minus(RETENTION);
        JsonObject savedHistory = new JsonObject();
        for (Map.Entry<UUID, Instant> entry : history.entrySet()) {
            if (!entry.getValue().isBefore(oldestAllowed)) {
                savedHistory.addProperty(entry.getKey().toString(), entry.getValue().toEpochMilli());
            }
        }

        Files.createDirectories(file.getParent());
        Path temporaryFile = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporaryFile, gson.toJson(savedHistory), StandardCharsets.UTF_8);
        try {
            Files.move(temporaryFile, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
