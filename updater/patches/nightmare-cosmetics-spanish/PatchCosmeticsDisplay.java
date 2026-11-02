import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Translate String arguments only at final rendering/layout APIs, not at semantic keys. */
public final class PatchCosmeticsDisplay {
    private static final String TARGET="com/github/hvnbael/trnightmare/client/screen/CosmeticScreen";
    private static final String BRIDGE="com/github/hvnbael/trnightmare/client/screen/CosmeticsSpanish";

    private static boolean displayCall(MethodInsnNode call) {
        return call.owner.equals("net/minecraft/client/gui/GuiGraphics") &&
                (call.name.equals("drawString") || call.name.equals("drawCenteredString"))
            || call.owner.equals("net/minecraft/client/gui/Font") &&
                (call.name.equals("width") || call.name.equals("plainSubstrByWidth"))
            || call.owner.equals("net/minecraft/network/chat/Component") && call.name.equals("literal");
    }

    public static void main(String[] args) throws Exception {
        Path file=Path.of(args[0]);ClassNode node=new ClassNode();new ClassReader(Files.readAllBytes(file)).accept(node,0);
        if(!node.name.equals(TARGET)) throw new IllegalArgumentException("Only CosmeticScreen may be patched");
        Map<String,Integer> counts=new TreeMap<>();int changes=0;
        for(MethodNode method:node.methods) {
            for(AbstractInsnNode instruction:method.instructions.toArray()) {
                if(!(instruction instanceof MethodInsnNode call) || !displayCall(call)) continue;
                Type[] types=Type.getArgumentTypes(call.desc);boolean hasString=false;
                for(Type type:types) if(type.equals(Type.getType(String.class))) hasString=true;
                if(!hasString) continue;
                int[] locals=new int[types.length];InsnList replacement=new InsnList();
                for(int index=types.length-1;index>=0;index--) {
                    locals[index]=method.maxLocals;method.maxLocals+=types[index].getSize();
                    replacement.add(new VarInsnNode(types[index].getOpcode(Opcodes.ISTORE),locals[index]));
                }
                for(int index=0;index<types.length;index++) {
                    replacement.add(new VarInsnNode(types[index].getOpcode(Opcodes.ILOAD),locals[index]));
                    if(types[index].equals(Type.getType(String.class)))
                        replacement.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"display","(Ljava/lang/String;)Ljava/lang/String;",false));
                }
                method.instructions.insertBefore(call,replacement);changes++;
                counts.merge(call.owner+"."+call.name,1,Integer::sum);
            }
        }
        if(changes<20) throw new IllegalStateException("Unexpected cosmetic renderer shape: "+changes+" calls");
        ClassWriter output=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(output);Files.write(file,output.toByteArray());
        System.out.println("Cosmetics display calls wrapped="+changes+" "+counts);
    }
}
