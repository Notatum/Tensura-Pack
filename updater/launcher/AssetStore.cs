using System;
using System.IO;
using System.Net;
using System.Linq;
using System.Collections.Generic;
using System.Security.Cryptography;
using System.Threading;
using System.Threading.Tasks;
using System.Web.Script.Serialization;

public static class TensuraAssetStore {
    public static string Hash(string path, string algorithm) {
        using (var hash = algorithm == "sha1" ? (HashAlgorithm)SHA1.Create() : SHA256.Create())
        using (var stream = File.OpenRead(path)) return BitConverter.ToString(hash.ComputeHash(stream)).Replace("-", "").ToLowerInvariant();
    }
    public static bool Matches(string path, string hash) {
        return File.Exists(path) && Hash(path, hash.Length == 40 ? "sha1" : "sha256").Equals(hash, StringComparison.OrdinalIgnoreCase);
    }
    public static void Ensure(string url, string target, string hash, string seed) {
        if (Matches(target, hash)) return;
        Directory.CreateDirectory(Path.GetDirectoryName(target));
        if (!String.IsNullOrEmpty(seed) && Matches(seed, hash)) {
            File.Copy(seed, target, true);
            if (Matches(target, hash)) return;
        }
        Exception last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            string temp = target + ".download-" + Guid.NewGuid().ToString("N");
            try {
                var request = (HttpWebRequest)WebRequest.Create(url);
                request.UserAgent = "TensuraLauncher/1.0"; request.Timeout = 45000; request.ReadWriteTimeout = 45000;
                using (var response = request.GetResponse())
                using (var input = response.GetResponseStream())
                using (var output = File.Create(temp)) input.CopyTo(output);
                if (!Matches(temp, hash)) throw new IOException("El archivo descargado no coincide con su hash: " + Path.GetFileName(target));
                File.Copy(temp, target, true); File.Delete(temp); return;
            } catch (Exception error) {
                last = error;
                if (File.Exists(temp)) File.Delete(temp);
                if (attempt < 2) Thread.Sleep(500 * (attempt + 1));
            }
        }
        throw new IOException("No se pudo preparar " + Path.GetFileName(target), last);
    }
    public static void Assets(string index, string root, string seed) {
        var serializer = new JavaScriptSerializer { MaxJsonLength = Int32.MaxValue };
        var data = serializer.Deserialize<Dictionary<string, object>>(File.ReadAllText(index));
        var objects = (Dictionary<string, object>)data["objects"];
        var hashes = objects.Values.Select(v => (string)((Dictionary<string, object>)v)["hash"]).Distinct().ToArray();
        int completed = 0;
        ServicePointManager.DefaultConnectionLimit = 16;
        ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12;
        Parallel.ForEach(hashes, new ParallelOptions { MaxDegreeOfParallelism = 10 }, hash => {
            if (!System.Text.RegularExpressions.Regex.IsMatch(hash, "^[a-f0-9]{40}$")) throw new IOException("Hash de recurso inválido");
            string relative = "objects/" + hash.Substring(0, 2) + "/" + hash;
            Ensure("https://resources.download.minecraft.net/" + hash.Substring(0, 2) + "/" + hash,
                Path.Combine(root, relative), hash, String.IsNullOrEmpty(seed) ? null : Path.Combine(seed, relative));
            int count = Interlocked.Increment(ref completed);
            if (count % 200 == 0 || count == hashes.Length) Console.WriteLine("TENSURA_STATUS|Preparando recursos: " + count + "/" + hashes.Length);
        });
    }
}
