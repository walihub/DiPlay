#!/usr/bin/env python3
"""Separate type-106 PIP launch target from the unmodified global default-navigation setting."""
from pathlib import Path
import re
CARD='smali/com/dudu/autoui/ui/activity/launcher/minimalism/g2/m2.smali'
CALLBACK='smali/com/dudu/autoui/ui/activity/launcher/minimalism/g2/m2$ArcfoxPipSelect.smali'
HELPER='Llocal/arcfox/pip/PipLaunchOverride;'
def method(text,signature,transform):
 pattern=r'(\.method '+re.escape(signature)+r'\n)(.*?)(\.end method)'
 def change(match):return match[1]+transform(match[2])+match[3]
 result,count=re.subn(pattern,change,text,flags=re.S)
 assert count==1,(signature,count)
 return result

def _patch_launch(base):
 base=Path(base);path=base/CARD;original=path.read_text();text=original
 assert HELPER not in text,'PIP override already patched'
 def selector(body):
  head,tail=body.split('    :cond_0',1)
  old='Lcom/dudu/autoui/ui/activity/launcher/minimalism/g2/m2$f;'
  assert head.count(old)==2
  head=head.replace(old,'Lcom/dudu/autoui/ui/activity/launcher/minimalism/g2/m2$g;')
  assert head.count('0x7f0f0d13')==1
  head=head.replace('0x7f0f0d13','0x7f0f0d2b')
  anchor='    sget-object v1, Lcom/dudu/autoui/ui/activity/launcher/minimalism/g2/d1;->a:Lcom/dudu/autoui/ui/activity/launcher/minimalism/g2/d1;'
  assert head.count(anchor)==1
  head=head.replace(anchor,'''    new-instance v1, Lcom/dudu/autoui/ui/activity/launcher/minimalism/g2/m2$ArcfoxPipSelect;
    invoke-direct {v1, p0}, Lcom/dudu/autoui/ui/activity/launcher/minimalism/g2/m2$ArcfoxPipSelect;-><init>(Landroid/view/View;)V''')
  return head+'    :cond_0'+tail
 text=method(text,'private y()V',selector)
 def title(body):
  assert body.count('0x7f0f0d13')==1
  return body.replace('0x7f0f0d13','0x7f0f0cef')
 text=method(text,'protected getMenuItems()Ljava/util/List;',title)
 signatures={
  'private y()V':2,
  'public synthetic a(Lcom/dudu/autoui/manage/h/x;)Z':1,
  'protected getMenuItems()Ljava/util/List;':1,
  'protected k()V':1,
  'public onEvent(Lcom/dudu/autoui/manage/s/k/j;)V':1,
  'public synthetic t()V':1,
 }
 for signature,expected in signatures.items():
  def resolve(body):
   pattern=r'(    invoke-virtual \{[^\n]+\}, Lcom/dudu/autoui/manage/s/f;->c\(\)Ljava/lang/String;\n\s*    move-result-object (v\d+)\n)'
   def insert(match):
    register=match[2]
    return match[1]+f'\n    invoke-static {{p0, {register}}}, {HELPER}->resolve(Landroid/view/View;Ljava/lang/String;)Ljava/lang/String;\n    move-result-object {register}\n'
   result,count=re.subn(pattern,insert,body)
   assert count==expected,(signature,count,expected)
   return result
  text=method(text,signature,resolve)
 assert text.count(HELPER+'->resolve')==7
 path.write_text(text)
 callback=base/CALLBACK
 callback.write_text(''' .class public final Lcom/dudu/autoui/ui/activity/launcher/minimalism/g2/m2$ArcfoxPipSelect;
.super Ljava/lang/Object;
.implements Lcom/dudu/autoui/ui/base/newUi2/y/p$c;
.field private final owner:Landroid/view/View;
.method public constructor <init>(Landroid/view/View;)V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    iput-object p1, p0, Lcom/dudu/autoui/ui/activity/launcher/minimalism/g2/m2$ArcfoxPipSelect;->owner:Landroid/view/View;
    return-void
.end method
.method public a(Lcom/dudu/autoui/manage/h/x;)Z
    .locals 1
    iget-object v0, p0, Lcom/dudu/autoui/ui/activity/launcher/minimalism/g2/m2$ArcfoxPipSelect;->owner:Landroid/view/View;
    invoke-static {v0, p1}, Llocal/arcfox/pip/PipLaunchOverride;->select(Landroid/view/View;Ljava/lang/Object;)Z
    move-result v0
    return v0
.end method
'''.lstrip())

# These are PIP launch selectors, not the independent default-navigation chooser m2$f/l2.
PIP_SELECTORS=[
 'smali/com/dudu/autoui/ui/activity/launcher/minimalism/g2/m2$g.smali',
 'smali/com/dudu/autoui/ui/activity/launcher/dpip/DoublePipRootView$d.smali',
 'smali/com/dudu/autoui/ui/activity/launcher/dpip/ShuDoublePipRootView$d.smali',
 'smali/com/dudu/autoui/ui/activity/launcher/unbounded/UnboundedRootView$o.smali',
 'smali/com/dudu/autoui/ui/activity/launcher/unbounded/UnboundedMiniRootView$m.smali',
]
PIP_ARRAY_CONSUMERS=[
 'smali/com/dudu/autoui/ui/activity/launcher/view/BasePipView.smali',
 'smali/com/dudu/autoui/ui/activity/launcher/unbounded/pip/UnBasePipView.smali',
 'smali/com/dudu/autoui/ui/activity/nnset/content/home/v2/o.smali',
 'smali/com/dudu/autoui/ui/activity/nnset/content/more/p3.smali',
]
def patch(base):
 base=Path(base);_patch_launch(base)
 for relative in PIP_SELECTORS:
  path=base/relative;text=path.read_text()
  def empty(body):return '    .locals 1\n    const/4 v0, 0x0\n    new-array v0, v0, [Ljava/lang/String;\n    return-object v0\n'
  text=method(text,'protected o()[Ljava/lang/String;',empty)
  # p.q=false selects manager.e(): the same list after SDATA_HIDE_APPS filtering.
  anchor='    invoke-direct {p0, p2, p3}, Lcom/dudu/autoui/ui/base/newUi2/y/p;-><init>(Ljava/lang/String;Z)V'
  assert text.count(anchor)==1,relative
  text=text.replace(anchor,'    const/4 p3, 0x0\n'+anchor)
  path.write_text(text)
 for relative in PIP_ARRAY_CONSUMERS:
  path=base/relative;text=path.read_text()
  pattern=r'    invoke-static \{\}, Lcom/dudu/autoui/common/c0;->a\(\)\[Ljava/lang/String;\n\s*    move-result-object (v\d+|p\d+)'
  def empty(match):
   register=match[1]
   return f'    const/4 {register}, 0x0\n    new-array {register}, {register}, [Ljava/lang/String;'
  text,count=re.subn(pattern,empty,text)
  expected=2 if relative.endswith('/v2/o.smali') else 1
  assert count==expected,(relative,count)
  path.write_text(text)

WHITE_LIST={CARD,CALLBACK,*PIP_SELECTORS,*PIP_ARRAY_CONSUMERS}
