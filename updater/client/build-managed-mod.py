"""Build the managed client with Java 21 and deterministic JAR entries.

No dependencies are downloaded and no manifests or installed games are modified.
Pass the Minecraft/NeoForge library trees and the supported Iris JAR explicitly.
Example (PowerShell):
  python updater/client/build-managed-mod.py --javac 'C:/jdk-21/bin/javac.exe' `
    --library-root 'C:/server/libraries' --library-root 'C:/game/libraries' `
    --iris-jar 'C:/game/mods/iris-neoforge-1.8.14-beta.1+mc1.21.1.jar' `
    --nightmare-jar 'C:/game/mods/trnightmare-1.0.6.1-neoforge-1.21.1.jar' `
    --output 'C:/review/candidate/Tensura-Managed-Client-1.0.1.jar'

Reproducibility is for the same Java compiler and dependency bytes. ZIP times,
permissions, resource newlines, and entry ordering are fixed.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import tomllib
import zipfile


SOURCE = Path(__file__).resolve().parent / "mod-src"
FIXED_TIME = (2026, 1, 1, 0, 0, 0)


def compiler_version(javac: str) -> str:
    result = subprocess.run([javac, "-version"], check=True, capture_output=True, text=True)
    version = (result.stdout + result.stderr).strip()
    if not re.fullmatch(r"javac 21(?:[.\s+\-].*)?", version):
        raise ValueError(f"Java 21 compiler required, received: {version}")
    return version


def quoted_arg(value: str) -> str:
    # javac argfiles use their own quoting; this is not shell interpolation.
    return '"' + value.replace("\\", "/").replace('"', '\\"') + '"'


def add_entry(archive: zipfile.ZipFile, name: str, data: bytes) -> None:
    entry = zipfile.ZipInfo(name, FIXED_TIME)
    entry.create_system = 3
    entry.external_attr = 0o100644 << 16
    entry.compress_type = zipfile.ZIP_DEFLATED
    archive.writestr(entry, data, compresslevel=9)


def build(args: argparse.Namespace) -> dict:
    javac = str(Path(args.javac).resolve()) if args.javac else shutil.which("javac")
    if not javac:
        raise FileNotFoundError("javac was not found; pass --javac from a Java 21 JDK")
    version = compiler_version(javac)
    libraries = set()
    for root in args.library_root:
        root = root.resolve(strict=True)
        if not root.is_dir():
            raise ValueError(f"Library root is not a directory: {root}")
        libraries.update(path.resolve() for path in root.rglob("*.jar") if path.is_file())
    iris = args.iris_jar.resolve(strict=True)
    with zipfile.ZipFile(iris) as archive:
        if "net/irisshaders/iris/config/IrisConfig.class" not in archive.namelist():
            raise ValueError("--iris-jar does not contain the required IrisConfig class")
    libraries.add(iris)
    nightmare = args.nightmare_jar.resolve(strict=True)
    with zipfile.ZipFile(nightmare) as archive:
        if "com/github/hvnbael/trnightmare/client/blackhole/compat/ShaderCompat.class" not in archive.namelist():
            raise ValueError("--nightmare-jar does not contain the supported ShaderCompat class")
        nightmare_version = tomllib.loads(archive.read("META-INF/neoforge.mods.toml").decode("utf-8"))["mods"][0]["version"]
    libraries.add(nightmare)
    libraries = sorted(libraries, key=lambda path: str(path).casefold())
    sources = sorted(SOURCE.rglob("*.java"))
    if not sources:
        raise ValueError("No managed client Java sources were found")
    resources = SOURCE / "resources"
    metadata = tomllib.loads((resources / "META-INF/neoforge.mods.toml").read_text("utf-8"))
    mod = metadata["mods"][0]
    if mod["modId"] != "tensura_managed_client":
        raise ValueError("Unexpected modId")
    nightmare_dependency = next(row for row in metadata["dependencies"]["tensura_managed_client"]
                                if row["modId"] == "trnightmare")
    if nightmare_dependency["type"] != "optional" or nightmare_dependency["versionRange"] != f"[{nightmare_version}]":
        raise ValueError("Nightmare compatibility range does not match the actual compile-time JAR metadata")
    mixins = json.loads((resources / "tensura-managed-client.mixins.json").read_text("utf-8"))
    for name in mixins["client"]:
        source = SOURCE / Path(*mixins["package"].split(".")) / (name + ".java")
        if not source.is_file():
            raise ValueError(f"Missing client mixin source: {name}")
    output = args.output.resolve()
    if output.suffix.lower() != ".jar":
        raise ValueError("--output must be a .jar file")
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="tensura-managed-build-") as temporary:
        temporary = Path(temporary).resolve()
        classes = temporary / "classes"
        classes.mkdir()
        values = ["--release", "21", "-proc:none", "-encoding", "UTF-8",
                  "-cp", os.pathsep.join(map(str, libraries)), "-d", str(classes)]
        values.extend(map(str, sources))
        argfile = temporary / "javac.args"
        argfile.write_text("\n".join(quoted_arg(value) for value in values) + "\n", "utf-8")
        subprocess.run([javac, "@" + str(argfile)], check=True)
        entries = {path.relative_to(classes).as_posix(): path.read_bytes()
                   for path in classes.rglob("*.class")}
        for path in resources.rglob("*"):
            if path.is_file():
                entries[path.relative_to(resources).as_posix()] = (
                    path.read_text("utf-8").replace("\r\n", "\n").encode("utf-8")
                )
        candidate = temporary / "managed-client.jar"
        with zipfile.ZipFile(candidate, "w") as archive:
            for name, data in sorted(entries.items()):
                add_entry(archive, name, data)
        # The completed candidate is copied only to the explicit caller's output.
        shutil.copyfile(candidate, output)
    result = {"output": str(output), "modId": mod["modId"], "version": mod["version"],
              "compiler": version, "sha256": hashlib.sha256(output.read_bytes()).hexdigest(),
              "size": output.stat().st_size, "classCount": len(sources),
              "clientMixins": mixins["client"], "libraryCount": len(libraries)}
    print(json.dumps(result, ensure_ascii=False))
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--javac", help="Path to a Java 21 javac; defaults to PATH")
    parser.add_argument("--library-root", type=Path, action="append", required=True,
                        help="Read-only dependency tree; repeat for client/server libraries")
    parser.add_argument("--iris-jar", type=Path, required=True)
    parser.add_argument("--nightmare-jar", type=Path, required=True,
                        help="Nightmare compile-time API; optional at runtime")
    parser.add_argument("--output", type=Path, required=True)
    build(parser.parse_args())


if __name__ == "__main__":
    main()
