#!/usr/bin/env python3
"""Build vehicle-data additions on the exact captured Dudu fix51, preserving original dex/resources."""
from pathlib import Path
from zipfile import ZipFile, ZIP_STORED
import hashlib
import os
import subprocess
import struct
import tempfile
from dudu_manifest import patch_manifest

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
SDK = Path('/Users/h/Documents/arcfox/.android-sdk')
JAVA = Path('/Users/h/Documents/arcfox/.android-tools/jdk21/Contents/Home')
TOOLS = SDK / 'build-tools/30.0.3'
SOURCE = ROOT / 'diagnostics/vehicle-readonly-20261016/apks/installed-com.dudu.autoui.apk'
OUTPUT = ROOT / 'build/arcfox-release/Dudu_fix51_ArcfoxVehicleData_v2_TyreCard_Offset40_platform_signed.apk'
READER = ROOT / 'shared/src/main/java/com/shilapi/xcertplay/vehicle/arcfox/data/ArcfoxCarReader.java'
ANDROID = SDK / 'platforms/android-30/android.jar'

def run(*args):
    subprocess.run([str(x) for x in args], check=True, env={**os.environ, 'JAVA_HOME': str(JAVA)})

def dex_classes(data):
    def word(offset): return struct.unpack_from('<I', data, offset)[0]
    string_table, type_table = word(0x3c), word(0x44)
    result = []
    for i in range(word(0x60)):
        type_index = word(word(0x64) + i * 32)
        string_index = word(type_table + type_index * 4)
        offset = word(string_table + string_index * 4)
        while data[offset] & 128: offset += 1
        offset += 1
        result.append(data[offset:data.index(b'\0', offset)].decode('utf-8'))
    return result

def main():
    assert hashlib.sha256(SOURCE.read_bytes()).hexdigest() == '6fb558b257d57a7baeb897fadc534342b834d45832625c0c6ab9a45cf292e38b'
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='arcfox-vehicle-') as temp:
        tmp = Path(temp); classes = tmp / 'classes'; dex = tmp / 'dex'
        classes.mkdir(); dex.mkdir()
        run(JAVA / 'bin/javac', '-source', '8', '-target', '8', '-cp', ANDROID,
            '-d', classes, READER, *sorted((HERE / 'stubs').rglob('*.java')),
            *sorted((HERE / 'java/local/arcfox/vehicle').glob('*.java')))
        # Dudu ABI declarations resolve to original classes at runtime, never duplicate them in dex.
        compiled = [p for p in classes.rglob('*.class') if '/com/dudu/' not in str(p)]
        run(SDK / 'build-tools/36.0.0/d8', '--min-api', '25', '--lib', ANDROID,
            '--classpath', classes, '--output', dex, *compiled)
        added_classes = dex_classes((dex / 'classes.dex').read_bytes())
        assert all(not name.startswith('Lcom/dudu/') for name in added_classes), added_classes
        assert 'Llocal/arcfox/vehicle/TyreCardSource;' in added_classes
        unsigned = tmp / 'unsigned.apk'
        with ZipFile(SOURCE) as original, ZipFile(unsigned, 'w') as out:
            assert 'classes4.dex' not in original.namelist()
            for item in original.infolist():
                if item.filename.upper().startswith('META-INF/') and item.filename.upper().endswith(('.RSA', '.DSA', '.EC', '.SF', 'MANIFEST.MF')):
                    continue
                payload = original.read(item.filename)
                if item.filename == 'AndroidManifest.xml': payload = patch_manifest(payload)
                out.writestr(item, payload)
            out.write(dex / 'classes.dex', 'classes4.dex', compress_type=ZIP_STORED)
        aligned = tmp / 'aligned.apk'
        run(TOOLS / 'zipalign', '-f', '4', unsigned, aligned)
        run(TOOLS / 'apksigner', 'sign', '--v4-signing-enabled', 'false',
            '--key', '/Users/h/Documents/arcfox/sign/platform.pk8',
            '--cert', '/Users/h/Documents/arcfox/sign/platform.x509.pem', '--out', OUTPUT, aligned)
        run(TOOLS / 'apksigner', 'verify', '--verbose', '--print-certs', OUTPUT)
        with ZipFile(SOURCE) as original, ZipFile(OUTPUT) as out:
            for name in original.namelist():
                if name == 'AndroidManifest.xml' or name.upper().startswith('META-INF/'): continue
                assert original.read(name) == out.read(name), name
    print('All original dex/resources/assets preserved byte-for-byte')
    print(hashlib.sha256(OUTPUT.read_bytes()).hexdigest(), OUTPUT)
if __name__ == '__main__': main()
