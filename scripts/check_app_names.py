"""Verify UTF-8 resources and the actual compiled launcher labels, not only in-app headings."""
import argparse
import re
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
EXPECTED={
 'debug': ('app/src/main/res/values/strings.xml', '六级背词 · 莫璃'),
 'parallel': ('app/src/parallel/res/values/strings.xml', '六级背词 · 莫璃新版'),
}

def check_sources():
    # This Gradle file previously contained GBK bytes which corrupted generated resource strings.
    (ROOT/'app/build.gradle.kts').read_text(encoding='utf-8')
    for _, (path,expected) in EXPECTED.items():
        xml=(ROOT/path).read_text(encoding='utf-8')
        name=ET.fromstring(xml).find("string[@name='app_name']")
        assert name is not None and name.text==expected, f'Incorrect application name in {path}'
    print('PASS: UTF-8 application name resources')

parser=argparse.ArgumentParser()
parser.add_argument('--aapt')
args=parser.parse_args()
check_sources()
if args.aapt:
    for variant,(_,expected) in EXPECTED.items():
        apk=ROOT/f'app/build/outputs/apk/{variant}/app-{variant}.apk'
        output=subprocess.check_output([args.aapt,'dump','badging',str(apk)]).decode('utf-8')
        label=re.search(r"^application-label:'(.*)'$",output,re.MULTILINE)
        launcher=re.search(r"^launchable-activity:.*label='([^']*)'",output,re.MULTILINE)
        assert label and label.group(1)==expected, f'Wrong compiled APK label for {variant}: {label.group(1) if label else "missing"}'
        assert launcher and launcher.group(1)==expected, f'Wrong launcher label for {variant}'
        print(f'PASS: {variant} APK and launcher label: {expected}')
