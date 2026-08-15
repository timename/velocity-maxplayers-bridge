package local.mmm.velocitybridge;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

final class RateLimitedLogger {

    private static final Duration INTERVAL = Duration.ofMinutes(1);

    private final Logger logger;
    private final Clock clock;
    private final Map<String, Instant> lastWarnings = new ConcurrentHashMap<>();

    RateLimitedLogger(Logger logger) {
        this(logger, Clock.systemUTC());
    }

    RateLimitedLogger(Logger logger, Clock clock) {
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
            logger.warn(message);
        } else {
            logger.warn(message, throwable);
        }
    }
}
