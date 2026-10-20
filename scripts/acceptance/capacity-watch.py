#!/usr/bin/env python3
"""Stream the remote health guard and stop this suite container on a guard event."""
import argparse
import json, pathlib, subprocess, time
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--reports',type=pathlib.Path,required=True)
parser.add_argument('--identity-file',type=pathlib.Path,required=True)
parser.add_argument('--known-hosts',type=pathlib.Path,required=True)
parser.add_argument('--host',default='ubuntu@18.139.212.35')
parser.add_argument('--load1-limit',type=float,default=8)
parser.add_argument('--health-latency-limit',type=float,default=5,
                    help='Remote health latency stop threshold in seconds')
parser.add_argument('--health-timeout',type=float,default=6,
                    help='Remote health probe timeout in seconds')
args=parser.parse_args()
assert args.load1_limit >= 0
if not 0 < args.health_latency_limit < args.health_timeout < float('inf'):
    parser.error('Require 0 < --health-latency-limit < --health-timeout < infinity')
root=args.reports.resolve()
root.mkdir(parents=True,exist_ok=True)
stop=root/'STOP'
ssh=['ssh','-i',str(args.identity_file),'-o','BatchMode=yes','-o','StrictHostKeyChecking=yes','-o','ServerAliveInterval=5','-o','ServerAliveCountMax=2','-o','UserKnownHostsFile='+str(args.known_hosts),args.host,
     f'python3 -u /tmp/sems5006-capacity-guard.py --load1-limit {args.load1_limit} --health-latency-limit {args.health_latency_limit} --health-timeout {args.health_timeout}']
p=subprocess.Popen(ssh,stdout=subprocess.PIPE,text=True)
count=0
try:
    with (root/'resources.jsonl').open('a') as out:
        for line in p.stdout:
            out.write(line); out.flush()
            sample=json.loads(line)
            count+=1
            if sample.get('stopReasons'):
                stop.write_text(json.dumps(sample)+'\n')
                print('GUARD STOP:',sample['stopReasons'],flush=True)
                break
            if count%6==0:
                print('Guard healthy; memory MiB',sample['availableMemoryMiB'],'load',sample['load1'],flush=True)
finally:
    if not stop.exists():
        stop.write_text('Monitor ended; stop load for operator review.\n')
    subprocess.run(['docker','stop','--time','3','sems5006-capacity-load'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    if p.poll() is None:
        p.terminate()
    p.wait(timeout=10)
