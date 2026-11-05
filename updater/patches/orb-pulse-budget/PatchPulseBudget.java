import java.nio.file.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class PatchPulseBudget {
    public static void main(String[] args) throws Exception {
        Path path=Path.of(args[0]);ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(path)).accept(c,0);
        int pulses=0,access=0;
        for(MethodNode m:c.methods){
            if(m.name.equals("pulse")&&m.desc.equals("(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/ItemStack;)V")){
                m.instructions.clear();m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();
                for(int local=0;local<3;local++)m.instructions.add(new VarInsnNode(Opcodes.ALOAD,local));
                m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"ar/juan/devouringorb/OrbPulseWork","start",m.desc,false));
                m.instructions.add(new InsnNode(Opcodes.RETURN));m.maxLocals=3;m.maxStack=3;pulses++;
            }else if(m.name.equals("consumeGround")||m.name.equals("isEnemy")){
                if((m.access&Opcodes.ACC_PRIVATE)==0)throw new IllegalStateException("Unexpected audited access "+m.name);
                m.access&=~Opcodes.ACC_PRIVATE;access++;
            }
        }
        if(pulses!=1||access!=2)throw new IllegalStateException("Unexpected pulse/access matches: "+pulses+"/"+access);
        ClassWriter out=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(out);Files.write(path,out.toByteArray());
    }
}
