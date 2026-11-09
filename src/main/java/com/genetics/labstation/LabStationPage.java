package com.genetics.labstation;

import com.genetics.genes.GeneSample;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.NonSerialized;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerBlockWindow;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.joml.Vector3d;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Map;

/**
 * The Lab Station screen: the station's single syringe slot, the player's inventory, and an analysis of the
 * sample in the slot (mob name and icon, max stats, one card per status effect). While a filled syringe is in
 * the slot, a scaled-down copy of the sampled mob's model stands on top of the station.
 */
public class LabStationPage extends InteractiveCustomUIPage<LabStationPage.EventPayload> {
    private static final String LAYOUT = "Pages/Genetics/LabStation.ui";
    private static final String STAT_ROW = "Pages/Genetics/StatRow.ui";
    private static final String EFFECT_CARD = "Pages/Genetics/EffectCard.ui";
    private static final float PREVIEW_SCALE = 0.5F;
    private static final double PREVIEW_HEIGHT = 1.0;

    private final World world;
    private final Vector3i blockPos;
    private final ItemContainer container;
    private final ContainerBlockWindow window;

    @Nullable
    private EventRegistration<?, ?> containerListener;
    @Nullable
    private Ref<EntityStore> preview;
    @Nullable
    private String previewModelId;
    private boolean closed;

    public LabStationPage(
            @Nonnull PlayerRef playerRef, @Nonnull World world, @Nonnull Vector3i blockPos, @Nonnull ItemContainer container, @Nonnull ContainerBlockWindow window
    ) {
        super(playerRef, CustomPageLifetime.CanDismiss, EventPayload.CODEC);
        this.world = world;
        this.blockPos = blockPos;
        this.container = container;
        this.window = window;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands, @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        commands.append(LAYOUT);
        commands.set("#SampleSlot.InventorySectionId", window.getId());
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", new EventData().append("Action", "Close"), false);

        writeSample(commands);
        updatePreview(ref, store);

        if (containerListener == null) {
            containerListener = container.registerChangeEvent(event -> world.execute(this::refresh));
            window.registerCloseEvent(event -> world.execute(this::close));
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull EventPayload data) {
        if ("Close".equals(data.action)) close();
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (closed) return;
        closed = true;

        if (containerListener != null) containerListener.unregister();
        removePreview(store);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null && player.getWindowManager().getWindow(window.getId()) == window) {
            player.getWindowManager().closeWindow(ref, window.getId(), store);
        }
    }

    /** Re-renders the analysis panel and preview after the slot changes. */
    private void refresh() {
        if (closed) return;
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null) return;

