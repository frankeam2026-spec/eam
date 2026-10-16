#!/usr/bin/env python3
"""Analytics acceptance fixtures: writes exclusively to the dedicated analytics test database."""
import pathlib,subprocess,json,secrets,hashlib,urllib.request,urllib.error,urllib.parse
R=pathlib.Path(__file__).resolve().parents[3]/'.local-runtime'
DB='eam_home_analytics_test_20261015'; assert DB=='eam_home_analytics_test_20261015'
BASE='http://127.0.0.1:8084/api/v1'
M=[str(R/'mysql-8.4.6-macos15-arm64/bin/mysql'),'--defaults-extra-file='+str(R/'root.cnf'),'--socket='+str(R/'mysql.sock'),'-N','--batch',DB]
def sql(s):
 r=subprocess.run(M,input=s,text=True,capture_output=True);assert r.returncode==0,r.stderr;return r.stdout.strip()
def lit(v):
 if v is None:return 'NULL'
 if isinstance(v,(int,float)):return str(v)
 if isinstance(v,(dict,list)):v=json.dumps(v,ensure_ascii=False)
 return "'"+str(v).replace('\\','\\\\').replace("'","''")+"'"
def insert(t,**v):sql('INSERT INTO '+t+' ('+','.join('`'+k+'`' for k in v)+') VALUES ('+','.join(map(lit,v.values()))+')')
def clone(t,**v):
 cols=sql("SELECT COLUMN_NAME FROM information_schema.columns WHERE TABLE_SCHEMA='"+DB+"' AND TABLE_NAME='"+t+"' AND GENERATION_EXPRESSION='' ORDER BY ORDINAL_POSITION").splitlines()
 sql('INSERT INTO '+t+' ('+','.join('`'+c+'`' for c in cols)+') SELECT '+','.join(lit(v[c]) if c in v else '`'+c+'`' for c in cols)+' FROM '+t+' WHERE id NOT BETWEEN 99510000 AND 99519999 ORDER BY id LIMIT 1')
def session(uid,key):
 token=secrets.token_urlsafe(36);insert('eam_auth_session',id=key,tenant_id=10001,user_id=uid,token_hash=hashlib.sha256(token.encode()).hexdigest(),expires_at='2026-12-31 23:59:59');return token
checks=[]
def check(name,ok=True):assert ok,name;checks.append(name);print('PASS',name)
sql('DELETE FROM eam_equipment_history WHERE equipment_id BETWEEN 99510000 AND 99519999')
sql('DELETE FROM eam_repair_event WHERE order_id BETWEEN 99510000 AND 99519999')
sql('DELETE FROM eam_inspection_event WHERE task_id BETWEEN 99510000 AND 99519999')
sql('DELETE FROM inv_usage WHERE allocation_id BETWEEN 99510000 AND 99519999')
# Delete only this script's fixtures in this dedicated DB; no demo DB mutations.
for t in ['eam_auth_session','eam_repair_event','eam_maintenance_event','eam_inspection_event','inv_usage','inv_issue_allocation','inv_cost_layer','inv_outbound_line','inv_issue','inv_balance','inv_material','inv_warehouse','eam_repair_order','eam_maintenance_task','eam_inspection_task','eam_equipment_anomaly','eam_equipment','eam_base_item','eam_sys_role_menu_permission']:
 sql(f'DELETE FROM {t} WHERE id BETWEEN 99510000 AND 99519999')
sql('DELETE FROM eam_home_overview_layout WHERE user_id BETWEEN 99510000 AND 99519999')
sql('DELETE FROM eam_sys_user_role WHERE user_id BETWEEN 99510000 AND 99519999')
for t in ['eam_sys_user','eam_sys_role']:sql(f'DELETE FROM {t} WHERE id BETWEEN 99510000 AND 99519999')
sql('DELETE FROM eam_org_unit WHERE id IN (99510003,99510004)');sql('DELETE FROM eam_org_unit WHERE id IN (99510001,99510002)')
companyA,companyB,deptA,deptB=99510001,99510002,99510003,99510004
for n in [1,2]:
 clone('eam_org_unit',id=99510000+n,code='QA-HOME-C'+str(n),name='分析测试公司'+str(n),org_type='公司',parent_id=int(sql("SELECT id FROM eam_org_unit WHERE org_type='公司' AND id<99510000 LIMIT 1")))
 clone('eam_org_unit',id=99510002+n,code='QA-HOME-D'+str(n),name='分析测试部门'+str(n),org_type='部门',parent_id=99510000+n)
