import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;

public final class PatchInsect {
    public static void main(String[] args)throws Exception {
        Path jar=Path.of(args[0]),helper=Path.of(args[1]),tmp=jar.resolveSibling(jar.getFileName()+".tmp");
        int count=0;
        try(ZipFile input=new ZipFile(jar.toFile());ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(tmp))) {
            var entries=input.entries();
            while(entries.hasMoreElements()) {
                ZipEntry e=entries.nextElement();byte[] bytes=input.getInputStream(e).readAllBytes();
                if(e.getName().contains("/race/insect/") && e.getName().substring(e.getName().lastIndexOf('/')+1).startsWith("Divine") && e.getName().endsWith("Race.class")) {
                    ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
                    for(MethodNode method:node.methods) {
                        if(!method.name.equals("getNextEvolutions"))continue;
                        method.instructions.clear();method.tryCatchBlocks.clear();
                        if(method.localVariables!=null)method.localVariables.clear();
                        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,1));
                        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,2));
                        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"local/tensura/patch/InsectEvolutionSupport","next",method.desc,false));
                        method.instructions.add(new InsnNode(Opcodes.ARETURN));method.maxStack=2;count++;
                    }
                    ClassWriter writer=new ClassWriter(0);node.accept(writer);bytes=writer.toByteArray();
                }
                if(e.getName().equals("local/tensura/patch/InsectEvolutionSupport.class"))continue;
                out.putNextEntry(new ZipEntry(e.getName()));out.write(bytes);out.closeEntry();
            }
            out.putNextEntry(new ZipEntry("local/tensura/patch/InsectEvolutionSupport.class"));out.write(Files.readAllBytes(helper));out.closeEntry();
        }
        if(count!=13)throw new IllegalStateException("Expected 13 divine insect branches, found "+count);
        Files.move(tmp,jar,StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Patched "+count+" divine insect branches using configured Origins predecessors");
    }
}
