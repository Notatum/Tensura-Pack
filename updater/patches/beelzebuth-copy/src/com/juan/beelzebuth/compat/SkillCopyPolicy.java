package com.juan.beelzebuth.compat;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import net.neoforged.fml.loading.FMLPaths;

/** Read once per server start; the managed updater distributes this policy. */
public final class SkillCopyPolicy {
    public static final boolean INCLUDE_LEARNING = load();
    private SkillCopyPolicy() {}
    private static boolean load() {
        var path = FMLPaths.CONFIGDIR.get().resolve("beelzebuth-copy.json");
        try {
            String json;
            // The server updater preserves local configs. Ship the owner's
            // published policy inside the verified mod so both sides agree.
            try (var bundled = SkillCopyPolicy.class.getResourceAsStream("/META-INF/beelzebuth-copy.json")) {
                if (bundled != null) json = new String(bundled.readAllBytes(), StandardCharsets.UTF_8);
                else if (Files.isRegularFile(path)) json = Files.readString(path);
                else return false;
            }
            var root = JsonParser.parseString(json).getAsJsonObject();
            if (root.get("schema").getAsInt() != 1) throw new IllegalArgumentException("schema");
            return root.get("includeLearningSkills").getAsBoolean();
        } catch (Exception error) {
            System.err.println("[Beelzebuth] No se pudo leer la política de copia; se copiarán las habilidades adquiridas. " + error);
            return false;
        }
    }
}
