#!/usr/bin/env python3
"""Isolated API acceptance scenarios. Never run against eam_demo.
Usage: python3 scripts/verify-outbounds.py (API 8081, DB eam_outbound_test).
"""
from decimal import Decimal
import urllib.parse
import concurrent.futures, hashlib, json, os, pathlib, secrets, subprocess, urllib.request, urllib.error, uuid
ROOT=pathlib.Path(__file__).resolve().parents[1]
RUNTIME=ROOT.parent/'.local-runtime'
DB='eam_outbound_test'
BASE='http://127.0.0.1:8081/api/v1'
assert DB!='eam_demo' and ':8081/' in BASE
REPORT=[]
def sql(command):
    r=subprocess.run([str(RUNTIME/'mysql-8.4.6-macos15-arm64/bin/mysql'),'--defaults-extra-file='+str(RUNTIME/'root.cnf'),'--socket='+str(RUNTIME/'mysql.sock'),'--batch','--skip-column-names',DB],input=command,text=True,capture_output=True)
    if r.returncode: raise RuntimeError(r.stderr)
    return r.stdout.strip()
def check(name,condition):
    assert condition,name
    REPORT.append(name);print('PASS',name)
# Only the dedicated clone is reset; no production database or accounts are touched.
for table in sql("SHOW TABLES LIKE 'inv_%'").splitlines(): sql(f"TRUNCATE TABLE `{table}`")
for table in ['eam_sys_user_role','eam_sys_role_menu_permission','eam_auth_session','eam_sys_role','eam_sys_user']:
    field='user_id' if table in ['eam_sys_user_role','eam_auth_session'] else 'id'
    sql(f"DELETE FROM {table} WHERE {field} BETWEEN 995100 AND 995600")
sql('DELETE FROM eam_repair_order WHERE id IN (995501,995502)')
tokens={}
def api(path,method='GET',body=None,actor='warehouse',expect=200):
    headers={'Cookie':'EAM_SESSION='+tokens[actor],'Origin':'http://127.0.0.1:8081','Content-Type':'application/json'}
    data=None if body is None else json.dumps(body,ensure_ascii=False).encode()
    try:
        with urllib.request.urlopen(urllib.request.Request(BASE+urllib.parse.quote(path,safe='/?:=&%'),data=data,method=method,headers=headers),timeout=30) as r:
            result=json.load(r);status=r.status
    except urllib.error.HTTPError as e: status=e.code;result=json.loads(e.read() or '{}')
    if status!=expect:raise AssertionError(f'{method} {path}: {status} != {expect}: {result}')
    return result
menus={
'inventoryOutbound':['查看','新增直接出库','编辑草稿','删除草稿','预留','释放预留','确认出库','关闭剩余需求','导出','查看金额'],
'inventoryReceipt':['查看','新增','编辑','入库','导出','查看金额'],
'inventoryWarehouse':['查看'], 'inventoryRequisition':['查看','新增','编辑'],
'inventoryReturn':['查看','新增','编辑','入库','查看金额'], 'inventoryReversal':['查看','新增','编辑','冲销','查看金额'],
'repair':['查看','处理'], 'workspace':['查看'], 'role':['查看','新增','编辑','授权']}
# Fixture IDs/codes are dedicated to this isolated database.
setup="""
INSERT INTO inv_warehouse(id,tenant_id,company_id,code,name) VALUES(995001,10001,71002,'QA-OUT-W','QA 出库验证仓'),(995002,10001,71006,'QA-OUT-OTHER','QA 跨公司仓');
INSERT INTO inv_bin(id,tenant_id,warehouse_id,code,name) VALUES(995011,10001,995001,'A','QA 库位A'),(995012,10001,995001,'B','QA 库位B'),(995013,10001,995002,'A','QA 跨公司库位');
INSERT INTO inv_material(id,tenant_id,code,name,unit,usage_type,batch_enabled,expiry_enabled,allocation_rule) VALUES(995021,10001,'QA-OUT-N','QA 非批次耗材','件','消耗型',0,0,''),(995022,10001,'QA-OUT-B','QA 多价批次备件','件','可安装',1,1,'FEFO');
"""
for n,actor in enumerate(['warehouse','warehouse2','owner','noMoney','outsider','finance'],1):
    uid=995100+n;rid=995200+n;token=secrets.token_urlsafe(36);tokens[actor]=token
    setup+=f"INSERT INTO eam_sys_user(id,tenant_id,account,display_name,org_id) VALUES({uid},10001,'qa-out-{actor}','QA {actor}',{71007 if actor=='outsider' else 71003});"
    setup+=f"INSERT INTO eam_sys_role(id,tenant_id,code,name,data_scope) VALUES({rid},10001,'QA-OUT-{actor}','QA {actor}','PER_MENU'); INSERT INTO eam_sys_user_role(tenant_id,user_id,role_id) VALUES(10001,{uid},{rid});"
    setup+=f"INSERT INTO eam_auth_session(id,tenant_id,user_id,token_hash,expires_at) VALUES({995300+n},10001,{uid},'{hashlib.sha256(token.encode()).hexdigest()}',DATE_ADD(NOW(),INTERVAL 2 DAY));"
    for j,(menu,actions) in enumerate(menus.items()):
        if actor=='owner' and menu not in ['repair','workspace']:continue
        if actor=='outsider' and menu!='inventoryOutbound':continue
        if actor=='finance' and menu!='inventoryRequisition':continue
        if actor=='finance':actions=['查看','查看金额']
        actions=[a for a in actions if not(actor=='noMoney' and a=='查看金额')]
        scopes=['all'] if menu in ['repair','workspace','role'] else ['warehouse']
        orgs=[] if scopes==['all'] else [995002 if actor=='outsider' else 995001]
        setup+=f"INSERT INTO eam_sys_role_menu_permission(id,tenant_id,role_id,menu_key,actions_json,data_scopes_json,org_ids_json) VALUES({995400+n*30+j},10001,{rid},'{menu}','{json.dumps(actions,ensure_ascii=False)}','{json.dumps(scopes)}','{json.dumps(orgs)}');"
