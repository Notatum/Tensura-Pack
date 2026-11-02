using System;
using System.IO;
using System.Net;
using System.Text;
using System.Linq;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Windows.Forms;
using System.Diagnostics;
using System.Collections.Generic;
using System.Security.Cryptography;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using System.Web.Script.Serialization;
using System.Reflection;

[assembly: AssemblyTitle("Tensura Launcher")]
[assembly: AssemblyProduct("Tensura Launcher")]
[assembly: AssemblyVersion("1.0.0.0")]
[assembly: AssemblyFileVersion("1.0.0.0")]

namespace TensuraLauncher {
    static class Program {
        public static string DataRoot;
        public static string ManifestOverride, PackOverride;
        public static readonly JavaScriptSerializer Json = new JavaScriptSerializer { MaxJsonLength = Int32.MaxValue };
        [STAThread] static int Main(string[] args) {
            ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12;
            DataRoot = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "TensuraLauncher");
            string player = null, render = null, initialAction = null;
            bool headless = false, selftest = false, recipe = false;
            for (int i = 0; i < args.Length; i++) {
                string arg = args[i];
                if (arg == "--data-dir" && i + 1 < args.Length) DataRoot = Path.GetFullPath(args[++i]);
                else if (arg == "--player" && i + 1 < args.Length) player = args[++i];
                else if (arg == "--manifest-url" && i + 1 < args.Length) ManifestOverride = args[++i];
                else if (arg == "--pack-url" && i + 1 < args.Length) PackOverride = args[++i];
                else if (arg == "--headless-update") headless = true;
                else if (arg == "--recipe") { headless = true; recipe = true; }
                else if (arg == "--self-test") selftest = true;
                else if (arg == "--update") initialAction = "Update";
                else if (arg == "--repair") initialAction = "Repair";
                else if (arg == "--render-ui" && i + 1 < args.Length) render = args[++i];
            }
            DataRoot = Path.GetFullPath(DataRoot).TrimEnd(Path.DirectorySeparatorChar);
            if (DataRoot.Equals(Path.GetPathRoot(DataRoot).TrimEnd(Path.DirectorySeparatorChar), StringComparison.OrdinalIgnoreCase)) return 2;
            Directory.CreateDirectory(DataRoot); Directory.CreateDirectory(Path.Combine(DataRoot, "logs"));
            try {
                if (selftest) { Host.SelfTest(); return 0; }
                if (headless) {
                    var settings = Settings.Load(); if (player != null) settings.PlayerName = player;
                    var host = new Host(Console.WriteLine, delegate { });
                    host.Prepare(false); return host.Run(settings, recipe ? "Recipe" : "Update");
                }
                Application.EnableVisualStyles(); Application.SetCompatibleTextRenderingDefault(false);
                bool created;
                string mutexName = "Local\\TensuraLauncher-" + Host.HashBytes(Encoding.UTF8.GetBytes(DataRoot)).Substring(0, 24);
                using (var mutex = new Mutex(true, mutexName, out created)) {
                    if (!created) { MessageBox.Show("Tensura Launcher ya está abierto.", "Tensura"); return 0; }
                    var form = new LauncherForm(render != null, initialAction);
                    if (player != null) form.SetPlayer(player);
                    if (render != null) {
                        form.CreateControl(); form.Show(); Application.DoEvents();
                        using (var bitmap = new Bitmap(form.Width, form.Height)) { form.DrawToBitmap(bitmap, new Rectangle(Point.Empty, bitmap.Size)); bitmap.Save(render); }
                        form.Dispose(); return 0;
                    }
                    Application.Run(form); return 0;
                }
            } catch (Exception error) {
                File.AppendAllText(Path.Combine(DataRoot, "logs", "launcher.log"), DateTime.Now + " " + error + Environment.NewLine);
                if (headless) Console.Error.WriteLine(error);
                else MessageBox.Show("No se pudo completar la operación.\n\n" + error.Message, "Tensura Launcher", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return 1;
            }
        }
    }

