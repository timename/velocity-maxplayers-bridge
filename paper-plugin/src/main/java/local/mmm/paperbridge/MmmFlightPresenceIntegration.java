package local.mmm.paperbridge;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

final class MmmFlightPresenceIntegration {

    private static final String SERVICE_TYPE = "local.mmm.flight.api.ProxyPresenceService";
    private static final String PRESENCE_TYPE = "local.mmm.flight.api.ProxyPresence";
    private static final String STATE_TYPE = "local.mmm.flight.api.ProxyPresenceState";

    private MmmFlightPresenceIntegration() {
    }

    static PresenceChangeNotifier register(PaperBridgePlugin plugin, PresenceServiceProvider presenceService) {
        Plugin flightPlugin = Bukkit.getPluginManager().getPlugin("MMMFlight");
        if (flightPlugin == null) {
            return null;
        }

        try {
            ClassLoader flightClassLoader = flightPlugin.getClass().getClassLoader();
            Class<?> serviceType = Class.forName(SERVICE_TYPE, true, flightClassLoader);
            Class<?> presenceType = Class.forName(PRESENCE_TYPE, true, flightClassLoader);
            Class<?> stateType = Class.forName(STATE_TYPE, true, flightClassLoader);
            Constructor<?> presenceConstructor = presenceType.getConstructor(stateType, long.class, String.class);
            Method stateValueOf = stateType.getMethod("valueOf", String.class);
            InvocationHandler handler = new PresenceInvocationHandler(
                    presenceService, presenceConstructor, stateValueOf);
            Object service = Proxy.newProxyInstance(flightClassLoader, new Class<?>[] {serviceType}, handler);
            registerService(plugin, serviceType, service);
            return createPresenceChangeNotifier(flightClassLoader, presenceConstructor, stateValueOf);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "无法注册 MMMFlight Presence 服务，公共回能将安全停用", exception);
            return null;
        }
    }

    private static PresenceChangeNotifier createPresenceChangeNotifier(
            ClassLoader flightClassLoader,
            Constructor<?> presenceConstructor,
            Method stateValueOf
    ) {
        try {
            Class<?> eventType = Class.forName(
                    "local.mmm.flight.api.ProxyPresenceChangedEvent", true, flightClassLoader);
            Constructor<?> eventConstructor = eventType.getConstructor(UUID.class, presenceConstructor.getDeclaringClass());
            return new ReflectionPresenceChangeNotifier(eventConstructor, presenceConstructor, stateValueOf);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return (playerUuid, snapshot) -> { };
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerService(PaperBridgePlugin plugin, Class<?> serviceType, Object service) {
        plugin.getServer().getServicesManager().register(
                (Class) serviceType, service, plugin, ServicePriority.Normal);
    }

    private record PresenceInvocationHandler(PresenceServiceProvider presenceService,
                                             Constructor<?> presenceConstructor,
                                             Method stateValueOf) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> proxy == arguments[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "MMMVelocityBridge MMMFlight Presence 服务";
                    default -> throw new UnsupportedOperationException(method.getName());
                };
            }
            if (!"lookup".equals(method.getName()) || arguments == null
                    || arguments.length != 1 || !(arguments[0] instanceof UUID playerId)) {
                throw new UnsupportedOperationException(method.toString());
            }
            return presenceService.lookup(playerId).thenApply(this::createPresence);
        }

        private Object createPresence(ProxyPresenceSnapshot snapshot) {
            try {
                Object state = stateValueOf.invoke(null, snapshot.state().name());
                return presenceConstructor.newInstance(
                        state, snapshot.lastDisconnectEpochMillis(), snapshot.currentServer());
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("无法创建 MMMFlight Presence 响应", exception);
            }
        }
    }

    interface PresenceChangeNotifier {
        void notifyChanged(UUID playerUuid, ProxyPresenceSnapshot snapshot);
    }

    private record ReflectionPresenceChangeNotifier(
            Constructor<?> eventConstructor,
            Constructor<?> presenceConstructor,
            Method stateValueOf
    ) implements PresenceChangeNotifier {

        @Override
        public void notifyChanged(UUID playerUuid, ProxyPresenceSnapshot snapshot) {
            try {
                Object state = stateValueOf.invoke(null, snapshot.state().name());
                Object presence = presenceConstructor.newInstance(
                        state, snapshot.lastDisconnectEpochMillis(), snapshot.currentServer());
                Bukkit.getPluginManager().callEvent((Event) eventConstructor.newInstance(playerUuid, presence));
            } catch (ReflectiveOperationException | RuntimeException exception) {
                throw new IllegalStateException("无法通知 MMMFlight Presence 变化", exception);
            }
        }
    }
}