setup+="""
INSERT INTO eam_repair_order(id,tenant_id,order_no,equipment_code,equipment_name,reported_at,fault_type,description,reporter,status,department_org_id,reporter_user_id,assignee_user_id) VALUES(995501,10001,'QA-WX-1','EQ-2026-0001','QA 设备',NOW(),'QA 故障','QA 出库验证','QA owner','处理中',71003,995103,995103),(995502,10001,'QA-WX-2','EQ-2026-0001','QA 设备',NOW(),'QA 故障','QA 合并验证','QA owner','处理中',71003,995103,995103);
"""
sql(setup)

def receipt(material,qty,price,batch='',expiry='',bin='995011',fee='0'):
    body={'warehouseId':'995001','supplier':'QA supplier','remark':'QA OUTBOUND','lines':[{'materialId':str(material),'binId':bin,'quantity':str(qty),'unitPrice':str(price),'allocatedFee':fee,'batchCode':batch,'expiryDate':expiry}]}
    d=api('/inventory/receipts','POST',body)['data'];return api('/inventory/receipts/'+d['id']+'/confirm','POST',{'rowVersion':d['rowVersion']})['data']
receipt(995021,100,10,fee='10')
receipt(995022,10,20,'QA-BATCH-1','2030-12-01')
receipt(995022,10,30,'QA-BATCH-1','2030-12-01')
receipt(995022,5,40,'QA-BATCH-2','2029-12-01',bin='995012')
check('入库费用分摊形成实际成本层',Decimal(sql('SELECT SUM(remaining_cost) FROM inv_cost_layer c JOIN inv_balance b ON b.id=c.balance_id WHERE b.material_id=995021'))==Decimal('1010'))

def draft(qty='3',material='995021',actor='warehouse',task=False):
    return api('/inventory/outbounds','POST',{'sourceKind':'直接出库','warehouseId':'995001','departmentId':'71003','recipientId':'995103','purpose':'QA 部门领用','taskKind':'维修' if task else '', 'taskId':'995501' if task else '', 'lines':[{'materialId':material,'quantity':str(qty),'selections':[]}]},actor)['data']
def preview(d,actor='warehouse'):
    return api('/inventory/outbounds/'+d['id']+'/preview','POST',{'rowVersion':d['rowVersion']},actor)['data']
def confirm(d,p,key=None,actor='warehouse',expect=200):
    return api('/inventory/outbounds/'+d['id']+'/confirm','POST',{'rowVersion':p['rowVersion'],'fingerprint':p['fingerprint'],'confirmationKey':key or str(uuid.uuid4())},actor,expect)
