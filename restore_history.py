from pathlib import Path
import hashlib,json,subprocess
root=Path(__file__).resolve().parent
m=json.loads((root/'manifest.json').read_text());output=root/m['bundle'];data=bytearray()
for p in m['parts']:
 content=(root/'parts'/p['name']).read_bytes()
 if len(content)!=p['size'] or hashlib.sha256(content).hexdigest()!=p['sha256']:raise SystemExit('Damaged part: '+p['name'])
 data.extend(content)
if len(data)!=m['size'] or hashlib.sha256(data).hexdigest()!=m['sha256']:raise SystemExit('Bundle checksum mismatch')
output.write_bytes(data)
print('Verified Git bundle:',output)
print('Restore with: git clone '+str(output)+' CivitatumCharacterCreation')
