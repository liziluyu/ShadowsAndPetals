package com.sshakusora.shadowsandpetals.blockentity;

import com.sshakusora.shadowsandpetals.registries.BlockEntityRegistry;
import com.sshakusora.shadowsandpetals.util.HedgeContents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/** Stores the leaf and trunk blocks used by the hedge's dynamic template model. */
public final class HedgeBlockEntity extends BlockEntity {
    public static final ModelProperty<HedgeContents.Materials> RENDER_DATA = new ModelProperty<>();

    private HedgeContents.Materials materials = HedgeContents.DEFAULT;

    public HedgeBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.HEDGE.get(), pos, state);
    }

    public HedgeContents.Materials getMaterials() {
        return materials;
    }

    public void setMaterials(HedgeContents.Materials materials) {
        this.materials = materials;
        setChanged();
        requestModelDataUpdate();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag input, HolderLookup.Provider registries) {
        super.loadAdditional(input, registries);
        ResourceLocation leaves = ResourceLocation.tryParse(input.getString(HedgeContents.LEAVES_KEY));
        ResourceLocation trunk = ResourceLocation.tryParse(input.getString(HedgeContents.TRUNK_KEY));
        materials = new HedgeContents.Materials(
                leaves != null && BuiltInRegistries.BLOCK.containsKey(leaves)
                        ? leaves : HedgeContents.DEFAULT.leavesBlockId(),
                trunk != null && BuiltInRegistries.BLOCK.containsKey(trunk)
                        ? trunk : HedgeContents.DEFAULT.trunkBlockId());
        requestModelDataUpdate();
    }

    @Override
    protected void saveAdditional(CompoundTag output, HolderLookup.Provider registries) {
        super.saveAdditional(output, registries);
        output.putString(HedgeContents.LEAVES_KEY, materials.leavesBlockId().toString());
        output.putString(HedgeContents.TRUNK_KEY, materials.trunkBlockId().toString());
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput components) {
        super.applyImplicitComponents(components);
        CustomData data = components.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data != null) {
            CompoundTag tag = data.copyTag();
            ResourceLocation leaves = ResourceLocation.tryParse(tag.getString(HedgeContents.LEAVES_KEY));
            ResourceLocation trunk = ResourceLocation.tryParse(tag.getString(HedgeContents.TRUNK_KEY));
            materials = new HedgeContents.Materials(
                    leaves != null && BuiltInRegistries.BLOCK.containsKey(leaves)
                            ? leaves : HedgeContents.DEFAULT.leavesBlockId(),
                    trunk != null && BuiltInRegistries.BLOCK.containsKey(trunk)
                            ? trunk : HedgeContents.DEFAULT.trunkBlockId());
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        loadWithComponents(packet.getTag(), registries);
        if (level != null && level.isClientSide()) {
            requestModelDataUpdate();
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public ModelData getModelData() {
        return ModelData.builder().with(RENDER_DATA, materials).build();
    }
}
