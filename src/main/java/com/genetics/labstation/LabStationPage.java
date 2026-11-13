package com.genetics.labstation;

import com.genetics.genome.Genome;
import com.genetics.interaction.Syringes;
import com.genetics.journal.GeneJournal;
import com.genetics.journal.Journal;
import com.genetics.process.Station;
import com.genetics.research.Research;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.hypixel.hytale.server.core.inventory.InventoryComponent.HOTBAR_SECTION_ID;
import static com.hypixel.hytale.server.core.inventory.InventoryComponent.STORAGE_SECTION_ID;

/**
 * The Lab Station screen: one panel with four tabs (Process, Splice, Catalogue, Research), the sample rail and the
 * player's inventory, all as server-filled grids. Drag &amp; drop between the grids is applied server-side from the
 * grid events the client reports. Slot B of the Splice tab is virtual: it points at a syringe in the inventory,
 * which stays where it is until the splice uses it.
 */
public class LabStationPage extends InteractiveCustomUIPage<LabStationPage.EventPayload> {
    private static final String LAYOUT = "Genetics/LabStation.ui";
    static final String SAMPLE_GRID = "#SampleSlot";
    static final String SPLICE_GRID = "#SpliceSlot";
    static final String STORAGE_GRID = "#Storage";
    static final String HOTBAR_GRID = "#Hotbar";
    static final short SAMPLE_SLOT = Station.SAMPLE_SLOT;
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
    private static final List<String> TABS = List.of("Process", "Splice", "Library", "Catalogue", "Research");
    /** Tabs that keep the sample rail and the inventory on screen. */
    private static final List<String> BENCH_TABS = List.of("Process", "Splice", "Library");
    static final List<String> SUBS = List.of("Creatures", "Genes", "Buffs", "Mutations");

    final Station station;
    private final World world;
    final ItemContainer container;
    private final List<EventRegistration<?, ?>> listeners = new ArrayList<>();
    @Nullable
    private EventPayload pendingDrop;
    @Nullable
    private String lastClickedGrid;
    @Nullable
    private String hoveredGrid;
    private int hoveredSlot = -1;
    private boolean closed;
    private boolean synced;

    // ---- Tab state
    String tab = "Process";
    /** Keep / make-permanent choices for the sample in the station (reset when it changes). */
    boolean[] keep = new boolean[0];
    boolean[] permanent = new boolean[0];
    /** Splice partner: a slot of the player's storage or hotbar, or none. */
    @Nullable
    ItemContainer bContainer;
    short bSlot = -1;
    boolean[] spliceKeep = new boolean[0];
    String sub = "Creatures";
    /** Gene of the genetic material picked for extraction, or -1. */
    int extractIndex = -1;
    /** Library entries ticked into the combination. */
    final java.util.Set<String> libraryUse = new java.util.LinkedHashSet<>();
    @Nullable
    String catalogueSelected;
    @Nullable
    String researchSelected;
    /** What the screen was built for: the sample and B (as genome JSON), to rebuild when they change. */
    private String builtSample = "";
    private String builtB = "";

    public LabStationPage(@Nonnull PlayerRef playerRef, @Nonnull Station station) {
        super(playerRef, CustomPageLifetime.CanDismiss, EventPayload.CODEC);
        this.station = station;
        this.world = station.world();
        this.container = station.container();
    }

    @Nonnull
    PlayerRef player() {
        return playerRef;
    }

    // ---- Building

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands, @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        if (!synced) {
            synced = true;
            Research.sync(ref, store);
        }
        validateB();
        String sampleKey = keyOf(container.getItemStack(SAMPLE_SLOT));
        if (!sampleKey.equals(builtSample)) {
            Genome sample = sample();
            keep = new boolean[sample != null ? sample.genes().size() : 0];
            extractIndex = -1;
            Arrays.fill(keep, true);
            permanent = new boolean[keep.length];
        }
        String bKey = keyOf(bStack());
        if (!sampleKey.equals(builtSample) || !bKey.equals(builtB)) spliceKeep = new boolean[0];
        builtSample = sampleKey;
        builtB = bKey;

