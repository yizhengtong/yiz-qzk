package net.minecraft.client.yiz.mixin;

import net.minecraft.client.yiz.core.StartupAbolishConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Map;

/**
 * 启动期 Item 注册替换。
 *
 * <p>把启动黑名单上的 Item 实例替换为基类 Item，
 * 让注册表里就根本没有该 mod 类的实例。</p>
 *
 * <h3>关键流程</h3>
 * <p>Item 注册表 hasIntrusiveHolders=true：</p>
 * <ol>
 *   <li>原 mod 创建黑名单物品 → 构造器调 createIntrusiveHolder
 *       → unregisteredIntrusiveHolders 多了 {黑名单物品 → holder_A}</li>
 *   <li>mod 调 register(id, key, 黑名单物品, info)</li>
 *   <li>我们的 mixin 在 HEAD 拦截：
 *     <ul>
 *       <li>构造一个新 Item(new Item.Properties())
 *           → 构造器自动调 createIntrusiveHolder
 *           → unregisteredIntrusiveHolders 多了 {emptyItem → holder_B}</li>
 *       <li>从 unregisteredIntrusiveHolders 删除黑名单物品的 holder_A
 *           （避免 freeze 时报"holders not registered"）</li>
 *       <li>返回 emptyItem 作为 register 的新 value 参数</li>
 *     </ul>
 *   </li>
 *   <li>register 继续：unregisteredIntrusiveHolders.remove(emptyItem) → holder_B
 *       → bindKey(infinity_sword key)
 *       → byKey/byLocation/byValue 都 put holder_B</li>
 * </ol>
 *
 * <p>最终：注册表 get(黑名单物品 id) → emptyItem，
 * holder.key 正常 bind，holder.value 也是 emptyItem。原 mod 的事件回调
 * `if (stack.getItem() == MyItems.XXX)` 返回 false，绕过。</p>
 */
@Mixin(MappedRegistry.class)
public abstract class StartupItemRegistryReplaceMixin {

    @Shadow
    private Map<Object, Holder.Reference<Object>> unregisteredIntrusiveHolders;

    @Unique
    private static final ThreadLocal<Boolean> yizmodqzk$replacing = ThreadLocal.withInitial(() -> false);

    @ModifyVariable(
            method = "register(ILnet/minecraft/resources/ResourceKey;Ljava/lang/Object;Lnet/minecraft/core/RegistrationInfo;)Lnet/minecraft/core/Holder$Reference;",
            at = @At("HEAD"),
            argsOnly = true,
            index = 3
    )
    private Object yizmodqzk$replaceItemValue(Object value, int id, ResourceKey<?> key,
                                               Object originalValue, RegistrationInfo info) {
        if (Boolean.TRUE.equals(yizmodqzk$replacing.get())) return value;
        if (key == null || key.registry() == null) return value;
        if (!key.registry().equals(Registries.ITEM.location())) return value;

        String idStr = key.location().toString().toLowerCase();
        if (!StartupAbolishConfig.isAbolished(idStr)) return value;

        yizmodqzk$replacing.set(true);
        try {
            // 新建一个空 Item。Item 构造器内部会调 createIntrusiveHolder，
            // 把 emptyItem 的 holder 加入 unregisteredIntrusiveHolders。
            // 这一步必须在 unregisteredIntrusiveHolders 字段已初始化的状态下。
            Item replacement = new Item(new Item.Properties());

            // 清理原 value（被黑名单物品）的 intrusive holder：
            // 它本来会在原 register 流程里被消费，但因为我们替换了 value，
            // 它会成为孤儿，freeze 时报错。
            if (this.unregisteredIntrusiveHolders != null && value != null) {
                this.unregisteredIntrusiveHolders.remove(value);
            }

            System.out.println("[StartupAbolish] Replaced " + idStr +
                    " (was " + (value != null ? value.getClass().getName() : "null") +
                    ") with empty Item");
            return replacement;
        } catch (Exception e) {
            // 注册表已冻结（如 NeoForge takeVanillaSnapshot 期间）→ new Item 会抛异常，
            // 跳过替换返回原值让快照正常完成
            System.err.println("[StartupAbolish] Cannot replace " + idStr +
                    " (registry frozen): " + e.getMessage());
            return value;
        } finally {
            yizmodqzk$replacing.set(false);
        }
    }
}
