package local.mmm.paperbridge;

import java.util.UUID;

record PresencePush(long sequence, UUID targetPlayerId, ProxyPresenceSnapshot snapshot) {
}
