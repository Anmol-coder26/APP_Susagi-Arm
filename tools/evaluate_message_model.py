"""Measure exported model scoring only, on the locked authored test split."""
import json, platform, statistics, time
from train_message_model import DATA, ASSET, REPORT, ROOT, load, features, summarize

def main():
    report = json.loads(REPORT.read_text())
    rows = [r for r in load(DATA) if r['category'] in report['groups']['test']]
    values = list(map(float, ASSET.read_text().splitlines()[2:]))
    bias, threshold, *weights = values
    def score(text):
        return bias + sum(weights[i] for i in sorted(features(text)))
    for row in rows:
        score(row['input'])
    timings = []
    groups = {}
    for row in rows:
        for _ in range(20):
            start = time.perf_counter_ns()
            margin = score(row['input'])
            timings.append(time.perf_counter_ns()-start)
        for key in ['all','category/'+row['category'],'language/'+row['language']]:
            groups.setdefault(key, []).append((margin,row['label']=='harmful'))
    timings.sort()
    output = dict(platform=platform.platform(), python=platform.python_version(), n=len(rows),
                  calls=len(timings), median_ns=statistics.median(timings),
                  p95_ns=timings[(len(timings)*95+99)//100-1], max_ns=max(timings),
                  groups={k:summarize(v,threshold) for k,v in groups.items()})
    (ROOT/'tools/message-model-host.json').write_text(json.dumps(output,indent=2)+'\n')
    print(output['median_ns'],output['p95_ns'],output['max_ns'])
if __name__=='__main__':
    main()
