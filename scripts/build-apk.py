#!/usr/bin/env python3
"""Dependency-free Android APK build; requires JDK17 and Android SDK35 tools."""
import os, pathlib, subprocess, zipfile, shutil
ROOT=pathlib.Path(__file__).resolve().parents[1]
os.chdir(ROOT)
def run(*args): subprocess.run([str(x) for x in args],check=True)
sdk=pathlib.Path(os.environ.get('ANDROID_SDK_ROOT',os.environ.get('ANDROID_HOME',ROOT/'.android-sdk')))
jars=list((sdk/'platforms').glob('**/android.jar'))
if not jars: raise SystemExit('Install Android SDK platform 35 and build-tools 35, then set ANDROID_SDK_ROOT.')
jar=next((p for p in jars if '35' in str(p)),jars[-1])
tools=next((p.parent for p in (sdk/'build-tools').glob('**/aapt2')),None)
if tools is None: raise SystemExit('Missing Android build-tools.')
for name in ['aapt2','d8','zipalign','apksigner']:(tools/name).chmod(0o755)
build=ROOT/'build';build.mkdir(exist_ok=True)
classes=build/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
assets=ROOT/'app/src/main/assets'
if not (assets/'index.html').exists():raise SystemExit('Missing UI assets/index.html')
run(tools/'aapt2','compile','--dir','app/src/main/res','-o',build/'resources.zip')
run(tools/'aapt2','link','-o',build/'unsigned.apk','-I',jar,'--manifest','app/src/main/AndroidManifest.xml','-A',assets,build/'resources.zip')
compiler=['javac'] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
run(*compiler,'--release','8','-classpath',jar,'-d',classes,*sorted((ROOT/'app/src/main/java').rglob('*.java')))
run(tools/'d8','--lib',jar,'--min-api','26','--output',build,*sorted(classes.rglob('*.class')))
with zipfile.ZipFile(build/'unsigned.apk','a',compression=zipfile.ZIP_DEFLATED) as z:z.write(build/'classes.dex','classes.dex')
run(tools/'zipalign','-f','-p','4',build/'unsigned.apk',build/'aligned.apk')
signing=ROOT/'.signing';signing.mkdir(mode=0o700,exist_ok=True)
key=signing/'debug.jks'
# Development signing only; no personal credentials. Keep this file out of git.
if not key.exists():
 run('keytool','-genkeypair','-keystore',key,'-storepass','android','-keypass','android','-alias','androiddebugkey','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Chat RPG Development,O=Personal,C=KR')
key.chmod(0o600)
out=build/'ChatRPG-v0.1.0.apk'
run(tools/'apksigner','sign','--ks',key,'--ks-key-alias','androiddebugkey','--ks-pass','pass:android','--key-pass','pass:android','--out',out,build/'aligned.apk')
run(tools/'apksigner','verify','--verbose','--print-certs',out)
run(tools/'zipalign','-c','-p','4',out)
print(out)