owner,collab,limited,outsider=99510011,99510012,99510013,99510014
for uid,dep in [(owner,deptA),(collab,deptA),(limited,deptA),(outsider,deptB)]:
 clone('eam_sys_user',id=uid,account='qa-analytics-'+str(uid),display_name='分析测试用户'+str(uid),org_id=dep,deleted=0,status=1)
 role=uid+10
 clone('eam_sys_role',id=role,code='QA-ANALYTICS-'+str(role),name='分析测试角色',data_scope='PER_MENU',deleted=0,status=1)
 insert('eam_sys_user_role',tenant_id=10001,user_id=uid,role_id=role)
 for i,menu in enumerate(['workspace','repair','maintenanceTask','inspectionDailyTask','inspectionPeriodicTask','anomaly','equipment','inventoryQuery','homeTemplate']):
  actions=['查看','导出']
  if menu=='workspace':actions+=['团队工作查看']
  if menu=='inventoryQuery' and uid!=limited:actions+=['查看金额']
  if menu=='homeTemplate':actions+=['新增','编辑','发布']
  insert('eam_sys_role_menu_permission',id=99511000+(uid-99510011)*100+i,tenant_id=10001,role_id=role,menu_key=menu,actions_json=actions,data_scopes_json=['company'],org_ids_json=[])
TOKENS={u:session(u,99510100+i) for i,u in enumerate([owner,collab,limited,outsider])};TOKEN=TOKENS[owner]
def api(path,body=None,method=None,token=None,expected=200,raw=False):
 req=urllib.request.Request(BASE+path,data=json.dumps(body,ensure_ascii=False).encode() if body is not None else None,method=method or ('POST' if body is not None else 'GET'),headers={'Cookie':'EAM_SESSION='+(token or TOKEN),'Content-Type':'application/json','Origin':'http://127.0.0.1:8084'})
 try:
  with urllib.request.urlopen(req) as r:status=r.status;data=r.read()
 except urllib.error.HTTPError as e:status=e.code;data=e.read()
 assert status==expected,(path,status,data[:1000])
 return data if raw else json.loads(data)
eqA,eqB=99510031,99510032
for eq,c,d in [(eqA,companyA,deptA),(eqB,companyB,deptB)]:clone('eam_equipment',id=eq,equipment_code='QA-EQ-'+str(eq),name='分析设备'+str(eq),company_org_id=c,department_org_id=d,owner_user_id=owner if c==companyA else outsider,deleted=0)
for n,typ in [(1,'FAULT_TYPE'),(2,'FAULT_CAUSE')]:insert('eam_base_item',id=99510040+n,tenant_id=10001,item_type=typ,code='QA-ANALYTICS-'+str(n),name='分析分类'+str(n),parent_id=None,status=1,sort_no=1)
for n,status,report,accepted in [(1,'已完成','2026-04-01','2026-04-30 18:00:00'),(2,'已完成','2026-04-30','2026-05-05 08:00:00'),(3,'处理中','2026-04-15',None),(4,'已取消','2026-04-15',None),(5,'已完成','2026-04-15','2026-04-16'),(6,'已完成','2026-04-10','2026-04-20'),(7,'已完成','2026-04-10',None)]:
 insert('eam_repair_order',id=99510200+n,tenant_id=10001,order_no='QA-WX-'+str(n),equipment_id=eqA,equipment_code='QA-EQ-'+str(eqA),equipment_name='分析设备',company_org_id=companyA,department_org_id=deptA,department='分析部门',fault_type_id=99510041,fault_type='分析分类1',fault_cause_id=99510042 if n!=6 else None,fault_cause='分析分类2' if n!=6 else '历史歧义文本',severity='一般',description='分析测试',reporter='分析报修人',reporter_user_id=owner,assignee_user_id=owner,assignee='分析负责人',status=status,reported_at=report+' 10:00:00',accepted_at=accepted,accepted_owner_id=owner if accepted else None,acceptance_score=(3 if n==6 else 5) if accepted else None,completed_at='2026-04-01 12:00:00' if n==1 else None,collaborators_json=[{'id':str(collab),'name':'分析协同人'}] if n==1 else [],deleted=1 if n==5 else 0)
