package com.sshakusora.shadowsandpetals.compat.jade;

import com.sshakusora.shadowsandpetals.block.decoration.RecessedLampCompositeBlock;
import com.sshakusora.shadowsandpetals.block.decoration.irori.IroriBlock;
import com.sshakusora.shadowsandpetals.registries.BlockRegistry;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public final class ShadowsAndPetalsJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(IroriBurnTimeServerDataProvider.INSTANCE, IroriBlock.class);
        registration.registerBlockDataProvider(SandExcavationCooldownServerDataProvider.INSTANCE, Block.class);
        registration.registerBlockDataProvider(RockeryHammerProgressServerDataProvider.INSTANCE, Block.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        // Hedge variants are carried by the picked item stack's block-entity data.
        registration.usePickedResult(BlockRegistry.HEDGE.get());
        registration.usePickedResult(BlockRegistry.TALL_HEDGE.get());
        registration.registerBlockComponent(IroriBurnTimeComponentProvider.INSTANCE, IroriBlock.class);
        registration.registerBlockComponent(SandExcavationCooldownComponentProvider.INSTANCE, Block.class);
        registration.registerBlockComponent(new RecessedLampHarvestComponentProvider(registration), RecessedLampCompositeBlock.class);
        registration.addTooltipCollectedCallback(RockeryHammerProgressOverlay::onTooltipCollected);
    }
}
