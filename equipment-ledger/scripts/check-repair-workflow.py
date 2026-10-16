"""Local workflow integration. Only unique test fixtures are cleaned in finally."""
import concurrent.futures
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.request
project=Path(__file__).resolve().parents[1]
mysql=str(project.parent/'.local-runtime/mysql-8.4.6-macos15-arm64/bin/mysql')
root='http://127.0.0.1:8080/api/v1/'
tag='WF'+str(time.time_ns()//1000)
order_id=None
order_no=None
result_id=None
stock_id=None
attachment=None
checks=0

def http(path,body=None,method=None):
    req=urllib.request.Request(root+path,data=None if body is None else json.dumps(body).encode(),headers={'Content-Type':'application/json'},method=method or ('POST' if body is not None else 'GET'))
    try:
        with urllib.request.urlopen(req,timeout=20) as response:return response.status,json.load(response)
    except urllib.error.HTTPError as e:return e.code,json.load(e)
def sql(statement):
    env=dict(os.environ);env['MYSQL_PWD']=env['DB_PASSWORD']
    return subprocess.run([mysql,'--no-defaults','--host=127.0.0.1','--user='+env['DB_USERNAME'],'eam_demo','-N','-B','-e',statement],env=env,check=True,capture_output=True,text=True).stdout.strip()
def ok(value,label):
    global checks
    assert value,label
    checks+=1

def action(kind,**fields):
    version=http(f'repairs/{order_id}/workflow')[1]['data']['order']['rowVersion']
    status,payload=http(f'repairs/{order_id}/workflow?rowVersion={version}',{'action':kind,**fields})
    ok(status==200,(kind,status,payload))
    return payload['data']
try:
    status,identity=http('auth/me');assert status==200 and identity['developmentMode']
    user_id=identity['data']['id']
    status,choices=http('repairs/user-choices');assert status==200
    other=next(x['id'] for x in choices['data'] if x['id']!=user_id)
    status,equipment=http('equipment?size=1');eq=equipment['data'][0]
    status,faults=http('base-data/fault-types');fault=next(x['name'] for x in faults['data'] if x['status']=='启用')
    report={'equipmentCode':eq['code'],'equipmentName':eq['name'],'department':eq['department'],'reportedAt':time.strftime('%Y-%m-%dT%H:%M'),'faultType':fault,'severity':'一般','description':tag,'reporter':'不可伪造报修人','attachments':[]}
    status,payload=http('repairs',report);ok(status==200,('report',payload));order_id=payload['data']['id'];order_no=payload['data']['orderNo']
    ok(payload['data']['reporter']==identity['data']['name'],'server reporter identity')
    r=action('dispatch',userIds=[user_id,other]);ok(r['status']=='待接单','dispatch pending, not processing')
    detail=http(f'repairs/{order_id}/workflow')[1]['data'];ok(len(detail['candidates'])==2,'two candidates')
    version=r['rowVersion']
    def claim():return http(f'repairs/{order_id}/workflow?rowVersion={version}',{'action':'claim'})
    with concurrent.futures.ThreadPoolExecutor(2) as pool:claims=list(pool.map(lambda _:claim(),range(2)))
    ok(sorted(x[0] for x in claims)==[200,409],('exactly one concurrent claim',claims))
    detail=http(f'repairs/{order_id}/workflow')[1]['data'];ok(detail['order']['status']=='处理中','claimed processing')
    ok(sum(x['status']=='已接单' for x in detail['candidates'])==1,'one owner only')
    action('transfer',userIds=[user_id,other],reason='调整维修安排')
    action('claim')
    action('suspend',reason='等待配件');action('resume',reason='配件到位');action('outsource',vendor='集成测试外协单位',reason='专用设备检测')
    status,stocks=http('repairs/stocks');before=len(stocks['data'])
    status,payload=http('repairs/stocks',{'code':tag,'name':'工作流测试轴承','model':'TEST','quantity':5});ok(status==200,('stock',payload))
    stocks=http('repairs/stocks')[1]['data'];stock_id=next(x['id'] for x in stocks if x['code']==tag)
    action('material',stockId=stock_id,quantity=2)
    stock=next(x for x in http('repairs/stocks')[1]['data'] if x['id']==stock_id);ok(float(stock['quantity'])==5,'request does not issue stock')
    detail=http(f'repairs/{order_id}/workflow')[1]['data'];material=detail['materials'][0]
    action('issue',materialId=material['id']);stock=next(x for x in http('repairs/stocks')[1]['data'] if x['id']==stock_id);ok(float(stock['quantity'])==3,'keeper issue reduces stock')
    action('install',materialId=material['id'],quantity=1)
    detail=http(f'repairs/{order_id}/workflow')[1]['data'];installed=next(x for x in detail['installed'] if x['code']==tag)
    history=http(f'equipment/{eq["id"]}/history')[1];ok(any(x['id']==installed['id'] for x in history['installedSpares']),'installed part appears in history')
    action('remove',installedId=installed['id'],reason='试装后更换')
    detail=http(f'repairs/{order_id}/workflow')[1]['data'];ok(not any(x['id']==installed['id'] for x in detail['installed']),'removed spare not active')
    status,payload=http('base-data/processing-results',{'code':tag,'name':tag+'继续观察','status':1});ok(status==200,'custom result');result_id=payload['data']['id']
    process={'faultCause':'轴承磨损','faultType':fault,'repairMethod':'自修','content':'更换轴承并试运行','result':tag+'继续观察','prevention':'按周期润滑检查','collaboratorIds':[other],'attachments':[]}
    r=action('process',**process);ok(r['status']=='处理中','custom result stays processing')
    boundary='eam-workflow-test-boundary'
    upload_body=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="workflow.txt"\r\nContent-Type: text/plain\r\n\r\n{tag}\r\n--{boundary}--\r\n').encode()
    request=urllib.request.Request(root+'repairs/attachments',data=upload_body,headers={'Content-Type':'multipart/form-data; boundary='+boundary},method='POST')
    with urllib.request.urlopen(request) as response: attachment=json.load(response)['data']
    process['attachments']=[attachment]
    process['result']='已解决';r=action('process',**process);ok(r['status']=='待验收','solved enters acceptance')
    with urllib.request.urlopen('http://127.0.0.1:8080'+attachment['url']) as response:ok(response.read().decode()==tag,'processing attachment download')
    inbox=http('repairs/inbox')[1]['data'];ok(any(x['orderId']==order_id and x['kind']=='待验收' and not x['done'] for x in inbox),'acceptance todo')
    r=action('return',score=2,comment='需要进一步检查振动',attachments=[attachment]);ok(r['status']=='处理中','return to process')
    action('process',**process);r=action('accept',score=5,comment='试运行正常，验收通过',attachments=[attachment]);ok(r['status']=='已完成','accepted closes order')
    detail=http(f'repairs/{order_id}/workflow')[1]['data'];actions=[x['action'] for x in detail['events']]
    for label in ['报修','派工','转派','接单','挂起','恢复处理','委外','领料申请','仓管出库','换上备件','换下备件','记录处理','提交验收','验收打回','验收通过']:ok(label in actions,'timeline '+label)
    ok(all(x['actor'] and x['time'] for x in detail['events']),'timeline actor and timestamp')
    ok(not any(x['orderId']==order_id and x['todo'] and not x['done'] for x in http('repairs/inbox')[1]['data']),'no stale todos after completion')
    status,payload=http('base-data/processing-results',{'code':tag+'FIXED','name':'已解决','status':1});ok(status==400,'solved cannot be redefined')
    print(f'PASS {checks} workflow checks')
finally:
    if order_id:
        # IDs were returned by this script's unique fixture creation; no business rows are selected by ranges.
        sql(f"DELETE FROM eam_repair_event WHERE tenant_id=10001 AND order_id={int(order_id)}; DELETE FROM eam_repair_inbox WHERE tenant_id=10001 AND order_id={int(order_id)}; DELETE FROM eam_repair_candidate WHERE tenant_id=10001 AND order_id={int(order_id)}; DELETE FROM eam_equipment_spare WHERE tenant_id=10001 AND order_id={int(order_id)}; DELETE FROM eam_repair_material WHERE tenant_id=10001 AND order_id={int(order_id)}; DELETE FROM eam_equipment_history WHERE tenant_id=10001 AND record_no='{order_no}'; DELETE FROM eam_repair_order WHERE tenant_id=10001 AND id={int(order_id)} AND description='{tag}';")
    if stock_id:sql(f"DELETE FROM eam_spare_stock WHERE tenant_id=10001 AND id={int(stock_id)} AND code='{tag}';")
    if result_id:sql(f"DELETE FROM eam_base_item WHERE tenant_id=10001 AND id={int(result_id)} AND code='{tag}';")

    if attachment:
        upload_dir=project/'backend/data/uploads'
        for file in upload_dir.glob(attachment['id']+'.*'):file.unlink()
