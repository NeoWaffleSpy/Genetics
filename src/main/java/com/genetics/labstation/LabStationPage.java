package com.genetics.labstation;

import com.genetics.capacity.GenomeCapacity;
import com.genetics.genome.ActiveGenome;
import com.genetics.genome.Genome;
import com.genetics.interaction.Syringes;
import com.genetics.journal.Journal;
import com.genetics.process.ProcessPage;
import com.genetics.process.Processing;
import com.genetics.process.SplicePage;
import com.genetics.process.Splicer;
import com.genetics.process.Station;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static com.hypixel.hytale.server.core.inventory.InventoryComponent.HOTBAR_SECTION_ID;
import static com.hypixel.hytale.server.core.inventory.InventoryComponent.STORAGE_SECTION_ID;

/**
 * Fully custom Lab Station screen: the station panel (sample slot and its analysis) in the middle, the player's
 * inventory at the bottom, all as server-filled grids.
 * Drag &amp; drop between the grids is applied server-side from the grid events the client reports.
 */
public class LabStationPage extends InteractiveCustomUIPage<LabStationPage.EventPayload> {
    private static final String LAYOUT = "Genetics/LabStation.ui";
    static final String SAMPLE_GRID = "#SampleSlot";
    static final String STORAGE_GRID = "#Storage";
    static final String HOTBAR_GRID = "#Hotbar";
    private static final short SAMPLE_SLOT = 0;
    /** Item consumed to identify a sample. */
    public static final String REAGENT_ID = "Genetics_Identification_Reagent";
    private static final List<String> GRIDS = List.of(SAMPLE_GRID, STORAGE_GRID, HOTBAR_GRID);
    /**
     * A drop arrives as two events, in this order: {@code Dropped} on the target grid (target slot, source slot,
     * item) and then {@code SlotMouseDragCompleted} on the source grid (which grid the drag came from).
     * The client never moves items itself; the server applies the move and re-sends the grids.
     * The other drag events are bound too: with only the two above, the client stopped letting items be picked up.
     */
    private static final List<CustomUIEventBindingType> SLOT_EVENTS = List.of(
            CustomUIEventBindingType.SlotClicking,
            CustomUIEventBindingType.SlotDoubleClicking,
            CustomUIEventBindingType.Dropped,
            CustomUIEventBindingType.DragCancelled,
            CustomUIEventBindingType.SlotMouseDragCompleted,
            CustomUIEventBindingType.SlotMouseDragExited,
            CustomUIEventBindingType.SlotClickPressWhileDragging,
            CustomUIEventBindingType.SlotClickReleaseWhileDragging,
            CustomUIEventBindingType.SlotMouseEntered,
            CustomUIEventBindingType.SlotMouseExited
    );

    private final Station station;
    private final World world;
    private final ItemContainer container;
    private final List<EventRegistration<?, ?>> listeners = new ArrayList<>();
    @Nullable
    private EventPayload pendingDrop;
    @Nullable
    private String lastClickedGrid;
    /** Slot under the mouse, described by our own tooltip (the grids' built-in tooltips are turned off). */
    @Nullable
    private String hoveredGrid;
    private int hoveredSlot = -1;
    private boolean closed;

