package com.sshakusora.shadowsandpetals.client.model.hedge;

import com.sshakusora.shadowsandpetals.ShadowsAndPetals;
import com.sshakusora.shadowsandpetals.blockentity.HedgeBlockEntity;
import com.sshakusora.shadowsandpetals.client.model.BakedModelSupport;
import com.sshakusora.shadowsandpetals.util.HedgeContents;
import com.sshakusora.shadowsandpetals.util.HedgeContents.Materials;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Baked hedge geometry with foliage and trunk textures supplied by the selected materials. */
public final class HedgeBakedModel extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
    public static final int FOLIAGE_TINT_INDEX = 0;
    public static final int TRUNK_TINT_INDEX = 1;

    private static final long RANDOM_SEED = 0x4845444745L;
    private static final ResourceLocation BASE_FOLIAGE = ShadowsAndPetals.asResource("block/autumn_oak_leaves_0");
    private static final ResourceLocation BASE_TRUNK = ShadowsAndPetals.asResource("block/ginkgo_log");

    private final @Nullable Materials fixedMaterials;
    private final Map<CacheKey, List<BakedQuad>> quadCache = new ConcurrentHashMap<>();
    private final Map<Materials, ChunkRenderTypeSet> renderTypeCache = new ConcurrentHashMap<>();
    private final Map<Materials, HedgeBakedModel> itemModelCache = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Integer> TARGET_TINT_CACHE = new ConcurrentHashMap<>();

    public HedgeBakedModel(BakedModel originalModel) {
        this(originalModel, null);
    }

    private HedgeBakedModel(BakedModel originalModel, @Nullable Materials fixedMaterials) {
        super(originalModel);
        this.fixedMaterials = fixedMaterials;
    }

    @Override
    public List<BakedQuad> getQuads(
            @Nullable BlockState state,
            @Nullable Direction side,
            RandomSource random,
            ModelData data,
            @Nullable RenderType renderType
    ) {
        Materials materials = materials(state, data);
        if (materials == null) {
            return originalModel.getQuads(state, side, random, data, renderType);
        }

        CacheKey key = new CacheKey(materials, side, renderType);
        return quadCache.computeIfAbsent(key, ignored -> bakeQuads(state, side, data, materials, renderType));
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        return getQuads(state, side, random, ModelData.EMPTY, null);
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        ChunkRenderTypeSet originalTypes = originalModel.getRenderTypes(state, random, data);
        Materials materials = materials(state, data);
        if (materials == null) {
            return originalTypes;
        }
        return ChunkRenderTypeSet.union(originalTypes, renderTypeCache.computeIfAbsent(materials,
                HedgeBakedModel::materialRenderTypes));
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        Materials materials = materials(null, data);
        if (materials == null) {
            materials = fixedMaterials;
        }
        TextureAtlasSprite sprite = materials == null ? null : targetSprite(materials.leavesBlockId());
        return sprite == null ? originalModel.getParticleIcon() : sprite;
    }

    /** Shares the placed hedge's geometry with the custom item renderer. */
    public HedgeBakedModel forItem(Materials materials) {
        return itemModelCache.computeIfAbsent(materials,
                selected -> new HedgeBakedModel(originalModel, selected));
    }

    private @Nullable Materials materials(@Nullable BlockState state, ModelData data) {
        if (fixedMaterials != null) {
            return fixedMaterials;
        }
        if (data.has(HedgeBlockEntity.RENDER_DATA)) {
            return data.get(HedgeBlockEntity.RENDER_DATA);
        }
        return null;
    }

    private List<BakedQuad> bakeQuads(@Nullable BlockState state, @Nullable Direction side, ModelData data,
                                     Materials materials, @Nullable RenderType renderType) {
        RandomSource random = RandomSource.create(RANDOM_SEED);
        List<BakedQuad> result = new ArrayList<>();
        for (BakedQuad source : BakedModelSupport.getQuads(originalModel, state, side, random, data, null)) {
            boolean foliage = spritesMatch(source.getSprite(), baseSprite(BASE_FOLIAGE));
            boolean trunk = spritesMatch(source.getSprite(), baseSprite(BASE_TRUNK));
            if (!foliage && !trunk) {
                if (renderType == null || state == null
                        || originalModel.getRenderTypes(state, random, data).contains(renderType)) {
                    result.add(source);
                }
                continue;
            }

            ResourceLocation targetId = foliage ? materials.leavesBlockId() : materials.trunkBlockId();
            Block target = BuiltInRegistries.BLOCK.get(targetId);
            if (target == Blocks.AIR) {
                continue;
            }
            BlockState targetState = target.defaultBlockState();
            ChunkRenderTypeSet targetTypes = targetModel(targetState).getRenderTypes(
                    targetState, RandomSource.create(RANDOM_SEED), ModelData.EMPTY);
            if (renderType != null && !targetTypes.contains(renderType)) {
                continue;
            }

            TextureAtlasSprite targetSprite = targetSprite(targetId);
            if (targetSprite == null) {
                continue;
            }
            int targetTint = targetTintIndex(targetId, targetState);
            int layerTint = targetTint < 0 ? -1 : (foliage ? FOLIAGE_TINT_INDEX : TRUNK_TINT_INDEX);
            result.add(remap(source, source.getSprite(), targetSprite, layerTint));
        }
        return List.copyOf(result);
    }

    private static ChunkRenderTypeSet materialRenderTypes(Materials materials) {
        Block leaves = BuiltInRegistries.BLOCK.get(materials.leavesBlockId());
        Block trunk = BuiltInRegistries.BLOCK.get(materials.trunkBlockId());
        List<ChunkRenderTypeSet> sets = new ArrayList<>(2);
        if (leaves != Blocks.AIR) {
            BlockState state = leaves.defaultBlockState();
            sets.add(targetModel(state).getRenderTypes(state, RandomSource.create(RANDOM_SEED), ModelData.EMPTY));
        }
        if (trunk != Blocks.AIR) {
            BlockState state = trunk.defaultBlockState();
            sets.add(targetModel(state).getRenderTypes(state, RandomSource.create(RANDOM_SEED), ModelData.EMPTY));
        }
        return ChunkRenderTypeSet.union(sets);
    }

    private static BakedModel targetModel(BlockState state) {
        return BakedModelSupport.blockModel(state);
    }

    private static @Nullable TextureAtlasSprite targetSprite(ResourceLocation blockId) {
        Block block = BuiltInRegistries.BLOCK.get(blockId);
        return block == Blocks.AIR ? null : targetModel(block.defaultBlockState()).getParticleIcon();
    }

    /** Returns the source-model tint index for the target's particle sprite, or -1 when untinted. */
    public static int targetTintIndex(ResourceLocation blockId, BlockState state) {
        return TARGET_TINT_CACHE.computeIfAbsent(blockId, ignored -> findTargetTintIndex(state));
    }

    private static int findTargetTintIndex(BlockState state) {
        BakedModel model = targetModel(state);
        TextureAtlasSprite particle = model.getParticleIcon();
        RandomSource random = RandomSource.create(RANDOM_SEED);
        for (Direction direction : Direction.values()) {
            for (BakedQuad quad : BakedModelSupport.getQuads(model, state, direction, random, ModelData.EMPTY, null)) {
                if (spritesMatch(quad.getSprite(), particle) && quad.getTintIndex() >= 0) {
                    return quad.getTintIndex();
                }
            }
        }
        for (BakedQuad quad : BakedModelSupport.getQuads(model, state, null, random, ModelData.EMPTY, null)) {
            if (spritesMatch(quad.getSprite(), particle) && quad.getTintIndex() >= 0) {
                return quad.getTintIndex();
            }
        }
        return -1;
    }

    private static BakedQuad remap(BakedQuad quad, TextureAtlasSprite sourceSprite,
                                   TextureAtlasSprite targetSprite, int tintIndex) {
        int[] vertices = quad.getVertices().clone();
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * 8;
            float u = Float.intBitsToFloat(vertices[offset + 4]);
            float v = Float.intBitsToFloat(vertices[offset + 5]);
            float localU = normalized(u, sourceSprite.getU0(), sourceSprite.getU1());
            float localV = normalized(v, sourceSprite.getV0(), sourceSprite.getV1());
            vertices[offset + 4] = Float.floatToRawIntBits(targetSprite.getU(localU));
            vertices[offset + 5] = Float.floatToRawIntBits(targetSprite.getV(localV));
        }
        return new BakedQuad(vertices, tintIndex, quad.getDirection(), targetSprite,
                quad.isShade(), quad.hasAmbientOcclusion());
    }

    private static TextureAtlasSprite baseSprite(ResourceLocation id) {
        return Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS).getSprite(id);
    }

    private static float normalized(float value, float min, float max) {
        return Math.clamp((value - min) / (max - min), 0.0F, 1.0F);
    }

    private static boolean spritesMatch(TextureAtlasSprite first, TextureAtlasSprite second) {
        return first.atlasLocation().equals(second.atlasLocation())
                && first.getU0() == second.getU0() && first.getU1() == second.getU1()
                && first.getV0() == second.getV0() && first.getV1() == second.getV1();
    }

    public static void invalidateCaches() {
        TARGET_TINT_CACHE.clear();
    }

    private record CacheKey(Materials materials, @Nullable Direction side, @Nullable RenderType renderType) {
    }
}
