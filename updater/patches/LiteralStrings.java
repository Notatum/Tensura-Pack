import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import com.google.gson.*;

public final class LiteralStrings {
    static boolean literal(AbstractInsnNode node){return node instanceof MethodInsnNode m&&m.owner.equals("net/minecraft/network/chat/Component")&&m.name.equals("literal")&&m.desc.equals("(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;");}
    public static void main(String[] args)throws Exception{
        Path mods=Path.of(args[0]);Map<String,String> translations=args.length>2?new Gson().fromJson(Files.readString(Path.of(args[2])),new com.google.gson.reflect.TypeToken<Map<String,String>>(){}.getType()):null;
        Map<String,List<String>> found=new TreeMap<>();int changes=0;
        for(Path path:Files.list(mods).filter(p->p.toString().endsWith(".jar")).toList()){
            Map<String,byte[]> entries=new LinkedHashMap<>();boolean dirty=false;
            try(ZipFile zip=new ZipFile(path.toFile())){
                var meta=zip.getEntry("META-INF/neoforge.mods.toml");if(meta==null)continue;
                String text=new String(zip.getInputStream(meta).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                if(!text.matches("(?s).*modId\\s*=\\s*\"(?:tensura[^\"]*|trorigins|mysticism|ascension|greatsage|trnightmare|trstarlight|ancient_artifacts|nightmareutils|elitetensura|beelzebuth[^\"]*|devouring_orb|epdungeon)\".*"))continue;
                var enumeration=zip.entries();while(enumeration.hasMoreElements()){
                    var e=enumeration.nextElement();byte[] data=zip.getInputStream(e).readAllBytes();
                    if(e.getName().endsWith(".class")){
                        ClassNode cls=new ClassNode();new ClassReader(data).accept(cls,0);boolean modified=false;
                        for(MethodNode method:cls.methods)for(var ins:method.instructions.toArray()){
                            if(!(ins instanceof LdcInsnNode ldc)||!(ldc.cst instanceof String value))continue;
                            var next=ins.getNext();while(next!=null&&next.getOpcode()<0)next=next.getNext();
                            if(!literal(next)||value.isBlank())continue;
                            found.computeIfAbsent(value,k->new ArrayList<>()).add(path.getFileName()+"::"+cls.name+"."+method.name);
                            if(translations!=null&&translations.containsKey(value)&&!translations.get(value).equals(value)){ldc.cst=translations.get(value);modified=dirty=true;changes++;}
                        }
                        if(modified){ClassWriter writer=new ClassWriter(0);cls.accept(writer);data=writer.toByteArray();}
                    }
                    entries.put(e.getName(),data);
                }
            }
            if(dirty){Path tmp=path.resolveSibling(path.getFileName()+".tmp");try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(tmp))){for(var e:entries.entrySet()){out.putNextEntry(new ZipEntry(e.getKey()));out.write(e.getValue());out.closeEntry();}}Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}
        }
        Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(found));System.out.println("Literal strings="+found.size()+"; replacements="+changes);
    }
}