def snapshot():return sql('SELECT CONCAT(SUM(quantity),":",SUM(reserved_quantity)) FROM inv_balance WHERE warehouse_id=995001;SELECT COUNT(*) FROM inv_transaction;SELECT COUNT(*) FROM inv_document_event;SELECT COUNT(*) FROM inv_reservation;')
d=draft();before=snapshot();p=preview(d);preview(d);check('预览不改变库存、预留、流水或时间轴',snapshot()==before)
key=str(uuid.uuid4());posted=confirm(d,p,key)['data'];again=confirm(d,p,key)['data'];check('直接出库及幂等重试仅扣一次',posted['id']==again['id'] and sql('SELECT SUM(quantity) FROM inv_balance WHERE material_id=995021')=='97.000000')
check('出库单保留实际成本',Decimal(str(posted['totalAmount']))==Decimal('30.30'))
api('/inventory/outbounds/'+d['id'],'PUT',d,expect=400);api('/inventory/outbounds/'+d['id'],'DELETE',{'rowVersion':posted['rowVersion']},expect=400);check('已生效单据不可编辑或删除',True)
no=api('/inventory/outbounds/'+d['id'],actor='noMoney')['data'];check('详情金额服务端隐藏','totalAmount' not in no and all('unitCost' not in a and 'amount' not in a for a in no['allocations']))
api('/inventory/outbounds/'+d['id'],actor='outsider',expect=403);check('跨公司仓库隔离',True)
nd=draft('1');np=preview(nd,'noMoney');check('预览金额服务端隐藏','totalAmount' not in np and all('unitCost' not in a and 'layerCost' not in a for a in np['allocations']))
confirm(nd,np,actor='noMoney');check('无金额权限仍可合法确认出库',True)

# FEFO picks the earlier expiry first; same physical batch may contain several prices.
bd=draft('17','995022');bp=preview(bd);check('FEFO及同批次多价成本层',[a['batchCode'] for a in bp['allocations']]==['QA-BATCH-2','QA-BATCH-1','QA-BATCH-1'] and [Decimal(str(a['unitCost'])) for a in bp['allocations']]==[40,20,30])
confirm(bd,bp)

# Source task performer has no inventory role, can still apply.
def requisition(task,qty):
    r=api('/inventory/requisitions','POST',{'warehouseId':'995001','taskKind':'维修','taskId':str(task),'remark':'QA 申请','lines':[{'materialId':'995021','quantity':str(qty)}]},actor='owner')['data']
    return api('/inventory/requisitions/'+r['id']+'/submit','POST',{'rowVersion':r['rowVersion']},actor='owner')['data']
r1=requisition(995501,10);r2=requisition(995502,12)
check('所有授权仓管收到待出库事项',int(sql(f"SELECT COUNT(DISTINCT user_id) FROM inv_inbox WHERE document_id={r1['id']} AND is_todo=1 AND user_id IN (995101,995102,995104)"))==3)
f=api('/inventory/outbounds/from-requests','POST',{'ids':[r1['id'],r2['id']]})['data'];f['lines'][0]['quantity']='3';f['lines'][1]['quantity']='4';m=api('/inventory/outbounds','POST',f)['data'];mp=preview(m);check('合并预览明细标明来源申请和领用人',{a['requisitionCode'] for a in mp['allocations']}=={r1['code'],r2['code']} and all(a['recipientName']=='QA owner' for a in mp['allocations']));mc=confirm(m,mp)['data'];check('合并部分出库形成一张单且保留各申请来源',len(mc['lines'])==2 and len({a['issueId'] for a in mc['allocations']})==1 and len({l['requisitionId'] for l in mc['lines']})==2)
check('部分出库剩余数量正确',sql(f"SELECT issued_quantity FROM inv_requisition_line WHERE requisition_id IN ({r1['id']},{r2['id']}) ORDER BY requisition_id").splitlines()==['3.000000','4.000000'])
rr=api('/inventory/requisitions/'+r1['id'])['data'];api('/inventory/requisitions/'+r1['id']+'/reserve','POST',{'rowVersion':rr['rowVersion']});reserved=snapshot();own=api('/inventory/outbounds/from-requests','POST',{'ids':[r1['id']]})['data'];own['lines'][0]['quantity']='2';od=api('/inventory/outbounds','POST',own)['data'];reserved=snapshot();op=preview(od);check('预览使用本申请预留但不改变预留',snapshot()==reserved and any(a.get('reservationId') for a in op['allocations']));confirm(od,op)
rr=api('/inventory/requisitions/'+r1['id'])['data'];api('/inventory/requisitions/'+r1['id']+'/close-remaining','POST',{'rowVersion':rr['rowVersion'],'reason':'QA 关闭剩余'},actor='owner');check('申请人关闭剩余并释放预留',sql(f"SELECT status FROM inv_requisition WHERE id={r1['id']}")=='已关闭' and sql(f"SELECT SUM(remaining_quantity) FROM inv_reservation WHERE requisition_id={r1['id']}")=='0.000000')

