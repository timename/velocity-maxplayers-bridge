package local.mmm.velocitybridge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DisconnectHistoryStoreTest {

    private static final Instant NOW = Instant.parse("2026-08-15T00:00:00Z");
    private static final UUID RECENT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID EXPIRED_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsRecentEntriesAndDiscardsHistoryOlderThanSevenDays() throws Exception {
        Path file = temporaryDirectory.resolve("last-disconnects.json");
        DisconnectHistoryStore store = new DisconnectHistoryStore(file);
        Map<UUID, Instant> history = new HashMap<>();
        history.put(RECENT_ID, NOW.minus(Duration.ofDays(6)));
        history.put(EXPIRED_ID, NOW.minus(Duration.ofDays(8)));

        store.save(history, NOW);

        assertEquals(Map.of(RECENT_ID, NOW.minus(Duration.ofDays(6))), store.load(NOW));
        assertEquals(true, Files.exists(file));
    }
}
