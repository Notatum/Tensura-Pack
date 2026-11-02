package ar.juan.tensuramanaged;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.neoforged.fml.loading.FMLPaths;

public final class ManagedPolicy {
    private static JsonObject policy;

    private static synchronized JsonObject read() {
        if (policy != null) return policy;
        Path config=FMLPaths.CONFIGDIR.get().resolve("tensura-managed-client.json");
        try {
            JsonObject loaded=JsonParser.parseString(Files.readString(config)).getAsJsonObject();
            if (loaded.get("schema").getAsInt()!=1) throw new IOException("Versión de política desconocida");
            String name=loaded.get("shaderPack").getAsString();
            if (!name.matches("[^\\\\/]+\\.zip")) throw new IOException("Nombre de shader inválido");
            if (loaded.get("mandatoryShaders").getAsBoolean()) {
                Path shader=FMLPaths.GAMEDIR.get().resolve("shaderpacks").resolve(name);
                String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(shader)));
                if (!actual.equals(loaded.get("shaderSha256").getAsString())) throw new IOException("El shader obligatorio falta o está alterado: "+name);
            }
            policy=loaded;
            System.out.println("[TensuraManaged] Shader obligatorio verificado: "+name);
            return loaded;
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo aplicar la configuración de Tensura. Cerrá el juego y ejecutá el updater.",error);
        }
    }
    public static boolean mandatory() { return read().get("mandatoryShaders").getAsBoolean(); }
    public static String shader() { return read().get("shaderPack").getAsString(); }
}
