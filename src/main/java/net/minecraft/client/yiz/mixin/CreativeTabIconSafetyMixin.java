package net.minecraft.client.yiz.mixin;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 防御：当启动期 Item 替换把某个 mod item 换成了普通 Item，
 * 该 mod 的创造标签页图标 supplier 里如果做了 {@code (MyItem) stack.getItem()}
 * 强转，会抛 ClassCastException 崩溃。
 *
 * <p>这个 Mixin 拦截 {@link CreativeModeTab#getIconItem()}，
 * 在调用 iconGenerator 时包一层 try-catch。首次崩溃 log warning，
 * 对该 tab 静默返回 AIR。</p>
 */
@Mixin(CreativeModeTab.class)
public class CreativeTabIconSafetyMixin {

    @Shadow
    private Supplier<ItemStack> iconGenerator;

    @Shadow
    private ItemStack iconItemStack;

    @Unique
    private static final Set<CreativeModeTab> yizmodqzk$warnedTabs = ConcurrentHashMap.newKeySet();

    @Inject(method = "getIconItem", at = @At("HEAD"), cancellable = true)
    private void yizmodqzk$safeGetIconItem(CallbackInfoReturnable<ItemStack> cir) {
        CreativeModeTab self = (CreativeModeTab) (Object) this;

        // Already cached → let vanilla return it normally
        if (this.iconItemStack != null && !this.iconItemStack.isEmpty()) return;

        // Try to generate icon, catch any ClassCastException
        try {
            this.iconItemStack = this.iconGenerator.get();
        } catch (ClassCastException e) {
            this.iconItemStack = new ItemStack(Items.AIR);
            if (yizmodqzk$warnedTabs.add(self)) {
                System.err.println("[YizQZK] Creative tab icon ClassCastException" +
                        " (item was replaced?): " + e.getMessage() +
                        " — falling back to AIR");
            }
        }

        cir.setReturnValue(this.iconItemStack);
    }
}