    public LabStationPage(@Nonnull PlayerRef playerRef, @Nonnull Station station) {
        super(playerRef, CustomPageLifetime.CanDismiss, EventPayload.CODEC);
        this.station = station;
        this.world = station.world();
        this.container = station.container();
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands, @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        commands.append(LAYOUT);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#Panel #CloseButton", new EventData().append("Action", "Close"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#IdentifyButton", new EventData().append("Action", "Identify"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#UpgradeButton", new EventData().append("Action", "Upgrade"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ProcessButton", new EventData().append("Action", "Process"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SpliceButton", new EventData().append("Action", "Splice"), false);
        for (String grid : GRIDS) {
            for (CustomUIEventBindingType type : SLOT_EVENTS) {
                events.addEventBinding(type, grid, new EventData().append("Action", type.name()).append("Grid", grid), false);
            }
        }

        render(commands, ref, store);

        if (listeners.isEmpty()) {
            listeners.add(container.registerChangeEvent(event -> world.execute(this::refresh)));
            for (int section : new int[]{STORAGE_SECTION_ID, HOTBAR_SECTION_ID}) {
                ItemContainer playerContainer = playerContainer(store, ref, section);
                if (playerContainer != null) listeners.add(playerContainer.registerChangeEvent(event -> world.execute(this::refresh)));
            }
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull EventPayload data) {
        if (data.action == null) return;
        switch (data.action) {
            case "Close" -> {
                if (!closed) close();
            }
            case "Identify" -> identify(ref, store);
            case "Upgrade" -> {
                String failure = LabStationBlock.upgrade(world, station.pos(), ref, store);
                playerRef.sendMessage(Message.raw(failure != null ? failure : "Lab Station upgraded to tier " + station.tier()
                        + ": you can now process and splice samples."));
                refresh();
            }
            case "Process" -> openProcessing(ref, store, false);
            case "Splice" -> openProcessing(ref, store, true);
            case "SlotClicking" -> lastClickedGrid = data.grid;
            case "SlotMouseEntered" -> {
                hoveredGrid = data.grid;
                hoveredSlot = data.slotIndex != null ? data.slotIndex : -1;
                refreshTooltip(ref, store);
            }
            case "SlotMouseExited" -> {
                if (data.grid != null && data.grid.equals(hoveredGrid) && (data.slotIndex == null || data.slotIndex == hoveredSlot)) {
                    hoveredGrid = null;
                    hoveredSlot = -1;
                    refreshTooltip(ref, store);
                }
            }
            case "Dropped" -> pendingDrop = data;
            case "SlotMouseDragCompleted" -> {
                EventPayload drop = pendingDrop;
                pendingDrop = null;
                if (drop != null && drop.slotIndex != null && data.slotIndex != null) {
                    move(containerFor(store, ref, data.grid), data.slotIndex, containerFor(store, ref, drop.grid), drop.slotIndex,
                            drop.itemStackId, drop.itemStackQuantity);
                }
            }
            case "SlotClickPressWhileDragging" -> {
                // The client only drops on the button that picked the item up; any other button is reported here
                // instead, so treat it as a drop too (inventories accept either button).
                if (data.clickMouseButton != null && !data.clickMouseButton.equals(data.dragPressedMouseButton)
                        && data.slotIndex != null && data.dragSourceSlotId != null) {
                    String sourceGrid = findDragSource(store, ref, data.dragSourceSlotId, data.dragItemStackId);
                    move(containerFor(store, ref, sourceGrid), data.dragSourceSlotId, containerFor(store, ref, data.grid), data.slotIndex,
                            data.dragItemStackId, data.dragItemStackQuantity);
                    // The client only ends a drag on a same-button drop, so it would keep showing the dragged item
                    // until the next input. Rebuilding the page recreates the grids, which ends the drag.
                    rebuild();
                }
            }
            default -> {
            }
        }
    }

    /**
     * The put-down event names the drag's source slot but not its grid: pick the grid whose slot holds the dragged
     * item, preferring the grid last clicked if more than one does.
     */
    @Nullable
    private String findDragSource(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, int slot, @Nullable String itemId) {
        String match = null;
        for (String grid : GRIDS) {
            ItemContainer candidate = containerFor(store, ref, grid);
            if (candidate == null || slot < 0 || slot >= candidate.getCapacity()) continue;
            ItemStack stack = candidate.getItemStack((short) slot);
            if (isEmpty(stack) || (itemId != null && !itemId.equals(stack.getItemId()))) continue;
            if (grid.equals(lastClickedGrid)) return grid;
            if (match == null) match = grid;
        }
        return match;
    }

    /** Moves (or merges, or swaps) a dragged stack from its source slot to the slot it was put down on. */
    private void move(
            @Nullable ItemContainer from, int fromIndex, @Nullable ItemContainer to, int toIndex, @Nullable String itemId, @Nullable Integer requestedQuantity
    ) {
        if (from == null || to == null) return;
        short fromSlot = (short) fromIndex;
        short toSlot = (short) toIndex;
        if (fromSlot < 0 || fromSlot >= from.getCapacity() || toSlot < 0 || toSlot >= to.getCapacity()) return;
        if (from == to && fromSlot == toSlot) {
            refresh();
            return;
        }

        ItemStack moving = from.getItemStack(fromSlot);
        if (isEmpty(moving) || (itemId != null && !itemId.equals(moving.getItemId()))) {
            refresh();
            return;
        }

        int quantity = requestedQuantity != null ? Math.clamp(requestedQuantity, 1, moving.getQuantity()) : moving.getQuantity();
        ItemStack target = to.getItemStack(toSlot);
        if (isEmpty(target) || moving.isStackableWith(target)) {
            from.moveItemStackFromSlotToSlot(fromSlot, quantity, to, toSlot, true);
        } else if (quantity == moving.getQuantity() && accepts(to, moving) && accepts(from, target)) {
            from.setItemStackForSlot(fromSlot, target);
            to.setItemStackForSlot(toSlot, moving);
        }
        // Re-send the grids even if nothing changed, so a refused drop snaps back.
        refresh();
    }

    /** The station's slot only takes syringes; the player's inventory takes anything. */
    private boolean accepts(@Nonnull ItemContainer target, @Nonnull ItemStack stack) {
        return target != container || Syringes.isSyringe(stack.getItemId());
    }

    @Nullable
    private ItemContainer containerFor(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nullable String grid) {
        if (grid == null) return null;
        return switch (grid) {
            case SAMPLE_GRID -> container;
            case STORAGE_GRID -> playerContainer(store, ref, STORAGE_SECTION_ID);
            case HOTBAR_GRID -> playerContainer(store, ref, HOTBAR_SECTION_ID);
            default -> null;
        };
    }

    private static boolean isEmpty(@Nullable ItemStack stack) {
        return stack == null || stack.isEmpty();
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (closed) return;
        closed = true;
        listeners.forEach(EventRegistration::unregister);
        listeners.clear();
    }

    private void refresh() {
        if (closed) return;
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null) return;
        UICommandBuilder commands = new UICommandBuilder();
        render(commands, ref, ref.getStore());
        sendUpdate(commands, false);
    }