        commands.append(LAYOUT);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#Panel #CloseButton", new EventData().append("Action", "Close"), false);
        for (String t : TABS) {
            for (String state : List.of("On", "Off")) {
                events.addEventBinding(CustomUIEventBindingType.Activating, "#Tab" + t + state, new EventData().append("Action", "Tab").append("Tab", t), false);
            }
        }
        for (String s : SUBS) {
            for (String state : List.of("On", "Off")) {
                events.addEventBinding(CustomUIEventBindingType.Activating, "#Sub" + s + state, new EventData().append("Action", "Sub").append("Tab", s), false);
            }
        }
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ProcessButton", new EventData().append("Action", "Process"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SpliceWithButton", new EventData().append("Action", "Tab").append("Tab", "Splice"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#UpgradeButton", new EventData().append("Action", "Upgrade"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#UpgradeStationButton", new EventData().append("Action", "Tab")
                .append("Tab", station.tier() < 2 ? "Splice" : "Library"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SpliceButton", new EventData().append("Action", "Splice"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ResearchButton", new EventData().append("Action", "Research"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ExtractButton", new EventData().append("Action", "Extract"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CombineButton", new EventData().append("Action", "Combine"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#LibUpgradeButton", new EventData().append("Action", "Upgrade"), false);
        for (String grid : List.of(SAMPLE_GRID, SPLICE_GRID, STORAGE_GRID, HOTBAR_GRID)) {
            for (CustomUIEventBindingType type : SLOT_EVENTS) {
                events.addEventBinding(type, grid, new EventData().append("Action", type.name()).append("Grid", grid), false);
            }
        }

        writeChrome(commands, store);
        writeGrids(commands, ref, store);
        writeTooltip(commands, ref, store);
        switch (tab) {
            case "Splice" -> SpliceTab.build(this, ref, store, commands, events);
            case "Library" -> LibraryTab.build(this, ref, store, commands, events);
            case "Catalogue" -> CatalogueTab.build(this, ref, store, commands, events);
            case "Research" -> ResearchTab.build(this, ref, store, commands, events);
            default -> ProcessTab.build(this, ref, store, commands, events);
        }
        writeRail(commands, ref, store);

        if (listeners.isEmpty()) {
            listeners.add(container.registerChangeEvent(event -> world.execute(this::refresh)));
            for (int section : new int[]{STORAGE_SECTION_ID, HOTBAR_SECTION_ID}) {
                ItemContainer playerContainer = playerContainer(store, ref, section);
                if (playerContainer != null) listeners.add(playerContainer.registerChangeEvent(event -> world.execute(this::refresh)));
            }
        }
    }

    /** Tabs, panel size, Science and station tier. */
    private void writeChrome(@Nonnull UICommandBuilder commands, @Nonnull Store<EntityStore> store) {
        boolean bench = BENCH_TABS.contains(tab);
        for (String t : TABS) {
            commands.set("#Tab" + t + "On.Visible", t.equals(tab));
            commands.set("#Tab" + t + "Off.Visible", !t.equals(tab));
            commands.set("#" + t + "Tab.Visible", t.equals(tab));
        }
        commands.set("#Rail.Visible", bench);
        commands.set("#InventoryPanel.Visible", bench);
        Anchor panel = new Anchor();
        panel.setWidth(Value.of(1000));
        panel.setHeight(Value.of(bench ? 530 : 900));
        panel.setBottom(Value.of(bench ? 466 : 60));
        commands.setObject("#Panel.Anchor", panel);
        writeScience(commands, store);
        commands.set("#StationTier.Text", "Station T" + station.tier());
    }

    void writeScience(@Nonnull UICommandBuilder commands, @Nonnull Store<EntityStore> store) {
        Ref<EntityStore> ref = playerRef.getReference();
        int science = ref != null ? Journal.of(ref, store).getScience() : 0;
        commands.set("#Science.Text", science + " Science");
    }

    /** The left rail: slot labels, slot B, and the card of who the sample came from. */
    private void writeRail(@Nonnull UICommandBuilder commands, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        boolean splice = tab.equals("Splice");
        commands.set("#SlotALabel.Text", splice ? "A" : "Sample");
        commands.set("#SlotBLabel.Visible", splice);
        commands.set("#PlusLabel.Visible", splice);
        commands.set("#SpliceSlot.Visible", splice);
        Genome a = sample();
        Genome b = spliceB();
        GeneJournal journal = Journal.of(ref, store);
        String icon = a != null && a.source() != null && !a.source().spliced() ? a.source().icon() : null;
        commands.set("#MobIcon.Visible", icon != null);
        if (icon != null) commands.set("#MobIcon.AssetPath", icon);
        if (splice) {
            commands.set("#SpeciesName.Text", "Splice inputs");
            commands.set("#SpeciesMeta.Text", "A  ·  " + (a != null ? a.title() + "  ·  Complexity " + com.genetics.genome.GeneText.number(a.complexity()) : "empty")
                    + "\nB  ·  " + (b != null ? b.title() + "  ·  Complexity " + com.genetics.genome.GeneText.number(b.complexity()) : "drag genetic material here from your inventory"));
        } else if (a == null) {
            commands.set("#SpeciesName.Text", "No sample");
            commands.set("#SpeciesMeta.Text", "Drag a filled syringe into the slot.");
        } else if (a.source() == null || a.source().spliced()) {
            boolean designed = a.source() != null && com.genetics.library.Library.SOURCE_ID.equals(a.source().mobId());
            commands.set("#SpeciesName.Text", designed ? "Designed" : a.source() != null ? "Spliced" : "Unknown");
            commands.set("#SpeciesMeta.Text", designed ? "Combined in the Genetic Library"
                    : a.source() != null ? "Mostly " + a.source().mobName() + " genes" : "");
        } else {
            String id = a.source().mobId();
            GeneJournal.Species entry = journal.getSpecies().get(id);
            int total = com.genetics.journal.Catalogue.possibleGenes(id).size();
            commands.set("#SpeciesName.Text", a.source().mobName());
            commands.set("#SpeciesMeta.Text", "Sampled " + (entry != null ? entry.getSamples() : 0) + " times  ·  "
                    + journal.genesOf(id).size() + " / " + total + " genes known");
        }
    }

    // ---- Events

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull EventPayload data) {
        if (data.action == null) return;
        switch (data.action) {
            case "Close" -> {
                if (!closed) close();
            }
            case "Tab" -> {
                if (data.tab != null && TABS.contains(data.tab) && !data.tab.equals(tab)) {
                    tab = data.tab;
                    hoveredGrid = null;
                    rebuild();
                }
            }
            case "Sub" -> {
                if (data.tab != null && SUBS.contains(data.tab) && !data.tab.equals(sub)) {
                    sub = data.tab;
                    catalogueSelected = null;
                    rebuild();
                }
            }
            case "Keep" -> {
                int i = parse(data.index);
                if (i >= 0 && i < keep.length) keep[i] = data.checked != null ? data.checked : !keep[i];
                partial(ref, store);
            }
            case "Permanent" -> {
                int i = parse(data.index);
                if (i >= 0 && i < permanent.length) permanent[i] = data.checked != null ? data.checked : !permanent[i];
                partial(ref, store);
            }
            case "SpliceKeep" -> {
                int i = parse(data.index);
                if (i >= 0 && i < spliceKeep.length) spliceKeep[i] = data.checked != null ? data.checked : !spliceKeep[i];
                partial(ref, store);
            }
            case "Entry" -> {
                if (data.node != null) {
                    catalogueSelected = data.node;
                    UICommandBuilder commands = new UICommandBuilder();
                    CatalogueTab.select(this, ref, store, commands);
                    sendUpdate(commands, false);
                }
            }
            case "Node" -> {
                if (data.node != null) {
                    researchSelected = data.node;
                    UICommandBuilder commands = new UICommandBuilder();
                    ResearchTab.detail(this, ref, store, commands);
                    sendUpdate(commands, false);
                }
            }
            case "Process" -> {
                String message = ProcessTab.process(this, ref, store);
                if (message != null) playerRef.sendMessage(Message.raw(message));
                rebuild();
            }
            case "PickExtract" -> {
                int i = parse(data.index);
                extractIndex = i == extractIndex ? -1 : i;
                partial(ref, store);
            }
            case "Extract" -> {
                String message = ProcessTab.extract(this, ref, store);
                if (message != null) playerRef.sendMessage(Message.raw(message));
                rebuild();
            }
            case "LibraryUse" -> {
                if (data.node != null) {
                    boolean use = data.checked != null ? data.checked : !libraryUse.contains(data.node);
                    if (use) libraryUse.add(data.node);
                    else libraryUse.remove(data.node);
                }
                partial(ref, store);
            }
            case "Combine" -> {
                String message = LibraryTab.combine(this, ref, store);
                if (message != null) playerRef.sendMessage(Message.raw(message));
                rebuild();
            }
            case "Refine" -> {
                if (data.node != null) playerRef.sendMessage(Message.raw(LibraryTab.refine(this, ref, store, data.node)));
                rebuild();
            }
            case "Discard" -> {
                if (data.node != null) playerRef.sendMessage(Message.raw(LibraryTab.discard(this, ref, store, data.node)));
                rebuild();
            }
            case "Splice" -> {
                String message = SpliceTab.splice(this, ref, store);
                if (message != null) playerRef.sendMessage(Message.raw(message));
                rebuild();
            }
            case "Upgrade" -> {
                String failure = LabStationBlock.upgrade(world, station.pos(), ref, store);
                playerRef.sendMessage(Message.raw(failure != null ? failure : "Lab Station upgraded to tier " + station.tier()
                        + (station.tier() >= 3 ? ": the Genetic Library is open." : ": you can now splice and process Moderate samples.")));
                rebuild();
            }
            case "Research" -> {
                String message = ResearchTab.research(this, ref, store);
                if (message != null) playerRef.sendMessage(Message.raw(message));
                rebuild();
            }
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
                if (drop == null || drop.slotIndex == null || data.slotIndex == null) return;
                if (SPLICE_GRID.equals(drop.grid)) {
                    setB(containerFor(store, ref, data.grid), data.slotIndex);
                } else if (SPLICE_GRID.equals(data.grid)) {
                    clearB();
                } else {
                    move(containerFor(store, ref, data.grid), data.slotIndex, containerFor(store, ref, drop.grid), drop.slotIndex,
                            drop.itemStackId, drop.itemStackQuantity);
                }
            }
            case "SlotClickPressWhileDragging" -> {
                // The client only drops on the button that picked the item up; any other button is reported here
                // instead, so treat it as a drop too (inventories accept either button).
                if (data.clickMouseButton != null && !data.clickMouseButton.equals(data.dragPressedMouseButton)
                        && data.slotIndex != null && data.dragSourceSlotId != null) {
                    if (SPLICE_GRID.equals(lastClickedGrid)) {
                        clearB();
                    } else {
                        String sourceGrid = findDragSource(store, ref, data.dragSourceSlotId, data.dragItemStackId);
                        if (SPLICE_GRID.equals(data.grid)) setB(containerFor(store, ref, sourceGrid), data.dragSourceSlotId);
                        else move(containerFor(store, ref, sourceGrid), data.dragSourceSlotId, containerFor(store, ref, data.grid), data.slotIndex,
                                data.dragItemStackId, data.dragItemStackQuantity);
                    }
                    // The client only ends a drag on a same-button drop, so it would keep showing the dragged item
                    // until the next input. Rebuilding the page recreates the grids, which ends the drag.
                    rebuild();
                }
            }
            default -> {
            }
        }
    }

    // ---- Slot B (virtual)

    /** Points slot B at a syringe of genetic material in the player's inventory. */
    private void setB(@Nullable ItemContainer from, int slot) {
        if (from == null || from == container || slot < 0 || slot >= from.getCapacity()) {
            rebuild();
            return;
        }
        Genome genome = Genome.read(from.getItemStack((short) slot));
        if (genome == null || !genome.processed()) {
            playerRef.sendMessage(Message.raw("Only genetic material (a processed sample) can go in slot B."));
            rebuild();
            return;
        }
        bContainer = from;
        bSlot = (short) slot;
        rebuild();
    }

    private void clearB() {
        bContainer = null;
        bSlot = -1;
        rebuild();
    }

    /** Forgets slot B if its syringe moved or stopped being genetic material. */
    private void validateB() {
        if (bContainer == null) return;
        Genome genome = Genome.read(bStack());
        if (genome == null || !genome.processed()) {
            bContainer = null;
            bSlot = -1;
        }
    }

    @Nullable
    ItemStack bStack() {
        if (bContainer == null || bSlot < 0 || bSlot >= bContainer.getCapacity()) return null;
        ItemStack stack = bContainer.getItemStack(bSlot);
        return isEmpty(stack) ? null : stack;
    }

    @Nullable
    Genome spliceB() {
        return Genome.read(bStack());
    }

    @Nullable
    ItemStack sampleStack() {
        ItemStack stack = container.getItemStack(SAMPLE_SLOT);
        return isEmpty(stack) ? null : stack;
    }

    @Nullable
    Genome sample() {
        return Genome.read(sampleStack());
    }

    // ---- Drag & drop between real grids

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

    static boolean isEmpty(@Nullable ItemStack stack) {
        return stack == null || stack.isEmpty();
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (closed) return;
        closed = true;
        listeners.forEach(EventRegistration::unregister);
        listeners.clear();
    }

    // ---- Updates

    /** Re-sends what changes without rebuilding; rebuilds instead if the sample or slot B changed. */
    private void refresh() {
        if (closed) return;
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null) return;
        validateB();
        if (!keyOf(container.getItemStack(SAMPLE_SLOT)).equals(builtSample) || !keyOf(bStack()).equals(builtB)) {
            rebuild();
            return;
        }
        partial(ref, ref.getStore());
    }

