import java.nio.file.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Redirect the two shared UI/combat calls without rebuilding the existing item. */
public final class PatchBowScaling {
    public static void main(String[] args) throws Exception {
        Path file = Path.of(args[0]);
        ClassNode node = new ClassNode();
        new ClassReader(Files.readAllBytes(file)).accept(node, 0);
        int changed = 0;
        for (MethodNode method : node.methods) {
            if (node.name.endsWith("/BowProgress") && method.name.equals("mainDamage")
                    && method.desc.equals("(Lnet/minecraft/world/item/ItemStack;FI)D")) {
                method.instructions.clear();
                method.tryCatchBlocks.clear();
                if (method.localVariables != null) method.localVariables.clear();
                method.visibleLocalVariableAnnotations = null;
                method.invisibleLocalVariableAnnotations = null;
                method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                method.instructions.add(new VarInsnNode(Opcodes.FLOAD, 1));
                method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                        "ar/juan/beelzebuthbow/BowBalance", "damage", method.desc, false));
                method.instructions.add(new InsnNode(Opcodes.DRETURN));
                changed++;
            } else if (node.name.endsWith("/DevouringBowItem") && method.name.equals("appendHoverText")) {
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction instanceof MethodInsnNode call
                            && call.owner.equals("ar/juan/beelzebuthbow/BowProgress")
                            && call.name.equals("bonusPercent")
                            && call.desc.equals("(Lnet/minecraft/world/item/ItemStack;)I")) {
                        call.owner = "ar/juan/beelzebuthbow/BowBalance";
                        call.name = "baseDamage";
                        changed++;
                    }
                }
            }
        }
        if (changed != 1) throw new IllegalStateException("Expected one redirected call: " + node.name + ": " + changed);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        Files.write(file, writer.toByteArray());
    }
}
