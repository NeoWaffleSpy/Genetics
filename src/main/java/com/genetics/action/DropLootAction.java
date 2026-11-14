package com.genetics.action;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import java.util.concurrent.ThreadLocalRandom;

/** {@code { "Type": "DropLoot" }}: rolls the entity's NPC drop list once more and drops the result (used on a kill). */
public class DropLootAction extends Action {
    public static final String TYPE = "DropLoot";

    public static final BuilderCodec<DropLootAction> CODEC = BuilderCodec.builder(DropLootAction.class, DropLootAction::new, BASE_CODEC).build();

    @Override
    public void run(@Nonnull ActionContext context) {
        NPCEntity npc = context.buffer().getComponent(context.entity(), NPCEntity.getComponentType());
        String dropList = npc != null && npc.getRole() != null ? npc.getRole().getDropListId() : null;
        ItemModule items = ItemModule.get();
        if (dropList == null || items == null || !items.isEnabled()) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (ItemStack stack : items.getRandomItemDrops(dropList)) {
            Vector3d direction = new Vector3d(random.nextDouble() - 0.5, 0.6, random.nextDouble() - 0.5).normalize();
            ItemUtils.throwItem(context.entity(), context.buffer(), stack, direction, 2F);
        }
    }
}
