#!/usr/bin/env bash
# Submits N jobs and measures submission and drain throughput.
set -euo pipefail

BASE="${NIMBUS_URL:-http://localhost:8081}"
N="${1:-1000}"
EMAIL="bench-$(date +%s)@nimbus.dev"

TOKEN=$(curl -s -X POST "$BASE/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"password123\",\"firstName\":\"B\",\"lastName\":\"M\"}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")

echo "Submitting $N jobs to $BASE ..."
python3 - "$BASE" "$TOKEN" "$N" << 'PY'
import json, sys, time, urllib.request
base, token, n = sys.argv[1], sys.argv[2], int(sys.argv[3])
hdr = {"Content-Type": "application/json", "Authorization": f"Bearer {token}"}

t0 = time.time()
for i in range(n):
    req = urllib.request.Request(f"{base}/api/jobs",
        data=json.dumps({"type": "echo", "payload": f"bench-{i}"}).encode(), headers=hdr)
    urllib.request.urlopen(req).read()
el = time.time() - t0
print(f"  submit : {n} jobs in {el:.2f}s  ->  {n/el:.1f} req/sec")

start = time.time()
while time.time() - start < 300:
    req = urllib.request.Request(f"{base}/api/jobs?size={n*2}", headers=hdr)
    jobs = json.loads(urllib.request.urlopen(req).read())["content"]
    if not [j for j in jobs if j["status"] in ("PENDING", "RUNNING")]:
        d = time.time() - start
        done = len([j for j in jobs if j["status"] == "SUCCEEDED"])
        print(f"  drain  : {done} jobs in {d:.2f}s  ->  {done/d:.1f} jobs/sec")
        break
    time.sleep(0.5)
else:
    print("  drain  : did not complete within 300s")
PY