# Preview conflict and concurrent confirmation across two warehouse actors.
a=draft('1');pa=preview(a);b=draft('1');confirm(b,preview(b));confirm(a,pa,expect=400);check('库存变化拒绝旧分配指纹',True)
c=draft('2');pc=preview(c);ck=str(uuid.uuid4())
with concurrent.futures.ThreadPoolExecutor(2) as pool:out=list(pool.map(lambda actor:confirm(c,pc,ck,actor=actor)['data'],['warehouse','warehouse2']))
check('并发同单确认只生成一份分配',out[0]['id']==out[1]['id'] and sql(f"SELECT SUM(quantity) FROM inv_issue_allocation WHERE issue_id={c['id']}")=='2.000000')

# Direct task use is traceable and does not deduct warehouse stock a second time.
t=draft('1',task=True);tc=confirm(t,preview(t))['data'];usage=api('/inventory/task-materials/维修/995501',actor='owner')['data'];alloc=next(a for a in usage['allocations'] if a['issueCode']==t['code']);balance=sql('SELECT SUM(quantity) FROM inv_balance WHERE material_id=995021');api('/inventory/task-materials/维修/995501/use','POST',{'allocationId':alloc['id'],'quantity':'1','confirmationKey':str(uuid.uuid4())},actor='owner');check('直接关联任务可登记用料且不重复扣库',sql('SELECT SUM(quantity) FROM inv_balance WHERE material_id=995021')==balance)
# Inspection of posted used allocation must block full reversal.
rev=api('/inventory/operations/reversals','POST',{'warehouseId':'995001','sourceKind':'issue','sourceId':t['id'],'remark':'QA 已用禁止冲销','lines':[]})['data'];api('/inventory/operations/reversals/'+rev['id']+'/confirm','POST',{'rowVersion':rev['rowVersion']},expect=400);check('已用出库阻止整单冲销',True)

# Direct return uses original allocation and original costs.
original=posted['allocations'][0];ret=api('/inventory/operations/returns','POST',{'warehouseId':'995001','remark':'QA 退料','lines':[{'allocationId':original['id'],'binId':'995011','quantity':'1'}]})['data'];api('/inventory/operations/returns/'+ret['id']+'/confirm','POST',{'rowVersion':ret['rowVersion']});check('直接出库退料保留原批次及成本',sql(f"SELECT returned_quantity FROM inv_issue_allocation WHERE id={original['id']}")=='1.000000')
check('出库时间轴包含退料记录',any('退料' in e['action'] for e in api('/inventory/outbounds/'+posted['id'])['data']['events']))

# Confirmation failure is transactional.
bad=draft('999999');before=snapshot();api('/inventory/outbounds/'+bad['id']+'/preview','POST',{'rowVersion':bad['rowVersion']},expect=400);check('库存不足预览不产生扣库或预留',snapshot()==before)
check('库存在库成本可由成本层核对',sql('SELECT COUNT(*) FROM inv_balance b WHERE b.warehouse_id=995001 AND b.quantity<>(SELECT COALESCE(SUM(remaining_quantity),0) FROM inv_cost_layer c WHERE c.balance_id=b.id)')=='0')

