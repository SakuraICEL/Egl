"""Build reproducible CET catalogs from the pinned, MIT-licensed ECDICT CSV.
Download URL and SHA-256 are recorded in catalog.json. Existing word IDs never change.
"""
import csv, hashlib, json
from pathlib import Path

root=Path(__file__).resolve().parents[1]
assets=root/'app/src/main/assets'
original=json.loads((assets/'words.json').read_text(encoding='utf-8'))
upstream=json.loads((root/'sources/ecdict-upstream.json').read_text(encoding='utf-8'))['sha']
raw=(root/'sources/ecdict.csv').read_bytes()
rows=list(csv.DictReader(raw.decode('utf-8-sig').splitlines()))
by_word={w['word'].casefold():w['id'] for w in original}
next_id=max(by_word.values())+1
entries=[]; full=set(); core=set()
for r in rows:
    tags=set(r['tag'].split())
    if not tags&{'cet4','cet6'}: continue
    name=r['word'].strip()
    assert name and r['translation'].strip()
    key=name.casefold()
    if key not in by_word:
        by_word[key]=next_id; next_id+=1
    word_id=by_word[key]
    entries.append(dict(id=word_id,word=name,phonetic=r['phonetic'],meaning=r['translation'].replace('\\n','\n'),definition=r['definition'].replace('\\n','\n'),source='ECDICT'))
    full.add(word_id)
    if 'cet6' in tags and 'cet4' not in tags: core.add(word_id)
high={w['id'] for w in original[:841]}
full|=high
catalog=dict(version=1,source='https://github.com/skywind3000/ECDICT',upstreamCommit=upstream,csvSha256=hashlib.sha256(raw).hexdigest(),license='MIT',
    coverage='ECDICT cet4 与 cet6 标签的联合词表，加原高频词补充；非官方穷尽词表。核心词为 cet6 且不含 cet4 标签的六级增量词。高频词沿用原工程用户提供的 841 词，未重新统计真题频次。',
    books=[dict(id='all',title='六级全部词汇',description='四六级联合词表，包含基础词；按来源覆盖范围收录',ids=sorted(full)),
           dict(id='high',title='六级高频词',description='原有高频词表，保留原词条和学习记录',ids=sorted(high)),
           dict(id='core',title='六级核心词',description='六级增量：ECDICT 标注 cet6、未标注 cet4',ids=sorted(core))],words=entries)
(assets/'catalog.json').write_text(json.dumps(catalog,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf-8')
(assets/'ECDICT-LICENSE.txt').write_bytes((root/'sources/ECDICT-LICENSE.txt').read_bytes())
assert len({w['id'] for w in entries})==len(entries)
assert high<=full and core<=full
known={w['id'] for w in original}|{w['id'] for w in entries}
assert all(set(b['ids'])<=known for b in catalog['books'])
print('Books:',[(b['title'],len(b['ids'])) for b in catalog['books']],'unique words:',len(known))
