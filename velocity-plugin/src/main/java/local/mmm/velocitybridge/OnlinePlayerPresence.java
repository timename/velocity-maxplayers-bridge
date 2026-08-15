package local.mmm.velocitybridge;

import java.util.UUID;

public record OnlinePlayerPresence(UUID uniqueId, String currentServer) {
}