insert('eam_repair_order',id=99510220,tenant_id=10001,order_no='QA-WX-OTHER',equipment_id=eqB,equipment_code='QA-EQ-'+str(eqB),equipment_name='无权设备',company_org_id=companyB,department_org_id=deptB,fault_type='其他',description='其他公司',reporter='其他',reported_at='2026-04-02',status='处理中',assignee_user_id=outsider)
# Maintenance task cohort: April planned, May deadlines and acceptance.
tasks=[('已完成','2026-05-06','2026-05-06 10:00:00','2026-05-08 10:00:00'),('已完成','2026-05-03','2026-05-05 10:00:00','2026-05-06 10:00:00'),('执行中','2026-05-03','2026-05-02 10:00:00',None),('已作废','2026-05-03',None,None),('过期作废','2026-05-03',None,None),('待执行','2026-12-20',None,None),('待验收','2026-05-05','2026-05-04 10:00:00',None)]
for i,(status,expiry,submit,accept) in enumerate(tasks):
 tid=99510300+i
 insert('eam_maintenance_task',id=tid,tenant_id=10001,code='QA-BY-'+str(i),plan_id=99510390,schedule_id=tid,equipment_id=eqA,company_id=companyA,department_id=deptA,due_date='2026-04-30',expires_date=expiry,owner_id=owner,status=status,snapshot={'equipmentName':'分析设备','owners':[str(owner)]})
 if submit:insert('eam_maintenance_event',id=99510400+i*3,tenant_id=10001,task_id=tid,actor_id=owner,actor_name='分析负责人',action='提交保养',details={},created_at=submit)
 if accept:insert('eam_maintenance_event',id=99510401+i*3,tenant_id=10001,task_id=tid,actor_id=owner,actor_name='分析负责人',action='验收通过',details={},created_at=accept)
 if i==2:insert('eam_maintenance_event',id=99510402+i*3,tenant_id=10001,task_id=tid,actor_id=owner,actor_name='分析负责人',action='验收打回',details={},created_at='2026-05-03')
for i,kind in enumerate(['daily','periodic']):
 tid=99510500+i
 insert('eam_inspection_task',id=tid,tenant_id=10001,kind=kind,code='QA-XJ-'+kind,plan_id=tid,equipment_id=eqA,company_id=companyA,department_id=deptA,owner_id=owner,due_at='2026-04-30 08:00:00',expires_at='2026-05-06 23:59:59',status='已审核',snapshot={'owners':[str(owner)]})
 for j,(action,date) in enumerate([('提交检查结果','2026-05-06 10:00:00'),('审核通过','2026-05-08 10:00:00')]):insert('eam_inspection_event',id=99510510+i*2+j,tenant_id=10001,task_id=tid,actor_id=owner,actor_name='测试',action=action,details={},created_at=date)
for i,status in enumerate(['待处理','已消除','已作废']):clone('eam_equipment_anomaly',id=99510600+i,code='QA-YC-'+str(i),equipment_id=eqA,company_org_id=companyA,department_org_id=deptA,status=status,discovered_at='2026-04-15 12:00:00',discovered_by=owner,source_kind='人工',source_task_id=None,source_item_id=None)
warehouse=99510700
clone('inv_warehouse',id=warehouse,code='QA-ANALYTICS-WH',name='分析测试仓库',company_id=companyA)
issue,allocation=99510710,99510720
clone('inv_issue',id=issue,code='QA-CK-CONSUMED',company_id=companyA,warehouse_id=warehouse,requisition_id=None,confirmation_key='qa-analytics-issue',reversed=0,deleted=0,total_amount=100)
clone('inv_issue_allocation',id=allocation,issue_id=issue,quantity=20,used_quantity=6,returned_quantity=0,returned_amount=0,amount=100,requisition_line_id=None,outbound_line_id=None)
material=int(sql('SELECT material_id FROM inv_usage ORDER BY id LIMIT 1'))
for i,(cost,currency,attribution,eq,status) in enumerate([(3.34,'CNY','equipment',eqA,'已换下'),(3.33,'CNY','equipment',eqA,'已使用'),(2,'CNY','shared',None,'已使用'),(1,'CNY','legacy_unassigned',None,'已使用'),(6.12,'USD','equipment',eqA,'已使用'),(None,'CNY','equipment',eqA,'已使用')]):
 insert('inv_usage',id=99510730+i,tenant_id=10001,allocation_id=allocation,material_id=material,quantity=1,kind='安装' if i==0 else '使用',confirmation_key='qa-analytics-usage-'+str(i),actor_id=owner,actor_name='测试用料',attribution=attribution,equipment_id=eq,status=status,cost_amount=cost,currency=currency,created_at='2026-04-15 10:00:00')