# Warehouse-scoped pending queries and export never reveal other warehouses or costs.
pending_rows=api('/inventory/outbounds/pending')['data']
check('待出库状态与预留情况独立展示',all(r['status'] in ['待出库','部分出库'] and r['reservationStatus'] in ['未预留','部分预留','已预留'] for r in pending_rows))
exported=api('/inventory/outbounds/export',actor='noMoney')['data'];check('导出金额服务端隐藏',all('totalAmount' not in r for r in exported))
check('领用人可查看自己的直接出库且无金额', 'totalAmount' not in api('/inventory/outbounds/'+posted['id'],actor='owner')['data'])
api('/inventory/outbounds/from-requests','POST',{'ids':[r2['id']]},actor='outsider',expect=403);check('合并来源逐申请权限校验',True)
# Legacy reservation API uses the same real outbound service without stock mutations in preview.
rr=api('/inventory/requisitions/'+r2['id'])['data'];rr=api('/inventory/requisitions/'+r2['id']+'/reserve','POST',{'rowVersion':rr['rowVersion']})['data'];selections=[{'reservationId':rr['reservations'][0]['id'],'quantity':'1'}];before=snapshot();lp=api('/inventory/requisitions/'+r2['id']+'/issue-preview','POST',{'rowVersion':rr['rowVersion'],'selections':selections})['data'];check('旧单申请预览不写入库存或单据',before==snapshot());legacy_key=str(uuid.uuid4());api('/inventory/requisitions/'+r2['id']+'/issue-confirm','POST',{'rowVersion':lp['rowVersion'],'selections':selections,'fingerprint':lp['fingerprint'],'confirmationKey':legacy_key});api('/inventory/requisitions/'+r2['id']+'/issue-confirm','POST',{'rowVersion':lp['rowVersion'],'selections':selections,'fingerprint':lp['fingerprint'],'confirmationKey':legacy_key});check('旧确认接口统一出库及幂等',sql(f"SELECT COUNT(*) FROM inv_issue WHERE confirmation_key='{legacy_key}'")=='1')
# Correcting a consolidated issue restores each still-open source, retaining closed source status.
rev=api('/inventory/operations/reversals','POST',{'warehouseId':'995001','sourceKind':'issue','sourceId':mc['id'],'remark':'QA 合并单冲销','lines':[]})['data'];api('/inventory/operations/reversals/'+rev['id']+'/confirm','POST',{'rowVersion':rev['rowVersion']});check('合并整单冲销恢复来源且不重新打开已关闭申请',sql(f"SELECT status FROM inv_issue WHERE id={mc['id']}")=='已冲销' and sql(f"SELECT status FROM inv_requisition WHERE id={r1['id']}")=='已关闭')
# Manual selection and invalid batch/warehouse state.
manual=draft('1','995022');stocks=api('/inventory/outbounds/stock?warehouseId=995001&materialId=995022')['data'];bid=next(b['id'] for b in stocks if b['batchCode']=='QA-BATCH-1');manual['lines'][0]['selections']=[{'balanceId':bid,'quantity':'1'}];manual=api('/inventory/outbounds/'+manual['id'],'PUT',manual)['data'];pp=preview(manual);check('手动批次及库位分配',all(a['batchCode']=='QA-BATCH-1' for a in pp['allocations']));confirm(manual,pp)
sql(f"UPDATE inv_balance SET stock_status='冻结' WHERE id={bid}");frozen=draft('1','995022');api('/inventory/outbounds/'+frozen['id']+'/preview','POST',{'rowVersion':frozen['rowVersion']},expect=400);check('冻结库存不能参与出库',True);sql(f"UPDATE inv_balance SET stock_status='可用' WHERE id={bid}")
sql("UPDATE inv_batch SET expiry_date='2000-01-01' WHERE code='QA-BATCH-1'");api('/inventory/outbounds/'+frozen['id']+'/preview','POST',{'rowVersion':frozen['rowVersion']},expect=400);check('过期批次不能参与出库',True);sql("UPDATE inv_batch SET expiry_date=NULL WHERE code='QA-BATCH-1'");api('/inventory/outbounds/'+frozen['id']+'/preview','POST',{'rowVersion':frozen['rowVersion']},expect=400);check('缺少有效期不能参与出库',True);sql("UPDATE inv_batch SET expiry_date='2030-12-01' WHERE code='QA-BATCH-1'")
ended=draft('1',task=True);sql("UPDATE eam_repair_order SET status='已完成' WHERE id=995501");api('/inventory/outbounds/'+ended['id']+'/preview','POST',{'rowVersion':ended['rowVersion']},expect=400);check('任务结束后禁止关联出库',True);sql("UPDATE eam_repair_order SET status='处理中' WHERE id=995501")
# Separate documents race for a new material; failed document remains a clean draft.
sql("INSERT INTO inv_material(id,tenant_id,code,name,unit) VALUES(995023,10001,'QA-OUT-RACE','QA 并发耗材','件')");receipt(995023,3,0.333333)
x=draft('3','995023');y=draft('3','995023');xp=preview(x);yp=preview(y)
def competing(pair):
    d,p=pair
    try:confirm(d,p);return 'confirmed'
    except AssertionError as e:
        if ': 400 != 200:' in str(e):return 'conflict'
        raise
