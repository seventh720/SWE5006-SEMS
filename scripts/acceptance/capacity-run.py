#!/usr/bin/env python3
"""Run the graded suite only after capacity-watch is active and fixtures exist."""
import argparse
from datetime import datetime, timezone
import json, os, pathlib, subprocess, threading, time
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--credentials',type=pathlib.Path,required=True)
parser.add_argument('--reports',type=pathlib.Path,required=True)
parser.add_argument('--read-start-level',type=int,choices=[50,100,250,500,1000],default=50)
parser.add_argument('--latency-report-only',action='store_true',help='Record failed latency thresholds but continue; requires target operator authorization')
parser.add_argument('--skip-preflight',action='store_true',help='Use only when preflight already passed with these same fixtures')
args=parser.parse_args()
root=pathlib.Path(__file__).resolve().parents[2]
reports=args.reports.resolve()
reports.mkdir(parents=True,exist_ok=True)
stop=reports/'STOP'
credentials=args.credentials.resolve(strict=True)
assert (reports/'resources.jsonl').exists(), 'Start capacity-watch before generating load'
latest=json.loads((reports/'resources.jsonl').read_text().splitlines()[-1])
assert (datetime.now(timezone.utc)-datetime.fromisoformat(latest['at'])).total_seconds()<15, 'Health monitor is stale'
assert not latest['stopReasons'], 'Health guard already stopped the run'
results=[]

def now(): return datetime.now(timezone.utc).isoformat()

def sample_client(done):
    with (reports/'generator-resources.jsonl').open('a') as out:
        while not done.is_set():
            p=subprocess.run(['docker','stats','--no-stream','--format','{{json .}}','sems5006-capacity-load'],capture_output=True,text=True)
            if p.returncode==0 and p.stdout.strip():
                for line in p.stdout.splitlines():
                    item=json.loads(line)
                    out.write(json.dumps({'at':now(), **{k:item[k] for k in ['Name','CPUPerc','MemUsage','MemPerc','PIDs']}})+'\n');out.flush()
            done.wait(5)

def run(mode,vus,hold):
    if stop.exists(): raise RuntimeError('Health guard or previous threshold stopped the suite')
    name=f'{mode}-{vus}vu'
    command=['docker','run','--rm','--name','sems5006-capacity-load','--cpus','4','--memory','2g','--user',f'{os.getuid()}:{os.getgid()}',
             '-e','K6_CREDENTIALS=/run/credentials.json','-e',f'CAPACITY_VUS={vus}','-e',f'CAPACITY_HOLD={hold}','-e',f'CAPACITY_MODE={mode}',
             '-e','CAPACITY_LATENCY_ABORT='+('false' if args.latency_report_only else 'true'),
             '-v',f'{credentials}:/run/credentials.json:ro','-v',f'{root}/scripts/acceptance/capacity.js:/scripts/capacity.js:ro','-v',f'{reports}:/reports',
             'grafana/k6:1.3.0','run','--quiet','--summary-export',f'/reports/{name}.json','/scripts/capacity.js']
    record={'name':name,'mode':mode,'targetVUs':vus,'rampSeconds':0 if mode=='contention' else 30,'plannedHold':hold,'startedAt':now()}
    done=threading.Event(); sampler=threading.Thread(target=sample_client,args=(done,),daemon=True);sampler.start()
    print('START',name,now(),flush=True)
    with (reports/f'{name}.log').open('w') as log:
        p=subprocess.run(command,stdout=log,stderr=subprocess.STDOUT)
    done.set();sampler.join(timeout=12)
    record.update(exitCode=p.returncode,endedAt=now())
    path=reports/f'{name}.json'
    if path.exists():
        data=json.loads(path.read_text());m=data['metrics']
        record.update(requests=m['http_reqs']['count'],p95Ms=m['http_req_duration']['p(95)'],
                      httpErrorRate=m['http_req_failed']['value'],unexpectedErrorRate=m['unexpected_failure']['value'])
        record['thresholds']={key:value['thresholds'] for key,value in m.items() if 'thresholds' in value}
        for key in ['http_req_duration{phase:hold}','unexpected_failure{phase:hold}','vus','vus_max','bookings_created','sold_out']:
            if key in m: record[key]=m[key]
    failed_metrics=[key for key,value in record.get('thresholds',{}).items() if any(value.values())]
    latency_only=(p.returncode==99 and bool(failed_metrics) and all(key.startswith('http_req_duration') for key in failed_metrics))
    record['continuedAfterLatencyFailure']=bool(args.latency_report_only and latency_only and not stop.exists())
    results.append(record);(reports/'suite.json').write_text(json.dumps(results,indent=2)+'\n')
    print('END',json.dumps(record),flush=True)
    if p.returncode and not record['continuedAfterLatencyFailure']:
        if not stop.exists():stop.write_text(f'k6 {name} exited {p.returncode}; no further load.\n')
        return False
    return True

# Low-volume validation exercises both the read-only and booking/cancel paths
# before large runs; it also validates the fixture IDs and actual JSON contracts.
if not args.skip_preflight:
    if not run('read',1,'5s'):raise SystemExit(1)
    if not run('mixed',1,'5s'):raise SystemExit(1)
for mode in ['read','mixed']:
    for vus in [50,100,250,500,1000]:
        if mode == 'read' and vus < args.read_start_level:
            continue
        if not run(mode,vus,'180s' if vus==1000 else '30s'):raise SystemExit(1)
        # Allow recovery and observe health between independent stages.
        for _ in range(10):
            if stop.exists():raise SystemExit(1)
            time.sleep(1)
if not run('contention',1000,'60s'):raise SystemExit(1)
print('SUITE COMPLETED',now(),flush=True)
