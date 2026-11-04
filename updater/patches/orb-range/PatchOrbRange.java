import java.nio.file.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Increase only the last radius-table entry from50to80; preserve the other stages. */
public final class PatchOrbRange {
    public static void main(String[] args) throws Exception {
        Path file=Path.of(args[0]);ClassNode node=new ClassNode();new ClassReader(Files.readAllBytes(file)).accept(node,0);
        if(!node.name.equals("ar/juan/devouringorb/OrbBalance"))throw new IllegalStateException("Unexpected class");
        int fields=0,entries=0;
        for(FieldNode field:node.fields)if(field.name.equals("MAX_RADIUS")&&field.desc.equals("D")){
            if(!Double.valueOf(50).equals(field.value))throw new IllegalStateException("Unexpected previous maximum");
            field.value=80d;fields++;
        }
        for(MethodNode method:node.methods)if(method.name.equals("<clinit>")){
            for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof LdcInsnNode constant && Double.valueOf(50).equals(constant.cst)){
                constant.cst=80d;entries++;
            }
        }
        if(fields!=1||entries!=1)throw new IllegalStateException("Final radius patch count mismatch: "+fields+"/"+entries);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);Files.write(file,writer.toByteArray());
    }
}
