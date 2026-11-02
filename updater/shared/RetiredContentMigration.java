import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.*;

/** Offline, transactional removal of retired registry entries. No Minecraft classes required. */
public final class RetiredContentMigration {
    private static final String MARKER=".tensura-nightmare-retired-v2";
    private static final int LIMIT=64*1024*1024;
    private record Tag(int type,Object value) {}
    private record Tags(int type,List<Tag> values) {}
    private record Change(String relative,boolean delete) {}
    private record Plan(Path root,Path backup,List<Change> changes) {}
    private static long replacements;

    public static void main(String[] args) throws Exception {
        if(args.length<3) throw new IllegalArgumentException("prepare-client|prepare-server|apply|rollback ROOT BACKUP");
        Path root=Path.of(args[1]).toRealPath(), backup=Path.of(args[2]).toAbsolutePath().normalize();
        switch(args[0]) {
            case "prepare-client" -> prepare(root,backup,true);
            case "prepare-server" -> prepare(root,backup,false);
            case "apply" -> apply(root,backup);
            case "rollback" -> rollback(root,backup);
            default -> throw new IllegalArgumentException("Acción de migración desconocida");
        }
    }

    private static Path child(Path root,Path path) throws IOException {
        Path result=path.toAbsolutePath().normalize();
        if(!result.startsWith(root) || result.equals(root)) throw new IOException("Ruta de migración fuera de la instalación: "+path);
        for(Path p=result; p!=null && !p.equals(root); p=p.getParent())
            if(Files.isSymbolicLink(p) || Files.exists(p,LinkOption.NOFOLLOW_LINKS) && (Files.readAttributes(p,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).isOther() || !p.toRealPath().startsWith(root.toRealPath())))
                throw new IOException("La migración no admite enlaces: "+p);
        return result;
    }

    private static boolean retired(String value) {
        return value.startsWith("trnightmare:") || value.startsWith("nightmareutils:")
            || value.equals("mod:trnightmare") || value.equals("mod:nightmareutils") || value.equals("mod:textanimator");
    }
    private static boolean retiredKey(String value) {
        String name=value.toLowerCase(Locale.ROOT);
        return name.startsWith("trnightmare") || name.startsWith("nightmareutils")
            || name.contains("/trnightmare:") || name.contains("/nightmareutils:");
    }

    @SuppressWarnings("unchecked") private static Map<String,Tag> compound(Tag t) { return (Map<String,Tag>)t.value; }
    private static String string(Map<String,Tag> map,String name) {
        Tag value=map.get(name);return value!=null && value.type==8?(String)value.value:"";
    }
    private static Tag text(String value) { return new Tag(8,value); }

    /** null removes a registry entry. Palette slots and fixed equipment slots retain their indices. */
    private static Tag clean(Tag tag,String key,int[] spawn) {
        if(tag.type==10) {
            Map<String,Tag> data=compound(tag);
            for(String field:List.of("id","Id","skill","ability","manascore_registry_storage_id"))
                if(retired(string(data,field))) { replacements++;return null; }
            for(String field:List.of("dimension","dimension_key","destination_dimension","target_dimension"))
                if(retired(string(data,field))) { replacements++;return null; }
            Tag nested=data.get("item");
            if(nested!=null && nested.type==10 && retired(string(compound(nested),"id"))) { replacements++;return null; }
            if(retired(string(data,"Name"))) {
                replacements++;data.put("Name",text("minecraft:air"));data.remove("Properties");
            }
            String race=string(data,"race");
            if(retired(race)) {
                replacements++;data.put("race",text(race.contains("slime")?"tensura:slime":"tensura:human"));
            }
            if(retired(string(data,"Dimension"))) {
                replacements++;data.put("Dimension",text("minecraft:overworld"));
                data.put("Pos",new Tag(9,new Tags(6,new ArrayList<>(List.of(new Tag(6,spawn[0]+0.5),new Tag(6,spawn[1]+1.0),new Tag(6,spawn[2]+0.5))))));
                data.put("FallDistance",new Tag(5,0.0f));
                data.remove("enteredNetherPosition");
            }
            for(var iterator=data.entrySet().iterator();iterator.hasNext();) {
                var entry=iterator.next();
                if(retiredKey(entry.getKey())) { replacements++;iterator.remove();continue; }
                Tag result=clean(entry.getValue(),entry.getKey(),spawn);
                if(result==null) iterator.remove();else entry.setValue(result);
            }
            return tag;
        }
        if(tag.type==9) {
            Tags list=(Tags)tag.value;
            for(var it=list.values.listIterator();it.hasNext();) {
                Tag original=it.next(), value;
                // Biome palette indices must survive the replacement.
                if(key.equals("palette") && original.type==8 && retired((String)original.value)) {
                    replacements++;value=text("minecraft:plains");
                } else value=clean(original,key,spawn);
                if(value==null && (key.equals("HandItems") || key.equals("ArmorItems"))) value=new Tag(10,new LinkedHashMap<String,Tag>());
                if(value==null) it.remove();else it.set(value);
            }
        } else if(tag.type==8 && retired((String)tag.value)) { replacements++;return null; }
        return tag;
    }

