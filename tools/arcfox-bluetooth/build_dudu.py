#!/usr/bin/env python3
"""Add the phone lease host to the exact user-provided Dudu fix51 APK."""
from pathlib import Path
from zipfile import ZipFile, ZIP_STORED
import hashlib
import tempfile
from build_bridge import HERE, JAVA, ANDROID_JAR, D8, TOOLS, KEY, CERT, run
from dudu_manifest import patch_manifest

SOURCE = Path('/Users/h/Documents/arcfox/build/Dudu_PIP_fix51_高德关闭保活_其他应用退出后台_platform_signed.apk')
OUTPUT = HERE / 'build/Dudu_PIP_fix51_ArcfoxPhoneLease_v1_platform_signed.apk'


def main():
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='diplay-dudu-phone-') as folder:
        temp = Path(folder)
        classes, dex = temp / 'classes', temp / 'dex'
        classes.mkdir(); dex.mkdir()
        run(JAVA / 'bin/javac', '-source', '8', '-target', '8', '-cp', ANDROID_JAR,
            '-d', classes, *sorted((HERE / 'java/local/arcfox/phone').glob('*.java')))
        run(D8, '--min-api', '21', '--lib', ANDROID_JAR, '--output', dex, *classes.rglob('*.class'))
        unsigned = temp / 'unsigned.apk'
        with ZipFile(SOURCE) as original, ZipFile(unsigned, 'w') as out:
            assert 'classes4.dex' not in original.namelist()
            for item in original.infolist():
                if item.filename.upper().startswith('META-INF/') and item.filename.upper().endswith(('.RSA', '.DSA', '.EC', '.SF', 'MANIFEST.MF')):
                    continue
                payload = original.read(item.filename)
                if item.filename == 'AndroidManifest.xml':
                    payload = patch_manifest(payload)
                out.writestr(item, payload)
            out.write(dex / 'classes.dex', 'classes4.dex', compress_type=ZIP_STORED)
        aligned = temp / 'aligned.apk'
        run(TOOLS / 'zipalign', '-f', '4', unsigned, aligned)
        run(TOOLS / 'apksigner', 'sign', '--v4-signing-enabled', 'false', '--key', KEY,
            '--cert', CERT, '--out', OUTPUT, aligned)
        run(TOOLS / 'apksigner', 'verify', '--verbose', '--print-certs', OUTPUT)
        # Existing executable code, resources and assets must remain byte-identical.
        with ZipFile(SOURCE) as original, ZipFile(OUTPUT) as result:
            for name in original.namelist():
                if name == 'AndroidManifest.xml' or name.upper().startswith('META-INF/'):
                    continue
                assert original.read(name) == result.read(name), name
    print('Preserved all original dex/resources/assets')
    print(hashlib.sha256(OUTPUT.read_bytes()).hexdigest(), OUTPUT)


if __name__ == '__main__':
    main()
