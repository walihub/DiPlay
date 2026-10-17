#!/usr/bin/env python3
"""Build the installable, non-persistent Arcfox phone companion APK."""

from pathlib import Path
import os
import subprocess
import tempfile
from zipfile import ZIP_STORED, ZipFile

HERE = Path(__file__).resolve().parent
SDK = Path("/Users/h/Documents/arcfox/.android-sdk")
TOOLS = SDK / "build-tools/30.0.3"
D8 = SDK / "build-tools/36.0.0/d8"
JAVA = Path("/Users/h/Documents/arcfox/.android-tools/jdk21/Contents/Home")
KEY = Path("/Users/h/Documents/arcfox/sign/platform.pk8")
CERT = Path("/Users/h/Documents/arcfox/sign/platform.x509.pem")
ANDROID_JAR = SDK / "platforms/android-30/android.jar"
OUTPUT = HERE / "build/ArcfoxPhoneBridge_v1_platform_signed.apk"


def run(*args):
    env = os.environ.copy()
    env["JAVA_HOME"] = str(JAVA)
    subprocess.run([str(arg) for arg in args], check=True, env=env)


def main():
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="arcfox-phone-bridge-") as path:
        temp = Path(path)
        classes = temp / "classes"
        classes.mkdir()
        java_files = sorted((HERE / "java/local/arcfox/phone").glob("*.java"))
        run(JAVA / "bin/javac", "-source", "8", "-target", "8", "-cp",
            ANDROID_JAR, "-d", classes, *java_files)
        dex = temp / "dex"
        dex.mkdir()
        run(D8, "--min-api", "28", "--lib", ANDROID_JAR, "--output", dex,
            *classes.rglob("*.class"))
        unsigned = temp / "unsigned.apk"
        run(TOOLS / "aapt", "package", "-f", "-M", HERE / "bridge/AndroidManifest.xml",
            "-I", ANDROID_JAR, "-F", unsigned)
        with ZipFile(unsigned, "a") as apk:
            apk.write(dex / "classes.dex", "classes.dex", compress_type=ZIP_STORED)
        aligned = temp / "aligned.apk"
        run(TOOLS / "zipalign", "-f", "4", unsigned, aligned)
        run(TOOLS / "apksigner", "sign", "--v4-signing-enabled", "false",
            "--key", KEY, "--cert", CERT, "--out", OUTPUT, aligned)
        run(TOOLS / "apksigner", "verify", "--verbose", "--print-certs", OUTPUT)
    print(OUTPUT)


if __name__ == "__main__":
    main()
