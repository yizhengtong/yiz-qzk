package net.minecraft.client.yiz.tool.health;

import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 禁疗配置（百分比 + 固定值）
 * <p>
 * 从健康值根本禁止治疗。
 * 外部模组可通过 {@link net.minecraft.client.yiz.api.YizModQZKAPI} 的公开方法设置。
 * </p>
 *
 * <h3>两种禁疗方式叠加规则</h3>
 * <ol>
 *   <li>先应用百分比禁疗（削减比率）</li>
 *   <li>再应用固定值禁疗（减去固定数值）</li>
 * </ol>
 */
public final class VitalitySeveranceConfig {

    private static final Map<UUID, Config> BANS = new ConcurrentHashMap<>();

    private VitalitySeveranceConfig() {}

    /**
     * 禁疗配置
     *
     * @param percent     百分比禁疗（0~100），如 50 = 削减 50% 治疗量
     * @param fixedAmount 固定值禁疗（≥0），如 10 = 每次治疗减 10 点
     */
    public record Config(float percent, float fixedAmount) {
        /**
         * 对治疗量应用禁疗。
         *
         * @param healAmount 原始治疗量
         * @return 禁疗后的实际治疗量（≥0）
         */
        public float apply(float healAmount) {
            float afterPercent = healAmount * (1.0f - Math.min(1.0f, percent / 100.0f));
            float result = afterPercent - Math.max(0, fixedAmount);
            return Math.max(0, result);
        }
    }

    /**
     * 设置实体的禁疗配置。
     * 如果 percent ≤ 0 且 fixedAmount ≤ 0，移除该实体的禁疗。
     */
    public static void set(LivingEntity entity, float percent, float fixedAmount) {
        if (percent <= 0 && fixedAmount <= 0) {
            BANS.remove(entity.getUUID());
        } else {
            BANS.put(entity.getUUID(), new Config(
                Math.max(0, percent),
                Math.max(0, fixedAmount)
            ));
        }
    }

    /**
     * 获取实体的禁疗配置，无配置时返回 null。
     */
    public static Config get(LivingEntity entity) {
        return BANS.get(entity.getUUID());
    }

    /**
     * 移除实体的禁疗配置。
     */
    public static void remove(LivingEntity entity) {
        BANS.remove(entity.getUUID());
    }

    /**
     * 实体死亡时自动清理。
     */
    public static void onEntityRemove(UUID uuid) {
        BANS.remove(uuid);
    }
}
