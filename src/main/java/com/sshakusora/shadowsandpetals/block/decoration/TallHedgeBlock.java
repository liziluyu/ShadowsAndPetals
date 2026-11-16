package com.sshakusora.shadowsandpetals.block.decoration;

import com.mojang.serialization.MapCodec;
import com.sshakusora.shadowsandpetals.blockentity.HedgeBlockEntity;
import com.sshakusora.shadowsandpetals.util.HedgeContents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A connected pair of hedge blocks rendered with separate lower and upper models. */
public class TallHedgeBlock extends HedgeBlock {
    public static final MapCodec<HedgeBlock> CODEC = simpleCodec(TallHedgeBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    private static final VoxelShape UPPER_SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public TallHedgeBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    public MapCodec<HedgeBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HALF);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos lowerPos = context.getClickedPos();
        BlockPos upperPos = lowerPos.above();
        Level level = context.getLevel();
        if (level.isOutsideBuildHeight(upperPos)
                || !level.getWorldBorder().isWithinBounds(upperPos)
                || !level.getBlockState(upperPos).canBeReplaced()) {
            return null;
        }
        BlockState lowerState = super.getStateForPlacement(context);
        return lowerState == null ? null : lowerState.setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        BlockPos upperPos = pos.above();
        BlockState upperState = defaultBlockState()
                .setValue(HALF, DoubleBlockHalf.UPPER)
                .setValue(WATERLOGGED, level.getFluidState(upperPos).getType() == Fluids.WATER);
        level.setBlock(upperPos, upperState, Block.UPDATE_ALL);

        HedgeContents.Materials materials = HedgeContents.get(stack);
        if (level.getBlockEntity(pos) instanceof HedgeBlockEntity lowerEntity) {
            lowerEntity.setMaterials(materials);
        }
        if (level.getBlockEntity(upperPos) instanceof HedgeBlockEntity upperEntity) {
            upperEntity.setMaterials(materials);
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        BlockState updated = super.updateShape(state, direction, neighborState, level, pos, neighborPos);
        if (direction.getAxis() != Direction.Axis.Y) {
            return updated;
        }
        DoubleBlockHalf half = state.getValue(HALF);
        Direction expected = half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN;
        if (direction != expected) {
            return updated;
        }
        boolean matchingOtherHalf = neighborState.getBlock() == this
                && neighborState.getValue(HALF) != half;
        if (matchingOtherHalf) {
            return updated;
        }
        return state.getValue(WATERLOGGED)
                ? Fluids.WATER.defaultFluidState().createLegacyBlock()
                : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            return super.canSurvive(state, level, pos);
        }
        BlockState lower = level.getBlockState(pos.below());
        return lower.getBlock() == this && lower.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos lowerPos = pos.below();
            BlockState lowerState = level.getBlockState(lowerPos);
            if (isMatchingOtherHalf(lowerState, state)) {
                if (!player.isCreative()) {
                    Block.popResource(level, pos, HedgeContents.create(this, materialsAt(level, pos)));
                }
                // Remove the lower half directly so its loot cannot also be emitted by
                // the neighbor update when the upper half disappears.
                BlockState replacement = lowerState.getValue(WATERLOGGED)
                        ? Fluids.WATER.defaultFluidState().createLegacyBlock() : Blocks.AIR.defaultBlockState();
                level.setBlock(lowerPos, replacement, Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return List.of();
        }
        return super.getDrops(state, params);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return HedgeContents.create(this, materialsAt(level, pos));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level,
                               BlockPos pos, CollisionContext context) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? UPPER_SHAPE : super.getShape(state, level, pos, context);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level,
                                        BlockPos pos, CollisionContext context) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER
                ? UPPER_SHAPE : super.getCollisionShape(state, level, pos, context);
    }

    private boolean isMatchingOtherHalf(BlockState other, BlockState state) {
        return other.getBlock() == this && other.getValue(HALF) != state.getValue(HALF);
    }

    private static HedgeContents.Materials materialsAt(LevelReader level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof HedgeBlockEntity hedge
                ? hedge.getMaterials() : HedgeContents.DEFAULT;
    }
}
