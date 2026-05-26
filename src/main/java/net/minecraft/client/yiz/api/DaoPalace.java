package net.minecraft.client.yiz.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 道宫数据记录 [record] — 一座道宫的所有状态。
 * <p>
 * 道宫是在固定坐标建造的立方体建筑。
 * 边长 L 只增不减，由放置的方块总数推动扩展公式自动进阶。
 * </p>
 *
 * @param anchorId       落点 ID（区分同一玩家的多座道宫）
 * @param centerPos      中心坐标
 * @param sideLength     当前正方体边长（边长 1 = 3×3×3，边长 3 = 7×7×7）
 * @param placedBlocks   已放置方块坐标集合（相对中心偏移）
 * @param totalAnchors   该玩家拥有的落点总数
 */
// 大白话: 道宫方法
public record DaoPalace(
    ResourceLocation anchorId,
    BlockPos centerPos,
    int sideLength,
    Set<BlockPos> placedBlocks,
    int totalAnchors
) {
    public DaoPalace {
        placedBlocks = Collections.unmodifiableSet(new HashSet<>(placedBlocks));
    }

    /** 当前正方体体积（已用/最大） */
    public int currentVolume() {
        return placedBlocks.size();
    }

    /** 当前边长对应的最大方块数 */
    public int maxVolume() {
        int edge = sideLength * 2 + 1;
        return edge * edge * edge;
    }

    /**
     * 根据蓝图公式计算下一级需要的方块数：
     * [(L+2)³ − L³] / (10 × 落点总数²)
     */
    public int costToExpand() {
        int currentEdge = sideLength * 2 + 1;
        int nextEdge = (sideLength + 2) * 2 + 1;
        int volumeDiff = nextEdge * nextEdge * nextEdge - currentEdge * currentEdge * currentEdge;
        int divisor = Math.max(10 * totalAnchors * totalAnchors, 1);
        return Math.max(1, volumeDiff / divisor);
    }

    /**
     * 影响力范围 = L × 4^落点总数。
     */
    public double influenceRange() {
        return sideLength * Math.pow(4, totalAnchors);
    }

    /** 添加一个坐标到已放置集合（返回新 record） */
    public DaoPalace withBlock(BlockPos pos) {
        Set<BlockPos> newBlocks = new HashSet<>(placedBlocks);
        newBlocks.add(pos);
        return new DaoPalace(anchorId, centerPos, sideLength, newBlocks, totalAnchors);
    }

    /** 进阶到新边长（返回新 record） */
    public DaoPalace withSideLength(int newSideLength) {
        return new DaoPalace(anchorId, centerPos, newSideLength, placedBlocks, totalAnchors);
    }

    /** 更新落点总数 */
    public DaoPalace withTotalAnchors(int newTotal) {
        return new DaoPalace(anchorId, centerPos, sideLength, placedBlocks, newTotal);
    }
}
