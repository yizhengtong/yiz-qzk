package net.minecraft.client.yiz.weapon;

import net.minecraft.client.yiz.api.IWeaponAbility;
import net.minecraft.client.yiz.api.IWeaponItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * 分级武器注册 DSL — 替代 {@code StagedItemHelper.registerStaged} 用于武器注册。
 *
 * <h3>两层架构</h3>
 * <ul>
 * <li><b>Layer 1</b> — 品质层级：{@link #defaultTiers()} / {@link #withTiers} / {@link #tierCount}</li>
 * <li><b>Layer 2</b> — 属性配置：{@link #profile(Supplier)}</li>
 * <li><b>横切</b> — 能力注入：{@link #withAbility(IWeaponAbility)}</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * public static final List<Supplier<Item>> TERRA_BLADES =
 *     StagedWeaponRegistration.create(ITEMS, MODID, "terra_blade", 5)
 *         .defaultTiers()
 *         .profile(TerraBladeItem::buildDefault)
 *         .register(TerraBladeItem::new);
 * }</pre>
 *
 * @param <T> 武器 Item 类型，必须实现 IWeaponItem
 */
public class StagedWeaponRegistration<T extends Item & IWeaponItem> {

    private final DeferredRegister<Item> register;
    private final String modId;
    private final String baseName;
    private final int count;

    // Layer 1
    private List<QualityTier> qualityTiers = QualityTier.DEFAULT_5;

    // Layer 2
    private Supplier<WeaponProfile> profileSupplier;

    // Cross-cutting
    private final List<IWeaponAbility> abilities = new ArrayList<>();

    private StagedWeaponRegistration(DeferredRegister<Item> register, String modId,
                                     String baseName, int count) {
        this.register = register;
        this.modId = modId;
        this.baseName = baseName;
        this.count = count;
    }

    /** 创建注册构建器。 */
    public static <T extends Item & IWeaponItem> StagedWeaponRegistration<T> create(
            DeferredRegister<Item> register, String modId, String baseName, int count) {
        return new StagedWeaponRegistration<>(register, modId, baseName, count);
    }

    // ── Layer 1: 品质层级 ──

    /** 使用默认 5 级品质（平凡→传说）。 */
    public StagedWeaponRegistration<T> defaultTiers() {
        this.qualityTiers = QualityTier.DEFAULT_5;
        return this;
    }

    /** 使用自定义品质层级列表。 */
    public StagedWeaponRegistration<T> withTiers(List<QualityTier> tiers) {
        this.qualityTiers = List.copyOf(tiers);
        return this;
    }

    /** 设置品质层级数量（从 DEFAULT_5 截取或扩展）。 */
    public StagedWeaponRegistration<T> tierCount(int n) {
        this.qualityTiers = QualityTier.defaultsUpTo(n);
        return this;
    }

    // ── Layer 2: Profile ──

    /**
     * 设置代码默认 Profile 的 Supplier。
     * 调用 register() 时会执行一次，结果注册到 WeaponProfileRegistry。
     */
    public StagedWeaponRegistration<T> profile(Supplier<WeaponProfile> profileSupplier) {
        this.profileSupplier = profileSupplier;
        return this;
    }

    // ── 横切: Ability ──

    /** 注入武器特殊能力。可多次调用添加多个 Ability。 */
    public StagedWeaponRegistration<T> withAbility(IWeaponAbility ability) {
        this.abilities.add(ability);
        return this;
    }

    // ── 注册 ──

    /**
     * 执行注册并返回分级物品 Supplier 列表。
     *
     * @param factory 接收 level (1..N) 返回武器实例的工厂方法
     * @return 按 level 排序的 Supplier 列表（下标 0 = level 1）
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<Supplier<Item>> register(IntFunction<? extends T> factory) {
        // 1. 构建代码默认 Profile 并注册到集中注册表
        ResourceLocation weaponId = ResourceLocation.fromNamespaceAndPath(modId, baseName);
        WeaponProfile profile = profileSupplier != null ? profileSupplier.get() : null;

        if (profile != null) {
            if (profile.levelCount() != count) {
                throw new IllegalStateException(
                    "WeaponProfile for " + weaponId + ": levelCount=" + profile.levelCount()
                    + " but registration count=" + count);
            }
            WeaponProfileRegistry.register(weaponId, profile);
        }

        // 2. 注册各级物品
        List<Supplier<Item>> result = new ArrayList<>(count);
        for (int level = 1; level <= count; level++) {
            final int lv = level;
            String name = baseName + "_" + lv;
            var holder = register.register(name, () -> {
                T item = factory.apply(lv);
                // 注入 Ability
                if (!abilities.isEmpty() && item instanceof net.minecraft.client.yiz.api.IWeaponAbility.Host host) {
                    host.yizweapon$setAbilities(abilities);
                }
                return item;
            });
            Supplier raw = holder;
            result.add(raw);
        }
        return result;
    }
}