clone('inv_issue',id=99510711,code='QA-CK-NOTUSED',company_id=companyA,warehouse_id=warehouse,requisition_id=None,confirmation_key='qa-analytics-not-used',reversed=0,deleted=0,total_amount=999)
CONTEXT={'period':{'preset':'custom','from':'2026-04-01','to':'2026-04-30'},'companyIds':[str(companyA)],'asOf':'2026-05-10T12:00:00'}
def widget(metric,**v):return {'id':'qa-'+metric,'metric':metric,'scope':'team','period':{'preset':'follow'},'topN':10,**v}
def query(metric,context=None,token=None,**v):return api('/home/analytics/query',{'widgets':[widget(metric,**v)],'context':context or CONTEXT},token=token)['data'][0]
def details(metric,filter=None,**v):return api('/home/analytics/details',{'widget':widget(metric,**v),'context':CONTEXT,'filter':filter or {},'page':1,'size':100})
cat=api('/home/analytics/catalog');check('12 metric registry',len(cat['data'])==12)
allrows=api('/home/analytics/query',{'widgets':[widget(m['id']) for m in cat['data']],'context':CONTEXT})['data'];check('12 metrics query without SQL errors',all(x['status'] in ['ok','empty','data_missing'] for x in allrows))
rank=query('repair_ranking');check('report once, excludes cancelled/deleted',sum(x['value'] for x in rank['rows'])==5)
check('summary/detail count consistent',details('repair_ranking')['total']==5)
trend=query('repair_trend');check('completion uses acceptance not submitted time',trend['rows'][0]['reported']==5 and trend['rows'][0]['completed']==2)
check('month point drill completion cohort',details('repair_trend',{'key':'2026-04','series':'completed'})['total']==2)
check('fault cause missing mapping retained',any(r['key']=='__unclassified__' for r in query('fault_causes')['rows']))
person=query('personnel')['rows'];p=next(r for r in person if r['key']==str(owner));c=next(r for r in person if r['key']==str(collab));check('personnel completion, unfinished, scores/sample',p['completed']==2 and p['unfinished']==1 and p['score']==4 and p['scoreCount']==2)
check('collaborator counted separately',c['completed']==0 and c['collaboration']==1)
check('collaboration drill matches',details('personnel',{'key':str(collab),'series':'collaboration'})['total']==1)
pm=query('maintenance_execution')['summary'];check('April plan remains April despite May deadline/acceptance',pm['generated']==7 and pm['total']==6 and pm['completed']==2)
check('return invalidates old submit; human/system cancellation distinct',pm['onTime']==2 and pm['evaluable']==5 and pm['cancelled']==1 and pm['overdue']==3)
check('on time submission excludes acceptance waiting',pm['onTimeRate']==40 and pm['completedOnTime']==1 and pm['completedLate']==1)
check('maintenance drill matches summary',details('maintenance_execution',{'series':'completed'})['total']==2 and details('maintenance_execution',{'series':'onTime'})['total']==2)
inspection=query('inspection_execution');check('daily/periodic separate with crossmonth cohort',len(inspection['rows'])==2 and all(r['completed']==1 and r['onTimeRate']==100 for r in inspection['rows']))
check('inspection dimension drill',details('inspection_execution',{'key':'daily','series':'completed'})['total']==1)
check('anomaly excludes invalid but retains resolved',query('anomaly_ranking')['rows'][0]['value']==2)
cost=query('material_trend',currency='CNY');check('usage facts only, installation once, removed retained, no issue cost',round(cost['summary']['totalAmount'],2)==9.67)
check('currencies not converted or mixed',round(query('material_trend',currency='USD')['summary']['totalAmount'],2)==6.12)
mr=query('equipment_material_ranking',currency='CNY');check('device/shared/unassigned reconcile',round(sum(x['value'] for x in mr['rows'])+mr['summary']['shared']['amount']+mr['summary']['unassigned']['amount'],2)==9.67)
check('missing cost disclosed',cost['quality']['missingCost']==1)
check('shared drill',details('equipment_material_ranking',{'key':'__shared__'},currency='CNY')['total']==1)
check('details server pagination',len(api('/home/analytics/details',{'widget':widget('repair_ranking'),'context':CONTEXT,'page':1,'size':2})['data'])==2)
check('no amount permission hides catalog',not any(m['money'] for m in api('/home/analytics/catalog',token=TOKENS[limited])['data']))
check('forged amount query forbidden',query('material_trend',token=TOKENS[limited])['status']=='forbidden')
api('/home/analytics/details',{'widget':widget('material_trend'),'context':CONTEXT},token=TOKENS[limited],expected=403);check('amount detail denied')
api('/home/analytics/export',{'widget':widget('material_trend'),'context':CONTEXT},token=TOKENS[limited],expected=403);check('amount export denied')
check('crosscompany scope refused',query('repair_ranking',token=TOKENS[outsider])['status']=='forbidden')
other={**CONTEXT,'companyIds':[str(companyB)]};check('crosscompany inventory/equipment scope refused',query('equipment_material_ranking',context=other)['status']=='forbidden')
csv=api('/home/analytics/export',{'widget':widget('repair_ranking'),'context':CONTEXT},raw=True).decode('utf-8-sig');check('export shares cohort',len(csv.strip().splitlines())==6)
api('/home/analytics/query',{'widgets':[widget('sql')],'context':CONTEXT},expected=400);check('unknown metric rejected')
api('/home/analytics/query',{'widgets':[widget('repair_ranking')]*13,'context':CONTEXT},expected=400);check('batch limited to 12')
# Personal layout and independent role template config preserve existing home layout.
h=api('/home/overview?templateId=0');old=api('/home?templateId=0')['layout']
layout={'layoutMode':'analysis','period':{'preset':'month'},'metrics':[],'blocks':[{'id':'qa-repeat-1','type':'analytics:repair_ranking','title':'本月报修','column':'main','width':12,'limit':5,'scope':'team','topN':5,'period':{'preset':'follow'}},{'id':'qa-repeat-2','type':'analytics:repair_ranking','title':'90天报修','column':'side','width':12,'limit':5,'scope':'team','topN':10,'period':{'preset':'last90'}}]}
body={'templateId':'0','templateVersion':h['templateVersion'],'rowVersion':h['rowVersion'],'layout':layout}
saved=api('/home/overview',body,'PUT');check('same analytics type can repeat by block ID',len(saved['layout']['blocks'])==2)
api('/home/overview',body,'PUT',expected=409);check('old personal version conflict')
check('original home unchanged',api('/home?templateId=0')['layout']==old)
t=api('/home-templates',{'name':'QA 独立首页2模板','roleIds':[str(owner+10)],'priority':1,'draft':{'blocks':[{'id':'old-work','type':'todo','title':'原首页待办','width':12,'limit':5,'scope':'personal'}]},'overviewDraft':layout})['data']
t=api('/home-templates/'+t['id']+'/publish',{'rowVersion':t['row_version']})['data'];check('independent overview role template published',api('/home/overview?templateId='+t['id'])['defaultLayout']['layoutMode']=='analysis')
check('role original home retained',api('/home?templateId='+t['id'])['layout']['blocks'][0]['type']=='todo')
# Actual usage registration captures allocation cents, immutable names and attribution.
newmaterial,balance,layer,line,alloc=99510800,99510801,99510802,99510803,99510804
clone('inv_material',id=newmaterial,code='QA-ANALYTICS-USAGE',name='成本尾差测试物料',usage_type='耗材',deleted=0)
clone('inv_balance',id=balance,company_id=companyA,warehouse_id=warehouse,material_id=newmaterial,batch_id=0,quantity=50,reserved_quantity=0)
clone('inv_cost_layer',id=layer,balance_id=balance,quantity=50,remaining_quantity=50,currency='CNY')
insert('inv_outbound_line',id=line,tenant_id=10001,issue_id=issue,line_no=91,material_id=newmaterial,quantity=3,task_kind='维修',task_id=99510203,department_id=deptA,recipient_id=owner)
clone('inv_issue_allocation',id=alloc,issue_id=issue,balance_id=balance,cost_layer_id=layer,requisition_line_id=None,outbound_line_id=line,quantity=3,amount=10,unit_cost=3.333333,used_quantity=0,returned_quantity=0,returned_amount=0)
for i in range(3):
 result=api('/inventory/task-materials/'+urllib.parse.quote('维修')+'/99510203/use',{'allocationId':str(alloc),'quantity':'1','confirmationKey':'qa-analytics-write-'+str(i)})
 if i==0:
  api('/inventory/task-materials/'+urllib.parse.quote('维修')+'/99510203/use',{'allocationId':str(alloc),'quantity':'1','confirmationKey':'qa-analytics-write-0'})
  check('usage idempotency keeps one fact',sql(f'SELECT COUNT(*) FROM inv_usage WHERE allocation_id={alloc}')=='1')
