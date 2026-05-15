package net.minecraft.client.yiz.core;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

/**
 * 保护态玩家。
 *
 * <p>通过 {@link PlayerClassSwapper} 的 {@code Unsafe} class 指针替换，
 * 将原 {@code ServerPlayer} 实例重定向为此类，实现实例级完全保护：</p>
 * <ul>
 *   <li>免疫一切伤害（{@link #hurt} 始终返回 false）</li>
 *   <li>生命值恒 &ge; 0.5（永不低于半心）</li>
 *   <li>不受无敌帧限制，不受任何 {@link DamageSource} 影响</li>
 * </ul>
 *
 * <p>此类不添加任何新字段——与 {@code ServerPlayer} 内存布局完全一致，
 * 确保 {@code Unsafe} class 指针替换后字段访问不出错。</p>
 *
 * <p>实例不由构造函数创建，由 {@code Unsafe.allocateInstance()} 生成
 * 幽灵实例（仅用于提取 klass 指针）。</p>
 */
public class ProtectedServerPlayer extends ServerPlayer {

    /**
     * 仅供 Unsafe.allocateInstance() 使用的空构造。
     * 不调用 super() —— Unsafe 不会触发构造器。
     */
    @SuppressWarnings("unused")
    private ProtectedServerPlayer() {
        // Unsafe 分配实例时不调用构造器，此构造仅满足编译要求
        super(null, (ServerLevel) null, null, null);
    }

    // ══════════════════════════════════════════════════════════════
    //  伤害免疫
    // ══════════════════════════════════════════════════════════════

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean isInvulnerable() {
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    //  生命值保护（≥ 0.5）
    // ══════════════════════════════════════════════════════════════

    @Override
    public float getHealth() {
        return Math.max(0.5F, super.getHealth());
    }

    @Override
    public void setHealth(float health) {
        super.setHealth(Math.max(0.5F, health));
    }

    @Override
    public boolean isDeadOrDying() {
        return false;
    }

    @Override
    public boolean isAlive() {
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    //  死亡拦截 — 覆盖所有直接致死路径
    // ══════════════════════════════════════════════════════════════

    @Override
    public void die(DamageSource source) {
        // 空操作 — 永不死亡。不调用 super.die() 避免 remove / dropLoot 等。
    }

    @Override
    public void kill() {
        // LivingEntity.kill() → hurt(MAX) 或 remove(KILLED)
        // 阻止：不调用 super，直接忽略
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (reason == Entity.RemovalReason.KILLED) return;
        super.remove(reason);
    }

    // ══════════════════════════════════════════════════════════════
    //  每 tick 兜底 — 防御 SynchedEntityData / NBT 直写
    // ══════════════════════════════════════════════════════════════

    @Override
    public void tick() {
        // 必须在 super.tick() 之前修正生命值 ——
        // LivingEntity.tick() 内部检测 health≤0 会触发死亡流程，
        // 等 super.tick() 返回再修正就来不及了
        if (super.getHealth() < 0.5F) {
            super.setHealth(0.5F);
        }
        super.tick();
        // 二次兜底：super.tick() 内部可能也有扣血逻辑（如药水效果）
        if (super.getHealth() < 0.5F) {
            super.setHealth(0.5F);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  身份保持
    // ══════════════════════════════════════════════════════════════

    @Override
    public boolean isSpectator() {
        return false;
    }

    @Override
    public boolean isCreative() {
        return false;
    }
}
