package com.sshakusora.shadowsandpetals.client.model.hedge;

import com.sshakusora.shadowsandpetals.ShadowsAndPetals;
import com.sshakusora.shadowsandpetals.blockentity.HedgeBlockEntity;
import com.sshakusora.shadowsandpetals.registries.BlockRegistry;
import com.sshakusora.shadowsandpetals.util.HedgeContents;
import com.sshakusora.shadowsandpetals.util.HedgeContents.Materials;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/** Client model and tint setup for material-selectable hedges. */
@EventBusSubscriber(modid = ShadowsAndPetals.MOD_ID, value = Dist.CLIENT)
public final class HedgeModelEvents {
    public static final ModelResourceLocation TALL_HEDGE_PREVIEW = ModelResourceLocation.standalone(
            ShadowsAndPetals.asResource("block/tree_deco/tall_hedge"));

    private HedgeModelEvents() {
    }

    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(TALL_HEDGE_PREVIEW);
    }

    @SubscribeEvent
    public static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        ResourceLocation hedgeId = BlockRegistry.HEDGE.getId();
        ResourceLocation tallHedgeId = BlockRegistry.TALL_HEDGE.getId();
        for (Map.Entry<ModelResourceLocation, BakedModel> entry : event.getModels().entrySet()) {
            ResourceLocation id = entry.getKey().id();
            boolean hedgeModel = id.equals(hedgeId) || id.equals(tallHedgeId)
                    || entry.getKey().equals(TALL_HEDGE_PREVIEW);
            if (!hedgeModel || entry.getKey().getVariant().equals("inventory")) {
                continue;
            }
            BakedModel model = entry.getValue();
            if (!(model instanceof HedgeBakedModel)) {
                entry.setValue(new HedgeBakedModel(model));
            }
        }
        HedgeBakedModel.invalidateCaches();
    }

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(HedgeModelEvents::blockColor, BlockRegistry.HEDGE.get(), BlockRegistry.TALL_HEDGE.get());
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(HedgeModelEvents::itemColor, BlockRegistry.HEDGE.get().asItem(), BlockRegistry.TALL_HEDGE.get().asItem());
    }

    private static int blockColor(BlockState state, @Nullable BlockAndTintGetter level,
                                  @Nullable BlockPos pos, int tintIndex) {
        if (level == null || pos == null) {
            return 0xFFFFFFFF;
        }
        if (!(level.getBlockEntity(pos) instanceof HedgeBlockEntity hedge)) {
            return 0xFFFFFFFF;
        }
        return materialBlockColor(hedge.getMaterials(), level, pos, tintIndex);
    }

    private static int itemColor(ItemStack stack, int tintIndex) {
        Materials materials = HedgeContents.get(stack);
        if (materials == null) {
            materials = HedgeContents.DEFAULT;
        }
        ResourceLocation targetId = targetId(materials, tintIndex);
        if (targetId == null) {
            return 0xFFFFFFFF;
        }
        Block target = BuiltInRegistries.BLOCK.get(targetId);
        if (target == Blocks.AIR) {
            return 0xFFFFFFFF;
        }
        BlockState targetState = target.defaultBlockState();
        int sourceTint = HedgeBakedModel.targetTintIndex(targetId, targetState);
        if (sourceTint < 0) {
            return 0xFFFFFFFF;
        }
        int color = Minecraft.getInstance().getItemColors().getColor(new ItemStack(target), sourceTint);
        return color == -1 ? 0xFFFFFFFF : color;
    }

    private static int materialBlockColor(Materials materials, BlockAndTintGetter level, BlockPos pos, int tintIndex) {
        ResourceLocation targetId = targetId(materials, tintIndex);
        if (targetId == null) {
            return 0xFFFFFFFF;
        }
        Block target = BuiltInRegistries.BLOCK.get(targetId);
        if (target == Blocks.AIR) {
            return 0xFFFFFFFF;
        }
        BlockState targetState = target.defaultBlockState();
        int sourceTint = HedgeBakedModel.targetTintIndex(targetId, targetState);
        if (sourceTint < 0) {
            return 0xFFFFFFFF;
        }
        int color = Minecraft.getInstance().getBlockColors().getColor(targetState, level, pos, sourceTint);
        return color == -1 ? 0xFFFFFFFF : color;
    }

    @Nullable
    private static ResourceLocation targetId(Materials materials, int tintIndex) {
        if (tintIndex == HedgeBakedModel.FOLIAGE_TINT_INDEX) {
            return materials.leavesBlockId();
        }
        if (tintIndex == HedgeBakedModel.TRUNK_TINT_INDEX) {
            return materials.trunkBlockId();
        }
        return null;
    }
}
