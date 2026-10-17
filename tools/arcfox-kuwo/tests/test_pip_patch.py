#!/usr/bin/env python3
from pathlib import Path
import tempfile,shutil,sys,re
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import pip_patch as patch
source=Path(__file__).resolve().parents[3]/'build/kuwo-analysis/dudu'
def body(text,signature):return re.search(r'\.method '+re.escape(signature)+r'\n(.*?)\.end method',text,re.S)[1]
with tempfile.TemporaryDirectory() as directory:
 root=Path(directory)
 for relative in patch.WHITE_LIST-{patch.CALLBACK}:
  dest=root/relative;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source/relative,dest)
 original={p.relative_to(root).as_posix():p.read_bytes() for p in root.rglob('*.smali')}
 patch.patch(root)
 modified={p.relative_to(root).as_posix() for p in root.rglob('*.smali') if original.get(p.relative_to(root).as_posix())!=p.read_bytes()}
 assert modified==patch.WHITE_LIST,(modified,patch.WHITE_LIST)
 card=(root/patch.CARD).read_text();old=original[patch.CARD].decode()
 assert card.count(patch.HELPER+'->resolve')==7
 for signature in ['public synthetic u()V','static synthetic c(Lcom/dudu/autoui/manage/h/x;)Z']:
  assert body(card,signature)==body(old,signature),'default navigation must be byte-identical'
 selector=body(card,'private y()V').split('    :cond_0')[0]
 assert 'm2$f;' not in selector and 'g2/d1;' not in selector
 assert 'm2$g;' in selector and 'm2$ArcfoxPipSelect;' in selector
 for relative in patch.PIP_SELECTORS:
  text=(root/relative).read_text()
  assert 'common/c0;' not in body(text,'protected o()[Ljava/lang/String;')
  assert 'const/4 p3, 0x0' in text
 for relative in patch.PIP_ARRAY_CONSUMERS:assert 'common/c0;->a()' not in (root/relative).read_text()
 # The shared candidate source already respects the launcher's hide list.
 apps=(source/'smali/com/dudu/autoui/manage/h/y.smali').read_text()
 assert 'SDATA_HIDE_APPS' in body(apps,'public e()Ljava/util/List;')
 helper=(Path(__file__).resolve().parents[1]/'pip-java/local/arcfox/pip/PipLaunchOverride.java').read_text()
 assert 'SDATA_NORMAL_NAVI_CLASS' not in helper and 'PipAppRouter' not in helper
 assert '==106' in helper and 'setAppClazz' in helper
print('PASS PIP whitelist (11 files), seven consistent reads, generic chooser, hidden-list-only candidates; global navigation/default chooser and other slot mappings unchanged')