    sealed class Settings {
        public string PlayerName { get; set; }
        public int MemoryMb { get; set; }
        public static Settings Load() {
            string file = Path.Combine(Program.DataRoot, "settings.json");
            if (File.Exists(file)) {
                try { var value = Program.Json.Deserialize<Settings>(File.ReadAllText(file)); if (value != null) { value.MemoryMb = Math.Max(2048, Math.Min(16384, value.MemoryMb)); return value; } } catch { }
            }
            string name = Regex.Replace(Environment.UserName, "[^A-Za-z0-9_]", "_");
            string profiles = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), ".tlauncher", "legacy", "Minecraft", "game", "tlauncher_profiles.json");
            try {
                var data = Program.Json.Deserialize<Dictionary<string, object>>(File.ReadAllText(profiles));
                var set = (Dictionary<string, object>)data["userSet"]; var selected = (Dictionary<string, object>)set["selected"];
                name = (string)selected["username"];
            } catch { }
            if (name.Length > 16) name = name.Substring(0, 16); if (name.Length < 3) name = "Jugador";
            return new Settings { PlayerName = name, MemoryMb = 6144 };
        }
        public void Save() {
            if (!Regex.IsMatch(PlayerName ?? "", "^[A-Za-z0-9_]{3,16}$")) throw new InvalidOperationException("El nombre debe tener entre 3 y 16 letras, números o guiones bajos.");
            string target = Path.Combine(Program.DataRoot, "settings.json"); string temp = target + ".new";
            File.WriteAllText(temp, Program.Json.Serialize(this), new UTF8Encoding(false));
            File.Copy(temp, target, true); File.Delete(temp);
        }
    }

    sealed class Host {
        readonly Action<string> status;
        readonly Action started;
        public string Commit, PackUrl, Engine;
        Dictionary<string, object> release;
        public Host(Action<string> status, Action started) { this.status = status; this.started = started; }
        public static string HashBytes(byte[] data) { using (var sha = SHA256.Create()) return BitConverter.ToString(sha.ComputeHash(data)).Replace("-", "").ToLowerInvariant(); }
        static string HashFile(string path) { using (var sha = SHA256.Create()) using (var stream = File.OpenRead(path)) return BitConverter.ToString(sha.ComputeHash(stream)).Replace("-", "").ToLowerInvariant(); }
        static string DownloadText(string url) {
            var request = (HttpWebRequest)WebRequest.Create(url); request.UserAgent = "TensuraLauncher/1.0"; request.Timeout = 30000; request.ReadWriteTimeout = 30000;
            using (var response = request.GetResponse()) using (var input = response.GetResponseStream()) using (var reader = new StreamReader(input, Encoding.UTF8)) return reader.ReadToEnd();
        }
        static void DownloadFile(string url, string target, string expected) {
            if (!Regex.IsMatch(expected ?? "", "^[a-f0-9]{64}$")) throw new IOException("El manifiesto contiene un hash inválido.");
            if (File.Exists(target) && HashFile(target) == expected) return;
            Directory.CreateDirectory(Path.GetDirectoryName(target)); string temp = target + ".download";
            var request = (HttpWebRequest)WebRequest.Create(url); request.UserAgent = "TensuraLauncher/1.0"; request.Timeout = 60000; request.ReadWriteTimeout = 60000;
            using (var response = request.GetResponse()) using (var input = response.GetResponseStream()) using (var output = File.Create(temp)) input.CopyTo(output);
            if (HashFile(temp) != expected) { File.Delete(temp); throw new IOException("La descarga no coincide con su publicación: " + Path.GetFileName(target)); }
            File.Copy(temp, target, true); File.Delete(temp);
        }
        public bool Prepare(bool allowSelfUpdate) {
            status("Comprobando la publicación de Tensura");
            if (!String.IsNullOrEmpty(Program.PackOverride)) { PackUrl = Program.PackOverride; Commit = HashBytes(Encoding.UTF8.GetBytes(PackUrl)); }
            else {
                string refs = DownloadText("https://github.com/Notatum/Tensura-Pack.git/info/refs?service=git-upload-pack");
                var match = Regex.Match(refs, "([a-f0-9]{40}) refs/heads/main(?:\\x00|\\n)");
                if (!match.Success) throw new IOException("No se pudo comprobar la publicación del servidor.");
                Commit = match.Groups[1].Value; PackUrl = "https://raw.githubusercontent.com/Notatum/Tensura-Pack/" + Commit + "/pack.toml";
            }
            string manifestUrl = Program.ManifestOverride ?? new Uri(new Uri(PackUrl), "updater/launcher/release.json").ToString();
            release = Program.Json.Deserialize<Dictionary<string, object>>(DownloadText(manifestUrl));
            if (Convert.ToInt32(release["schema"]) != 1) throw new IOException("La publicación del launcher no es compatible.");
            Engine = Path.Combine(Program.DataRoot, "cache", "launcher-engine", Commit); Directory.CreateDirectory(Engine);
            foreach (Dictionary<string, object> file in (System.Collections.IEnumerable)release["files"]) {
                string name = (string)file["name"], path = (string)file["path"];
                if (!Regex.IsMatch(name, "^[A-Za-z0-9_.-]+$") || path.Contains("..") || !Regex.IsMatch(path, "^updater/(launcher|client)/[A-Za-z0-9_.-]+$")) throw new IOException("Ruta inválida en la publicación del launcher.");
                DownloadFile(new Uri(new Uri(PackUrl), path).ToString(), Path.Combine(Engine, name), (string)file["sha256"]);
            }
            File.WriteAllText(Path.Combine(Engine, "release.json"), Program.Json.Serialize(release), new UTF8Encoding(false));
            if (allowSelfUpdate) {
                var app = (Dictionary<string, object>)release["application"];
                string expected = (string)app["sha256"], current = Application.ExecutablePath;
                if (HashFile(current) != expected) {
                    status("Actualizando el launcher"); string replacement = Path.Combine(Engine, "TensuraLauncher.exe");
                    DownloadFile(new Uri(new Uri(PackUrl), (string)app["path"]).ToString(), replacement, expected);
                    string script = Path.Combine(Engine, "Apply-Launcher-Update.ps1");
                    var args = new[] { "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", script, "-Target", current, "-Source", replacement, "-Expected", expected, "-WaitPid", Process.GetCurrentProcess().Id.ToString(), "-DataRoot", Program.DataRoot };
                    Process.Start(new ProcessStartInfo("powershell.exe", String.Join(" ", args.Select(Quote))) { UseShellExecute = false, CreateNoWindow = true });
                    return false;
                }
            }
            status("Listo para preparar el cliente"); return true;
        }
        public int Run(Settings settings, string action) {
            settings.Save(); string cancel = Path.Combine(Program.DataRoot, "cache", "cancel.request"); if (File.Exists(cancel)) File.Delete(cancel);
            string logPath = Path.Combine(Program.DataRoot, "logs", "launcher.log");
            var args = new[] { "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", Path.Combine(Engine, "Launcher-Engine.ps1"), "-DataRoot", Program.DataRoot, "-PlayerName", settings.PlayerName, "-MemoryMb", settings.MemoryMb.ToString(), "-Action", action, "-PackUrl", PackUrl, "-ClientBootstrap", Path.Combine(Engine, "Actualizar-Tensura-Cliente.ps1"), "-LauncherManifest", Path.Combine(Engine, "release.json") };
            var info = new ProcessStartInfo("powershell.exe", String.Join(" ", args.Select(Quote))) { UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true, RedirectStandardError = true, StandardOutputEncoding = Encoding.UTF8, StandardErrorEncoding = Encoding.UTF8 };
            using (var log = new StreamWriter(logPath, true, new UTF8Encoding(false))) using (var process = new Process()) {
                process.StartInfo = info; object logLock = new object(); string lastError = null;
                Action<string> line = value => {
                    if (value == null) return;
                    lock (logLock) { log.WriteLine(DateTime.Now.ToString("s") + " " + value); log.Flush(); }
                    if (value.StartsWith("TENSURA_STATUS|")) status(value.Substring(15));
                    else if (value.StartsWith("TENSURA_ERROR|")) { lastError = value.Substring(14); status(lastError); }
                    else if (value.StartsWith("TENSURA_GAME_STARTED|")) { status("Minecraft está en ejecución"); started(); }
                    else if (value == "TENSURA_GAME_CLOSED") status("Minecraft se cerró correctamente");
                };
                process.OutputDataReceived += (s, e) => line(e.Data); process.ErrorDataReceived += (s, e) => line(e.Data);
                process.Start(); process.BeginOutputReadLine(); process.BeginErrorReadLine(); process.WaitForExit();
                if (process.ExitCode != 0) throw new IOException(lastError ?? "No se pudo completar la preparación. Abrí el registro para ver el detalle.");
                return process.ExitCode;
            }
        }
        public static string Quote(string value) {
            if (value == null) value = "";
            var result = new StringBuilder("\""); int slashes = 0;
            foreach (char c in value) {
                if (c == '\\') { slashes++; continue; }
                if (c == '"') { result.Append('\\', slashes * 2 + 1); result.Append('"'); slashes = 0; continue; }
                result.Append('\\', slashes); slashes = 0; result.Append(c);
            }
            result.Append('\\', slashes * 2); result.Append('"'); return result.ToString();
        }
        public static void SelfTest() {
            if (Quote("abc") != "\"abc\"" || Quote("C:\\A B\\") != "\"C:\\A B\\\\\"") throw new Exception("Argument quoting failed");
            var settings = new Settings { PlayerName = "Notatum", MemoryMb = 4096 }; settings.Save();
            var restored = Settings.Load(); if (restored.PlayerName != "Notatum" || restored.MemoryMb != 4096) throw new Exception("Settings roundtrip failed");
            Console.WriteLine("PASS: Windows quoting and player settings");
        }
    }

    sealed class LauncherForm : Form {
        readonly TextBox player = new TextBox(); readonly NumericUpDown memory = new NumericUpDown();
        readonly Label message = new Label(); readonly Button play = new Button(); readonly Button update = new Button(); readonly Button repair = new Button();
        readonly ProgressBar progress = new ProgressBar(); readonly Settings settings;
        Host host; bool busy, gameRunning, closeRequested, renderOnly;
        readonly Color background = Color.FromArgb(15, 18, 31), muted = Color.FromArgb(159, 173, 197), accent = Color.FromArgb(118, 225, 220);
        public LauncherForm(bool renderOnly, string initialAction) {
            this.renderOnly = renderOnly; settings = Settings.Load(); Text = "Tensura Launcher"; ClientSize = new Size(960, 640); MinimumSize = new Size(976, 679); MaximumSize = new Size(976, 679); MaximizeBox = false;
            StartPosition = FormStartPosition.CenterScreen; BackColor = background; ForeColor = Color.White; Font = new Font("Segoe UI", 10); DoubleBuffered = true;
            AddLabel("TENSURA", 44, 37, 600, 58, 32, Color.White, true);
            AddLabel("LAUNCHER INDEPENDIENTE", 48, 103, 610, 25, 10, accent, true);
            AddLabel("Tu cliente del servidor, siempre actualizado.", 48, 142, 680, 30, 13, muted, false);
            var card = new Panel { Location = new Point(44, 203), Size = new Size(872, 240), BackColor = Color.FromArgb(26, 32, 49) }; Controls.Add(card);
            CardLabel(card, "Nombre de jugador", 24, 23, 300, 26, 11, muted);
            player.Text = settings.PlayerName; player.Location = new Point(24, 58); player.Size = new Size(440, 34); player.Font = new Font("Segoe UI", 15); player.BorderStyle = BorderStyle.FixedSingle; player.BackColor = Color.FromArgb(14, 20, 34); player.ForeColor = Color.White; player.MaxLength = 16; card.Controls.Add(player);
            CardLabel(card, "Perfil local para el servidor en modo offline", 24, 103, 510, 24, 10, muted);
            CardLabel(card, "Memoria para Minecraft", 554, 23, 280, 26, 11, muted);
            memory.Minimum = 2048; memory.Maximum = 16384; memory.Increment = 512; memory.Value = Math.Max(2048, settings.MemoryMb); memory.Location = new Point(554, 59); memory.Size = new Size(190, 34); memory.Font = new Font("Segoe UI", 14); memory.BackColor = Color.FromArgb(14, 20, 34); memory.ForeColor = Color.White; card.Controls.Add(memory);
            CardLabel(card, "MB", 756, 64, 50, 30, 12, muted);
            CardLabel(card, "Minecraft 1.21.1   /   NeoForge 21.1.250", 24, 159, 650, 26, 12, Color.White);
            CardLabel(card, "Unbound obligatorio  ·  Configuración administrada", 24, 194, 760, 24, 10, accent);
            Style(play, "JUGAR", 44, 469, 334, 54, accent, Color.FromArgb(14, 28, 37)); play.Font = new Font("Segoe UI", 13, FontStyle.Bold);
            Style(update, "Actualizar", 394, 469, 166, 54, Color.FromArgb(42, 52, 74), Color.White);
            Style(repair, "Reparar", 574, 469, 148, 54, Color.FromArgb(42, 52, 74), Color.White);
            var folder = new Button(); Style(folder, "Abrir carpeta", 736, 469, 180, 54, Color.FromArgb(42, 52, 74), Color.White);
            folder.Click += (s, e) => { string path = Path.Combine(Program.DataRoot, "game"); Directory.CreateDirectory(path); Process.Start("explorer.exe", Host.Quote(path)); };
            message.Location = new Point(44, 546); message.Size = new Size(740, 35); message.ForeColor = muted; message.Text = "Verificando la publicación..."; Controls.Add(message);
            var log = new LinkLabel { Text = "Ver registro", Location = new Point(814, 548), Size = new Size(104, 25), LinkColor = accent, ActiveLinkColor = Color.White }; log.LinkClicked += (s, e) => { string path = Path.Combine(Program.DataRoot, "logs", "launcher.log"); if (!File.Exists(path)) File.WriteAllText(path, "Tensura Launcher\r\n"); Process.Start("notepad.exe", Host.Quote(path)); }; Controls.Add(log);
            progress.Location = new Point(44, 592); progress.Size = new Size(872, 6); progress.Style = ProgressBarStyle.Marquee; progress.MarqueeAnimationSpeed = 35; Controls.Add(progress);
            play.Enabled = update.Enabled = repair.Enabled = false;
            play.Click += async (s, e) => await Run("Play"); update.Click += async (s, e) => await Run("Update"); repair.Click += async (s, e) => await Run("Repair");
            Shown += async (s, e) => { if (this.renderOnly) { message.Text = "Listo para jugar · instalación independiente"; progress.Style = ProgressBarStyle.Continuous; progress.Value = 100; play.Enabled = update.Enabled = repair.Enabled = true; return; } await Initialize(); if (!IsDisposed && host != null && initialAction != null) await Run(initialAction); };
            FormClosing += (s, e) => { if (busy && !gameRunning) { e.Cancel = true; closeRequested = true; Directory.CreateDirectory(Path.Combine(Program.DataRoot, "cache")); File.WriteAllText(Path.Combine(Program.DataRoot, "cache", "cancel.request"), "cancel"); SetMessage("Terminando de forma segura antes de cerrar..."); } };
        }
        public void SetPlayer(string name) { player.Text = name; }
        async Task Initialize() {
            busy = true;
            host = new Host(SetMessage, () => { gameRunning = true; SetButtons(false); });
            try { bool prepared = await Task.Run(() => host.Prepare(true)); if (!prepared) { busy = false; Close(); return; } play.Text = "JUGAR"; SetButtons(true); }
            catch (Exception error) { SetMessage(error.Message); play.Text = "REINTENTAR"; play.Enabled = true; host = null; }
            finally { busy = false; progress.Style = ProgressBarStyle.Continuous; if (closeRequested) Close(); }
        }
        async Task Run(string action) {
            if (host == null) { await Initialize(); return; }
            try { settings.PlayerName = player.Text.Trim(); settings.MemoryMb = (int)memory.Value; settings.Save(); }
            catch (Exception error) { SetMessage(error.Message); return; }
            busy = true; gameRunning = false; SetButtons(false); progress.Style = ProgressBarStyle.Marquee;
            try {
                bool prepared = await Task.Run(() => host.Prepare(true));
                if (!prepared) { busy = false; Close(); return; }
                if (closeRequested) return;
                await Task.Run(() => host.Run(settings, action)); SetMessage("Listo. Tu cliente está actualizado.");
            }
            catch (Exception error) { SetMessage(error.Message); }
            finally { busy = false; gameRunning = false; progress.Style = ProgressBarStyle.Continuous; progress.Value = 100; SetButtons(true); if (closeRequested) Close(); }
        }
        void SetButtons(bool enabled) { if (IsDisposed) return; if (InvokeRequired) { BeginInvoke((Action)(() => SetButtons(enabled))); return; } play.Enabled = update.Enabled = repair.Enabled = player.Enabled = memory.Enabled = enabled; }
        void SetMessage(string text) { if (IsDisposed) return; if (InvokeRequired) { BeginInvoke((Action)(() => SetMessage(text))); return; } message.Text = text; }
        void AddLabel(string text, int x, int y, int width, int height, float size, Color color, bool bold) { var label = new Label { Text = text, Location = new Point(x,y), Size = new Size(width,height), ForeColor = color, BackColor = Color.Transparent, Font = new Font("Segoe UI", size, bold ? FontStyle.Bold : FontStyle.Regular) }; Controls.Add(label); }
        void CardLabel(Panel panel, string text, int x, int y, int width, int height, float size, Color color) { panel.Controls.Add(new Label { Text = text, Location = new Point(x,y), Size = new Size(width,height), ForeColor = color, Font = new Font("Segoe UI", size), BackColor = Color.Transparent }); }
        void Style(Button button, string text, int x, int y, int width, int height, Color color, Color fore) { button.Text = text; button.Location = new Point(x,y); button.Size = new Size(width,height); button.BackColor = color; button.ForeColor = fore; button.FlatStyle = FlatStyle.Flat; button.FlatAppearance.BorderSize = 0; button.Cursor = Cursors.Hand; Controls.Add(button); }
        protected override void OnPaintBackground(PaintEventArgs e) {
            e.Graphics.Clear(background); e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            using (var brush = new LinearGradientBrush(new Rectangle(570, 0, 390, 205), Color.FromArgb(15, 18, 31), Color.FromArgb(38, 34, 70), 0f)) e.Graphics.FillRectangle(brush, 570, 0, 390, 205);
            using (var pen = new Pen(Color.FromArgb(48, 112, 139), 2f)) { e.Graphics.DrawEllipse(pen, 789, 27, 91, 91); e.Graphics.DrawEllipse(pen, 802, 40, 65, 65); }
            using (var pen = new Pen(accent, 3f)) { e.Graphics.DrawLine(pen, 820, 53, 850, 53); e.Graphics.DrawLine(pen, 835, 53, 835, 86); }
        }
    }
}
