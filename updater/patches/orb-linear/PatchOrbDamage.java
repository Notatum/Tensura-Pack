import java.nio.file.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Replace every old damage call chain, asserting the expected number of consumers. */
public final class PatchOrbDamage {
    public static void main(String[] args) throws Exception {
        Path file=Path.of(args[0]);
        ClassNode node=new ClassNode();new ClassReader(Files.readAllBytes(file)).accept(node,0);
        int calls=0, tooltip=0;
        String prefix="ar/juan/devouringorb/";
        for (MethodNode method:node.methods) {
            for (AbstractInsnNode instruction:method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode first) || !first.owner.equals(prefix+"OrbProgress") || !first.name.equals("exactPoints")) continue;
                AbstractInsnNode next=first.getNext();
                if (!(next instanceof MethodInsnNode second) || !second.owner.equals(prefix+"OrbBalance") || !second.name.equals("damage")) continue;
                AbstractInsnNode after=second.getNext();
                boolean formatted=after instanceof MethodInsnNode third && third.owner.equals(prefix+"OrbProgress") && third.name.equals("format");
                first.owner=prefix+"OrbDamage";first.name=formatted?"formatted":"damage";
                first.desc="(Lnet/minecraft/world/item/ItemStack;)"+(formatted?"Ljava/lang/String;":"D");
                method.instructions.remove(second);
                if (formatted) method.instructions.remove(after);
                calls++;
            }
            if (node.name.equals(prefix+"DevouringOrbItem") && method.name.equals("appendHoverText")) {
                for (AbstractInsnNode instruction:method.instructions.toArray()) if (instruction.getOpcode()==Opcodes.RETURN) {
                    InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,1));hook.add(new VarInsnNode(Opcodes.ALOAD,3));
                    hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,prefix+"OrbDamage","explain","(Lnet/minecraft/world/item/ItemStack;Ljava/util/List;)V",false));
                    method.instructions.insertBefore(instruction,hook);tooltip++;
                }
            }
        }
        if(calls!=1 || (node.name.endsWith("DevouringOrbItem") && tooltip!=1)) throw new IllegalStateException("Unexpected bytecode consumers: "+node.name+" calls="+calls+" tooltips="+tooltip);
        ClassWriter output=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(output);Files.write(file,output.toByteArray());
        System.out.println(node.name+": verified "+calls+" damage consumer, "+tooltip+" tooltip hook");
    }
}
