import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class PatchRetainedFixes {
    static final String GS="dev/dedek/greatsage/",EP="net/studio/redraven/tensura_ep_scaling/";
    static void replace(MethodNode m,InsnList instructions){m.instructions=instructions;m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();}
    static void earlyRank(MethodNode m,int local){
        InsnList ins=new InsnList();LabelNode pass=new LabelNode();
        ins.add(new VarInsnNode(Opcodes.ALOAD,local));ins.add(new MethodInsnNode(Opcodes.INVOKESTATIC,GS+"UniversalAnalysisAccess","alwaysAvailable","(L"+GS+"SageNode;)Z",false));
        ins.add(new JumpInsnNode(Opcodes.IFEQ,pass));ins.add(new InsnNode(Opcodes.ICONST_1));ins.add(new InsnNode(Opcodes.IRETURN));ins.add(pass);ins.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));m.instructions.insert(ins);
    }
    public static void main(String[] args)throws Exception{
        Path jar=Path.of(args[0]),old=Path.of(args[1]);boolean sage=args[2].equals("sage");
        Map<String,byte[]> entries=new LinkedHashMap<>();
        try(ZipFile z=new ZipFile(jar.toFile())){var es=z.entries();while(es.hasMoreElements()){var e=es.nextElement();entries.put(e.getName(),z.getInputStream(e).readAllBytes());}}
        try(ZipFile z=new ZipFile(old.toFile())){var es=z.entries();while(es.hasMoreElements()){
            var e=es.nextElement();String n=e.getName();
            if(sage?n.equals(GS+"UniversalAnalysisAccess.class"):(n.startsWith(EP+"common/EvolutionComponents")||n.equals(EP+"neoforge/EvolutionRepair.class")))entries.put(n,z.getInputStream(e).readAllBytes());
        }}
        int changed=0;
        for(var entry:entries.entrySet()){
            String name=entry.getKey();if(!name.endsWith(".class"))continue;
            boolean selected=sage?List.of(GS+"server/SageProgress.class",GS+"client/SageProgressClient.class",GS+"GreatSageGameRules.class",GS+"client/GreatSageRules.class").contains(name):List.of(EP+"common/ItemUtils.class",EP+"common/gear_ep/EPItemHandler.class",EP+"common/events/ItemEvoEvent.class",EP+"neoforge/TensuraEPScalingNeoForge.class").contains(name);
            if(!selected)continue;
            ClassNode node=new ClassNode();new ClassReader(entry.getValue()).accept(node,0);
            for(MethodNode m:node.methods){
                if(sage){
                    if(name.endsWith("/SageProgress.class")||name.endsWith("/SageProgressClient.class")){
                        if(m.name.equals("has"))earlyRank(m,name.endsWith("/server/SageProgress.class")?1:0);
                        if(m.name.equals("strings")&&m.desc.equals("(Lnet/minecraft/nbt/CompoundTag;Ljava/lang/String;)Ljava/util/Set;"))for(var i:m.instructions.toArray())if(i.getOpcode()==Opcodes.ARETURN){InsnList ins=new InsnList();ins.add(new VarInsnNode(Opcodes.ALOAD,1));ins.add(new MethodInsnNode(Opcodes.INVOKESTATIC,GS+"UniversalAnalysisAccess","includeDefaultRanks","(Ljava/util/Set;Ljava/lang/String;)Ljava/util/Set;",false));m.instructions.insertBefore(i,ins);}
                        for(var i:m.instructions.toArray())if(i instanceof MethodInsnNode call && call.owner.equals(GS+"SageNode")&&call.name.equals("payable")){call.setOpcode(Opcodes.INVOKESTATIC);call.owner=GS+"UniversalAnalysisAccess";call.name="costsPoints";call.desc="(L"+GS+"SageNode;)Z";call.itf=false;}
                    }
                    if(name.endsWith("/GreatSageGameRules.class")&&m.name.equals("access")){InsnList ins=new InsnList();ins.add(new VarInsnNode(Opcodes.ALOAD,0));ins.add(new MethodInsnNode(Opcodes.INVOKESTATIC,GS+"GreatSageGameRules","tools",m.desc,false));ins.add(new InsnNode(Opcodes.IRETURN));replace(m,ins);}
                    if(name.endsWith("/client/GreatSageRules.class")&&m.name.equals("allows"))for(var i:m.instructions.toArray())if(i instanceof FieldInsnNode f&&f.name.equals("requireSage"))m.instructions.set(i,new InsnNode(Opcodes.ICONST_0));
                }else{
                    if(name.endsWith("/ItemUtils.class")&&m.name.equals("handleEPItem")){InsnList ins=new InsnList();ins.add(new VarInsnNode(Opcodes.ALOAD,1));ins.add(new MethodInsnNode(Opcodes.INVOKESTATIC,EP+"neoforge/EvolutionRepair","repairStackIfTracked","(Lnet/minecraft/world/item/ItemStack;)V",false));m.instructions.insert(ins);}
                    if(name.endsWith("/EPItemHandler.class")&&m.name.equals("reload"))for(var i:m.instructions.toArray())if(i.getOpcode()==Opcodes.RETURN)m.instructions.insertBefore(i,new MethodInsnNode(Opcodes.INVOKESTATIC,EP+"common/EvolutionComponents","rebuildPredecessors","()V",false));
                    if(name.endsWith("/ItemEvoEvent.class")&&m.name.equals("<init>"))for(var i:m.instructions.toArray())if(i instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.PUTFIELD&&f.name.equals("evoStack")){InsnList ins=new InsnList();ins.add(new VarInsnNode(Opcodes.ALOAD,0));ins.add(new FieldInsnNode(Opcodes.GETFIELD,node.name,"evoStack","Lnet/minecraft/world/item/ItemStack;"));ins.add(new VarInsnNode(Opcodes.ALOAD,0));ins.add(new FieldInsnNode(Opcodes.GETFIELD,node.name,"stack","Lnet/minecraft/world/item/ItemStack;"));ins.add(new MethodInsnNode(Opcodes.INVOKESTATIC,EP+"common/EvolutionComponents","copyForEvolution","(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)V",false));m.instructions.insert(i,ins);}
                    if(name.endsWith("/TensuraEPScalingNeoForge.class")&&m.name.equals("<init>"))for(var i:m.instructions.toArray())if(i.getOpcode()==Opcodes.RETURN)m.instructions.insertBefore(i,new MethodInsnNode(Opcodes.INVOKESTATIC,EP+"neoforge/EvolutionRepair","register","()V",false));
                }
            }
            ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);entry.setValue(writer.toByteArray());changed++;
        }
        Path tmp=jar.resolveSibling(jar.getFileName()+".tmp");try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(tmp))){for(var e:entries.entrySet()){out.putNextEntry(new ZipEntry(e.getKey()));out.write(e.getValue());out.closeEntry();}}
        Files.move(tmp,jar,StandardCopyOption.REPLACE_EXISTING);System.out.println("Retained "+args[2]+" fixes in "+changed+" classes");
    }
}
