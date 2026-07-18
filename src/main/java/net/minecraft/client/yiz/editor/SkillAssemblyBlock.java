package net.minecraft.client.yiz.editor;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 技能装配台方块。
 *
 * <p>纯媒介方块，不存储任何数据。右键打开技能配置界面。
 * 放置时根据玩家朝向旋转（类似熔炉），技能数据由 {@link SkillConfigStorage} 按玩家管理。</p>
 */
public class SkillAssemblyBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<SkillAssemblyBlock> CODEC =
        simpleCodec(SkillAssemblyBlock::new);

    private static final Component TITLE = Component.translatable("gui.yizmodqzk.skill_config");

    public SkillAssemblyBlock(Properties properties) {
        super(properties);
        // 默认朝北
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    // ── 方块渲染属性 ──────────────────────────────────────────

    /** 透明方块：相邻方块的面不被剔除，正常渲染。 */
    @Override
    public boolean skipRendering(BlockState state, BlockState adjacentState, Direction direction) {
        return adjacentState.is(this) || super.skipRendering(state, adjacentState, direction);
    }

    /** 用碰撞箱形状做光照遮挡判定（透明部分不挡光）。 */
    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    /** 天光可穿透本方块（透明）。 */
    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    // ── 右键交互 ──────────────────────────────────────────────

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, net.minecraft.world.InteractionHand hand,
                                              BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            SkillConfigStorage.Data data = SkillConfigStorage.getOrCreate(serverPlayer.getUUID());
            SkillConfigStorage.loadFromPlayerData(serverPlayer, data);
            serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (containerId, playerInv, p) ->
                    new SkillConfigMenu(containerId, playerInv,
                        data.skillUpgrade(), data.bigLoad(),
                        data.skillLoad(), data.passiveLoad(),
                        data.skillLibrary()),
                TITLE));
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
