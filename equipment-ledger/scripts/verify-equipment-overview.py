#!/usr/bin/env python3
"""Read-only acceptance checks against a running local equipment API; no fixtures are written."""
import json
import os
import urllib.error
import urllib.request

base = os.environ.get('EAM_OVERVIEW_TEST_URL', 'http://localhost:8080/api/v1')
count = 0

def get(path):
    with urllib.request.urlopen(base + path) as r:
        return json.load(r)

def check(ok, label):
    global count
    assert ok, label
    count += 1

def rejected(path):
    try:
        get(path)
    except urllib.error.HTTPError as e:
        return 400 <= e.code < 500
    return False

records = get('/equipment?page=1&size=100')['data']
for equipment in records:
    eid = equipment['id']
    overview = get(f'/equipment/{eid}/overview')
    expected = sum(overview['counts'][k] for k in ['repair', 'maintenance', 'inspection'])
    attention = get(f'/equipment/{eid}/attention?kind=all&page=1&size=100')
    check(attention['total'] == expected, f'{eid}: task counts match')
    check(len(overview['tasks']) == min(5, expected), f'{eid}: preview cap')
    check(all(t['deadline'] or not t['overdue'] for t in attention['data']), f'{eid}: no invented overdue')
    for kind in ['repair', 'maintenance', 'inspection', 'anomaly']:
        page1 = get(f'/equipment/{eid}/attention?kind={kind}&page=1&size=2')
        page2 = get(f'/equipment/{eid}/attention?kind={kind}&page=2&size=2')
        check(page1['total'] == overview['counts'][kind] == page2['total'], f'{eid}: {kind} totals match')
        check(not {t['id'] for t in page1['data']} & {t['id'] for t in page2['data']}, f'{eid}: {kind} pages do not overlap')
    history = get(f'/equipment/{eid}/history')
    check(overview['installedTotal'] == len(history['installedSpares']), f'{eid}: installed source matches history')
    check(len(overview['installed']) <= 5, f'{eid}: installed preview bounded')
    events = get(f'/equipment/{eid}/events?sort=desc&page=1&size=5')
    times = [v['time'].replace('T', ' ') for v in events['data']]
    check(times == sorted(times, reverse=True), f'{eid}: newest events first')
    old = get(f'/equipment/{eid}/events?page=1&size=5')
    times = [v['time'].replace('T', ' ') for v in old['data']]
    check(times == sorted(times), f'{eid}: legacy ascending default')
    check(old['total'] == events['total'], f'{eid}: sorting keeps cohort')
check(rejected('/equipment/9002/events?sort=invalid'), 'invalid event sort rejected')
check(rejected('/equipment/9002/attention?kind=invalid'), 'invalid attention kind rejected')
print(f'{count} read-only API checks passed for {len(records)} equipment records')