    private static int size(DataInput input) throws IOException {
        int n=input.readInt();if(n<0 || n>LIMIT) throw new IOException("Longitud NBT inválida: "+n);return n;
    }
    private static Tag read(DataInput in,int type,int depth) throws IOException {
        if(depth>512) throw new IOException("NBT demasiado profundo");
        return new Tag(type,switch(type) {
            case 1 -> in.readByte();case 2 -> in.readShort();case 3 -> in.readInt();case 4 -> in.readLong();case 5 -> in.readFloat();case 6 -> in.readDouble();
            case 7 -> { byte[] bytes=new byte[size(in)];in.readFully(bytes);yield bytes; }
            case 8 -> in.readUTF();
            case 9 -> {int kind=in.readUnsignedByte(),n=size(in);if(kind==0 && n!=0) throw new IOException("Lista NBT inválida");List<Tag> values=new ArrayList<>();for(int i=0;i<n;i++) values.add(read(in,kind,depth+1));yield new Tags(kind,values);}
            case 10 -> {Map<String,Tag> values=new LinkedHashMap<>();int kind;while((kind=in.readUnsignedByte())!=0) values.put(in.readUTF(),read(in,kind,depth+1));yield values;}
            case 11 -> {int n=size(in);int[] values=new int[n];for(int i=0;i<n;i++) values[i]=in.readInt();yield values;}
            case 12 -> {int n=size(in);long[] values=new long[n];for(int i=0;i<n;i++) values[i]=in.readLong();yield values;}
            default -> throw new IOException("Tipo NBT desconocido: "+type);
        });
    }
    private static void write(DataOutput out,Tag tag) throws IOException {
        switch(tag.type) {
            case 1 -> out.writeByte((Byte)tag.value);case 2 -> out.writeShort((Short)tag.value);case 3 -> out.writeInt((Integer)tag.value);case 4 -> out.writeLong((Long)tag.value);case 5 -> out.writeFloat((Float)tag.value);case 6 -> out.writeDouble((Double)tag.value);
            case 7 -> {byte[] b=(byte[])tag.value;out.writeInt(b.length);out.write(b);}case 8 -> out.writeUTF((String)tag.value);
            case 9 -> {Tags values=(Tags)tag.value;out.writeByte(values.type);out.writeInt(values.values.size());for(Tag value:values.values) write(out,value);}
            case 10 -> {for(var entry:compound(tag).entrySet()) {out.writeByte(entry.getValue().type);out.writeUTF(entry.getKey());write(out,entry.getValue());}out.writeByte(0);}
            case 11 -> {int[] a=(int[])tag.value;out.writeInt(a.length);for(int value:a) out.writeInt(value);}
            case 12 -> {long[] a=(long[])tag.value;out.writeInt(a.length);for(long value:a) out.writeLong(value);}
            default -> throw new IOException("Tipo NBT inválido");
        }
    }
    private static InputStream decompress(byte[] bytes,int compression) throws IOException {
        return switch(compression) {case 1 -> new GZIPInputStream(new ByteArrayInputStream(bytes));case 2 -> new InflaterInputStream(new ByteArrayInputStream(bytes));case 3 -> new ByteArrayInputStream(bytes);default -> throw new IOException("Compresión de región no admitida: "+compression);};
    }
    private static byte[] compress(byte[] bytes,int compression) throws IOException {
        ByteArrayOutputStream result=new ByteArrayOutputStream();
        OutputStream out=switch(compression) {case 1 -> new GZIPOutputStream(result);case 2 -> new DeflaterOutputStream(result);case 3 -> result;default -> throw new IOException("Compresión inválida");};
        out.write(bytes);out.close();return result.toByteArray();
    }
    private static byte[] nbt(byte[] bytes,int compression,int[] spawn) throws IOException {
        long before=replacements;
        Tag root;String name;
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(decompress(bytes,compression)))) {
            int type=in.readUnsignedByte();name=in.readUTF();root=read(in,type,0);
            if(type!=10) throw new IOException("Raíz NBT inválida");
        }
        if(clean(root,"",spawn)==null) return null;
        if(before==replacements) return bytes;
        ByteArrayOutputStream result=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(result);
        out.writeByte(root.type);out.writeUTF(name);write(out,root);out.flush();return compress(result.toByteArray(),compression);
    }

    private static byte[] region(byte[] input,int[] spawn) throws IOException {
        if(input.length==0) return input; // RegionFile may create an empty POI file before its first save.
        if(input.length<8192) throw new IOException("Región truncada");
        ByteArrayOutputStream output=new ByteArrayOutputStream();output.write(Arrays.copyOf(input,8192));
        byte[] header=Arrays.copyOf(input,8192);ByteBuffer h=ByteBuffer.wrap(header), source=ByteBuffer.wrap(input);
        long before=replacements;
        for(int slot=0;slot<1024;slot++) {
            int location=source.getInt(slot*4),offset=(location>>>8)*4096,count=location&255;
            if(location==0) continue;
            if(offset<8192 || count==0 || (long)offset+5>input.length) throw new IOException("Ubicación de chunk inválida");
            int length=source.getInt(offset);if(length<1 || length>count*4096-4 || (long)offset+4+length>input.length) throw new IOException("Longitud de chunk inválida");
            int compression=input[offset+4]&255;
            // External/LZ4 chunks are rejected before any original file changes.
            byte[] data=nbt(Arrays.copyOfRange(input,offset+5,offset+4+length),compression,spawn);
            if(data==null) throw new IOException("La raíz del chunk no tiene el formato de una región");
            int sectors=(data.length+5+4095)/4096;
            if(sectors>255) throw new IOException("El chunk requiere almacenamiento externo");
            h.putInt(slot*4,((output.size()/4096)<<8)|sectors);
            output.write(ByteBuffer.allocate(5).putInt(data.length+1).put((byte)compression).array());output.write(data);
            output.write(new byte[sectors*4096-data.length-5]);
        }
        if(before==replacements) return input;
        byte[] result=output.toByteArray();System.arraycopy(header,0,result,0,8192);return result;
    }

    /** Small strict JSON codec for vanilla advancements/stats; preserves numbers as written. */
    private static final class Json {
        private record Number(String text) {}
        final String source;int position;
        Json(String source) {this.source=source;}
        void space() {while(position<source.length() && Character.isWhitespace(source.charAt(position))) position++;}
        char take() throws IOException {if(position>=source.length()) throw new IOException("JSON truncado");return source.charAt(position++);}
        String string() throws IOException {
            if(take()!='"') throw new IOException("Cadena JSON inválida");StringBuilder value=new StringBuilder();
            for(char c;(c=take())!='"';) {
                if(c=='\\') {c=take();switch(c) {case '"','\\','/' -> value.append(c);case 'b' -> value.append('\b');case 'f' -> value.append('\f');case 'n' -> value.append('\n');case 'r' -> value.append('\r');case 't' -> value.append('\t');case 'u' -> {if(position+4>source.length()) throw new IOException("Escape JSON truncado");try {value.append((char)Integer.parseInt(source.substring(position,position+4),16));}catch(NumberFormatException e) {throw new IOException("Escape JSON inválido",e);}position+=4;}default -> throw new IOException("Escape JSON inválido");}}
                else {if(c<32) throw new IOException("Control JSON inválido");value.append(c);}
            }return value.toString();
        }
        Object value(int depth) throws IOException {
            if(depth>512) throw new IOException("JSON demasiado profundo");space();if(position>=source.length()) throw new IOException("JSON vacío");char c=source.charAt(position);
            if(c=='"') return string();
            if(c=='{' || c=='[') {
                position++;space();boolean map=c=='{';char close=map?'}':']';Map<String,Object> entries=new LinkedHashMap<>();List<Object> list=new ArrayList<>();
                if(position<source.length() && source.charAt(position)==close) {position++;return map?entries:list;}
                while(true) {
                    space();if(map) {String key=string();space();if(take()!=':') throw new IOException("Objeto JSON inválido");entries.put(key,value(depth+1));}else list.add(value(depth+1));
                    space();char separator=take();if(separator==close) break;if(separator!=',') throw new IOException("Separador JSON inválido");
                }return map?entries:list;
            }
            int start=position;while(position<source.length() && ",]} \r\n\t".indexOf(source.charAt(position))<0) position++;
            String token=source.substring(start,position);
            if(token.equals("true")) return Boolean.TRUE;if(token.equals("false")) return Boolean.FALSE;if(token.equals("null")) return null;
            if(!token.matches("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")) throw new IOException("Número JSON inválido");return new Number(token);
        }
        static String quote(String s) {
            StringBuilder out=new StringBuilder("\"");for(char c:s.toCharArray()) switch(c) {case '"','\\' -> out.append('\\').append(c);case '\n' -> out.append("\\n");case '\r' -> out.append("\\r");case '\t' -> out.append("\\t");default -> {if(c<32) out.append(String.format("\\u%04x",(int)c));else out.append(c);}}return out.append('"').toString();
        }
        static String encode(Object value) {
            if(value instanceof String s) return quote(s);if(value instanceof Number n) return n.text;
            if(value instanceof Map<?,?> map) {List<String> parts=new ArrayList<>();for(var e:map.entrySet()) parts.add(quote((String)e.getKey())+":"+encode(e.getValue()));return "{"+String.join(",",parts)+"}";}
            if(value instanceof List<?> list) {List<String> parts=new ArrayList<>();for(Object item:list) parts.add(encode(item));return "["+String.join(",",parts)+"]";}
            return String.valueOf(value);
        }
        static Object clean(Object value) {
            if(value instanceof Map<?,?> raw) {
                @SuppressWarnings("unchecked") Map<String,Object> map=(Map<String,Object>)raw;
                for(var it=map.entrySet().iterator();it.hasNext();) {var e=it.next();if(retiredKey(e.getKey()) || retired(e.getKey()) || e.getValue() instanceof String s && retired(s)) {replacements++;it.remove();}else e.setValue(clean(e.getValue()));}
            } else if(value instanceof List<?> raw) {
                @SuppressWarnings("unchecked") List<Object> list=(List<Object>)raw;
                for(var it=list.listIterator();it.hasNext();) {Object item=it.next();if(item instanceof String s && retired(s)) {replacements++;it.remove();}else it.set(clean(item));}
            }return value;
        }
    }
    private static byte[] json(byte[] input) throws IOException {
        long before=replacements;Json parser=new Json(new String(input,java.nio.charset.StandardCharsets.UTF_8));Object value=parser.value(0);parser.space();
        if(parser.position!=parser.source.length()) throw new IOException("Contenido posterior al JSON");Json.clean(value);
        return replacements==before?input:(Json.encode(value)+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static int[] spawn(Path world) throws IOException {
        try(DataInputStream in=new DataInputStream(new GZIPInputStream(Files.newInputStream(world.resolve("level.dat"))))) {
            int type=in.readUnsignedByte();in.readUTF();Map<String,Tag> root=compound(read(in,type,0));Tag data=root.get("Data");
            Map<String,Tag> values=data==null?root:compound(data);int[] result={0,80,0};String[] keys={"SpawnX","SpawnY","SpawnZ"};
            for(int i=0;i<3;i++) if(values.containsKey(keys[i])) result[i]=((Number)values.get(keys[i]).value).intValue();return result;
        }
    }
    private static void stage(Plan plan,Path file,byte[] data) throws IOException {
        file=child(plan.root,file);String relative=plan.root.relativize(file).toString();
        if(plan.changes.stream().anyMatch(c->c.relative.equals(relative))) return;
        if(Files.isRegularFile(file)) {Path original=plan.backup.resolve("original").resolve(relative);Files.createDirectories(original.getParent());Files.copy(file,original);}
        if(data!=null) {Path pending=plan.backup.resolve("pending").resolve(relative);Files.createDirectories(pending.getParent());Files.write(pending,data);}
        plan.changes.add(new Change(relative,data==null));
    }
    private static void retireTree(Plan plan,Path dir) throws IOException {
        if(!Files.exists(dir)) return;child(plan.root,dir);
        try(Stream<Path> files=Files.walk(dir)) {for(Path file:files.filter(Files::isRegularFile).toList()) stage(plan,file,null);}
    }
    public static void prepare(Path root,Path backup,boolean client) throws Exception {
        child(root,backup);if(Files.exists(backup.resolve("plan.bin"))) throw new IOException("Ya existe un plan de migración en "+backup);
        Files.createDirectories(backup);Plan plan=new Plan(root,backup,new ArrayList<>());List<Path> worlds=new ArrayList<>();
        if(client) {Path saves=root.resolve("saves");if(Files.isDirectory(saves)) try(Stream<Path> list=Files.list(saves)) {worlds.addAll(list.filter(p->Files.isRegularFile(p.resolve("level.dat"))).toList());}}
        else {Properties props=new Properties();try(Reader reader=Files.newBufferedReader(root.resolve("server.properties"))) {props.load(reader);}String name=props.getProperty("level-name","world");Path world=child(root,root.resolve(name));if(Files.isRegularFile(world.resolve("level.dat"))) worlds.add(world);}
        for(Path world:worlds) {
            child(root,world);if(Files.exists(world.resolve(MARKER))) continue;
            // The server updater already holds this lock. Client worlds are checked independently.
            FileChannel channel=null;FileLock lock=null;
            try {
                if(client && Files.exists(world.resolve("session.lock"))) {channel=FileChannel.open(world.resolve("session.lock"),StandardOpenOption.WRITE);lock=channel.tryLock();if(lock==null) throw new IOException("Cerrá el mundo antes de actualizar: "+world.getFileName());}
                int[] spawn=spawn(world);
                retireTree(plan,world.resolve("dimensions/trnightmare"));retireTree(plan,world.resolve("dimensions/nightmareutils"));
                retireTree(plan,world.resolve("serverconfig/nightmare"));retireTree(plan,world.resolve("serverconfig/nightmareutils"));
                try(Stream<Path> files=Files.walk(world)) {
                    for(Path file:files.filter(Files::isRegularFile).toList()) {
                        Path relative=world.relativize(file);String name=file.getFileName().toString();
                        if(relative.toString().startsWith("dimensions"+File.separator+"trnightmare") || relative.toString().startsWith("dimensions"+File.separator+"nightmareutils") || relative.toString().contains(".tensura")) continue;
                        if(retiredKey(name) && (name.endsWith(".dat") || name.endsWith(".dat_old"))) {stage(plan,file,null);continue;}
                        boolean json=name.endsWith(".json") && (relative.startsWith("advancements") || relative.startsWith("stats"));
                        if(!(name.endsWith(".mca") || name.endsWith(".dat") || name.endsWith(".dat_old") || json)) continue;
                        child(root,file);byte[] before=Files.readAllBytes(file),after;
                        try {after=json?json(before):name.endsWith(".mca")?region(before,spawn):nbt(before,1,spawn);}
                        catch(Exception e) {throw new IOException("No se pudo preparar "+relative+"; los originales se conservaron",e);}
                        if(!Arrays.equals(before,after)) stage(plan,file,after);
                    }
                }
                stage(plan,world.resolve(MARKER),("Nightmare retired v2\n"+LocalDateTime.now()+"\nBackup="+backup+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } finally {if(lock!=null) lock.close();if(channel!=null) channel.close();}
        }
        for(String config:List.of("config/nightmare","config/nightmareutils","defaultconfigs/nightmare","defaultconfigs/nightmareutils")) retireTree(plan,root.resolve(config));
        try(DataOutputStream out=new DataOutputStream(Files.newOutputStream(backup.resolve("plan.bin")))) {
            out.writeUTF(root.toString());out.writeInt(plan.changes.size());for(Change change:plan.changes) {out.writeUTF(change.relative);out.writeBoolean(change.delete);}
        }
        Files.writeString(backup.resolve("LEEME.txt"),"Respaldo previo a retirar TR: Nightmare.\nLos archivos originales están en original/.\nSe conserva el EP, el inventario ajeno a Nightmare y los mundos existentes.\nRazas de Nightmare: slime de Tensura si eran slimes; humano en los demás casos.\nEntradas retiradas: "+replacements+"\nArchivos preparados: "+plan.changes.size()+"\n");
        System.out.println("[Tensura] Nightmare: "+replacements+" entradas retiradas; "+plan.changes.size()+" archivos preparados con respaldo.");
    }
    private static Plan plan(Path root,Path backup) throws IOException {
        child(root,backup);List<Change> changes=new ArrayList<>();
        try(DataInputStream in=new DataInputStream(Files.newInputStream(backup.resolve("plan.bin")))) {
            if(!Path.of(in.readUTF()).equals(root)) throw new IOException("El respaldo pertenece a otra instalación");
            int size=size(in);for(int i=0;i<size;i++) {String path=in.readUTF();child(root,root.resolve(path));changes.add(new Change(path,in.readBoolean()));}
        }
        return new Plan(root,backup,changes);
    }
    private static void replace(Path from,Path to) throws IOException {
        Files.createDirectories(to.getParent());Path temporary=to.resolveSibling(to.getFileName()+".tensura-pending");
        Files.copy(from,temporary,StandardCopyOption.REPLACE_EXISTING);
        try {Files.move(temporary,to,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException e) {Files.move(temporary,to,StandardCopyOption.REPLACE_EXISTING);}
    }
    public static void apply(Path root,Path backup) throws Exception {
        Plan plan=plan(root,backup);
        try {
            for(Change c:plan.changes) {Path target=child(root,root.resolve(c.relative));if(c.delete) Files.deleteIfExists(target);else replace(backup.resolve("pending").resolve(c.relative),target);}
            for(Change c:plan.changes) if(c.delete) {
                for(Path parent=child(root,root.resolve(c.relative)).getParent();parent!=null && !parent.equals(root);parent=parent.getParent()) {
                    if(!Files.isDirectory(parent)) break;
                    try(Stream<Path> list=Files.list(parent)) {if(list.findAny().isPresent()) break;}
                    Files.delete(parent);
                }
            }
        } catch(Exception e) {try {rollback(root,backup);} catch(Exception failed) {e.addSuppressed(failed);}throw e;}
        System.out.println("[Tensura] Migración de Nightmare aplicada. Respaldo: "+backup);
    }
    public static void rollback(Path root,Path backup) throws Exception {
        Plan plan=plan(root,backup);
        for(Change c:plan.changes) {Path target=child(root,root.resolve(c.relative)),original=backup.resolve("original").resolve(c.relative);if(Files.exists(original)) replace(original,target);else Files.deleteIfExists(target);}
        System.out.println("[Tensura] Datos previos a la migración restaurados.");
    }
}