check('usage final cent reconciles to posted allocation',sql(f'SELECT SUM(cost_amount) FROM inv_usage WHERE allocation_id={alloc}')=='10.00')
check('single equipment auto attribution captured',sql(f"SELECT COUNT(*) FROM inv_usage WHERE allocation_id={alloc} AND equipment_id={eqA} AND attribution='equipment' AND equipment_name_snapshot IS NOT NULL AND company_id_snapshot={companyA}")=='3')
check('task usage response hides analytics cost snapshots',all('costAmount' not in u and 'currency' not in u for u in result['data']['usage']))
api('/inventory/task-materials/'+urllib.parse.quote('维修')+'/99510203/use',{'allocationId':str(alloc),'quantity':'1','equipmentId':str(eqB),'confirmationKey':'qa-analytics-cross-equip'},expected=400);check('usage cannot attribute another task device')
# Explicit shared use is accepted without selecting an arbitrary task device.
insert('inv_outbound_line',id=line+10,tenant_id=10001,issue_id=issue,line_no=92,material_id=newmaterial,quantity=1,task_kind='维修',task_id=99510203,department_id=deptA,recipient_id=owner)
clone('inv_issue_allocation',id=alloc+10,issue_id=issue,balance_id=balance,cost_layer_id=layer,requisition_line_id=None,outbound_line_id=line+10,quantity=1,amount=2,unit_cost=2,used_quantity=0,returned_quantity=0,returned_amount=0)
api('/inventory/task-materials/'+urllib.parse.quote('维修')+'/99510203/use',{'allocationId':str(alloc+10),'quantity':'1','attribution':'shared','confirmationKey':'qa-analytics-explicit-shared'})
check('single-device use always auto attributed',sql(f"SELECT COUNT(*) FROM inv_usage WHERE allocation_id={alloc+10} AND equipment_id={eqA} AND attribution='equipment'")=='1')

