#!/usr/bin/env python3
from pathlib import Path
import subprocess
import tempfile
from build_dudu import JAVA, ANDROID, HERE, READER

with tempfile.TemporaryDirectory(prefix='arcfox-tyre-tests-') as folder:
    sources = [READER, *sorted((HERE/'stubs').rglob('*.java')),
               *sorted((HERE/'java/local/arcfox/vehicle').glob('*.java')),
               *sorted((HERE/'tests').rglob('*.java'))]
    subprocess.run([str(JAVA/'bin/javac'), '-source','8','-target','8','-cp',str(ANDROID),
                    '-d',folder,*map(str,sources)], check=True)
    for test in ['VehicleValuesTest','TyreCardBridgeTest']:
        subprocess.run([str(JAVA/'bin/java'),'-cp',folder+':'+str(ANDROID),test],check=True)
