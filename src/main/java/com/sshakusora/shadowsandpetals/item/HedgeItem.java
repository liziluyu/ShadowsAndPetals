package com.sshakusora.shadowsandpetals.item;

import com.sshakusora.shadowsandpetals.block.decoration.TallHedgeBlock;
import com.sshakusora.shadowsandpetals.util.HedgeContents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** Block item whose embedded block-entity data selects the hedge foliage. */
public final class HedgeItem extends BlockItem {
    public HedgeItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        Block leaves = HedgeContents.get(stack).leavesBlock();
        String nameKey = getBlock() instanceof TallHedgeBlock
                ? "item.shadowsandpetals.tall_hedge.variant" : "item.shadowsandpetals.hedge.variant";
        return Component.translatable(nameKey, leaves.getName());
    }
}