# Multi-equipment usage must be assigned explicitly, split by quantity, or marked common.
eqA2=99510033
clone('eam_equipment',id=eqA2,equipment_code='QA-EQ-MULTI',name='分析设备二',company_org_id=companyA,department_org_id=deptA,owner_user_id=owner,deleted=0)
multi=99510502
insert('eam_inspection_task',id=multi,tenant_id=10001,kind='periodic',code='QA-XJ-MULTI',plan_id=multi,equipment_id=0,company_id=companyA,department_id=deptA,owner_id=owner,due_at='2026-10-15 08:00:00',expires_at='2026-12-20 23:59:59',status='执行中',snapshot={'owners':[str(owner)],'reviewers':[],'items':[],'equipment':[{'id':str(eqA),'name':'分析设备一'},{'id':str(eqA2),'name':'分析设备二'}]})
insert('inv_outbound_line',id=line+20,tenant_id=10001,issue_id=issue,line_no=93,material_id=newmaterial,quantity=2,task_kind='点巡检',task_id=multi,department_id=deptA,recipient_id=owner)
clone('inv_issue_allocation',id=alloc+20,issue_id=issue,balance_id=balance,cost_layer_id=layer,requisition_line_id=None,outbound_line_id=line+20,quantity=2,amount=4,unit_cost=2,used_quantity=0,returned_quantity=0,returned_amount=0)
path='/inventory/task-materials/'+urllib.parse.quote('点巡检')+'/'+str(multi)+'/use'
api(path,{'allocationId':str(alloc+20),'quantity':'1','confirmationKey':'qa-analytics-multi-no-target'},expected=400);check('multi-device does not guess attribution')
api(path,{'allocationId':str(alloc+20),'quantity':'1','attribution':'shared','confirmationKey':'qa-analytics-multi-common'})
api(path,{'allocationId':str(alloc+20),'quantity':'0.6','equipmentId':str(eqA),'confirmationKey':'qa-analytics-multi-device-1'})
api(path,{'allocationId':str(alloc+20),'quantity':'0.4','equipmentId':str(eqA2),'confirmationKey':'qa-analytics-multi-device-2'})
check('multi-device common and split usage reconcile',sql(f"SELECT COUNT(*),SUM(cost_amount) FROM inv_usage WHERE allocation_id={alloc+20}")=='3\t4.00')
check('explicit common usage remains common',sql(f"SELECT COUNT(*) FROM inv_usage WHERE allocation_id={alloc+20} AND equipment_id IS NULL AND attribution='shared'")=='1')

