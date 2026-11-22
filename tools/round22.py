"""Round 22: bounded lexical logistic regression; original artifact and group split frozen."""
import collections, hashlib, json, math, random, re, statistics, time
from pathlib import Path
import train_message_model as old

ROOT=old.ROOT
ASSET=ROOT/'app/src/main/assets/security/message-v2.tsv'
SPEC=ROOT/'app/src/main/assets/security/intent-v2.tsv'
def tokenize(text):
    text=text[:2048].translate(str.maketrans('ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'))
    return re.findall(r'[a-z0-9\u0900-\u097f]+',text)[:256]

def features(text):
    words=tokenize(text)
    keys=set(words)|{a+'_'+b for a,b in zip(words,words[1:])}
    flags={name for name,terms in intents if keys.intersection(terms)}
    return keys|{'@'+f for f in flags}|{'@'+a+'+'+b for a in flags for b in flags if a<b}

intents=[(r.split('\t')[0],set(r.split('\t')[1].split())) for r in SPEC.read_text(encoding='utf-8-sig').splitlines()]
def main():
    rows=old.load(old.DATA)
    prior=json.loads(old.REPORT.read_text())
    assert hashlib.sha256(old.DATA.read_bytes().replace(b'\r\n',b'\n')).hexdigest()==prior['source_sha256']
    splits={s:[r for r in rows if r['category'] in cats] for s,cats in prior['groups'].items()}
    train=splits['train']
    vocab=sorted(set.union(*(features(r['input']) for r in train)))
    w=dict.fromkeys(vocab,0.0); bias=0.
    rng=random.Random(22)
    for epoch in range(70):
        order=train[:]; rng.shuffle(order)
        for row in order:
            x=sorted(features(row['input']))
            z=bias+sum(w[k] for k in x)
            error=(row['label']=='harmful')-1/(1+math.exp(-z))
            lr=.1/math.sqrt(1+epoch)
            for k in x: w[k]+=lr*(error-.03*w[k])
            bias+=lr*error
    def score(t): return bias+sum(w.get(k,0.) for k in sorted(features(t)))
    def pairs(rs): return [(score(r['input']),r['label']=='harmful') for r in rs]
    val=pairs(splits['validation'])
    # Predeclared: maximize recall with no more than 10% empirical validation FPR.
    candidates=[s for s,_ in val]+[max(s for s,_ in val)+1]
    t=max((s for s in candidates if old.summarize(val,s)['fpr']<=.10),key=lambda s:(old.summarize(val,s)['recall'],-old.summarize(val,s)['fp'],s))
    ASSET.write_bytes(('lexical-v2\n'+str(bias)+'\n'+str(t)+'\n'+''.join(k+'\t'+str(v)+'\n' for k,v in w.items())).encode())
    result={'source':prior['source_sha256'],'groups':prior['groups'],'threshold':t,'bytes':ASSET.stat().st_size,'sha256':hashlib.sha256(ASSET.read_bytes()).hexdigest(),'spec_sha256':hashlib.sha256(SPEC.read_bytes()).hexdigest()}
    orig=list(map(float,old.ASSET.read_text().splitlines()[2:]))
    times=[]
    for split in ('validation','test'):
        rs=splits[split]
        a=[(orig[0]+sum(orig[i+2] for i in sorted(old.features(r['input']))),r['label']=='harmful') for r in rs]
        result[split]={'original':old.summarize(a,orig[1]),'original_threshold':orig[1],'original_zero':old.summarize(a,0),'candidate':old.summarize(pairs(rs),t),'abstentions':0}
        result[split]['score_ranges']={str(y):[min(s for s,label in a if label==y),max(s for s,label in a if label==y)] for y in (False,True)}
        result[split]['category']={c:old.summarize(pairs([r for r in rs if r['category']==c]),t) for c in prior['groups'][split]}
        result[split]['errors']=[r['id'] for r in rs if (score(r['input'])>=t)!=(r['label']=='harmful')]
    grams={b[i:i+3] for r in train for b in [r['input'].encode().lower()] for i in range(len(b)-2)}
    result['original_unique_trigrams']=len(grams);result['original_occupied_bins']=len(set.union(*(old.features(r['input']) for r in train)))
    for _ in range(20):
        for r in splits['test']:
            start=time.perf_counter_ns(); score(r['input']);times.append(time.perf_counter_ns()-start)
    result['python_ns']=[statistics.median(times),sorted(times)[math.ceil(.95*len(times))-1],max(times)]
    (ROOT/'tools/round22-results.json').write_text(json.dumps(result,indent=2)+'\n')
    p=ROOT/'app/src/test/resources/safety/model-parity.tsv'
    (p.parent/'lexical-parity.tsv').write_bytes(('input_hex\tmargin\n'+''.join(h+'\t'+str(score(bytes.fromhex(h).decode()))+'\n' for line in p.read_text().splitlines()[1:] for h in [line.split('\t')[0]])).encode())
    print(result['validation']['candidate'],result['test']['candidate'],result['sha256'],result['spec_sha256'])
if __name__=='__main__':main()
