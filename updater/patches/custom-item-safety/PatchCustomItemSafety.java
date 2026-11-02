import java.nio.file.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Targeted changes; unrelated dungeon, combat and progression bytecode is retained. */
public final class PatchCustomItemSafety {
    public static void main(String[] args) throws Exception {
        Path file=Path.of(args[1]);ClassNode node=new ClassNode();
        new ClassReader(Files.readAllBytes(file)).accept(node,0);
        int changed=0;
        for(MethodNode method:node.methods) {
            if(args[0].equals("config")) {
                for(AbstractInsnNode instruction:method.instructions) {
                    if(instruction instanceof LdcInsnNode constant && constant.cst instanceof String text) {
                        if(text.equals("EP Dungeon: ajustes propios. No modifica las reglas globales de Tensura.")) {
                            constant.cst="Ajustes de EP Dungeon. El addon también excluye Charybdis del requisito global de reinicio.";changed++;
                        }
                        if(text.equals("Multiplica el EP base de los 5 niveles: 5k, 15k, 37.5k, 75k, 150k.")) {
                            constant.cst="Multiplica el EP nominal de los cinco pisos. El ajuste posterior x0.408 deja 2040, 6120, 15300, 30600 y 61200 EP para enemigos normales con multiplicador 1.";changed++;
                        }
                    }
                }
            }
            if(args[0].equals("dungeon") && method.name.equals("blockFloor6Items")) {
                method.instructions.clear();method.instructions.add(new InsnNode(Opcodes.RETURN));
                method.tryCatchBlocks.clear();if(method.localVariables!=null)method.localVariables.clear();
                method.maxStack=0;method.maxLocals=2;changed++;
            }
            if(args[0].equals("bow") && method.name.equals("use")) {
                for(AbstractInsnNode instruction:method.instructions) {
                    if(instruction instanceof MethodInsnNode call && call.getOpcode()==Opcodes.INVOKESTATIC
                        && call.owner.equals("ar/juan/beelzebuthbow/BowHands") && call.name.equals("isBow")) {
                        call.owner="ar/juan/beelzebuthbow/BowFoodGuard";call.name="isProtectedWeapon";changed++;
                    }
                }
            }
        }
        int expected=args[0].equals("config")?2:1;
        if(changed!=expected)throw new IllegalStateException("Expected "+expected+" targeted changes, found "+changed);
        ClassWriter writer=new ClassWriter(0);node.accept(writer);Files.write(file,writer.toByteArray());
        System.out.println("Patched "+args[0]+" item protection");
    }
}