# Fresh installation/removal remains one usage fact and does not deduct stock twice.
installmat,installbalance,installline,installalloc=99510830,99510831,99510832,99510833
clone('inv_material',id=installmat,code='QA-ANALYTICS-INSTALL',name='安装统计测试物料',usage_type='可安装',deleted=0)
clone('inv_balance',id=installbalance,company_id=companyA,warehouse_id=warehouse,material_id=installmat,batch_id=0,quantity=50,reserved_quantity=0)
insert('inv_outbound_line',id=installline,tenant_id=10001,issue_id=issue,line_no=94,material_id=installmat,quantity=1,task_kind='维修',task_id=99510203,department_id=deptA,recipient_id=owner)
clone('inv_issue_allocation',id=installalloc,issue_id=issue,balance_id=installbalance,cost_layer_id=layer,requisition_line_id=None,outbound_line_id=installline,quantity=1,amount=4,unit_cost=4,used_quantity=0,returned_quantity=0,returned_amount=0)
api('/inventory/task-materials/'+urllib.parse.quote('维修')+'/99510203/use',{'allocationId':str(installalloc),'quantity':'1','confirmationKey':'qa-analytics-install-once'})
usage=sql(f'SELECT id FROM inv_usage WHERE allocation_id={installalloc}')
check('new installation records one usage fact',sql(f'SELECT COUNT(*),SUM(cost_amount) FROM inv_usage WHERE allocation_id={installalloc}')=='1\t4.00')
api('/inventory/task-materials/'+urllib.parse.quote('维修')+'/99510203/remove',{'usageId':usage,'destination':'测试换下回收'})
check('removal retains historical consumption amount',sql(f"SELECT status,cost_amount FROM inv_usage WHERE allocation_id={installalloc}")=='已换下\t4.00')
check('using or installing does not deduct stock again',sql(f'SELECT quantity FROM inv_balance WHERE id={balance}')=='50.000000' and sql(f'SELECT quantity FROM inv_balance WHERE id={installbalance}')=='50.000000')
# Top-N overflow drill uses the same excluded groups, not a separate approximation.
for i in range(8):
 insert('eam_base_item',id=99510050+i,tenant_id=10001,item_type='FAULT_TYPE',code='QA-EXTRA-TYPE-'+str(i),name='分布类型'+str(i),status=1,sort_no=i)
 clone('eam_repair_order',id=99510240+i,order_no='QA-DIST-'+str(i),equipment_id=eqA,equipment_code='QA-EQ-'+str(eqA),equipment_name='分析设备',company_org_id=companyA,department_org_id=deptA,fault_type_id=99510050+i,fault_type='分布类型'+str(i),status='待派工',reported_at='2026-04-30 23:59:59.999',accepted_at=None,deleted=0)
distribution=query('fault_types',topN=5);other=next(r for r in distribution['rows'] if r['key']=='__other__')
check('classification Top-N overflow grouped as Other',other['value']==4 and len(distribution['rows'])==6)
check('Other drill matches summary exactly',details('fault_types',{'key':'__other__'},topN=5)['total']==other['value'])
check('date end day includes last millisecond',query('repair_ranking')['rows'][0]['value']==13)

sql("UPDATE eam_sys_role_menu_permission SET actions_json=JSON_ARRAY('查看') WHERE role_id=99510023 AND menu_key='workspace'")
check('organization statistics require explicit team action',query('repair_ranking',token=TOKENS[limited])['status']=='forbidden')
check('personal statistics remain accessible without team action',query('repair_ranking',token=TOKENS[limited],scope='personal')['status']=='empty')
check('detail IDs preserve full MySQL bigint precision',isinstance(details('repair_ranking')['data'][0]['source_id'],str))
print('TOTAL',len(checks),'checks passed')
(pathlib.Path(__file__).parent/'last-result.json').write_text(json.dumps({'database':DB,'checks':checks},ensure_ascii=False,indent=2))
