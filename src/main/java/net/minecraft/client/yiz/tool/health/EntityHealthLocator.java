package net.minecraft.client.yiz.tool.health;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.loading.FMLPaths;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体真实血量字段定位器 —— 「全能扫描 / 偏移匹配」。
 *
 * <p>解决自研血量实体（override {@code getHealth()} 不走原版，如部分模组的
 * {@code totalDamageTaken} 式血量）无法被 {@link EntityASMUtil#modifyHealth}（Delta 通道）持久扣血的问题。</p>
 *
 * <p><b>原理</b>：对实体类型<b>首个实例</b>做一次「细微改动 + 偏移匹配」：
 * <ol>
 *   <li>快照实体所有数值字段（float/double/int/long，含父类链）</li>
 *   <li>隐蔽触发：{@code hurt(generic, maxHealth × 0.01%)}——极小伤害，几乎无感；generic 无攻击者，
 *       不会触发 {@code onHurtReturn} 的攻方属性消费（无递归）</li>
 *   <li>重扫：找值变化量 ≈ 0.01% maxHealth 的字段——即存储真实血量的字段
 *       （反向：{@code totalDamageTaken} 受击时 +delta → 血量 = maxHealth - 字段；正向：血量存储字段 -delta）</li>
 *   <li>按实体类缓存到 {@code config/yizmodqzk/entity_health_slots.json}，后续实例直接复用，避免重复扫描</li>
 * </ol></p>
 *
 * <p>持久扣血 {@link #applyPersistentDamage}：直接反射写该字段（inverse → 字段 +amount，血量 -amount；
 * 正向 → 字段 -amount），不经过 Delta/衰减，不会回弹。</p>
 */
public final class EntityHealthLocator {

    private static final String FILE_NAME = "yizmodqzk/entity_health_slots.json";

    /** 隐蔽触发伤害 = maxHealth 的 0.01%。 */
    private static final double TRIGGER_RATIO = 0.0001;

    /** 实体类名 → 血量槽信息（JSON 持久化）。 */
    public record HealthSlot(String className, String fieldName, String type, boolean inverse) {}

    /** 实体类名 → 解析后的反射字段（运行期缓存）。 */
    private static final Map<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, HealthSlot> CACHE = new ConcurrentHashMap<>();

    private static final ThreadLocal<Boolean> SCANNING = ThreadLocal.withInitial(() -> false);

    private EntityHealthLocator() {}

    // ==================== 公共 API ====================

    /** 该实体类型是否已有血量槽缓存。 */
    public static boolean hasSlot(LivingEntity entity) {
        return entity != null && CACHE.containsKey(entity.getClass().getName());
    }

    /** 获取实体类型的血量槽；无缓存则依次尝试字节码探测 → 运行期扫描，并保存。 */
    public static HealthSlot locate(LivingEntity entity) {
        if (entity == null) return null;
        String key = entity.getClass().getName();
        HealthSlot cached = CACHE.get(key);
        if (cached != null) return cached;
        if (SCANNING.get()) return null; // 防递归：扫描中不再触发
        HealthSlot slot = detectViaBytecode(entity); // 新增：字节码探测（静态、无副作用）
        if (slot == null) {
            slot = scan(entity); // 现有：运行期细微伤害 + 偏移匹配（fallback）
        }
        if (slot != null) {
            CACHE.put(key, slot);
            save();
        }
        return slot;
    }

    /**
     * 读取定位字段当前值（反射）；未定位/失败返回 null。
     * 供绝妄生机字段级禁疗（{@code VitalitySeveranceHandler.enforceFieldTick}）使用。
     */
    public static Double readLocated(LivingEntity entity) {
        HealthSlot slot = locate(entity);
        if (slot == null) return null;
        Field f = resolveField(slot);
        if (f == null) return null;
        try {
            return readField(f, entity);
        } catch (Exception e) {
            return null;
        }
    }

    /** 按定位槽写入字段（反射）。 */
    public static void writeLocated(LivingEntity entity, double value) {
        HealthSlot slot = locate(entity);
        if (slot == null) return;
        Field f = resolveField(slot);
        if (f == null) return;
        try {
            writeField(f, entity, value);
        } catch (Exception ignored) {}
    }

    /**
     * 持久扣血（经定位到的真实血量字段）。
     * <ul>
     *   <li><b>inverse=true</b>（totalDamageTaken 类，血量 = maxHealth − 字段）：<b>降低 maxHealth</b>——
     *       自研血量的回血（heal）只减字段不减 maxHealth，故降 maxHealth 扣血不会回弹；用固定 modifier id 累计。</li>
     *   <li><b>正向</b>（血量存储类）：直接减字段。</li>
     * </ul>
     *
     * @return true 表示已持久扣血；false（未定位/失败）调用方应回退 Delta。
     */
    public static boolean applyPersistentDamage(LivingEntity entity, float amount) {
        // 【调试】入口：确认调用 + slot 定位
        net.minecraft.client.yiz.tizMod.LOGGER.info("[Apply] {} amount={}",
            entity != null ? entity.getClass().getSimpleName() : "null", amount);
        if (entity == null || amount <= 0) return false;
        HealthSlot slot = locate(entity);
        net.minecraft.client.yiz.tizMod.LOGGER.info("[Apply] {} slot={}", entity.getClass().getSimpleName(), slot);
        if (slot == null) return false;
        Field f = resolveField(slot);
        if (f == null) return false;
        try {
            double cur = readField(f, entity);
            // inverse=true（totalDamageTaken 类）：扣血 = 字段 +amount（血量 = maxHealth − 字段）；
            // 正向（血量存储类）：扣血 = 字段 −amount
            double next = slot.inverse() ? cur + amount : cur - amount;
            float healthBefore = entity.getHealth();
            writeField(f, entity, next);
            // 【调试】确认字段写入 + 写入后立即回读（弹回定位）
            net.minecraft.client.yiz.tizMod.LOGGER.info("[Persist] {} field={} before={} after={} readBack={}",
                entity.getClass().getSimpleName(), f.getName(), cur, next, readField(f, entity));
            // 写入验证：真实血量槽写入后 getHealth 必须同步下降。
            // 下降下限用 min(amount*0.5, healthBefore)：目标快死时 getHealth 被钳到 ≥0，
            // 下降量会被 0 下限截断（如 3 血写 10 → 只降 3）。若仍按 amount*0.5 判定会误判假槽 →
            // 回滚删缓存 + fallback Delta → 最初梦幻对 totalDamageTaken 型实体「最后一段血」失效（卡血）。
            // 若不变 → 历史误判槽（lastHurt/damageBucket/totalDamageTakenInCombat），回滚并移除缓存，
            // 返回 false 让调用方回退 Delta（原本可改的路径）。
            float healthAfter = entity.getHealth();
            if (healthBefore - healthAfter < Math.min(amount * 0.5f, healthBefore)) {
                writeField(f, entity, cur); // 回滚
                CACHE.remove(entity.getClass().getName());
                save();
                net.minecraft.client.yiz.tizMod.LOGGER.warn("[Apply] {} slot {} is bogus: getHealth {} -> {}, rollback -> fallback Delta",
                    entity.getClass().getSimpleName(), f.getName(), healthBefore, healthAfter);
                return false;
            }
            // 本模组主动扣血后更新字段基线，防止字段级绝妄生机/写入守卫把这次扣血当成「回弹」抵消回去
            net.minecraft.client.yiz.tool.health.VitalitySeveranceHandler.updateFieldBaseline(entity);
            net.minecraft.client.yiz.tool.health.HealthWriteGuard.updateBaseline(entity);
            return true;
        } catch (Exception e) {
            net.minecraft.client.yiz.tizMod.LOGGER.warn("[Apply] write failed: {}", e.toString());
            return false;
        }
    }

    // ==================== 扫描定位 ====================

    private static HealthSlot scan(LivingEntity entity) {
        if (entity.level().isClientSide()) return null;
        float delta = (float) (entity.getMaxHealth() * TRIGGER_RATIO);
        if (delta <= 0) return null;

        List<Field> fields = collectNumericFields(entity.getClass());
        if (fields.isEmpty()) return null;

        SCANNING.set(true);
        try {
            Map<Field, Double> before = snapshot(entity, fields);
            // 隐蔽触发：极小伤害（0.01% maxHealth）。generic 无攻击者 → onHurtReturn 提前 return，无递归
            entity.hurt(entity.damageSources().generic(), delta);
            Map<Field, Double> after = snapshot(entity, fields);

            double tolerance = Math.max(delta * 0.5, 1e-4);
            for (Field f : fields) {
                double b = before.getOrDefault(f, 0.0);
                double a = after.getOrDefault(f, 0.0);
                double diff = a - b;
                if (Math.abs(diff) < 1e-9) continue;
                boolean inverse;
                // 反向（totalDamageTaken 类）：受击 +delta → 血量 = maxHealth - 字段
                if (Math.abs(diff - delta) <= tolerance) {
                    inverse = true;
                } else if (Math.abs(diff + delta) <= tolerance) {
                    // 正向（血量存储类）：受击 -delta
                    inverse = false;
                } else {
                    continue;
                }
                // 语义验证：写入该字段后 getHealth() 必须同步下降 ≈delta。
                // 否则是「伤害累积」干扰字段（部分模组 Boss 的伤害累积桶、测试假人的累计受击计数
                // 等，受击时也 +delta 但 getHealth 不读它）——写入无效，不能当血量槽缓存。
                if (isRealHealthField(entity, f, delta, inverse)) {
                    return new HealthSlot(entity.getClass().getName(), f.getName(), typeName(f), inverse);
                }
            }
        } finally {
            SCANNING.remove();
        }
        return null;
    }

    // ==================== 字节码探测（补充路径） ====================

    /**
     * 字节码探测：分析 {@code getHealth()} 方法的字节码，定位其直接返回的 float/double 字段。
     * <p>针对「重写 getHealth 直接返回某字段」的自研血量实体（如 {@code return this.currentHp;}），
     * 静态无副作用——不需对实体触发伤害（解决扫描被禁/不受伤害实体的盲区）。</p>
     * <p>1.21.1 运行期官方映射 → 方法名 {@code getHealth} 恒定，无需 SRG {@code m_} 双名兜底。</p>
     * <p>返回 {@code HealthSlot(className, fieldName, type, inverse=false)}（正向血量存储字段）；
     * 全 try-catch 失败返回 null → 回落现有运行期扫描。</p>
     */
    private static HealthSlot detectViaBytecode(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide()) return null;
        try {
            Class<?> clazz = entity.getClass();
            String internalName = clazz.getName().replace('.', '/');
            byte[] bytes;
            try (InputStream in = clazz.getClassLoader().getResourceAsStream(internalName + ".class")) {
                if (in == null) return null;
                bytes = in.readAllBytes();
            }
            if (bytes.length == 0) return null;

            ClassReader cr = new ClassReader(bytes);
            // 匹配 getHealth ()F；返回直接 GETFIELD/GETSTATIC 的 float/double 字段
            String[] found = new String[1];
            cr.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    // 只分析 getHealth 方法（运行期官方映射名恒定）
                    if (!"getHealth".equals(name) || !"()F".equals(descriptor)) {
                        return super.visitMethod(access, name, descriptor, signature, exceptions);
                    }
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitFieldInsn(int opcode, String owner, String name2, String descriptor2) {
                            if (found[0] != null) return;
                            // 找 GETFIELD/GETSTATIC 且类型为 float 或 double
                            if ((opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC)
                                    && ("F".equals(descriptor2) || "D".equals(descriptor2))) {
                                found[0] = name2;
                            }
                            super.visitFieldInsn(opcode, owner, name2, descriptor2);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            if (found[0] == null) return null;
            // 字段必须真实存在于类层级（getHealth 可能返回父类字段）
            Field f = findFieldInHierarchy(clazz.getName(), found[0]);
            if (f == null) return null;
            return new HealthSlot(clazz.getName(), found[0], typeName(f), false);
        } catch (Throwable e) {
            return null; // 任何异常（类未加载/字节不可读）静默失败 → 回落运行期扫描
        }
    }

    /**
     * 语义验证候选字段是否为真实血量槽：
     * 按槽语义写入一次微小探测，检查 {@link LivingEntity#getHealth()} 是否同步下降 ≈delta。
     * <ul>
     *   <li>真血量槽（totalDamageTaken 型）：getHealth 读该字段，写入后血量随之下降 ✓</li>
     *   <li>伤害累积干扰字段（部分模组的伤害累积桶/累计受击计数、基类 lastHurt）：
     *       getHealth 不读它，写入后血量不变 ✗</li>
     * </ul>
     * 探测后回滚写入，不残留副作用。
     */
    private static boolean isRealHealthField(LivingEntity entity, Field f, double delta, boolean inverse) {
        try {
            double cur = readField(f, entity);
            float healthBefore = entity.getHealth();
            double probe = inverse ? cur + delta : cur - delta;
            writeField(f, entity, probe);
            float healthAfter = entity.getHealth();
            writeField(f, entity, cur); // 回滚探测写入
            // inverse: 字段 +delta → 血量 −delta；正向: 字段 −delta → 血量 −delta。两者都应使血量下降 ≈delta
            return healthBefore - healthAfter >= delta * 0.5f;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 反射工具 ====================

    private static Field resolveField(HealthSlot slot) {
        String key = slot.className() + "#" + slot.fieldName();
        Field f = FIELD_CACHE.get(key);
        if (f == null) {
            f = findFieldInHierarchy(slot.className(), slot.fieldName());
            if (f != null) FIELD_CACHE.put(key, f);
        }
        return f;
    }

    /** 从实体类沿父类链查找字段（如 totalDamageTaken 定义在 IAnimatedBoss，getDeclaredField 只查当前类会漏）。 */
    private static Field findFieldInHierarchy(String className, String fieldName) {
        try {
            Class<?> c = Class.forName(className);
            for (Class<?> cl = c; cl != null && cl != Object.class; cl = cl.getSuperclass()) {
                try {
                    Field f = cl.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException ignored) {}
            }
        } catch (ClassNotFoundException ignored) {}
        return null;
    }

    private static List<Field> collectNumericFields(Class<?> clazz) {
        List<Field> out = new ArrayList<>();
        // 只收集 LivingEntity 子类声明的字段，跳过原版基类（LivingEntity / Entity）。
        // 基类里的 lastHurt / hurtTime / invulnerableTime / 位置字段等受击必变的干扰字段，
        // 会被「细微伤害 + 偏移匹配」误判成 totalDamageTaken 型血量槽（inverse=true）并持久化缓存，
        // 使 applyPersistentDamage 命中假槽、不再回退 Delta —— 最初梦幻 / Delta 打不动这些实体。
        // 真实血量字段（totalDamageTaken 等）都定义在模组自有类上，
        // 仍在 LivingEntity 之下，不受影响。
        for (Class<?> c = clazz; c != null && c != LivingEntity.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isFinal(mod)) continue;
                Class<?> t = f.getType();
                if (t == float.class || t == double.class || t == int.class || t == long.class) {
                    try {
                        f.setAccessible(true);
                        out.add(f);
                    } catch (Exception ignored) {}
                }
            }
        }
        return out;
    }

    private static Map<Field, Double> snapshot(LivingEntity entity, List<Field> fields) {
        Map<Field, Double> map = new HashMap<>();
        for (Field f : fields) {
            try { map.put(f, readField(f, entity)); } catch (Exception ignored) {}
        }
        return map;
    }

    private static double readField(Field f, Object target) throws IllegalAccessException {
        Class<?> t = f.getType();
        if (t == float.class) return f.getFloat(target);
        if (t == double.class) return f.getDouble(target);
        if (t == int.class) return f.getInt(target);
        if (t == long.class) return f.getLong(target);
        return 0;
    }

    private static void writeField(Field f, Object target, double v) throws IllegalAccessException {
        Class<?> t = f.getType();
        if (t == float.class) f.setFloat(target, (float) v);
        else if (t == double.class) f.setDouble(target, v);
        else if (t == int.class) f.setInt(target, (int) v);
        else if (t == long.class) f.setLong(target, (long) v);
    }

    private static String typeName(Field f) {
        return f.getType().getSimpleName();
    }

    // ==================== JSON 缓存（参考 HudPositionConfig） ====================

    public static void load() {
        try {
            Path p = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
            if (!Files.exists(p)) return;
            JsonObject root = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
            JsonObject slots = root.getAsJsonObject("slots");
            if (slots == null) return;
            for (String key : slots.keySet()) {
                JsonObject o = slots.getAsJsonObject(key);
                CACHE.put(key, new HealthSlot(
                    o.get("class").getAsString(),
                    o.get("field").getAsString(),
                    o.get("type").getAsString(),
                    o.has("inverse") && o.get("inverse").getAsBoolean()));
            }
        } catch (Exception ignored) {}
    }

    public static void save() {
        try {
            Path p = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
            Files.createDirectories(p.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("_version", 1);
            JsonObject slots = new JsonObject();
            CACHE.forEach((k, s) -> {
                JsonObject o = new JsonObject();
                o.addProperty("class", s.className());
                o.addProperty("field", s.fieldName());
                o.addProperty("type", s.type());
                o.addProperty("inverse", s.inverse());
                slots.add(k, o);
            });
            root.add("slots", slots);
            Files.writeString(p, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (Exception ignored) {}
    }
}
