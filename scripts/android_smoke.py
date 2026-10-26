"""Run only on a disposable CI emulator: validate offline launch, book switching and persisted review state."""
import os, re, subprocess, time, xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE=os.environ.get('CET6_SMOKE_PACKAGE','com.moli.cet6')
APK=os.environ.get('CET6_SMOKE_APK','app/build/outputs/apk/debug/app-debug.apk')

def adb(*args):
    return subprocess.check_output(['adb',*args],timeout=30).decode('utf-8',errors='replace')

serial=adb('get-serialno').strip()
assert serial.startswith('emulator-'), 'This smoke test clears data and must only run on a disposable emulator.'
Path('smoke-results').mkdir(exist_ok=True)
import sys

def report_failure(kind, value, traceback):
    try:
        Path('smoke-results/failure-ui.xml').write_text(adb('shell','cat','/sdcard/moli-window.xml'),encoding='utf-8')
        Path('smoke-results/logcat.txt').write_text(adb('logcat','-d','-t','3000'),encoding='utf-8')
        Path('smoke-results/failure.png').write_bytes(subprocess.check_output(['adb','exec-out','screencap','-p'],timeout=30))
    finally:
        sys.__excepthook__(kind, value, traceback)
sys.excepthook = report_failure

def nodes():
    adb('shell','uiautomator','dump','/sdcard/moli-window.xml')
    return ET.fromstring(adb('shell','cat','/sdcard/moli-window.xml')).findall('.//node')

def find(text,exact=False):
    return next((n for n in nodes() if (n.attrib.get('text','')==text if exact else text in n.attrib.get('text',''))),None)

def wait_for(text):
    for _ in range(12):
        node=find(text)
        if node is not None:return node
        time.sleep(1)
    raise AssertionError('Missing UI text: '+text)

def tap(text,exact=False):
    node=find(text,exact)
    assert node is not None,'Missing tap target: '+text
    points=list(map(int,re.findall(r'\d+',node.attrib['bounds'])))
    assert len(points)==4
    adb('shell','input','tap',str((points[0]+points[2])//2),str((points[1]+points[3])//2))
    time.sleep(0.8)

def launch():
    result=adb('shell','am','start','-W','-n',PACKAGE+'/.MainActivity')
    assert 'Error:' not in result,result
    wait_for('六级背词')

adb('install','-r',APK)
if PACKAGE.endswith('.next'):
    assert 'package:com.moli.cet6' in adb('shell','pm','list','packages','com.moli.cet6'), 'Old app was removed'
adb('shell','pm','clear',PACKAGE)
adb('shell','svc','wifi','disable')
adb('shell','svc','data','disable')
launch()
wait_for('5806')
tap('六级全部词汇')
tap('六级核心词')
wait_for('1956')
tap('背词',exact=True)
wait_for('显示释义')
tap('显示释义')
tap('不会',exact=True)
tap('首页',exact=True)
wait_for('今日任务')
assert find('1 / 30 次') is not None,'Feedback count not updated'
# Capture a genuine running UI, not a mockup.
Path('smoke-results/home.png').write_bytes(subprocess.check_output(['adb','exec-out','screencap','-p'],timeout=30))
adb('shell','am','force-stop',PACKAGE)
launch()
wait_for('1956')
wait_for('1 / 30 次')
prefs=adb('shell','run-as',PACKAGE,'cat','shared_prefs/cet6_study.xml')
assert 'selectedBook' in prefs and 'core' in prefs and 'reviews' in prefs and 'familiarity' in prefs and 'streak' in prefs and 'due' in prefs
# Check library and original practice navigation while offline.
tap('阅读',exact=True)
wait_for('Libraries beyond the Bookshelf')
tap('开始阅读',exact=True)
wait_for('Libraries beyond the Bookshelf')
assert adb('shell','pidof',PACKAGE).strip(),'App exited unexpectedly'
Path('smoke-results/reading.png').write_bytes(subprocess.check_output(['adb','exec-out','screencap','-p'],timeout=30))
Path('smoke-results/result.txt').write_text('PASS: offline launch; all/core counts; switching; rating; due/familiarity/streak persistence after restart; reading launch.\n',encoding='utf-8')
print('Android emulator smoke tests passed.')
