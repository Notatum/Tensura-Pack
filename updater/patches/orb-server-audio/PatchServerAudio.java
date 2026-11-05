import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Small, checked hooks preserve all existing gameplay and audio decoder bytecode. */
public final class PatchServerAudio implements Opcodes {
    private static final String COMMON="ar/juan/devouringorb/",CLIENT=COMMON+"client/";
    public static void main(String[] args)throws Exception{
        Path file=Path.of(args[0]);ClassNode node=new ClassNode();new ClassReader(Files.readAllBytes(file)).accept(node,0);
        int changed=0;
        if(node.name.equals(COMMON+"OrbNetwork")){
            for(var m:node.methods)if(m.name.equals("register"))for(var i:m.instructions.toArray())if(i instanceof LdcInsnNode c&&c.cst.equals("4")){c.cst="5";changed++;}
            if(changed!=1)throw new IllegalStateException("Protocol hook mismatch");
        }else if(node.name.equals(COMMON+"OrbSpeaker")){
            changed=wrap(node,"control","localControl",COMMON+"ServerAudioServer");if(changed!=1)throw new IllegalStateException("Speaker hook mismatch");
        }else if(node.name.equals(CLIENT+"AudioLibrary")){
            changed+=wrap(node,"files","localFiles",CLIENT+"ServerAudioClient");changed+=wrap(node,"resolve","localResolve",CLIENT+"ServerAudioClient");if(changed!=2)throw new IllegalStateException("Library hook mismatch");
        }else if(node.name.equals(CLIENT+"LocalAudio")){
            for(String name:List.of("music","preview","playing","stopPreview","clear"))changed+=wrap(node,name,"local"+Character.toUpperCase(name.charAt(0))+name.substring(1),CLIENT+"ServerAudioClient");
            for(var m:node.methods){
                if(m.name.equals("tick")){m.instructions.insert(new MethodInsnNode(INVOKESTATIC,CLIENT+"ServerAudioClient","tick","()V",false));changed++;}
                if(m.name.equals("localPreview"))for(var i:m.instructions.toArray())if(i instanceof MethodInsnNode call&&call.owner.equals(node.name)&&call.name.equals("stopPreview"))call.name="localStopPreview";
                if(m.name.startsWith("lambda$tick$")&&m.instructions.iterator().hasNext()){
                    boolean music=false;for(var i:m.instructions.toArray())if(i instanceof FieldInsnNode f&&f.owner.equals(CLIENT+"LocalAudio$Playing")&&f.name.equals("sound"))music=true;
                    if(music){
                        if(!m.desc.equals("(Lnet/minecraft/client/sounds/SoundManager;Ljava/util/Map$Entry;)Z"))throw new IllegalStateException("Music cleanup signature changed: "+m.desc);
                        m.instructions.clear();m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();
                        m.instructions.add(new VarInsnNode(ALOAD,1));m.instructions.add(new MethodInsnNode(INVOKEINTERFACE,"java/util/Map$Entry","getValue","()Ljava/lang/Object;",true));m.instructions.add(new TypeInsnNode(CHECKCAST,CLIENT+"LocalAudio$Playing"));m.instructions.add(new FieldInsnNode(GETFIELD,CLIENT+"LocalAudio$Playing","sound","L"+CLIENT+"LocalFileSound;"));m.instructions.add(new MethodInsnNode(INVOKESTATIC,CLIENT+"ServerAudioClient","finishedMusic","(L"+CLIENT+"LocalFileSound;)Z",false));m.instructions.add(new InsnNode(IRETURN));changed++;
                    }
                }
            }
            if(changed!=7)throw new IllegalStateException("Playback hook mismatch");
        }else if(node.name.equals(CLIENT+"OrbSpeakerScreen")){
            for(var f:node.fields)if(f.name.equals("musicTab")||f.name.equals("status")){f.access&=~ACC_PRIVATE;changed++;}
            for(var m:node.methods){
                if(m.name.equals("refresh")){m.access&=~ACC_PRIVATE;changed++;}
                if(m.name.equals("init"))for(var i:m.instructions.toArray())if(i instanceof LdcInsnNode text&&text.cst.equals("folder")){text.cst="server_local_folder";changed++;}
                if(m.name.equals("render")){InsnList hook=new InsnList();hook.add(new VarInsnNode(ALOAD,0));hook.add(new MethodInsnNode(INVOKESTATIC,CLIENT+"ServerAudioClient","decorate","(L"+CLIENT+"OrbSpeakerScreen;)V",false));m.instructions.insert(hook);changed++;}
            }
            if(changed!=5)throw new IllegalStateException("Screen hook mismatch");
        }else throw new IllegalArgumentException(node.name);
        ClassWriter out=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(out);Files.write(file,out.toByteArray());System.out.println(node.name+": "+changed+" hooks");
    }
    private static int wrap(ClassNode node,String name,String original,String delegate){
        List<MethodNode> matches=node.methods.stream().filter(m->m.name.equals(name)).toList();
        if(matches.size()!=1)throw new IllegalStateException("Expected one method: "+name);
        MethodNode old=matches.getFirst();if((old.access&ACC_STATIC)==0)throw new IllegalStateException("Expected static method");
        old.name=original;old.access=(old.access&~(ACC_PRIVATE|ACC_PROTECTED))|ACC_PUBLIC;
        MethodNode wrapper=new MethodNode(old.access,name,old.desc,old.signature,old.exceptions.toArray(String[]::new));
        int slot=0;for(Type arg:Type.getArgumentTypes(old.desc)){wrapper.instructions.add(new VarInsnNode(arg.getOpcode(ILOAD),slot));slot+=arg.getSize();}
        wrapper.instructions.add(new MethodInsnNode(INVOKESTATIC,delegate,name,old.desc,false));wrapper.instructions.add(new InsnNode(Type.getReturnType(old.desc).getOpcode(IRETURN)));node.methods.add(wrapper);return 1;
    }
}
