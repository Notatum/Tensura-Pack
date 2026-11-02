import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class PatchRetainedFixes {
    static final String EP="net/studio/redraven/tensura_ep_scaling/";
    static void replace(MethodNode m,InsnList instructions){m.instructions=instructions;m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();}
    public static void main(String[] args)throws Exception{
        if (args.length != 3 || !args[2].equals("ep")) throw new IllegalArgumentException("Solo se conservan los parches de EP Scaling. Great Sage debe permanecer idéntico a su publicación oficial.");
        Path jar=Path.of(args[0]),old=Path.of(args[1]);
        Map<String,byte[]> entries=new LinkedHashMap<>();
        try(ZipFile z=new ZipFile(jar.toFile())){var es=z.entries();while(es.hasMoreElements()){var e=es.nextElement();entries.put(e.getName(),z.getInputStream(e).readAllBytes());}}
        try(ZipFile z=new ZipFile(old.toFile())){var es=z.entries();while(es.hasMoreElements()){
            var e=es.nextElement();String n=e.getName();
            if(n.startsWith(EP+"common/EvolutionComponents")||n.equals(EP+"neoforge/EvolutionRepair.class"))entries.put(n,z.getInputStream(e).readAllBytes());
        }}
        int changed=0;
        for(var entry:entries.entrySet()){
            String name=entry.getKey();if(!name.endsWith(".class"))continue;
            boolean selected=List.of(EP+"common/ItemUtils.class",EP+"common/gear_ep/EPItemHandler.class",EP+"common/events/ItemEvoEvent.class",EP+"neoforge/TensuraEPScalingNeoForge.class").contains(name);
            if(!selected)continue;
            ClassNode node=new ClassNode();new ClassReader(entry.getValue()).accept(node,0);
            for(MethodNode m:node.methods){
                    if(name.endsWith("/ItemUtils.class")&&m.name.equals("handleEPItem")){InsnList ins=new InsnList();ins.add(new VarInsnNode(Opcodes.ALOAD,1));ins.add(new MethodInsnNode(Opcodes.INVOKESTATIC,EP+"neoforge/EvolutionRepair","repairStackIfTracked","(Lnet/minecraft/world/item/ItemStack;)V",false));m.instructions.insert(ins);}
                    if(name.endsWith("/EPItemHandler.class")&&m.name.equals("reload"))for(var i:m.instructions.toArray())if(i.getOpcode()==Opcodes.RETURN)m.instructions.insertBefore(i,new MethodInsnNode(Opcodes.INVOKESTATIC,EP+"common/EvolutionComponents","rebuildPredecessors","()V",false));
                    if(name.endsWith("/ItemEvoEvent.class")&&m.name.equals("<init>"))for(var i:m.instructions.toArray())if(i instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.PUTFIELD&&f.name.equals("evoStack")){InsnList ins=new InsnList();ins.add(new VarInsnNode(Opcodes.ALOAD,0));ins.add(new FieldInsnNode(Opcodes.GETFIELD,node.name,"evoStack","Lnet/minecraft/world/item/ItemStack;"));ins.add(new VarInsnNode(Opcodes.ALOAD,0));ins.add(new FieldInsnNode(Opcodes.GETFIELD,node.name,"stack","Lnet/minecraft/world/item/ItemStack;"));ins.add(new MethodInsnNode(Opcodes.INVOKESTATIC,EP+"common/EvolutionComponents","copyForEvolution","(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)V",false));m.instructions.insert(i,ins);}
                    if(name.endsWith("/TensuraEPScalingNeoForge.class")&&m.name.equals("<init>"))for(var i:m.instructions.toArray())if(i.getOpcode()==Opcodes.RETURN)m.instructions.insertBefore(i,new MethodInsnNode(Opcodes.INVOKESTATIC,EP+"neoforge/EvolutionRepair","register","()V",false));
            }
            ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);entry.setValue(writer.toByteArray());changed++;
        }
        Path tmp=jar.resolveSibling(jar.getFileName()+".tmp");try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(tmp))){for(var e:entries.entrySet()){out.putNextEntry(new ZipEntry(e.getKey()));out.write(e.getValue());out.closeEntry();}}
        Files.move(tmp,jar,StandardCopyOption.REPLACE_EXISTING);System.out.println("Retained "+args[2]+" fixes in "+changed+" classes");
    }
}