    /** Grids and the analysis of the sample in the slot. */
    private void render(@Nonnull UICommandBuilder commands, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        writeGrids(commands, ref, store);
        writeTooltip(commands, ref, store);
        EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
        ActiveGenome active = store.getComponent(ref, ActiveGenome.getComponentType());
        double used = active != null && active.getGenome() != null ? active.getGenome().complexity() : 0;
        Genome sample = Genome.read(container.getItemStack(SAMPLE_SLOT));
        SampleView.write(commands, sample, playerRef.getLanguage(),
                used, GenomeCapacity.capacity(stats), countReagents(store, ref));
        StationView.write(commands, station.tier(), sample, ref, store);
    }

    /** Opens the processing or splicing screen for the sample in the slot, if the station and the sample allow it. */
    private void openProcessing(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, boolean splice) {
        ItemStack stack = container.getItemStack(SAMPLE_SLOT);
        Genome sample = Genome.read(stack);
        String refusal = station.tier() < 2 ? "Upgrade the Lab Station to tier 2 first."
                : splice ? Splicer.refusal(sample) : Processing.refusal(sample);
        if (refusal != null || stack == null || sample == null) {
            playerRef.sendMessage(Message.raw(refusal != null ? refusal : "Put a filled syringe in the slot."));
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        player.getPageManager().openCustomPage(ref, store, splice
                ? new SplicePage(playerRef, station, stack, sample)
                : new ProcessPage(playerRef, station, stack, sample));
    }

    /**
     * Identifies the sample in the slot for one Identification Reagent from the player's inventory (design doc §6):
     * its genes and Complexity are revealed and recorded in the Gene Journal.
     */
    private void identify(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        ItemStack stack = container.getItemStack(SAMPLE_SLOT);
        Genome genome = Genome.read(stack);
        if (genome == null || genome.identified()) return;
        for (int section : new int[]{HOTBAR_SECTION_ID, STORAGE_SECTION_ID}) {
            ItemContainer inventory = playerContainer(store, ref, section);
            if (inventory == null) continue;
            for (short slot = 0; slot < inventory.getCapacity(); slot++) {
                ItemStack candidate = inventory.getItemStack(slot);
                if (isEmpty(candidate) || !REAGENT_ID.equals(candidate.getItemId())) continue;
                if (!inventory.removeItemStackFromSlot(slot, 1).succeeded()) continue;
                Genome identified = genome.asIdentified();
                container.setItemStackForSlot(SAMPLE_SLOT, Syringes.fill(stack, identified));
                Journal.onIdentify(ref, store, identified);
                refresh();
                return;
            }
        }
        playerRef.sendMessage(Message.raw("You need an Identification Reagent to identify this sample."));
    }

    private static int countReagents(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        int count = 0;
        for (int section : new int[]{HOTBAR_SECTION_ID, STORAGE_SECTION_ID}) {
            ItemContainer inventory = playerContainer(store, ref, section);
            if (inventory != null) count += inventory.countItemStacks(stack -> REAGENT_ID.equals(stack.getItemId()));
        }
        return count;
    }

    /** Sends only the tooltip: re-sending the grids while the mouse moves could interrupt a drag. */
    private void refreshTooltip(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (closed) return;
        UICommandBuilder commands = new UICommandBuilder();
        writeTooltip(commands, ref, store);
        sendUpdate(commands, false);
    }

    /** Shows {@link ItemTooltip} for the hovered slot, or hides it. */
    private void writeTooltip(@Nonnull UICommandBuilder commands, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        ItemContainer hovered = containerFor(store, ref, hoveredGrid);
        ItemStack stack = hovered != null && hoveredSlot >= 0 && hoveredSlot < hovered.getCapacity()
                ? hovered.getItemStack((short) hoveredSlot) : null;
        if (isEmpty(stack) || hoveredGrid == null) {
            commands.set("#TooltipLayer.Visible", false);
            return;
        }
        ItemTooltip.write(commands, stack, hoveredGrid, hoveredSlot, playerRef.getLanguage());
    }

    private void writeGrids(@Nonnull UICommandBuilder commands, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        commands.set(SAMPLE_GRID + ".Slots", slotsOf(container));
        ItemContainer storage = playerContainer(store, ref, STORAGE_SECTION_ID);
        ItemContainer hotbar = playerContainer(store, ref, HOTBAR_SECTION_ID);
        if (storage != null) commands.set(STORAGE_GRID + ".Slots", slotsOf(storage));
        if (hotbar != null) commands.set(HOTBAR_GRID + ".Slots", slotsOf(hotbar));
    }

    @Nonnull
    private static ItemGridSlot[] slotsOf(@Nonnull ItemContainer container) {
        ItemGridSlot[] slots = new ItemGridSlot[container.getCapacity()];
        for (short i = 0; i < slots.length; i++) {
            ItemStack stack = container.getItemStack(i);
            // The client rejected full stacks (metadata/durability), so the grid shows a plain id + quantity copy.
            slots[i] = !isEmpty(stack) ? new ItemGridSlot(new ItemStack(stack.getItemId(), stack.getQuantity())) : new ItemGridSlot();
            slots[i].setActivatable(true);
        }
        return slots;
    }

    @Nullable
    private static ItemContainer playerContainer(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, int sectionId) {
        InventoryComponent component = store.getComponent(ref, InventoryComponent.getComponentTypeById(sectionId));
        return component != null ? component.getInventory() : null;
    }

    /** Data sent back by the page: our {@code Action}/{@code Grid} keys plus the client's drag fields. */
    public static class EventPayload {
        public static final BuilderCodec<EventPayload> CODEC = BuilderCodec.builder(EventPayload.class, EventPayload::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (p, v) -> p.action = v, p -> p.action).add()
                .append(new KeyedCodec<>("Grid", Codec.STRING), (p, v) -> p.grid = v, p -> p.grid).add()
                .append(new KeyedCodec<>("SlotIndex", Codec.INTEGER), (p, v) -> p.slotIndex = v, p -> p.slotIndex).add()
                .append(new KeyedCodec<>("ItemStackId", Codec.STRING), (p, v) -> p.itemStackId = v, p -> p.itemStackId).add()
                .append(new KeyedCodec<>("ItemStackQuantity", Codec.INTEGER), (p, v) -> p.itemStackQuantity = v, p -> p.itemStackQuantity).add()
                .append(new KeyedCodec<>("DragSourceSlotId", Codec.INTEGER), (p, v) -> p.dragSourceSlotId = v, p -> p.dragSourceSlotId).add()
                .append(new KeyedCodec<>("DragItemStackId", Codec.STRING), (p, v) -> p.dragItemStackId = v, p -> p.dragItemStackId).add()
                .append(new KeyedCodec<>("DragItemStackQuantity", Codec.INTEGER), (p, v) -> p.dragItemStackQuantity = v, p -> p.dragItemStackQuantity).add()
                .append(new KeyedCodec<>("DragPressedMouseButton", Codec.INTEGER), (p, v) -> p.dragPressedMouseButton = v, p -> p.dragPressedMouseButton).add()
                .append(new KeyedCodec<>("ClickMouseButton", Codec.INTEGER), (p, v) -> p.clickMouseButton = v, p -> p.clickMouseButton).add()
                .build();

        private String action;
        private String grid;
        private Integer slotIndex;
        private String itemStackId;
        private Integer itemStackQuantity;
        private Integer dragSourceSlotId;
        private String dragItemStackId;
        private Integer dragItemStackQuantity;
        private Integer dragPressedMouseButton;
        private Integer clickMouseButton;
    }
}
