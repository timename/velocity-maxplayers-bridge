package local.mmm.paperbridge;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

final class RateLimitedWarningLogger {

    private static final Duration INTERVAL = Duration.ofMinutes(1);

    private final Logger logger;
    private final Clock clock;
    private final Map<String, Instant> lastWarnings = new ConcurrentHashMap<>();

    RateLimitedWarningLogger(Logger logger) {
        this(logger, Clock.systemUTC());
    }

    RateLimitedWarningLogger(Logger logger, Clock clock) {
        this.logger = logger;
        this.clock = clock;
    }

    synchronized void warn(String key, String message) {
        warn(key, message, null);
    }

    synchronized void warn(String key, String message, Throwable throwable) {
        Instant now = clock.instant();
        Instant previous = lastWarnings.get(key);
        if (previous != null && previous.plus(INTERVAL).isAfter(now)) {
            return;
        }
        lastWarnings.put(key, now);
        if (throwable == null) {
            logger.warning(message);
        } else {
            logger.log(java.util.logging.Level.WARNING, message, throwable);
        }
    }
}
