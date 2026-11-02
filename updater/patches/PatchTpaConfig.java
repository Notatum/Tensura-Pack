import java.nio.file.*;
import java.util.zip.*;
import java.io.*;
import org.objectweb.asm.*;

/** Limit TPA 2.1.0's config handlers to their actual lifecycle events. */
public class PatchTpaConfig {
    public static void main(String[] args) throws Exception {
        Path target=Path.of(args[0]), temporary=target.resolveSibling(target.getFileName()+".tmp");
        int[] changes={0};
        try(ZipFile input=new ZipFile(target.toFile()); ZipOutputStream output=new ZipOutputStream(Files.newOutputStream(temporary))) {
            var entries=input.entries();
            while(entries.hasMoreElements()) {
                ZipEntry entry=entries.nextElement();
                byte[] bytes=input.getInputStream(entry).readAllBytes();
                if(entry.getName().equals("fox/mods/tpa/config/ModConfig.class")) {
                    ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(0);
                    reader.accept(new ClassVisitor(Opcodes.ASM9,writer) {
                        @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions) {
                            if(descriptor.equals("(Lnet/neoforged/fml/event/config/ModConfigEvent;)V")) {
                                if(name.equals("onLoad")) { descriptor="(Lnet/neoforged/fml/event/config/ModConfigEvent$Loading;)V"; changes[0]++; }
                                if(name.equals("onReload")) { descriptor="(Lnet/neoforged/fml/event/config/ModConfigEvent$Reloading;)V"; changes[0]++; }
                            }
                            return super.visitMethod(access,name,descriptor,signature,exceptions);
                        }
                    },0);
                    bytes=writer.toByteArray();
                }
                ZipEntry copy=new ZipEntry(entry.getName());copy.setTime(entry.getTime());
                output.putNextEntry(copy);output.write(bytes);output.closeEntry();
            }
        }
        if(changes[0]!=2) { Files.delete(temporary);throw new IOException("Expected exactly two original TPA handlers; found "+changes[0]); }
        Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Patched both TPA config handlers; unloading no longer reads unloaded config");
    }
}
