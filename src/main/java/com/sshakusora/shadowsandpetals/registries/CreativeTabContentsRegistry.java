package com.sshakusora.shadowsandpetals.registries;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class CreativeTabContentsRegistry {
    private static final Map<CreativeTabRegistry.CreativeTabKey, List<Entry>> CONTENTS = new EnumMap<>(CreativeTabRegistry.CreativeTabKey.class);

    private static final Map<CreativeTabRegistry.CreativeTabKey, List<Supplier<List<ItemStack>>>> STACK_CONTENTS =
            new EnumMap<>(CreativeTabRegistry.CreativeTabKey.class);

    private record Entry(Supplier<? extends ItemLike> supplier, CreativeTabOrder order) {}

    private CreativeTabContentsRegistry() {}

    public static void add(CreativeTabRegistry.CreativeTabKey tab, Supplier<? extends ItemLike> supplier) {
        add(tab, supplier, CreativeTabOrder.DEFAULT);
    }

    public static void add(CreativeTabRegistry.CreativeTabKey tab, Supplier<? extends ItemLike> supplier, CreativeTabOrder order) {
        CONTENTS.computeIfAbsent(tab, ignored -> new ArrayList<>()).add(new Entry(supplier, order));
    }

    public static void addStacks(CreativeTabRegistry.CreativeTabKey tab, Supplier<List<ItemStack>> supplier) {
        STACK_CONTENTS.computeIfAbsent(tab, ignored -> new ArrayList<>()).add(supplier);
    }

    public static Consumer<CreativeModeTab.Output> generator(CreativeTabRegistry.CreativeTabKey tab) {
        return output -> {
            List<Entry> entries = new ArrayList<>(CONTENTS.getOrDefault(tab, List.of()));
            entries.sort(Comparator.comparingInt(entry -> entry.order().ordinal()));
            for (Entry entry : entries) {
                output.accept(entry.supplier().get());
            }
            for (Supplier<List<ItemStack>> supplier : STACK_CONTENTS.getOrDefault(tab, List.of())) {
                for (ItemStack stack : supplier.get()) {
                    output.accept(stack);
                }
            }
        };
    }
}
