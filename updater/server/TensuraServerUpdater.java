import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public final class TensuraServerUpdater {
    private static final String DEFAULT_PACK =
        "https://raw.githubusercontent.com/Notatum/Tensura-Pack/main/pack.toml";
    private static final String DEFAULT_BOOTSTRAP =
        "https://github.com/packwiz/packwiz-installer-bootstrap/releases/download/v0.0.3/packwiz-installer-bootstrap.jar";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private record Options(Path server, URI pack, URI bootstrap, boolean local, List<String> command) {}

    public static void main(String[] args) {
        try {
            Options options = parse(args);
            update(options);
            if (!options.command().isEmpty()) {
                System.out.println("[Tensura] Iniciando servidor: " + String.join(" ", options.command()));
                int exit = new ProcessBuilder(options.command()).directory(options.server().toFile())
                    .inheritIO().start().waitFor();
                if (exit != 0) System.exit(exit);
            }
        } catch (Exception ex) {
            System.err.println("[Tensura] ERROR: " + ex.getMessage());
            if (ex.getCause() != null) System.err.println("[Tensura] Causa: " + ex.getCause());
            System.exit(1);
        }
    }

    private static Options parse(String[] args) {
        Path server = Path.of(".").toAbsolutePath().normalize();
        String pack = System.getenv().getOrDefault("TENSURA_PACK_URL", DEFAULT_PACK);
        String bootstrap = System.getenv().getOrDefault("TENSURA_BOOTSTRAP_URL", DEFAULT_BOOTSTRAP);
        boolean local = false;
        List<String> command = List.of();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--server-dir" -> server = Path.of(requireValue(args, ++i)).toAbsolutePath().normalize();
                case "--pack-url" -> { pack = requireValue(args, ++i); local = false; }
                case "--remote" -> local = false;
                case "--local" -> local = true;
                case "--bootstrap-url" -> bootstrap = requireValue(args, ++i);
                case "--" -> { command = Arrays.asList(args).subList(i + 1, args.length); i = args.length; }
                case "--help" -> {
                    System.out.println("Uso: java -jar Actualizador-Tensura-Servidor.jar "
                        + "[--server-dir RUTA] [--local | --remote | --pack-url URL] [-- java ...] ");
                    System.exit(0);
                }
                default -> throw new IllegalArgumentException("Argumento desconocido: " + args[i]);
            }
        }
        URI packUri = URI.create(pack), bootstrapUri = URI.create(bootstrap);
        if (!List.of("https", "http").contains(packUri.getScheme())
                || !List.of("https", "http").contains(bootstrapUri.getScheme())) {
            throw new IllegalArgumentException("Las URL del pack y del instalador deben ser HTTP(S)");
        }
        return new Options(server, packUri, bootstrapUri, local, command);
    }

    private static String requireValue(String[] args, int position) {
        if (position >= args.length) throw new IllegalArgumentException("Falta valor para " + args[position - 1]);
        return args[position];
    }

    private static void update(Options options) throws Exception {
        Path server = options.server();
        if (!Files.isDirectory(server)) throw new IOException("No existe la carpeta del servidor: " + server);
        if (!Files.exists(server.resolve("server.properties")) && !Files.exists(server.resolve("mods"))
                && !Files.exists(server.resolve("eula.txt"))) {
            throw new IOException("La ruta no parece ser la raíz del servidor: " + server);
        }
        Path state = server.resolve(".tensura-updater");
        Files.createDirectories(state);
        Path lockPath = state.resolve("updater.lock");
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock updaterLock = channel.tryLock();
             SessionLock session = checkServerStopped(server)) {
            if (updaterLock == null) throw new IOException("Otro actualizador ya está ejecutándose");

            Path source;
            Path local = state.resolve("local-mods");
            if (options.local()) {
                if (!Files.isDirectory(local)) throw new IOException("No existe una copia local verificada; usá el modo remoto");
                verifyLocalMods(local, state.resolve("local-mods.sha256"));
                source = local;
                System.out.println("[Tensura] Usando la instalación local verificada, con español y evolución de insectos corregida.");
            } else {
                Path bootstrap = state.resolve("packwiz-installer-bootstrap.jar");
                ensureBootstrap(bootstrap, options.bootstrap());
                Path staging = state.resolve("staging");
                Files.createDirectories(staging);
                ensureBootstrap(staging.resolve("packwiz-installer.jar"), URI.create("https://github.com/packwiz/packwiz-installer/releases/download/v0.5.14/packwiz-installer.jar"));
                URI pinnedPack = pinGitHubPack(options.pack());
                Map<Path, String> published = publishedMods(pinnedPack);
                checkModCache(staging.resolve("mods"), published, true);
                System.out.println("[Tensura] Revisando Packwiz (modo servidor): " + pinnedPack);
                List<String> install = List.of(javaExecutable(), "-jar", bootstrap.toString(),
                    "--bootstrap-no-update", "-g", "-s", "server", pinnedPack.toString());
                int exit = new ProcessBuilder(install).directory(staging.toFile()).inheritIO().start().waitFor();
                if (exit != 0) throw new IOException("Packwiz falló (código " + exit + "); mods/ no se modificó");
                checkModCache(staging.resolve("mods"), published, false);
                source = staging.resolve("mods");
            }

            Path candidate = state.resolve("candidate-mods");
            if (Files.exists(candidate)) deleteTree(candidate);
            Files.createDirectories(candidate);
            int[] stats = copyServerMods(source, candidate);
            if (stats[0] == 0) throw new IOException("El pack no entregó ningún JAR de servidor; mods/ no se modificó");
            System.out.println("[Tensura] JARs de servidor: " + stats[0]
                + " | archivos de Sodium o vehículos excluidos: " + stats[1]);

            Path live = server.resolve("mods");
            if (Files.isSymbolicLink(live)) throw new IOException("mods/ es un enlace simbólico; se canceló el cambio");
            if (sameContents(live, candidate)) {
                deleteTree(candidate);
                System.out.println("[Tensura] Los mods ya están actualizados.");
                return;
            }
            swapWithBackup(live, candidate, state.resolve("backups"));
            System.out.println("[Tensura] Mods actualizados. Mundo y configuraciones conservados.");
        } catch (OverlappingFileLockException ex) {
            throw new IOException("Otro actualizador ya está ejecutándose", ex);
        }
    }

    private static String javaExecutable() {
        String executable = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
            ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    private static Map<Path, String> publishedMods(URI pack) throws Exception {
        URI url = pack.resolve("updater/manifests/mods-server.json");
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        HttpRequest request = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(30))
            .header("User-Agent", "Tensura-Server-Updater/1.2").GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("No se pudo comprobar el manifiesto de mods: HTTP " + response.statusCode());
        if (!java.util.regex.Pattern.compile("\"side\"\\s*:\\s*\"server\"").matcher(response.body()).find())
            throw new IOException("El manifiesto publicado no corresponde a servidor");
        var entry = java.util.regex.Pattern.compile("\"file\"\\s*:\\s*\"([^\"\\\\/]+\\.jar)\"\\s*,\\s*\"sha256\"\\s*:\\s*\"([a-f0-9]{64})\"").matcher(response.body());
        Map<Path, String> expected = new HashMap<>();
        while (entry.find()) {
            Path relative = Path.of(entry.group(1));
            if (relative.isAbsolute() || relative.getNameCount() != 1 || expected.put(relative, entry.group(2)) != null)
                throw new IOException("Nombre de mod inválido o repetido en el manifiesto");
        }
        if (expected.isEmpty()) throw new IOException("El manifiesto publicado no contiene mods válidos");
        return expected;
    }

    private static void checkModCache(Path directory, Map<Path, String> expected, boolean repair) throws Exception {
        for (var entry : expected.entrySet()) {
            Path file = directory.resolve(entry.getKey()).normalize();
            if (!file.startsWith(directory) || Files.isSymbolicLink(file)) throw new IOException("Ruta inválida en la caché: " + file);
            if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                if (!java.util.HexFormat.of().formatHex(digest(file)).equals(entry.getValue())) {
                    if (!repair) throw new IOException("El mod descargado no coincide con el manifiesto: " + entry.getKey());
                    Files.delete(file);
                    System.out.println("[Tensura] Se descargará otra vez el mod de caché alterado: " + entry.getKey());
                }
            } else if (!repair) throw new IOException("Falta el mod publicado: " + entry.getKey());
        }
        if (!repair && !filesByRelativePath(directory).keySet().equals(expected.keySet()))
            throw new IOException("La caché contiene archivos de mods ajenos al manifiesto publicado");
    }

    private static URI pinGitHubPack(URI pack) throws Exception {
        if (!pack.toString().equals(DEFAULT_PACK)) return pack;
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://github.com/Notatum/Tensura-Pack.git/info/refs?service=git-upload-pack"))
            .timeout(Duration.ofSeconds(30)).header("User-Agent", "Tensura-Server-Updater/1.1")
            .header("Cache-Control", "no-cache").GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("No se pudo comprobar la versión de GitHub: HTTP " + response.statusCode());
        var match = java.util.regex.Pattern.compile("([0-9a-f]{40}) refs/heads/main(?:\\x00|\\n)").matcher(response.body());
        if (!match.find()) throw new IOException("GitHub no devolvió un identificador de versión válido");
        return URI.create(DEFAULT_PACK.replace("/main/", "/" + match.group(1) + "/"));
    }

    private static void verifyLocalMods(Path directory, Path manifest) throws Exception {
        if (!Files.isRegularFile(manifest)) throw new IOException("Falta el manifiesto de la instalación local verificada");
        Map<Path, Path> actual = filesByRelativePath(directory);
        java.util.Set<Path> expected = new java.util.HashSet<>();
        for (String line : Files.readAllLines(manifest)) {
            if (line.isBlank()) continue;
            String[] entry = line.split("\t", 2);
            if (entry.length != 2 || !entry[0].matches("[0-9a-f]{64}")) throw new IOException("Manifiesto local inválido");
            Path relative = Path.of(entry[1]);
            Path file = directory.resolve(relative).normalize();
            if (relative.isAbsolute() || !file.startsWith(directory) || !expected.add(relative)
                    || !Files.isRegularFile(file) || Files.isSymbolicLink(file)
                    || !java.util.HexFormat.of().formatHex(digest(file)).equals(entry[0])) {
                throw new IOException("El mod local no coincide con el manifiesto: " + entry[1]);
            }
        }
        if (expected.isEmpty() || !actual.keySet().equals(expected)) throw new IOException("La instalación local contiene archivos ajenos al manifiesto");
    }

    private static boolean isVehicleFile(Path file) throws IOException {
        if (!file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) return false;
        try (JarFile jar = new JarFile(file.toFile())) {
            var entry = jar.getJarEntry("META-INF/neoforge.mods.toml");
            if (entry == null) entry = jar.getJarEntry("META-INF/mods.toml");
            if (entry == null) return false;
            try (var input = jar.getInputStream(entry)) {
                String metadata = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                return java.util.regex.Pattern.compile("modId\\s*=\\s*[\"'](?:mts|mtsofficialpack|nihon_racing|oamp|svmp|vehiclesofsurvival|ccynho|panzerkrieg|iv_tpp(?:_v4_tests)?|iv_tcp_v3(?:_civil|_parts|_service|_txs|_utility)?|blockyrage(?:civil|aftermarket|emergency|heavy|other|stock))[\"']")
                    .matcher(metadata).find();
            }
        }
    }

    private static void ensureBootstrap(Path target, URI url) throws Exception {
        if (Files.exists(target)) {
            validateJar(target);
            return;
        }
        System.out.println("[Tensura] Descargando el instalador oficial de Packwiz...");
        Path tmp = target.resolveSibling(target.getFileName() + ".download");
        try {
            HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20)).build();
            HttpRequest request = HttpRequest.newBuilder(url).timeout(Duration.ofMinutes(2))
                .header("User-Agent", "Tensura-Server-Updater/1.0").GET().build();
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(tmp));
            if (response.statusCode() != 200) throw new IOException("Descarga del instalador: HTTP " + response.statusCode());
            validateJar(tmp);
            move(tmp, target);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static void validateJar(Path file) throws IOException {
        try (JarFile jar = new JarFile(file.toFile())) {
            if (jar.getManifest() == null || jar.getManifest().getMainAttributes().getValue("Main-Class") == null) {
                throw new IOException("No es un instalador JAR ejecutable: " + file);
            }
        }
    }

    private static int[] copyServerMods(Path source, Path destination) throws IOException {
        if (!Files.isDirectory(source)) throw new IOException("Packwiz no generó staging/mods");
        int[] stats = {0, 0};
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path file : paths.sorted().toList()) {
                Path relative = source.relativize(file);
                if (relative.toString().isEmpty()) continue;
                if (Files.isSymbolicLink(file)) throw new IOException("Enlace simbólico en el pack: " + relative);
                if (relative.toString().toLowerCase(Locale.ROOT).contains("sodium")
                        || (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && isVehicleFile(file))) {
                    if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) stats[1]++;
                    continue;
                }
                Path target = destination.resolve(relative).normalize();
                if (!target.startsWith(destination)) throw new IOException("Ruta fuera de mods: " + relative);
                if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(target);
                else if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(target.getParent());
                    Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                    if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) stats[0]++;
                } else throw new IOException("Tipo de archivo desconocido en el pack: " + relative);
            }
        }
        return stats;
    }

    private static boolean sameContents(Path a, Path b) throws Exception {
        if (!Files.isDirectory(a)) return false;
        Map<Path, Path> left = filesByRelativePath(a), right = filesByRelativePath(b);
        if (!left.keySet().equals(right.keySet())) return false;
        for (Path key : left.keySet()) {
            Path first = left.get(key), second = right.get(key);
            if (Files.size(first) != Files.size(second)) return false;
            if (!Arrays.equals(digest(first), digest(second))) return false;
        }
        return true;
    }

    private static Map<Path, Path> filesByRelativePath(Path directory) throws IOException {
        Map<Path, Path> result = new HashMap<>();
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path file : paths.toList()) {
                if (Files.isSymbolicLink(file)) throw new IOException("Enlace simbólico dentro de mods: " + file);
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) result.put(directory.relativize(file), file);
            }
        }
        return result;
    }

    private static byte[] digest(Path file) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = input.read(buffer)) != -1) sha.update(buffer, 0, n);
        }
        return sha.digest();
    }

    private static void swapWithBackup(Path live, Path candidate, Path backups) throws IOException {
        Files.createDirectories(backups);
        Path backup = backups.resolve("mods-" + STAMP.format(LocalDateTime.now())
            + "-" + UUID.randomUUID().toString().substring(0, 8));
        boolean hadLive = Files.exists(live);
        if (hadLive) move(live, backup);
        try {
            move(candidate, live);
        } catch (IOException ex) {
            if (hadLive && !Files.exists(live)) {
                try { move(backup, live); }
                catch (IOException restore) { ex.addSuppressed(restore); }
            }
            throw new IOException("No se pudo instalar mods; se intentó restaurar la copia anterior", ex);
        }
        if (hadLive) System.out.println("[Tensura] Copia anterior: " + backup);
        try { pruneBackups(backups); }
        catch (IOException ex) { System.err.println("[Tensura] No se pudieron limpiar copias antiguas: " + ex); }
    }

    private static void move(Path from, Path to) throws IOException {
        // Windows scanners can briefly hold newly copied JARs open.
        // Retry the same rename; never replace an existing destination.
        IOException failure = null;
        for (int attempt = 0; attempt < 10; attempt++) {
            if (Files.exists(to, LinkOption.NOFOLLOW_LINKS)) throw new IOException("La ruta de destino ya existe: " + to);
            try {
                try { Files.move(from, to, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException ex) { Files.move(from, to); }
                return;
            } catch (java.nio.file.FileSystemException ex) {
                failure = ex;
                try { Thread.sleep(100L + attempt * 50L); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Actualización interrumpida al mover " + from, interrupted);
                }
            }
        }
        throw failure;
    }

    private static void pruneBackups(Path backups) throws IOException {
        try (Stream<Path> dirs = Files.list(backups)) {
            List<Path> old = dirs.filter(p -> p.getFileName().toString().startsWith("mods-"))
                .sorted(Comparator.reverseOrder()).skip(2).toList();
            for (Path path : old) deleteTree(path);
        }
    }

    private static void deleteTree(Path directory) throws IOException {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return;
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path file : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
        }
    }

    private record SessionLock(FileChannel channel, FileLock lock) implements AutoCloseable {
        @Override public void close() throws IOException {
            if (lock != null) lock.close();
            if (channel != null) channel.close();
        }
    }

    private static SessionLock checkServerStopped(Path server) throws IOException {
        Properties properties = new Properties();
        Path config = server.resolve("server.properties");
        if (Files.exists(config)) {
            try (var input = Files.newInputStream(config)) { properties.load(input); }
        }
        String levelName = properties.getProperty("level-name", "world");
        Path world = server.resolve(levelName).normalize();
        if (!world.startsWith(server)) throw new IOException("level-name fuera de la carpeta del servidor");
        Path session = world.resolve("session.lock");
        if (!Files.exists(session)) return new SessionLock(null, null);
        FileChannel channel = FileChannel.open(session, StandardOpenOption.READ, StandardOpenOption.WRITE);
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) throw new IOException("El mundo está abierto: detené el servidor antes de actualizar");
            return new SessionLock(channel, lock);
        } catch (IOException | OverlappingFileLockException ex) {
            channel.close();
            throw new IOException("El mundo parece estar en uso: detené el servidor antes de actualizar", ex);
        }
    }
}
