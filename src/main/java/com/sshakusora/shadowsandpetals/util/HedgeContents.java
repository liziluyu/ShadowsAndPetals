package com.sshakusora.shadowsandpetals.util;

import com.sshakusora.shadowsandpetals.registries.BlockEntityRegistry;
import com.sshakusora.shadowsandpetals.registries.BlockRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Item and persistence helpers for the variable leaf/trunk materials used by a hedge. */
public final class HedgeContents {
    public static final String LEAVES_KEY = "LeavesBlock";
    public static final String TRUNK_KEY = "TrunkBlock";

    public static final Materials DEFAULT = new Materials(
            ResourceLocation.withDefaultNamespace("oak_leaves"),
            ResourceLocation.withDefaultNamespace("oak_log"));

    private HedgeContents() {}

    public record Materials(ResourceLocation leavesBlockId, ResourceLocation trunkBlockId) {
        public Materials {
            if (!isRegistered(leavesBlockId)) {
                leavesBlockId = ResourceLocation.withDefaultNamespace("oak_leaves");
            }
            if (!isRegistered(trunkBlockId)) {
                trunkBlockId = ResourceLocation.withDefaultNamespace("oak_log");
            }
        }

        public Block leavesBlock() {
            return BuiltInRegistries.BLOCK.get(leavesBlockId);
        }

        public Block trunkBlock() {
            return BuiltInRegistries.BLOCK.get(trunkBlockId);
        }
    }

    public static Materials get(ItemStack stack) {
        CustomData data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data == null) {
            return DEFAULT;
        }
        CompoundTag tag = data.copyTag();
        return new Materials(parse(tag.getString(LEAVES_KEY), DEFAULT.leavesBlockId()),
                parse(tag.getString(TRUNK_KEY), DEFAULT.trunkBlockId()));
    }

    public static ItemStack create(Block leaves) {
        return create(BlockRegistry.HEDGE.get(), leaves);
    }

    public static ItemStack create(Block hedgeBlock, Block leaves) {
        return create(hedgeBlock, new Materials(BuiltInRegistries.BLOCK.getKey(leaves), inferTrunk(leaves)));
    }

    public static ItemStack create(Materials materials) {
        return create(BlockRegistry.HEDGE.get(), materials);
    }

    public static ItemStack create(Block hedgeBlock, Materials materials) {
        ItemStack stack = new ItemStack(hedgeBlock);
        apply(stack, materials);
        return stack;
    }

    public static void apply(ItemStack stack, Materials materials) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(BlockEntityRegistry.HEDGE.get()).toString());
        tag.putString(LEAVES_KEY, materials.leavesBlockId().toString());
        tag.putString(TRUNK_KEY, materials.trunkBlockId().toString());
        stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(tag));
    }

    /** Produces one creative variant for every registered leaves block with an item. */
    public static List<ItemStack> creativeStacks() {
        return creativeStacks(BlockRegistry.HEDGE.get());
    }

    public static List<ItemStack> creativeStacks(Block hedgeBlock) {
        List<ItemStack> result = new ArrayList<>();
        for (Block leaves : leafBlocks()) {
            result.add(create(hedgeBlock, leaves));
        }
        return result;
    }

    /** Shares the same supported species between creative variants and runtime recipes. */
    public static List<Block> leafBlocks() {
        List<Block> leaves = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if ((block instanceof LeavesBlock || block.defaultBlockState().is(BlockTags.LEAVES))
                    && block.asItem() instanceof BlockItem) {
                leaves.add(block);
            }
        }
        leaves.sort(Comparator.comparing(block -> BuiltInRegistries.BLOCK.getKey(block).toString()));
        return leaves;
    }

    private static ResourceLocation parse(String text, ResourceLocation fallback) {
        ResourceLocation id = ResourceLocation.tryParse(text);
        return id != null && isRegistered(id) ? id : fallback;
    }

    private static boolean isRegistered(ResourceLocation id) {
        return BuiltInRegistries.BLOCK.containsKey(id) && BuiltInRegistries.BLOCK.get(id) != Blocks.AIR;
    }

    private static ResourceLocation inferTrunk(Block leaves) {
        ResourceLocation leavesId = BuiltInRegistries.BLOCK.getKey(leaves);
        String path = leavesId.getPath();
        String candidate = path.endsWith("_leaves")
                ? path.substring(0, path.length() - "_leaves".length()) + "_log"
                : path + "_log";
        ResourceLocation candidateId = ResourceLocation.fromNamespaceAndPath(leavesId.getNamespace(), candidate);
        return isRegistered(candidateId) ? candidateId : DEFAULT.trunkBlockId();
    }
}
