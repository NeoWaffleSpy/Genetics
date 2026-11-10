package com.genetics.action;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.asset.type.camera.CameraEffect;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** {@code { "Type": "CameraShake", "Id": "Impact_Strong" }}: shakes the player's camera, using a vanilla camera effect. */
public class CameraShakeAction extends Action {
    public static final String TYPE = "CameraShake";

    public static final BuilderCodec<CameraShakeAction> CODEC = BuilderCodec.builder(CameraShakeAction.class, CameraShakeAction::new, BASE_CODEC)
            .append(new KeyedCodec<>("Id", Codec.STRING), (t, v) -> t.id = v, t -> t.id)
            .documentation("Camera effect id (Server/Camera/CameraEffect).").add()
            .build();

    private String id;

    @Nullable
    public String getId() {
        return id;
    }

    @Override
    public void run(@Nonnull ActionContext context) {
        if (id == null) return;
        PlayerRef player = context.buffer().getComponent(context.entity(), PlayerRef.getComponentType());
        CameraEffect effect = CameraEffect.getAssetMap().getAsset(id);
        if (player != null && effect != null) player.getPacketHandler().writeNoCache(effect.createCameraShakePacket());
    }
}
