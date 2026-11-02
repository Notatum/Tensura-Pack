import java.nio.file.*;
import java.util.zip.*;
import java.io.*;
import org.objectweb.asm.*;

/** Preserve existing EP Scaling fixes while accepting Tensura's optional maxEP. */
public class PatchGearDefaults {
    public static void main(String[] args) throws Exception {
        Path target=Path.of(args[0]), helper=Path.of(args[1]), temporary=target.resolveSibling(target.getFileName()+".tmp");
        int[] changes={0};
        try(ZipFile input=new ZipFile(target.toFile()); ZipOutputStream output=new ZipOutputStream(Files.newOutputStream(temporary))) {
            var entries=input.entries();
            while(entries.hasMoreElements()) {
                ZipEntry entry=entries.nextElement();byte[] bytes=input.getInputStream(entry).readAllBytes();
                if(entry.getName().equals("net/studio/redraven/tensura_ep_scaling/common/gear_ep/EPItemHandler.class")) {
                    ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(0);
                    reader.accept(new ClassVisitor(Opcodes.ASM9,writer) {
                        @Override public MethodVisitor visitMethod(int a,String name,String d,String s,String[] e) {
                            MethodVisitor next=super.visitMethod(a,name,d,s,e);
                            if(!name.startsWith("lambda$loadTensuraGear$")) return next;
                            return new MethodVisitor(Opcodes.ASM9,next) {
                                @Override public void visitMethodInsn(int opcode,String owner,String method,String descriptor,boolean itf) {
                                    if(opcode==Opcodes.INVOKEVIRTUAL && owner.equals("com/google/gson/JsonObject") && method.equals("get") && descriptor.equals("(Ljava/lang/String;)Lcom/google/gson/JsonElement;")) {
                                        super.visitMethodInsn(Opcodes.INVOKESTATIC,"net/studio/redraven/tensura_ep_scaling/compat/GearJsonCompat","get","(Lcom/google/gson/JsonObject;Ljava/lang/String;)Lcom/google/gson/JsonElement;",false);changes[0]++;
                                    } else super.visitMethodInsn(opcode,owner,method,descriptor,itf);
                                }
                            };
                        }
                    },0);bytes=writer.toByteArray();
                }
                ZipEntry copy=new ZipEntry(entry.getName());copy.setTime(entry.getTime());output.putNextEntry(copy);output.write(bytes);output.closeEntry();
            }
            output.putNextEntry(new ZipEntry("net/studio/redraven/tensura_ep_scaling/compat/GearJsonCompat.class"));output.write(Files.readAllBytes(helper));output.closeEntry();
        }
        if(changes[0]!=4) { Files.delete(temporary);throw new IOException("Expected four gear JSON reads, found "+changes[0]); }
        Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);
        System.out.println("EP Scaling now uses Tensura's default maxEP=2000000 when the optional JSON field is absent");
    }
}
