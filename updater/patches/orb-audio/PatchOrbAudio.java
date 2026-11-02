import java.nio.file.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class PatchOrbAudio {
    public static void main(String[] args) throws Exception {
        Path file=Path.of(args[0]);ClassNode node=new ClassNode();new ClassReader(Files.readAllBytes(file)).accept(node,0);
        int lengths=0,protocol=0;
        for(MethodNode method:node.methods) {
            for(AbstractInsnNode instruction:method.instructions.toArray()) {
                if(instruction instanceof IntInsnNode constant && constant.operand==96 &&
                    (node.name.endsWith("AudioRules") || node.name.endsWith("OrbNetwork") && (method.name.equals("writeSettings") || method.name.equals("readSettings")) || node.name.endsWith("OrbSpeakerScreen") && method.name.equals("init"))) {
                    method.instructions.set(constant,new IntInsnNode(Opcodes.SIPUSH,255));lengths++;
                }
                if(node.name.endsWith("OrbNetwork") && method.name.equals("register") && instruction instanceof LdcInsnNode constant && constant.cst.equals("3")) {constant.cst="4";protocol++;}
            }
        }
        if(node.name.endsWith("AudioRules")) {
            for(FieldNode field:node.fields) if(field.name.equals("MAX_NAME")) field.value=255;
            if(lengths!=1) throw new IllegalStateException("AudioRules length guard missing");
        } else if(node.name.endsWith("OrbNetwork")) {
            if(lengths!=4 || protocol!=1) throw new IllegalStateException("Network boundaries missing: "+lengths+" / "+protocol);
        } else if(lengths!=1) throw new IllegalStateException("Search length missing");
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);Files.write(file,writer.toByteArray());
        System.out.println(node.name+": length guards="+lengths+", protocol="+protocol);
    }
}
