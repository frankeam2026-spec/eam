#!/usr/bin/env python3
"""Verify scheduled cleanup on the dedicated clone after API scenarios.
Start the isolated API on 8082 with cleanup delay 250ms before running this script.
"""
import json, pathlib, subprocess, time
root=pathlib.Path(__file__).resolve().parents[1];runtime=root.parent/'.local-runtime'
def sql(statement):
 r=subprocess.run([str(runtime/'mysql-8.4.6-macos15-arm64/bin/mysql'),'--defaults-extra-file='+str(runtime/'root.cnf'),'--socket='+str(runtime/'mysql.sock'),'--batch','--skip-column-names','eam_outbound_test'],input=statement,text=True,capture_output=True,check=True);return r.stdout.strip()
qty=sql('SELECT SUM(quantity) FROM inv_balance');cost=sql('SELECT SUM(remaining_cost) FROM inv_cost_layer')
assert int(sql("SELECT COUNT(*) FROM inv_requisition WHERE task_id=995502 AND status IN ('待预留','部分预留','已预留','部分发料')"))>0
sql("UPDATE eam_repair_order SET status='已完成' WHERE id=995502")
for _ in range(60):
 if sql("SELECT COUNT(*) FROM inv_requisition WHERE task_id=995502 AND status IN ('待预留','部分预留','已预留','部分发料')")=='0':break
 time.sleep(.2)
else:raise AssertionError('cleanup scheduler did not close pending demand')
assert sql('SELECT SUM(quantity) FROM inv_balance')==qty
assert sql('SELECT SUM(remaining_cost) FROM inv_cost_layer')==cost
assert sql('SELECT COUNT(*) FROM inv_reservation h JOIN inv_requisition r ON r.id=h.requisition_id WHERE r.task_id=995502 AND h.remaining_quantity>0')=='0'
assert sql("SELECT COUNT(*) FROM inv_inbox i JOIN inv_requisition r ON r.id=i.document_id WHERE r.task_id=995502 AND i.route_kind='requisitions' AND i.is_todo=1 AND i.done=0")=='0'
(root/'artifacts/qa/outbounds/cleanup-results.json').write_text(json.dumps({'passed':['任务结束自动关闭未发需求','预留自动释放','所有仓管待办同步结束','实物数量与成本不被重复扣减']},ensure_ascii=False,indent=2))
print('PASS scheduled cleanup (4 assertions)')
