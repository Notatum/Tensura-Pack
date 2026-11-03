import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Keep Ascension's ritual and progression, removing only its multiblock and cooldown. */
public final class PatchSimpleAltar {
    private static final String ADDON = "com/simplygray/tensuraaddon/";
    private static final String RITUAL = ADDON + "handler/AwakeningRitual.class";
    private static final String CONFIG = ADDON + "AddonConfig.class";
    private static final String HOLDER = "net/neoforged/neoforge/registries/DeferredHolder";

    private static AbstractInsnNode nextCode(AbstractInsnNode instruction) {
        do { instruction = instruction.getNext(); }
        while (instruction != null && instruction.getOpcode() < 0);
        return instruction;
    }

    private static void clearCode(MethodNode method) {
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        if (method.localVariables != null) method.localVariables.clear();
        if (method.visibleLocalVariableAnnotations != null) method.visibleLocalVariableAnnotations.clear();
        if (method.invisibleLocalVariableAnnotations != null) method.invisibleLocalVariableAnnotations.clear();
    }

    private static byte[] patchRitual(byte[] source) {
        ClassNode node = new ClassNode();
        new ClassReader(source).accept(node, 0);
        int validation = 0, blueprint = 0, cooldown = 0;
        for (MethodNode method : node.methods) {
            if (method.name.equals("validateMultiblock") && method.desc.equals(
                    "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z")) {
                clearCode(method);
                method.instructions.add(new InsnNode(Opcodes.ICONST_1));
                method.instructions.add(new InsnNode(Opcodes.IRETURN));
                validation++;
            } else if (method.name.equals("blueprint") && method.desc.equals("()Ljava/util/Map;")) {
                clearCode(method);
                method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,
                        "net/minecraft/core/BlockPos", "ZERO", "Lnet/minecraft/core/BlockPos;"));
                method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,
                        ADDON + "registry/AddonBlocks", "AWAKENING_ALTAR", "L" + HOLDER + ";"));
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                        HOLDER, "get", "()Ljava/lang/Object;", false));
                method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST,
                        "net/minecraft/world/level/block/Block"));
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                        "net/minecraft/world/level/block/Block", "defaultBlockState",
                        "()Lnet/minecraft/world/level/block/state/BlockState;", false));
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                        "java/util/Map", "of", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;", true));
                method.instructions.add(new InsnNode(Opcodes.ARETURN));
                blueprint++;
            } else {
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (!(instruction instanceof FieldInsnNode field)
                            || field.getOpcode() != Opcodes.GETSTATIC
                            || !field.owner.equals(ADDON + "AddonConfig")
                            || !field.name.equals("AWAKENING_COOLDOWN_MINUTES")) continue;
                    AbstractInsnNode next = nextCode(field);
                    if (!(next instanceof MethodInsnNode get) || !get.name.equals("get")
                            || !get.desc.equals("()Ljava/lang/Object;")) {
                        throw new IllegalStateException("Cooldown read shape changed: " + method.name);
                    }
                    // Use zero in both eligibility and its progress report, including old configs/saves.
                    InsnList zero = new InsnList();
                    zero.add(new InsnNode(Opcodes.ICONST_0));
                    zero.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer",
                            "valueOf", "(I)Ljava/lang/Integer;", false));
                    method.instructions.insertBefore(field, zero);
                    method.instructions.remove(field);
                    method.instructions.remove(get);
                    cooldown++;
                }
            }
        }
        if (validation != 1 || blueprint != 1 || cooldown != 2)
            throw new IllegalStateException("Unexpected patch counts: " + validation + "/" + blueprint + "/" + cooldown);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] patchConfig(byte[] source) {
        ClassNode node = new ClassNode();
        new ClassReader(source).accept(node, 0);
        int defaults = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("<clinit>")) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof LdcInsnNode key) || !key.cst.equals("cooldownMinutes")) continue;
                AbstractInsnNode value = nextCode(key);
                if (!(value instanceof IntInsnNode number) || number.operand != 120)
                    throw new IllegalStateException("Cooldown default changed upstream");
                method.instructions.set(value, new InsnNode(Opcodes.ICONST_0));
                defaults++;
            }
        }
        if (defaults != 1) throw new IllegalStateException("Expected one cooldown default, got " + defaults);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[0]), target = Path.of(args[1]);
        if (source.toAbsolutePath().equals(target.toAbsolutePath()))
            throw new IllegalArgumentException("Use a separate output JAR");
        int classes = 0;
        try (ZipFile input = new ZipFile(source.toFile());
             ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(target))) {
            var entries = input.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                byte[] bytes = input.getInputStream(entry).readAllBytes();
                if (entry.getName().equals(RITUAL)) { bytes = patchRitual(bytes); classes++; }
                if (entry.getName().equals(CONFIG)) { bytes = patchConfig(bytes); classes++; }
                output.putNextEntry(new ZipEntry(entry));
                output.write(bytes);
                output.closeEntry();
            }
        }
        if (classes != 2) {
            Files.deleteIfExists(target);
            throw new IllegalStateException("Expected two changed classes, got " + classes);
        }
        System.out.println("Patched altar validation, one-block blueprint, both cooldown reads and default");
    }
}
