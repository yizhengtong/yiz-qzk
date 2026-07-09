package net.minecraft.client.yiz.tool.abolish;

import net.minecraft.client.yiz.api.DamageReductionRegistry;
import net.minecraft.client.yiz.core.AbolitionStateManager;
import net.minecraft.client.yiz.core.VTableReplace;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 背包防御废除工具。
 *
 * <p>通过 VTable 入口覆写 + 代码层开关，彻底禁用玩家从背包/穿戴物品获得的所有防御效果。</p>
 *
 * <h3>防御覆盖路径</h3>
 * <ol>
 *   <li><b>香版护甲值</b> — VTable 覆写 {@link LivingEntity#getArmorValue()} → 返回 0</li>
 *   <li><b>香版附魔保护</b> — VTable 覆写 {@link LivingEntity#getDamageAfterMagicAbsorb(float, float)} → 返回原伤害</li>
 *   <li><b>DamageReductionRegistry</b> — {@link DamageReductionRegistry#setAbolished(boolean)} 全局开关</li>
 * </ol>
 *
 * <p>注意：香版护甲韧性 ({@code getArmorValue()} → 0) 和附魔保护
 * ({@code getDamageAfterMagicAbsorb}) 通过 VTable 入口覆写实现，
 * 影响所有 {@link LivingEntity} 子类实例。如需按玩家粒度控制，需要额外逻辑。</p>
 *
 * <h3>使用示例</h3>
 * <pre>
 * // 废除指定玩家的背包防御
 * InventoryDefenseAbolisher.abolishPlayerDefense(player);
 *
 * // 恢复
 * InventoryDefenseAbolisher.restorePlayerDefense(player);
 * </pre>
 */
public final class InventoryDefenseAbolisher {
    private static final Logger LOGGER = LoggerFactory.getLogger("InventoryDefenseAbolish");

    /** {@code int getArmorValue()} */
    private static final String DESC_GET_ARMOR_VALUE = "()I";

    /** {@code float getDamageAfterMagicAbsorb(float, float)} */
    private static final String DESC_DMG_AFTER_MAGIC = "(FF)F";

    /** 是否已执行过 VTable 层覆写（全局一次，对所有 LivingEntity 生效） */
    private static volatile boolean vtableApplied = false;

    private InventoryDefenseAbolisher() {}

    // ══════════════════════════════════════════════════════════
    //  全局 VTable 层 — 执行一次，永久有效
    // ══════════════════════════════════════════════════════════

    /**
     * 执行 VTable 层防御覆写。
     * <p>
     * 覆写 {@link LivingEntity#getArmorValue()} 返回 0，
     * 覆写 {@link LivingEntity#getDamageAfterMagicAbsorb(float, float)} 返回原伤害。
     * 全局执行一次即可，对以后创建的所有实例生效。
     * </p>
     * <p>
     * 此方法线程安全，重复调用只执行一次。
     * </p>
     *
     * @throws IllegalStateException 如果 VTableReplace 不可用
     */
    public static synchronized void applyVTableOverrides() {
        if (vtableApplied) return;
        if (!VTableReplace.isAvailable()) {
            throw new IllegalStateException("VTableReplace not available, cannot apply defense abolition");
        }

        boolean ok = true;

        // getArmorValue() → 0：用 emptyZeroInt donor
        ok &= VTableReplace.replaceMethod(
                LivingEntity.class, "getArmorValue", DESC_GET_ARMOR_VALUE);
        if (!ok) {
            LOGGER.warn("Failed to override LivingEntity.getArmorValue()");
        }

        // getDamageAfterMagicAbsorb(FF)F → 返回原伤害：用 emptyReturnFirstFloat donor
        ok &= VTableReplace.replaceMethod(
                LivingEntity.class, "getDamageAfterMagicAbsorb", DESC_DMG_AFTER_MAGIC);
        if (!ok) {
            LOGGER.warn("Failed to override LivingEntity.getDamageAfterMagicAbsorb()");
        }

        vtableApplied = true;
        LOGGER.info("VTable defense overrides applied: getArmorValue→0, getDamageAfterMagicAbsorb→identity");
    }

    /**
     * 查询 VTable 层是否已覆写。
     */
    public static boolean isVTableApplied() {
        return vtableApplied;
    }

    // ══════════════════════════════════════════════════════════
    //  玩家粒度控制
    // ══════════════════════════════════════════════════════════

    /**
     * 废除指定玩家的背包防御。
     * <p>
     * 组合所有四层防御路径：
     * <ol>
     *   <li>VTable 覆写（全局一次）</li>
     *   <li>ItemAttributeHandler 跳过物品 % 减伤</li>
     *   <li>DamageReductionRegistry 全局废除</li>
     * </ol>
     * </p>
     *
     * @param player 目标玩家
     */
    public static void abolishPlayerDefense(Player player) {
        if (!vtableApplied) {
            try {
                applyVTableOverrides();
            } catch (IllegalStateException e) {
                LOGGER.error("Cannot abolish defense: {}", e.getMessage());
                return;
            }
        }

        DamageReductionRegistry.setAbolished(true);
        AbolitionStateManager.setArmorAbolished(true);

        LOGGER.info("Defense abolished for player {}", player.getName().getString());
    }

    public static void restorePlayerDefense(Player player) {
        DamageReductionRegistry.setAbolished(false);
        AbolitionStateManager.setArmorAbolished(false);

        LOGGER.info("Defense restored for player {}",
                player != null ? player.getName().getString() : "global");
    }

    public static boolean isPlayerAbolished(Player player) {
        return DamageReductionRegistry.isAbolished();
    }
}