    /** Grids, tooltip, Science and the open tab's texts (no rows added or removed). */
    private void partial(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (closed) return;
        UICommandBuilder commands = new UICommandBuilder();
        writeGrids(commands, ref, store);
        writeTooltip(commands, ref, store);
        writeScience(commands, store);
        switch (tab) {
            case "Process" -> ProcessTab.update(this, ref, store, commands);
            case "Splice" -> SpliceTab.update(this, ref, store, commands);
            case "Library" -> LibraryTab.update(this, ref, store, commands);
            default -> {
            }
        }
        sendUpdate(commands, false);
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
        ItemStack stack;
        if (SPLICE_GRID.equals(hoveredGrid)) {
            stack = bStack();
        } else {
            ItemContainer hovered = containerFor(store, ref, hoveredGrid);
            stack = hovered != null && hoveredSlot >= 0 && hoveredSlot < hovered.getCapacity() ? hovered.getItemStack((short) hoveredSlot) : null;
        }
        boolean bench = BENCH_TABS.contains(tab);
        if (!bench || isEmpty(stack) || hoveredGrid == null) {
            commands.set("#TooltipLayer.Visible", false);
            return;
        }
        ItemTooltip.write(commands, stack, hoveredGrid, hoveredSlot, playerRef.getLanguage());
    }

