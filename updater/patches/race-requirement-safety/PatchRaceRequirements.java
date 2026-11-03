import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Narrow guards for the two absent-race lookups observed in the client crash logs. */
public final class PatchRaceRequirements {
    public static void main(String[] args) throws Exception {
        Path source=Path.of(args[0]),target=Path.of(args[1]);
        boolean starlight=args[2].equals("starlight");
        String entry=starlight
            ?"com/github/propheticeclipse/tensurastarlight/ability/skill/ultimate/LetheSkill.class"
            :"io/github/Memoires/mysticism/handler/AbilityHandler.class";
        String owner=starlight
            ?"com/github/propheticeclipse/tensurastarlight/registry/StarlightRaces"
            :"io/github/Memoires/mysticism/registry/race/MysticismRaces";
        String field=starlight?"ASPECT_OF_REGRESSION":"INSECT";
        int changed=0;
        try(ZipFile input=new ZipFile(source.toFile());ZipOutputStream output=new ZipOutputStream(Files.newOutputStream(target))) {
            var entries=input.entries();
            while(entries.hasMoreElements()) {
                ZipEntry original=entries.nextElement();byte[] bytes=input.getInputStream(original).readAllBytes();
                if(original.getName().equals(entry)) {
                    ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
                    for(MethodNode method:node.methods) {
                        if(starlight && (!method.name.equals("checkAcquiringRequirement") || !method.desc.equals("(Lnet/minecraft/world/entity/player/Player;D)Z"))) continue;
                        for(AbstractInsnNode instruction:method.instructions.toArray()) {
                            if(!(instruction instanceof FieldInsnNode lookup) || lookup.getOpcode()!=Opcodes.GETSTATIC || !lookup.owner.equals(owner) || !lookup.name.equals(field)) continue;
                            AbstractInsnNode next=instruction.getNext();while(next!=null && next.getOpcode()<0)next=next.getNext();
                            if(!(next instanceof MethodInsnNode call) || !call.name.equals("get") || !call.desc.equals("()Ljava/lang/Object;")) throw new IllegalStateException("Race lookup shape changed");
                            if(starlight) {
                                InsnList guard=new InsnList();LabelNode present=new LabelNode();
                                guard.add(new FieldInsnNode(Opcodes.GETSTATIC,owner,field,lookup.desc));
                                guard.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,"dev/architectury/registry/registries/RegistrySupplier","isPresent","()Z",true));
                                guard.add(new JumpInsnNode(Opcodes.IFNE,present));guard.add(new InsnNode(Opcodes.ICONST_0));guard.add(new InsnNode(Opcodes.IRETURN));
                                guard.add(present);guard.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));method.instructions.insert(guard);
                            } else {
                                // Only the optional insect identity comparison gets a nullable fallback.
                                // Other race-change callbacks and the normal insect lottery remain intact.
                                method.instructions.insertBefore(call,new InsnNode(Opcodes.ACONST_NULL));
                                call.name="orElse";call.desc="(Ljava/lang/Object;)Ljava/lang/Object;";
                            }
                            changed++;
                        }
                    }
                    ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);bytes=writer.toByteArray();
                }
                output.putNextEntry(new ZipEntry(original));output.write(bytes);output.closeEntry();
            }
        }
        if(changed!=1){Files.deleteIfExists(target);throw new IllegalStateException("Expected one exact lookup, found "+changed);}
        System.out.println("Patched "+args[2]+": "+entry+"; exact lookups="+changed);
    }
}
