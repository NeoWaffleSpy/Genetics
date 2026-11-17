package com.genetics.util;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Lazily created entity components. A Store can't be written to while it is running its systems (an interaction
 * opening the Lab Station runs inside one), so a component created then is added on the world thread right after,
 * and the same instance is handed out until it is: changes made meanwhile are kept.
 */
public final class Components {
    private static final Map<Pending, Component<EntityStore>> PENDING = new ConcurrentHashMap<>();

    private record Pending(Ref<EntityStore> ref, ComponentType<EntityStore, ?> type) {
    }

    private Components() {
    }

    @Nonnull
    @SuppressWarnings({"unchecked", "deprecation"})
    public static <T extends Component<EntityStore>> T getOrCreate(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor,
                                                                   @Nonnull ComponentType<EntityStore, T> type, @Nonnull Supplier<T> create) {
        T existing = accessor.getComponent(ref, type);
        if (existing != null) return existing;
        Pending key = new Pending(ref, type);
        T pending = (T) PENDING.get(key);
        if (pending != null) return pending;
        T created = create.get();
        if (accessor instanceof Store<EntityStore> store && store.isProcessing()) {
            PENDING.put(key, created);
            store.getExternalData().getWorld().execute(() -> {
                PENDING.remove(key);
                if (ref.isValid() && store.getComponent(ref, type) == null) store.putComponent(ref, type, created);
            });
        } else {
            accessor.putComponent(ref, type, created);
        }
        return created;
    }
}
