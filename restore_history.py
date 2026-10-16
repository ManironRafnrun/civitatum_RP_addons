"""Restore complete native CCC history from the unchanged base and verified update."""
from pathlib import Path
import hashlib
import json
import subprocess
import sys

root=Path(__file__).resolve().parent
manifest=json.loads((root/'manifest.json').read_text())
base=manifest['base'];output=root/base['bundle'];digest=hashlib.sha256();size=0
with output.open('wb') as result:
    for item in base['parts']:
        data=(root/'base-parts'/item['name']).read_bytes()
        if len(data)!=item['size'] or hashlib.sha256(data).hexdigest()!=item['sha256']:
            raise SystemExit('Damaged history part: '+item['name'])
        result.write(data);digest.update(data);size+=len(data)
if size!=base['size'] or digest.hexdigest()!=base['sha256']:
    raise SystemExit('Base history checksum mismatch')
update=manifest['update'];path=root/update['file']
if hashlib.sha256(path.read_bytes()).hexdigest()!=update['sha256']:
    raise SystemExit('Update history checksum mismatch')
destination=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else root/'restored-CCC-0.4.2'
if destination.exists():raise SystemExit('Destination already exists: '+str(destination))
subprocess.run(['git','clone',str(output),str(destination)],check=True)
subprocess.run(['git','bundle','verify',str(path)],cwd=destination,check=True)
subprocess.run(['git','fetch',str(path),'refs/heads/*:refs/remotes/updates/*','refs/tags/*:refs/tags/*'],cwd=destination,check=True)
subprocess.run(['git','switch','codex/ccc/development'],cwd=destination,check=True)
subprocess.run(['git','merge','--ff-only',manifest['tag']],cwd=destination,check=True)
subprocess.run(['git','fsck','--full'],cwd=destination,check=True)
actual=subprocess.check_output(['git','rev-parse','HEAD'],cwd=destination,text=True).strip()
tree=subprocess.check_output(['git','rev-parse','HEAD^{tree}'],cwd=destination,text=True).strip()
if actual!=manifest['commit'] or tree!=manifest['tree']:
    raise SystemExit('Restored release does not match the recorded commit/tree')
print('NATIVE_HISTORY_RESTORED',actual,destination)
