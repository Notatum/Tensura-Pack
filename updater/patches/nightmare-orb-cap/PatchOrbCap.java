import java.nio.file.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class PatchOrbCap {
    public static void main(String[] args) throws Exception {
        Path file=Path.of(args[0]);ClassNode node=new ClassNode();new ClassReader(Files.readAllBytes(file)).accept(node,0);int count=0;
        String helper="com/github/hvnbael/trnightmare/feature/combat/OrbCapCompatibility";
        for(MethodNode method:node.methods) if(method.name.equals("apply") && method.desc.equals("(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)F")) {
            for(AbstractInsnNode instruction:method.instructions.toArray()) if(instruction instanceof MethodInsnNode call && call.owner.equals(node.name)) {
                String result=Type.getReturnType(call.desc).getDescriptor();
                if(call.name.equals("limits") || call.name.equals("perHitCap")) {
                    InsnList filter=new InsnList();filter.add(new VarInsnNode(Opcodes.ALOAD,1));
                    filter.add(new MethodInsnNode(Opcodes.INVOKESTATIC,helper,call.name.equals("limits")?"limits":"cap","("+result+"Lnet/minecraft/world/damagesource/DamageSource;)"+result,false));
                    method.instructions.insert(call,filter);count++;
                }
            }
        }
        if(count!=2) throw new IllegalStateException("Damage cap consumers changed: "+count);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);Files.write(file,writer.toByteArray());System.out.println("Orb-only cap filters="+count+"; boss phase gate preserved");
    }
}