with concurrent.futures.ThreadPoolExecutor(2) as pool:out=list(pool.map(competing,[(x,xp),(y,yp)]))
check('并发不同单据防超发且失败整体回滚',sorted(out)==['confirmed','conflict'] and sql('SELECT SUM(quantity) FROM inv_balance WHERE material_id=995023')=='0.000000' and sql(f"SELECT COUNT(*) FROM inv_issue WHERE id IN ({x['id']},{y['id']}) AND status='草稿'")=='1')
check('成本层最后一次发料结清尾差',sql('SELECT SUM(remaining_cost) FROM inv_cost_layer c JOIN inv_balance b ON b.id=c.balance_id WHERE b.material_id=995023')=='0.000000')

# Held quantities belonging to another application cannot be used by a direct issue.
held=Decimal(sql("SELECT SUM(reserved_quantity) FROM inv_balance WHERE material_id=995021"));check('其他任务预留仍然保留',held>0)
protected=draft(sql("SELECT SUM(quantity) FROM inv_balance WHERE material_id=995021"));api('/inventory/outbounds/'+protected['id']+'/preview','POST',{'rowVersion':protected['rowVersion']},expect=400);check('直接出库不能占用其他申请预留',True)
# Missing cost prevents issue, and restored cost does not alter the original provenance.
layer=sql("SELECT c.id FROM inv_cost_layer c JOIN inv_balance b ON b.id=c.balance_id WHERE b.material_id=995022 AND remaining_quantity>0 ORDER BY origin_received_at,c.id LIMIT 1");sql(f"UPDATE inv_cost_layer SET cost_status='待确定' WHERE id={layer}");api('/inventory/outbounds/'+frozen['id']+'/preview','POST',{'rowVersion':frozen['rowVersion']},expect=400);check('成本未确定不能出库',True);sql(f"UPDATE inv_cost_layer SET cost_status='已确定' WHERE id={layer}")
# New issued records surface through return choices and authoritative inbox queries.
return_choices=api('/inventory/operations/returns/choices')['data'];check('直接及合并分配都进入退料来源',any(a['issueId']==tc['id'] for a in return_choices['allocations']) is False and any(a['issueId']==manual['id'] for a in return_choices['allocations']))
api('/work-inbox/summary');check('统一待办汇总查询正常',True)

# Historical grouped records remain one visible document, with unchanged allocation identities.
h1=draft('1');h2=draft('1');confirm(h1,preview(h1));confirm(h2,preview(h2));group=995950
sql(f"INSERT INTO inv_issue_group(id,tenant_id,code,warehouse_id,confirmation_key,request_ids,actor_id,actor_name) VALUES({group},10001,'QA-HIST-GROUP',995001,'QA-HIST-KEY',JSON_ARRAY(),995101,'QA warehouse');UPDATE inv_issue SET group_id={group} WHERE id IN ({h1['id']},{h2['id']})")
history=api('/inventory/outbounds/g'+str(group))['data'];check('历史合并组统一查询且保留分配ID',len(history['allocations'])==2 and all(a['id'] for a in history['allocations']))
found=api('/inventory/outbounds?q=QA-HIST-GROUP')['data'];check('历史合并组列表不重复展示子单',len(found)==1 and found[0]['id']=='g'+str(group))
# A bad source in a multi-request operation cannot leak another document or reserve stock.
api('/inventory/outbounds/from-requests','POST',{'ids':[r2['id'],'995000000']},expect=400);check('合并错误来源整体拒绝',True)

role=api('/system/roles','POST',{'code':'QA-OUT-GRANT-'+uuid.uuid4().hex[:6],'name':'QA 出库权限配置','description':'QA','status':1,'menuPermissions':{'inventoryOutbound':{'actions':['查看','新增直接出库','编辑草稿','删除草稿','确认出库'],'dataScopes':['warehouse'],'orgIds':[995001]}}})
check('角色编辑接口支持细分出库权限',bool(role))

