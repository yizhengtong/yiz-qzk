var Opcodes = Java.type('org.objectweb.asm.Opcodes');
var VarInsnNode = Java.type('org.objectweb.asm.tree.VarInsnNode');
var MethodInsnNode = Java.type('org.objectweb.asm.tree.MethodInsnNode');
var InsnList = Java.type('org.objectweb.asm.tree.InsnList');
var ArrayList = Java.type('java.util.ArrayList');

function initializeCoreMod() {
    return {
        'yizmodqzk_gethealth_cap': {
            'target': {
                'type': 'CLASS',
                'name': 'net.minecraft.world.entity.LivingEntity'
            },
            'transformer': function (classNode) {
                var methods = classNode.methods;
                for (var i = 0; i < methods.size(); i++) {
                    var method = methods.get(i);
                    if (method.name !== 'getHealth' || method.desc !== '()F') continue;

                    var instructions = method.instructions;
                    var returns = new ArrayList();
                    for (var j = 0; j < instructions.size(); j++) {
                        var insn = instructions.get(j);
                        if (insn.getOpcode() === Opcodes.FRETURN) {
                            returns.add(insn);
                        }
                    }

                    for (var k = 0; k < returns.size(); k++) {
                        var ret = returns.get(k);
                        var inject = new InsnList();
                        inject.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        inject.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            'net/minecraft/client/yiz/tool/health/EntityASMUtil',
                            'specialCoreModGetHealth',
                            '(FLnet/minecraft/world/entity/LivingEntity;)F',
                            false
                        ));
                        instructions.insertBefore(ret, inject);
                    }
                    break;
                }
                return classNode;
            }
        }
    };
}
