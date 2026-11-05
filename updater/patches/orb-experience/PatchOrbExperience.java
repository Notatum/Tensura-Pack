import java.nio.file.Files;
import java.nio.file.Path;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public final class PatchOrbExperience {
    public static void main(String[] args) throws Exception {
        Path file=Path.of(args[0]);
        ClassNode node=new ClassNode();new ClassReader(Files.readAllBytes(file)).accept(node,0);
        int edits=0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("consumeGround") || !method.desc.equals("(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;ILnet/minecraft/world/item/ItemStack;Ljava/util/List;)Lar/juan/devouringorb/FeedingBatch;")) continue;
            for (AbstractInsnNode insn : method.instructions)
                if (insn instanceof MethodInsnNode call && call.owner.equals("ar/juan/devouringorb/OrbExperience"))
                    throw new IllegalStateException("XP pickup already patched");
            InsnList call=new InsnList();
            for(int local=0;local<4;local++) call.add(new VarInsnNode(Opcodes.ALOAD,local));
            call.add(new VarInsnNode(Opcodes.ILOAD,4));
            call.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"ar/juan/devouringorb/OrbExperience","collect","(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;I)V",false));
            method.instructions.insert(call);edits++;
        }
        if(edits!=1) throw new IllegalStateException("Expected exactly one audited ground-consumption method, got "+edits);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);Files.write(file,writer.toByteArray());
    }
}