        UICommandBuilder commands = new UICommandBuilder();
        writeSample(commands);
        sendUpdate(commands, false);
        updatePreview(ref, ref.getStore());
    }

    private void writeSample(@Nonnull UICommandBuilder commands) {
        commands.clear("#StatList");
        commands.clear("#EffectList");

        BsonDocument sample = GeneSample.read(container.getItemStack((short) 0));
        commands.set("#EmptyLabel.Visible", sample == null);
        commands.set("#Details.Visible", sample != null);
        if (sample == null) return;

        commands.set("#MobName.Text", string(sample, "MobName", "Unknown"));
        BsonDocument model = sample.isDocument("Model") ? sample.getDocument("Model") : null;
        String icon = model != null ? string(model, "Icon", null) : null;
        commands.set("#MobIcon.Visible", icon != null);
        if (icon != null) commands.set("#MobIcon.AssetPath", icon);

        if (sample.isDocument("Stats")) {
            int i = 0;
            for (Map.Entry<String, BsonValue> stat : sample.getDocument("Stats").entrySet()) {
                if (!stat.getValue().isDocument()) continue;
                String row = "#StatList[" + i++ + "]";
                commands.append("#StatList", STAT_ROW);
                commands.set(row + " #Name.Text", stat.getKey());
                commands.set(row + " #Value.Text", number(stat.getValue().asDocument(), "Max"));
            }
        }

        BsonArray effects = sample.isArray("Effects") ? sample.getArray("Effects") : new BsonArray();
        commands.set("#NoEffects.Visible", effects.isEmpty());
        int i = 0;
        for (BsonValue value : effects) {
            if (!value.isDocument()) continue;
            BsonDocument effect = value.asDocument();
            String card = "#EffectList[" + i++ + "]";
            commands.append("#EffectList", EFFECT_CARD);
            commands.set(card + " #Name.Text", string(effect, "Name", string(effect, "Id", "Unknown")));
            commands.set(card + " #Details.Text", describeEffect(effect));

            String effectIcon = string(effect, "Icon", null);
            commands.set(card + " #Icon.Visible", effectIcon != null);
            if (effectIcon != null) commands.set(card + " #Icon.AssetPath", effectIcon);
        }
    }

    /** Spawns, swaps or removes the model standing on the station to match the slot. */
    private void updatePreview(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        BsonDocument sample = GeneSample.read(container.getItemStack((short) 0));
        BsonDocument modelData = sample != null && sample.isDocument("Model") ? sample.getDocument("Model") : null;
        String modelId = modelData != null ? string(modelData, "Id", null) : null;
        if (modelId != null && modelId.equals(previewModelId) && preview != null && preview.isValid()) return;

        removePreview(store);
        ModelAsset asset = modelId != null ? ModelAsset.getAssetMap().getAsset(modelId) : null;
        if (asset == null) return;

        float scale = (float) (modelData.isNumber("Scale") ? modelData.getNumber("Scale").doubleValue() : 1.0) * PREVIEW_SCALE;
        Vector3d position = new Vector3d(blockPos.x + 0.5, blockPos.y + PREVIEW_HEIGHT, blockPos.z + 0.5);
        Rotation3f rotation = facing(ref, store, position);

        Holder<EntityStore> holder = store.getRegistry().newHolder();
        holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.addComponent(EntityStore.REGISTRY.getNonSerializedComponentType(), NonSerialized.get());
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(position, rotation));
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(Model.createScaledModel(asset, scale)));
        holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(rotation));
        preview = store.addEntity(holder, AddReason.SPAWN);
        previewModelId = modelId;
    }

    private void removePreview(@Nonnull Store<EntityStore> store) {
        if (preview != null && preview.isValid()) store.removeEntity(preview, RemoveReason.REMOVE);
        preview = null;
        previewModelId = null;
    }

    /** Rotation that makes the preview look at the player. */
    @Nonnull
    private static Rotation3f facing(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Vector3d position) {
        TransformComponent player = store.getComponent(ref, TransformComponent.getComponentType());
        if (player == null) return new Rotation3f();
        Vector3d relative = new Vector3d(player.getPosition()).sub(position);
        relative.y = 0;
        return Rotation3f.lookAt(relative);
    }

    @Nonnull
    private static String describeEffect(@Nonnull BsonDocument effect) {
        String kind = effect.isBoolean("Debuff") && effect.getBoolean("Debuff").getValue() ? "Debuff" : "Buff";
        if (effect.isBoolean("Infinite") && effect.getBoolean("Infinite").getValue()) return kind + "  ·  Infinite";
        return kind + "  ·  " + number(effect, "RemainingDuration") + "s left of " + number(effect, "InitialDuration") + "s";
    }

    @Nullable
    private static String string(@Nonnull BsonDocument doc, @Nonnull String key, @Nullable String fallback) {
        return doc.isString(key) ? doc.getString(key).getValue() : fallback;
    }

    @Nonnull
    private static String number(@Nonnull BsonDocument doc, @Nonnull String key) {
        if (!doc.isNumber(key)) return "-";
        double value = doc.getNumber(key).doubleValue();
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    /** Data sent back by the page's buttons. */
    public static class EventPayload {
        public static final BuilderCodec<EventPayload> CODEC = BuilderCodec.builder(EventPayload.class, EventPayload::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (payload, value) -> payload.action = value, payload -> payload.action)
                .add()
                .build();

        private String action;
    }
}
