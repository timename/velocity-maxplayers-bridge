package local.mmm.velocitybridge;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

final class PresenceHistory implements AutoCloseable {

    private static final long SAVE_DELAY_SECONDS = 5L;
    private static final long CLOSE_WAIT_SECONDS = 3L;

    private final DisconnectHistoryStore store;
    private final Clock clock;
    private final RateLimitedLogger warnings;
    private final ConcurrentMap<UUID, Instant> lastDisconnects = new ConcurrentHashMap<>();
    private final AtomicLong mutationVersion = new AtomicLong();
    private final ScheduledExecutorService writer = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "mmm-velocity-bridge-history");
        thread.setDaemon(true);
        return thread;
    });
    private final Object saveLock = new Object();
    private ScheduledFuture<?> scheduledSave;
    private boolean closing;

    PresenceHistory(Path file, Clock clock, RateLimitedLogger warnings) {
        this.store = new DisconnectHistoryStore(file);
        this.clock = clock;
        this.warnings = warnings;
    }

    void load() {
        try {
            lastDisconnects.putAll(store.load(clock.instant()));
        } catch (IOException exception) {
            warnings.warn("history-load", "无法读取玩家断开历史，将以空历史继续", exception);
        }
    }

    Optional<Instant> lastDisconnectAt(UUID playerId) {
        Instant disconnectAt = lastDisconnects.get(playerId);
        if (disconnectAt == null) {
            return Optional.empty();
        }
        if (!disconnectAt.isBefore(clock.instant().minus(DisconnectHistoryStore.RETENTION))) {
            return Optional.of(disconnectAt);
        }
        if (lastDisconnects.remove(playerId, disconnectAt)) {
            mutationVersion.incrementAndGet();
            scheduleSave();
        }
        return Optional.empty();
    }

    void markOnline(UUID playerId) {
        if (lastDisconnects.remove(playerId) != null) {
            mutationVersion.incrementAndGet();
            scheduleSave();
        }
    }

    void markDisconnected(UUID playerId) {
        lastDisconnects.put(playerId, clock.instant());
        mutationVersion.incrementAndGet();
        scheduleSave();
    }

    private void scheduleSave() {
        synchronized (saveLock) {
            if (closing) {
                return;
            }
            if (scheduledSave == null || scheduledSave.isDone()) {
                scheduledSave = writer.schedule(this::saveSnapshot, SAVE_DELAY_SECONDS, TimeUnit.SECONDS);
            }
        }
    }

    private void saveSnapshot() {
        long savedVersion = mutationVersion.get();
        try {
            store.save(Map.copyOf(lastDisconnects), clock.instant());
        } catch (IOException exception) {
            warnings.warn("history-save", "无法保存玩家断开历史", exception);
        } finally {
            synchronized (saveLock) {
                scheduledSave = null;
                if (!closing && mutationVersion.get() != savedVersion) {
                    scheduleSave();
                }
            }
        }
    }

    @Override
    public void close() {
        synchronized (saveLock) {
            closing = true;
            if (scheduledSave != null) {
                scheduledSave.cancel(false);
            }
        }
        Future<?> finalSave = writer.submit(this::saveSnapshot);
        try {
            finalSave.get(CLOSE_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException exception) {
            warnings.warn("history-close", "关闭时未能完成玩家断开历史保存", exception);
        } finally {
            writer.shutdownNow();
        }
    }
}
