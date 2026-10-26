"""Move a debuggable v0.2 local study record to the side-by-side v0.3 app.

Requires Android platform-tools, an authorized USB-debugging device, and both apps installed.
Keeps the source app and its data. Refuses an existing target record unless --overwrite.
"""
import argparse
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

OLD = 'com.moli.cet6'
NEW = 'com.moli.cet6.next'
PREFS = 'shared_prefs/cet6_study.xml'

parser = argparse.ArgumentParser(description='Copy the old local study record to the side-by-side app')
parser.add_argument('--serial', required=True, help='Android device ID from adb devices')
parser.add_argument('--backup', type=Path, default=Path('dist/legacy-prefs-backup.xml'))
parser.add_argument('--overwrite', action='store_true', help='Replace an existing new-app record after a local backup')
parser.add_argument('--allow-emulator', action='store_true', help=argparse.SUPPRESS)
args = parser.parse_args()
assert args.allow_emulator or not args.serial.startswith('emulator-'), 'Use a USB-debugging phone. --allow-emulator is only for CI.'

base=['adb','-s',args.serial]
def run(*parts, input=None):
    return subprocess.run(base+list(parts),input=input,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=30)

state=run('get-state')
assert state.returncode==0 and state.stdout.strip()==b'device', 'Device is not authorized or connected.'
old=run('shell','run-as',OLD,'cat',PREFS)
assert old.returncode==0 and old.stdout, 'Cannot read old study data; old app must be installed and debuggable.'
root=ET.fromstring(old.stdout)
assert root.tag=='map' and any(item.attrib.get('name') in ('day','todayCount','reviews','notebook') for item in root), 'Unexpected old study data format.'

existing=run('shell','run-as',NEW,'cat',PREFS)
if existing.returncode==0 and existing.stdout and not args.overwrite:
    raise SystemExit('New app already has study data. Stopped without changes; use --overwrite only after reviewing both records.')
# Verify the target package exists and run-as works before writing anything.
check=run('shell','run-as',NEW,'pwd')
assert check.returncode==0, 'New app is not installed or is not debuggable.'
args.backup.parent.mkdir(parents=True,exist_ok=True)
if args.backup.exists():
    raise SystemExit(f'Backup path already exists: {args.backup}. Choose another --backup path.')
args.backup.write_bytes(old.stdout)
if existing.returncode==0 and existing.stdout:
    target_backup=args.backup.with_name(args.backup.stem+'-new-app.xml')
    if target_backup.exists():
        raise SystemExit('New-app backup path already exists; stopped before replacing target data.')
    target_backup.write_bytes(existing.stdout)

stop=run('shell','am','force-stop',NEW)
assert stop.returncode==0, 'Could not stop new app before writing preferences.'
write=run('shell','-T',f"run-as {NEW} sh -c 'mkdir -p shared_prefs && cat > {PREFS} && chmod 600 {PREFS}'",input=old.stdout)
assert write.returncode==0, 'Writing new-app study data failed; source and local backup remain intact. Device error: '+write.stderr.decode('utf-8',errors='replace')[:500]
verify=run('shell','run-as',NEW,'cat',PREFS)
assert verify.returncode==0 and verify.stdout==old.stdout, 'Target verification failed; source and local backup remain intact.'
print(f'Copied study data to {NEW}. Old app remains installed. Backup: {args.backup.resolve()}')
