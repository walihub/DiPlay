#!/usr/bin/env python3
"""Rebuild the captured Arcfox Bluetooth system APK with narrow vendor fixes."""

from pathlib import Path
import os
import shutil
import subprocess
import tempfile
from zipfile import ZipFile, ZIP_STORED


ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
SOURCE = ROOT / "arcfox-capture/2026-10-14-reboot-risk/Bluetooth.apk"
VENDOR_SOURCE = ROOT / "arcfox-capture/2026-10-14-reboot-risk/nFore_BT_UI_service.apk"
APKTOOL = Path("/Users/h/Documents/arcfox/.android-tools/bin/apktool")
BUILD_TOOLS = Path("/Users/h/Documents/arcfox/.android-sdk/build-tools/30.0.3")
D8 = Path("/Users/h/Documents/arcfox/.android-sdk/build-tools/36.0.0/d8")
KEY = Path("/Users/h/Documents/arcfox/sign/platform.pk8")
CERT = Path("/Users/h/Documents/arcfox/sign/platform.x509.pem")
OUTPUT = HERE / "build/Bluetooth_Arcfox_CoverOnly_v2_platform_signed.apk"
VENDOR_OUTPUT = HERE / "build/nFore_BT_UI_service_Arcfox_PhoneDual_v2_platform_signed.apk"


def replace_once(path: Path, old: str, new: str) -> None:
    source = path.read_text()
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected one patch anchor, found {count}")
    path.write_text(source.replace(old, new, 1))


def run(*args: object) -> None:
    env = os.environ.copy()
    env["JAVA_HOME"] = "/Users/h/Documents/arcfox/.android-tools/jdk21/Contents/Home"
    subprocess.run([str(arg) for arg in args], check=True, env=env)


def patch(decoded: Path) -> None:
    smali = decoded / "smali/com/android/bluetooth/nfservice"
    shutil.copy2(HERE / "smali/ArcfoxCoverGate.smali", smali / "ArcfoxCoverGate.smali")

    avrcp = smali / "NfAvrcpJniByPass.smali"
    replace_once(avrcp,
                 '    :goto_0\n    const/16 v1, 0x8\n\n    if-ne p3, v1, :cond_0',
                 '    :goto_0\n'
                 '    const/4 v1, 0x1\n'
                 '    if-ne p3, v1, :arcfox_not_title\n'
                 '    invoke-static {p6, p5}, Lcom/android/bluetooth/nfservice/ArcfoxCoverGate;->onTitle([B[B)V\n'
                 '    :arcfox_not_title\n'
                 '    const/16 v1, 0x8\n\n    if-ne p3, v1, :cond_0')
    replace_once(avrcp,
                 '    .line 171\n    invoke-static {}, Lcom/android/bluetooth/nfservice/bip/AvrcpControllerBipService;->getAvrcpControllerBipService()',
                 '    .line 171\n'
                 '    invoke-static {p6, v0}, Lcom/android/bluetooth/nfservice/ArcfoxCoverGate;->shouldFetch([BLjava/lang/String;)Z\n'
                 '    move-result v1\n'
                 '    if-eqz v1, :cond_0\n'
                 '    invoke-static {}, Lcom/android/bluetooth/nfservice/bip/AvrcpControllerBipService;->getAvrcpControllerBipService()')
    replace_once(avrcp,
                 '    .line 128\n    sget-object v0, Lcom/android/bluetooth/nfservice/NfAvrcpJniByPass;->mDoCallbackJni:',
                 '    .line 128\n'
                 '    invoke-static {}, Lcom/android/bluetooth/nfservice/ArcfoxCoverGate;->reset()V\n'
                 '    sget-object v0, Lcom/android/bluetooth/nfservice/NfAvrcpJniByPass;->mDoCallbackJni:')
    replace_once(avrcp,
                 '    .line 70\n    if-nez p1, :cond_0',
                 '    .line 70\n    if-nez p1, :arcfox_connected\n'
                 '    invoke-static {}, Lcom/android/bluetooth/nfservice/ArcfoxCoverGate;->reset()V\n'
                 '    :arcfox_connected\n    if-nez p1, :cond_0')

    listener = smali / "NfJniService$1.smali"
    replace_once(listener,
                 '    .line 182\n    iget-object v0, p0, Lcom/android/bluetooth/nfservice/NfJniService$1;->this$0:',
                 '    invoke-static {p1, p2, p3}, Lcom/android/bluetooth/nfservice/ArcfoxCoverGate;->shouldBroadcast(Landroid/bluetooth/BluetoothDevice;Ljava/lang/String;[B)Z\n'
                 '    move-result v0\n'
                 '    if-nez v0, :arcfox_new_cover\n'
                 '    return-void\n'
                 '    :arcfox_new_cover\n'
                 '    .line 182\n    iget-object v0, p0, Lcom/android/bluetooth/nfservice/NfJniService$1;->this$0:')
    replace_once(listener,
                 '    invoke-direct {v0, v1}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V\n\n'
                 '    .line 184',
                 '    invoke-direct {v0, v1}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V\n'
                 '    const-string v1, "com.adayo.app.adayobluetoothphone"\n'
                 '    invoke-virtual {v0, v1}, Landroid/content/Intent;->setPackage(Ljava/lang/String;)Landroid/content/Intent;\n\n'
                 '    .line 184')


