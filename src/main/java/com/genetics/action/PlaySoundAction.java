package com.genetics.action;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** {@code { "Type": "PlaySound", "Id": "SFX_…", "Pitch": 1.0 }}: plays a sound event from the entity, heard by everyone nearby. */
public class PlaySoundAction extends Action {
    public static final String TYPE = "PlaySound";

    public static final BuilderCodec<PlaySoundAction> CODEC = BuilderCodec.builder(PlaySoundAction.class, PlaySoundAction::new, BASE_CODEC)
            .append(new KeyedCodec<>("Id", Codec.STRING), (t, v) -> t.id = v, t -> t.id)
            .documentation("Sound event id.").add()
            .append(new KeyedCodec<>("Pitch", Codec.DOUBLE), (t, v) -> t.pitch = v, t -> t.pitch)
            .documentation("Pitch multiplier. Default 1.").add()
            .build();

    private String id;
    private double pitch = 1;

    @Nullable
    public String getId() {
        return id;
    }

    @Override
    public void run(@Nonnull ActionContext context) {
        play(context, id, pitch);
    }

    static void play(@Nonnull ActionContext context, @Nullable String soundId, double pitch) {
        if (soundId == null) return;
        int index = SoundEvent.getAssetMap().getIndex(soundId);
        TransformComponent transform = context.buffer().getComponent(context.entity(), TransformComponent.getComponentType());
        if (index == Integer.MIN_VALUE || transform == null) return;
        Vector3d at = transform.getPosition();
        // A positioned world sound: entity-attached sounds aren't played back to the entity's own player.
        SoundUtil.playSoundEvent3d(index, SoundCategory.SFX, at.x, at.y + 1.5, at.z, 1.0F, (float) pitch, context.buffer());
    }
}