    private void writeGrids(@Nonnull UICommandBuilder commands, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        commands.set(SAMPLE_GRID + ".Slots", slotsOf(container));
        ItemStack b = bStack();
        ItemGridSlot bSlotView = b != null ? new ItemGridSlot(new ItemStack(b.getItemId(), b.getQuantity())) : new ItemGridSlot();
        bSlotView.setActivatable(true);
        commands.set(SPLICE_GRID + ".Slots", new ItemGridSlot[]{bSlotView});
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
    static ItemContainer playerContainer(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, int sectionId) {
        InventoryComponent component = store.getComponent(ref, InventoryComponent.getComponentTypeById(sectionId));
        return component != null ? component.getInventory() : null;
    }

    /** Identity of a stack for change detection: item and genome. */
    @Nonnull
    private static String keyOf(@Nullable ItemStack stack) {
        if (isEmpty(stack)) return "";
        Genome genome = Genome.read(stack);
        return stack.getItemId() + "|" + (genome != null ? genome.toBson().toJson() : "");
    }

    static int parse(@Nullable String value) {
        try {
            return value != null ? Integer.parseInt(value.trim()) : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Data sent back by the page: our keys plus the client's drag fields. */
    public static class EventPayload {
        public static final BuilderCodec<EventPayload> CODEC = BuilderCodec.builder(EventPayload.class, EventPayload::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (p, v) -> p.action = v, p -> p.action).add()
                .append(new KeyedCodec<>("Grid", Codec.STRING), (p, v) -> p.grid = v, p -> p.grid).add()
                .append(new KeyedCodec<>("Tab", Codec.STRING), (p, v) -> p.tab = v, p -> p.tab).add()
                .append(new KeyedCodec<>("Index", Codec.STRING), (p, v) -> p.index = v, p -> p.index).add()
                .append(new KeyedCodec<>("Node", Codec.STRING), (p, v) -> p.node = v, p -> p.node).add()
                .append(new KeyedCodec<>("@Checked", Codec.BOOLEAN), (p, v) -> p.checked = v, p -> p.checked).add()
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
        private String tab;
        private String index;
        private String node;
        private Boolean checked;
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
