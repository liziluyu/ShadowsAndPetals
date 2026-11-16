package com.sshakusora.shadowsandpetals.client.model.hedge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sshakusora.shadowsandpetals.registries.BlockRegistry;
import com.sshakusora.shadowsandpetals.util.HedgeContents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/** Like boxes, hedge previews render from the material data on the actual item stack. */
public final class HedgeItemModel implements IClientItemExtensions {
    private BlockEntityWithoutLevelRenderer renderer;

    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        if (renderer == null) {
            Minecraft minecraft = Minecraft.getInstance();
            renderer = new Renderer(minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
        }
        return renderer;
    }

    private static final class Renderer extends BlockEntityWithoutLevelRenderer {
        private Renderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) {
            super(dispatcher, models);
        }

        @Override
        public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                                 MultiBufferSource buffers, int packedLight, int packedOverlay) {
            Minecraft minecraft = Minecraft.getInstance();
            BakedModel base = stack.is(BlockRegistry.TALL_HEDGE.get().asItem())
                    ? minecraft.getModelManager().getModel(HedgeModelEvents.TALL_HEDGE_PREVIEW)
                    : minecraft.getBlockRenderer().getBlockModel(BlockRegistry.HEDGE.get().defaultBlockState());
            if (!(base instanceof HedgeBakedModel hedge)) {
                return;
            }
            HedgeBakedModel model = hedge.forItem(HedgeContents.get(stack));
            for (RenderType renderType : model.getRenderTypes(stack, false)) {
                VertexConsumer vertices = ItemRenderer.getFoilBuffer(buffers, renderType, true, stack.hasFoil());
                minecraft.getItemRenderer().renderModelLists(model, stack, packedLight, packedOverlay, poseStack, vertices);
            }
        }
    }
}