def patch_vendor(decoded: Path) -> None:
    service = decoded / "smali/com/nforetek/bt/customer/service/BtService.smali"
    replace_once(service,
                 ".field private final mBinder:Lcom/nforetek/bt/aidl/UiCommand$Stub;",
                 ".field private final mBinder:Lcom/nforetek/bt/aidl/UiCommand$Stub;\n"
                 ".field private mArcfoxPhone:Llocal/arcfox/phone/PhoneLeaseBinder;")
    replace_once(service,
                 '    .line 699\n    sget-object v0, Lcom/nforetek/bt/customer/service/BtService;->TAG:Ljava/lang/String;',
                 '    if-eqz p1, :arcfox_normal_bind\n'
                 '    invoke-virtual {p1}, Landroid/content/Intent;->getAction()Ljava/lang/String;\n'
                 '    move-result-object v0\n'
                 '    const-string v1, "arcfox.phone.lease.v1"\n'
                 '    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z\n'
                 '    move-result v0\n'
                 '    if-eqz v0, :arcfox_normal_bind\n'
                 '    iget-object v0, p0, Lcom/nforetek/bt/customer/service/BtService;->mArcfoxPhone:Llocal/arcfox/phone/PhoneLeaseBinder;\n'
                 '    return-object v0\n'
                 '    :arcfox_normal_bind\n'
                 '    .line 699\n    sget-object v0, Lcom/nforetek/bt/customer/service/BtService;->TAG:Ljava/lang/String;')
    replace_once(service,
                 '    .line 260\n    invoke-static {v0, v1}, Lcom/nforetek/bt/util/Utils;->iLog(Ljava/lang/String;Ljava/lang/String;)V\n\n    return-void\n.end method\n\n.method public onDestroy()V',
                 '    .line 260\n    invoke-static {v0, v1}, Lcom/nforetek/bt/util/Utils;->iLog(Ljava/lang/String;Ljava/lang/String;)V\n\n'
                 '    new-instance v1, Llocal/arcfox/phone/PhoneLeaseBinder;\n'
                 '    invoke-direct {v1, p0}, Llocal/arcfox/phone/PhoneLeaseBinder;-><init>(Landroid/content/Context;)V\n'
                 '    iput-object v1, p0, Lcom/nforetek/bt/customer/service/BtService;->mArcfoxPhone:Llocal/arcfox/phone/PhoneLeaseBinder;\n'
                 '    invoke-virtual {v1}, Llocal/arcfox/phone/PhoneLeaseBinder;->start()V\n'
                 '    return-void\n.end method\n\n.method public onDestroy()V')
    replace_once(service,
                 '    .line 619\n    sget-object v0, Lcom/nforetek/bt/customer/service/BtService;->TAG:Ljava/lang/String;',
                 '    iget-object v0, p0, Lcom/nforetek/bt/customer/service/BtService;->mArcfoxPhone:Llocal/arcfox/phone/PhoneLeaseBinder;\n'
                 '    if-eqz v0, :arcfox_phone_stopped\n'
                 '    invoke-virtual {v0}, Llocal/arcfox/phone/PhoneLeaseBinder;->stop()V\n'
                 '    :arcfox_phone_stopped\n'
                 '    .line 619\n    sget-object v0, Lcom/nforetek/bt/customer/service/BtService;->TAG:Ljava/lang/String;')


def build_one(source: Path, output: Path, patcher) -> None:
    with tempfile.TemporaryDirectory(prefix="arcfox-bluetooth-") as temp:
        temp = Path(temp)
        decoded = temp / "decoded"
        unsigned = temp / "unsigned.apk"
        aligned = temp / "aligned.apk"
        run(APKTOOL, "d", "-r", "-f", source, "-o", decoded)
        patcher(decoded)
        run(APKTOOL, "b", decoded, "-o", unsigned)
        if source == VENDOR_SOURCE:
            classes = temp / "classes"
            classes.mkdir()
            android_jar = Path("/Users/h/Documents/arcfox/.android-sdk/platforms/android-30/android.jar")
            java_files = list((HERE / "java").rglob("*.java"))
            run(Path("/Users/h/Documents/arcfox/.android-tools/jdk21/Contents/Home/bin/javac"),
                "-source", "8", "-target", "8", "-cp", android_jar,
                "-d", classes, *java_files)
            dex_dir = temp / "dex"
            dex_dir.mkdir()
            run(D8, "--min-api", "30", "--lib", android_jar,
                "--output", dex_dir, *classes.rglob("*.class"))
            with ZipFile(unsigned, "a") as apk:
                apk.write(dex_dir / "classes.dex", "classes2.dex", compress_type=ZIP_STORED)
        run(BUILD_TOOLS / "zipalign", "-f", "4", unsigned, aligned)
        v1, v3 = ("false", "true") if source == SOURCE else ("true", "false")
        run(BUILD_TOOLS / "apksigner", "sign", "--v4-signing-enabled", "false",
            "--v1-signing-enabled", v1, "--v2-signing-enabled", "false",
            "--v3-signing-enabled", v3,
            "--key", KEY, "--cert", CERT,
            "--out", output, aligned)
        run(BUILD_TOOLS / "apksigner", "verify", "--verbose", output)
    print(output)

def main() -> None:
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    # OEM resources reference private framework colors. Keep compiled resources
    # and binary manifest intact; only rebuild the Dalvik code.
    build_one(SOURCE, OUTPUT, patch)
    build_one(VENDOR_SOURCE, VENDOR_OUTPUT, patch_vendor)


if __name__ == "__main__":
    main()