# Installation references a task's direct issue and updates installed spares without another deduction.
installed_doc=draft('1','995022',task=True);installed_post=confirm(installed_doc,preview(installed_doc))['data'];source=api('/inventory/task-materials/维修/995501',actor='owner')['data'];a=next(a for a in source['allocations'] if a['issueCode']==installed_doc['code']);before=sql('SELECT SUM(quantity) FROM inv_balance WHERE material_id=995022');api('/inventory/task-materials/维修/995501/use','POST',{'allocationId':a['id'],'equipmentId':source['equipment'][0]['id'],'quantity':'1','confirmationKey':str(uuid.uuid4())},actor='owner');after=api('/inventory/task-materials/维修/995501',actor='owner')['data'];check('直接出库备件安装进入在用备件且不重复扣库',sql('SELECT SUM(quantity) FROM inv_balance WHERE material_id=995022')==before and any(str(x['materialId'])=='995022' for x in after['installed']))
# Long close reasons are persisted outside the action label.
long_req=requisition(995501,1);api('/inventory/requisitions/'+long_req['id']+'/close-remaining','POST',{'rowVersion':long_req['rowVersion'],'reason':'QA长说明'*140},actor='owner');check('关闭剩余完整保留长说明',any(len(e.get('reason') or '')>255 for e in api('/inventory/requisitions/'+long_req['id'],actor='owner')['data']['events']))

full=requisition(995501,1);ff=api('/inventory/outbounds/from-requests','POST',{'ids':[full['id']]})['data'];fd=api('/inventory/outbounds','POST',ff)['data'];confirm(fd,preview(fd));check('全部领料完成后关闭全部仓管待办',sql(f"SELECT status FROM inv_requisition WHERE id={full['id']}")=='已完成' and sql(f"SELECT COUNT(*) FROM inv_inbox WHERE document_id={full['id']} AND is_todo=1 AND done=0")=='0')
lm1=requisition(995501,2);lm2=requisition(995502,3);before=snapshot();lp=api('/inventory/requisitions/merged-preview','POST',{'ids':[lm1['id'],lm2['id']]})['data'];check('旧合并入口预览不产生预留或模拟确认',before==snapshot());lk=str(uuid.uuid4());body={'ids':[lm1['id'],lm2['id']],'fingerprint':lp['preview']['fingerprint'],'confirmationKey':lk};merged=api('/inventory/requisitions/merged-confirm','POST',body)['data'];again=api('/inventory/requisitions/merged-confirm','POST',body)['data'];check('旧合并入口使用统一单据且幂等',merged['outboundId']==again['outboundId'] and sql(f"SELECT COUNT(*) FROM inv_issue WHERE confirmation_key='{lk}'")=='1')

sql("INSERT INTO inv_material(id,tenant_id,code,name,unit) VALUES(995024,10001,'QA-OUT-ROUND','QA 多次尾差耗材','件')");receipt(995024,3,0.333333);amounts=[]
for _ in range(3):
  d=draft('1','995024');amounts.append(Decimal(str(confirm(d,preview(d))['data']['totalAmount'])))
check('多次部分发料金额尾差归入最后一次',amounts==[Decimal('0.33'),Decimal('0.33'),Decimal('0.34')])

finance=api('/inventory/requisitions/'+r2['id'],actor='finance')['data'];check('原领料金额查询权限保留历史读取',any('amount' in a for issue in finance['issues'] for a in issue['allocations']));api('/inventory/outbounds/'+mc['id'],actor='finance',expect=403);check('历史金额查询不扩大为新出库单权限',True)

return_data=api('/inventory/operations/returns/choices')['data'];check('退料候选提供后台定点计算的已发未用数量',all(Decimal(a['availableQuantity'])==Decimal(a['quantity'])-Decimal(a['usedQuantity'])-Decimal(a['returnedQuantity']) for a in return_data['allocations']))

amount_conditions=json.dumps([{'field':'totalQuantity','operator':'range','values':['2','3']}]);filtered=api('/inventory/outbounds?q='+posted['code']+'&conditions='+urllib.parse.quote(amount_conditions))['data'];check('出库数量支持数值范围筛选',len(filtered)==1)
second=posted['createdAt'].replace('T',' ')[:19];date_conditions=json.dumps([{'field':'createdAt','operator':'range','values':[second,second]}]);filtered=api('/inventory/outbounds?q='+posted['code']+'&conditions='+urllib.parse.quote(date_conditions))['data'];check('日期范围按显示秒包含完整边界且无时区偏移',len(filtered)==1)
(ROOT/'artifacts/qa/outbounds/api-results.json').write_text(json.dumps({'database':DB,'passed':REPORT},ensure_ascii=False,indent=2))
print('ALL PASS',len(REPORT))
