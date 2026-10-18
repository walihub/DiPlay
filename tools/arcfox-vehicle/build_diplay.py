#!/usr/bin/env python3
"""Local deployment build. Preserve the previous installation's authentication assets exactly."""
from pathlib import Path
from zipfile import ZipFile
import argparse
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'build/arcfox-release/DiPlay-0.2.19-Arcfox-PhoneAudio-Hotspot-AutoPermissions-system.apk'
OUTPUT = ROOT / 'build/arcfox-release/DiPlay-0.2.19-Arcfox-VehicleData-system.apk'
JAVA = '/Users/h/Documents/极狐车机/.android-tools/jdk21/Contents/Home'
SDK = '/Users/h/Documents/极狐车机/.android-sdk'

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=OUTPUT)
    parser.add_argument("--native-mfi", action="store_true", help="Use OEM mfiserver; do not import or package offline credentials")
    options = parser.parse_args()
    output = options.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='arcfox-local-release-') as folder:
        tmp = Path(folder)
        assets = tmp / 'assets/offline-mfi'; assets.mkdir(parents=True)
        if not options.native_mfi:
            with ZipFile(BASE) as z:
                for name in ('identity.pk8', 'certificate.p7b'):
                    p = assets / name
                    p.write_bytes(z.read('assets/offline-mfi/' + name)); p.chmod(0o600)
        pem = tmp / 'platform.pem'; store = tmp / 'platform.p12'
        subprocess.run(['openssl', 'pkcs8', '-inform', 'DER', '-nocrypt', '-in',
            '/Users/h/Documents/极狐车机/系统签名/platform.pk8', '-out', str(pem)], check=True)
        pem.chmod(0o600)
        subprocess.run(['openssl', 'pkcs12', '-export', '-inkey', str(pem), '-in',
            '/Users/h/Documents/极狐车机/系统签名/platform.x509.pem', '-name', 'platform',
            '-out', str(store), '-passout', 'pass:local-build'], check=True)
        env = {**os.environ, 'JAVA_HOME': JAVA, 'ANDROID_HOME': SDK,
            'GRADLE_USER_HOME': os.environ.get('GRADLE_USER_HOME', str(ROOT.parent.parent / '.gradle')),
            'DIPLAY_AUTH_ASSETS_DIR': str(assets.parent), 'ANDROID_KEYSTORE_PATH': str(store),
            'ANDROID_KEYSTORE_PASSWORD': 'local-build', 'ANDROID_KEY_PASSWORD': 'local-build',
            'ANDROID_KEY_ALIAS': 'platform'}
        if options.native_mfi:
            env.pop('DIPLAY_AUTH_ASSETS_DIR', None)
        command = ['./gradlew']
        if options.native_mfi:
            command.append('-ParcfoxNativeMfi=true')
        command += [':mobile:verifyStandaloneAuthentication', ':mobile:assembleRelease', ':mobile:lintRelease']
        subprocess.run(command, cwd=ROOT, env=env, check=True)
        shutil.copy2(ROOT / 'mobile/build/outputs/apk/release/mobile-release.apk', output)
        with ZipFile(output) as after:
            if options.native_mfi:
                assert not any('offline-mfi/' in name for name in after.namelist())
            else:
                with ZipFile(BASE) as before:
                    for name in ('identity.pk8', 'certificate.p7b'):
                        path = 'assets/offline-mfi/' + name
                        assert before.read(path) == after.read(path)
        subprocess.run([JAVA + '/bin/java', '-jar', SDK + '/build-tools/36.0.0/lib/apksigner.jar',
            'verify', '--verbose', '--print-certs', str(output)], check=True)
    print('Native MFi: no offline credentials packaged' if options.native_mfi else 'Authentication assets preserved byte-for-byte')
    print('Local temporary signing keys removed')
    print(output)

if __name__ == '__main__': main()
