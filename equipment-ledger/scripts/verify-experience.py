#!/usr/bin/env python3
"""Writes ONLY the dedicated experience QA database; never uses eam_demo."""
import pathlib,json,subprocess,secrets,hashlib,urllib.request,urllib.error,concurrent.futures,datetime
R=pathlib.Path(__file__).resolve().parents[2]/'.local-runtime'
DB='eam_experience_test_20261013';assert DB=='eam_experience_test_20261013'
BASE='http://127.0.0.1:8081/api/v1'
def sql(s):
 return subprocess.run([str(R/'mysql-8.4.6-macos15-arm64/bin/mysql'),'--defaults-extra-file='+str(R/'root.cnf'),'--socket='+str(R/'mysql.sock'),DB],input=s,text=True,capture_output=True,check=True).stdout.strip()
def session(uid,key):
 t=secrets.token_urlsafe(36);sql(f"INSERT INTO eam_auth_session(id,tenant_id,user_id,token_hash,expires_at) VALUES({key},10001,{uid},'{hashlib.sha256(t.encode()).hexdigest()}',DATE_ADD(NOW(),INTERVAL 1 DAY)) ON DUPLICATE KEY UPDATE token_hash=VALUES(token_hash),expires_at=VALUES(expires_at)");return t
sql("DELETE FROM eam_experience_page_preference; DELETE FROM eam_home_layout; DELETE FROM eam_home_template_version; DELETE FROM eam_home_template; DELETE FROM eam_home_preference; DELETE FROM eam_equipment_anomaly; DELETE FROM eam_single_repair_plan; DELETE FROM eam_experience_event;")
sql("INSERT IGNORE INTO eam_base_item(id,tenant_id,item_type,code,name,status,sort_no) VALUES(98901001,10001,'FAULT_TYPE','QA-FAULT','QA 机械故障',1,1),(98901002,10001,'FAULT_CAUSE','QA-CAUSE','QA 磨损',1,1);")
TOKEN=session(73001,98900001)
def api(path,method='GET',body=None,status=200,token=None):
 req=urllib.request.Request(BASE+path,data=json.dumps(body,ensure_ascii=False).encode() if body is not None else None,method=method,headers={'Cookie':'EAM_SESSION='+(token or TOKEN),'Content-Type':'application/json','Origin':'http://127.0.0.1:8081'})
 try:
  with urllib.request.urlopen(req) as r:actual=r.status;data=json.load(r)
 except urllib.error.HTTPError as e:actual=e.code;data=json.loads(e.read())
 assert actual==status,(path,actual,data)
 return data
checks=[]
def check(n,ok=True):assert ok,n;checks.append(n);print('PASS',n)
h=api('/home');check('管理员采用配置维护首页',h['selected']['name']=='系统维护')
ts=api('/home-templates')['data'];check('原有六类预置模板保留',{'设备使用与报修','运维执行','设备主管','仓库工作','公司与集团管理','系统维护'}.issubset({t['name'] for t in ts}))
t=next(x for x in ts if x['name']=='运维执行');initial_version=t['published_version'];t.update(name='QA 个人工作首页',roleIds=['72001'],priority=1,rowVersion=t['row_version'])
t=api('/home-templates/'+t['id'],'PUT',t)['data'];t=api('/home-templates/'+t['id']+'/publish','POST',{'rowVersion':t['row_version']})['data'];check('模板发布版本',t['published_version']==initial_version+1)
h=api('/home?templateId='+t['id']);payload={'templateId':t['id'],'templateVersion':h['templateVersion'],'rowVersion':h['rowVersion'],'layout':h['layout']}
saved=api('/home/layout','PUT',payload);check('个人布局保存',saved['rowVersion']==0)
api('/home/layout','PUT',payload,409);check('重复旧版本布局拒绝')
payload['rowVersion']=0
with concurrent.futures.ThreadPoolExecutor(2) as pool:
 def race():
  try:api('/home/layout','PUT',payload);return 200
  except AssertionError as e:return 409 if '409' in str(e) else str(e)
 result=list(pool.map(lambda _:race(),range(2)))
check('双窗口保存只有一个成功',sorted(result)==[200,409])
t=api('/home-templates/'+t['id']+'/publish','POST',{'rowVersion':t['row_version']})['data'];h=api('/home?templateId='+t['id']);check('新版保留个人布局并提示',h['updateAvailable'] and h['templateVersion']==initial_version+1)
api('/home/reset','POST',{'templateId':t['id']});check('恢复新版模板',api('/home?templateId='+t['id'])['templateVersion']==initial_version+2)
for typ in ['todo','today','active','created','shortcuts','recent','stats','inventory','surplus','anomalies','load']:
 result=api('/home/blocks','POST',{'id':'qa-'+typ,'type':typ,'title':'QA '+typ,'width':6,'limit':5,'scope':'team' if typ=='load' else 'personal','module':''});check('区块 '+typ,isinstance(result,dict))
api('/home/blocks','POST',{'id':'bad','type':'sql','title':'QA','width':6,'limit':5},400);check('未知区块拒绝')
api('/home/recent','POST',{'url':'/equipment'});check('最近访问授权入口',api('/home/blocks','POST',{'id':'recent','type':'recent','title':'QA','width':6,'limit':5})['total']==1)
api('/home/recent','POST',{'url':'https://example.com'},400);check('最近访问禁止任意地址')
a=api('/anomalies','POST',{'equipmentId':'9001','description':'QA 异常闭环','severity':'一般'})['data'];check('登记异常',a['status']=='待处理')
a=api('/anomalies/'+a['id']+'/actions','POST',{'action':'resolve','rowVersion':a['rowVersion'],'reason':'QA 现场调整完成'})['data'];check('直接消除',a['status']=='已消除')
api('/anomalies/'+a['id']+'/actions','POST',{'action':'resolve','rowVersion':0,'reason':'QA'},409);check('异常旧版本拒绝')
fault=api('/base-data/fault-types')['data'][0]['name'];cause=api('/base-data/fault-causes')['data'][0]['name']
a=api('/anomalies','POST',{'equipmentId':'9001','description':'QA 计划维修异常','severity':'一般'})['data']
plan=api('/repair-plans','POST',{'equipmentId':'9001','description':'QA 单次维修计划','plannedAt':(datetime.datetime.now()+datetime.timedelta(days=1)).isoformat(timespec='seconds'),'ownerId':'73001','faultType':fault,'severity':'一般','anomalyIds':[a['id']]})['data'];check('计划关联异常',api('/anomalies/'+a['id'])['data']['status']=='已安排')
plan=api('/repair-plans/'+plan['id']+'/actions','POST',{'action':'generate','rowVersion':plan['rowVersion']})['data'];check('单次计划生成工单',plan['status']=='已生成' and bool(plan['repair_id']))
api('/repair-plans/'+plan['id']+'/actions','POST',{'action':'generate','rowVersion':0},409);check('生成不能重复提交')
r=api('/repairs/'+plan['repair_id']+'/workflow')['data']['order']
for action,extra in [('dispatch',{'userIds':['73001']}),('claim',{}),('process',{'faultCause':cause,'faultType':fault,'repairMethod':'自修','content':'QA 修复','result':'已解决','collaboratorIds':[],'attachments':[]}),('return',{'score':3,'comment':'QA 打回','attachments':[]}),('process',{'faultCause':cause,'faultType':fault,'repairMethod':'自修','content':'QA 再次修复','result':'已解决','collaboratorIds':[],'attachments':[]}),('accept',{'score':5,'comment':'QA 验收通过','attachments':[]})]:
 r=api('/repairs/'+r['id']+'/workflow?rowVersion='+str(r['rowVersion']),'POST',{'action':action,**extra})['data']
 if action=='process':check('提交解决后异常未提前关闭',api('/anomalies/'+a['id'])['data']['status']=='处理中')
 if action=='return':check('验收打回继续跟踪',api('/anomalies/'+a['id'])['data']['status']=='处理中')
check('验收通过消除异常',api('/anomalies/'+a['id'])['data']['status']=='已消除')
check('设备概览授权查询',isinstance(api('/equipment/9001/overview')['tasks'],list));check('设备事件分页',api('/equipment/9001/events?size=2')['total']>2 and len(api('/equipment/9001/events?size=2')['data'])==2)
# Dedicated powerless account, with home access only. No existing user's roles are changed.
sql("INSERT IGNORE INTO eam_sys_user(id,tenant_id,account,display_name,org_id) VALUES(98900101,10001,'qa-experience-limited','QA 限权用户',71003); INSERT IGNORE INTO eam_sys_role(id,tenant_id,code,name,data_scope) VALUES(98900102,10001,'QA-EXPERIENCE-LIMITED','QA 限权角色','PER_MENU'); INSERT IGNORE INTO eam_sys_user_role(tenant_id,user_id,role_id) VALUES(10001,98900101,98900102); INSERT IGNORE INTO eam_sys_role_menu_permission(id,tenant_id,role_id,menu_key,actions_json,data_scopes_json,org_ids_json) VALUES(98900103,10001,98900102,'workspace',JSON_ARRAY('查看'),JSON_ARRAY('all'),JSON_ARRAY());")
limited=session(98900101,98900104)
api('/home-templates',token=limited,status=403);check('模板管理越权拒绝')
api('/home?templateId='+t['id'],token=limited);check('未分配模板安全回退',api('/home?templateId='+t['id'],token=limited)['selected']['id']=='0')
api('/home/blocks','POST',{'id':'team','type':'load','title':'QA','width':6,'limit':5,'scope':'team'},403,limited);check('团队范围越权拒绝')
check('异常列表不泄漏',api('/anomalies',token=limited)['total']==0)
# Authoritative widget filtering and personal page preferences.
check('角色选项可加载',len(api('/home-templates/role-choices')['data'])>0)
check('计划负责人选择可加载',len(api('/repair-plans/owner-choices')['data'])>0)
check('授权组织筛选选项',isinstance(api('/home/filter-choices?team=true')['companies'],list))
api('/home/page-preferences/anomalies','PUT',{'query':'QA saved','page':2,'size':20,'hiddenColumns':['severity']})
check('列表偏好跨请求保存',api('/home/page-preferences/anomalies')['data']['query']=='QA saved')
check('列表偏好不同用户隔离',api('/home/page-preferences/anomalies',token=limited)['data']=={})
api('/home/page-preferences/anomalies','PUT',{})
filtered=api('/home/blocks','POST',{'id':'filter','type':'anomalies','title':'QA filter','width':6,'limit':5,'scope':'personal','status':'已消除'})
check('异常首页区块状态过滤',all(v['status']=='已消除' for v in filtered['data']))
# Upload, bind, preview and source attachment security.
import base64
png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j9WQAAAAASUVORK5CYII=')
boundary='QAExperienceBoundary'
content=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="qa-evidence.png"\r\nContent-Type: image/png\r\n\r\n'.encode()+png+f'\r\n--{boundary}--\r\n'.encode())
req=urllib.request.Request(BASE+'/anomalies/files',data=content,method='POST',headers={'Cookie':'EAM_SESSION='+TOKEN,'Content-Type':'multipart/form-data; boundary='+boundary,'Origin':'http://127.0.0.1:8081'})
with urllib.request.urlopen(req) as response:file=json.load(response)['data']
f=api('/anomalies','POST',{'equipmentId':'9001','description':'QA attachment anomaly','severity':'一般','attachments':[file]})['data']
ref=f['attachments'][0]
req=urllib.request.Request('http://127.0.0.1:8081'+ref['url'],headers={'Cookie':'EAM_SESSION='+TOKEN})
with urllib.request.urlopen(req) as response:check('异常图片绑定后可预览',response.read()==png)
api('/anomalies/'+f['id']+'/files/'+file['id'],token=limited,status=403);check('异常附件跨范围访问拒绝')
# Plan edits release removed anomalies, maintaining closed-loop status.
b=api('/anomalies','POST',{'equipmentId':'9001','description':'QA edit plan'})['data']
p=api('/repair-plans','POST',{'equipmentId':'9001','description':'QA edit plan','plannedAt':(datetime.datetime.now()+datetime.timedelta(days=2)).isoformat(timespec='seconds'),'ownerId':'73001','faultType':fault,'severity':'一般','anomalyIds':[b['id']]})['data']
p=api('/repair-plans/'+p['id'],'PUT',{'equipmentId':'9001','description':'QA edited','plannedAt':(datetime.datetime.now()+datetime.timedelta(days=3)).isoformat(timespec='seconds'),'ownerId':'73001','faultType':fault,'severity':'一般','anomalyIds':[],'rowVersion':p['rowVersion']})['data']
check('计划删除异常关联释放异常',api('/anomalies/'+b['id'])['data']['status']=='待处理')
# Source task abnormal results create traceable anomalies once.
snapshot={'equipmentCode':'EQ-2026-0001','equipmentName':'QA equipment','owners':['73001'],'reviewers':['73001'],'items':[{'id':'qa-source-item','name':'QA 温度','valueType':'数值','lower':'1','upper':'10','required':True}]}
sql("DELETE FROM eam_maintenance_event WHERE task_id=98904001 AND tenant_id=10001; DELETE FROM eam_maintenance_task WHERE id=98904001 AND tenant_id=10001;")
encoded=json.dumps(snapshot,ensure_ascii=False).replace("'","''")
sql(f"INSERT INTO eam_maintenance_task(id,tenant_id,code,plan_id,schedule_id,equipment_id,company_id,department_id,due_date,owner_id,status,snapshot) VALUES(98904001,10001,'QA-BY-source',98904002,98904003,9001,71002,71003,CURDATE(),73001,'执行中','{encoded}');")
api('/maintenance/tasks/98904001/process','POST',{'rowVersion':0,'startAt':datetime.datetime.now().isoformat(timespec='seconds'),'endAt':datetime.datetime.now().isoformat(timespec='seconds'),'items':[{'id':'qa-source-item','value':'99','resolved':False,'images':[]}],'attachments':[],'collaborators':[]})
source=api('/anomalies/source?kind='+urllib.parse.quote('保养')+'&taskId=98904001')['data'];check('保养异常自动捕获',len(source)==1 and source[0]['source_item_id']=='qa-source-item')
api('/anomalies/backfill','POST',{});check('历史回填不重复捕获',len(api('/anomalies/source?kind='+urllib.parse.quote('保养')+'&taskId=98904001')['data'])==1)
# Enable a near-future plan and let the actual scheduled worker generate it.
scheduled=api('/repair-plans','POST',{'equipmentId':'9001','description':'QA scheduler','plannedAt':(datetime.datetime.now()+datetime.timedelta(seconds=3)).isoformat(timespec='seconds'),'ownerId':'73001','faultType':fault,'severity':'一般','anomalyIds':[]})['data']
scheduled=api('/repair-plans/'+scheduled['id']+'/actions','POST',{'action':'enable','rowVersion':scheduled['rowVersion']})['data']
import time
start=time.monotonic()
while scheduled['status']!='已生成' and time.monotonic()-start<70:
 time.sleep(2);scheduled=api('/repair-plans/'+scheduled['id'])['data']
check('真实定时扫描自动生成工单',scheduled['status']=='已生成')
check('计划生成来源唯一',sql("SELECT COUNT(*) FROM eam_repair_order WHERE id="+scheduled['repair_id'])=='COUNT(*)\n1')
print('VERIFIED',len(checks),'checks; isolated database',DB)
(R/'experience-qa/api-results.json').write_text(json.dumps({'checks':checks,'database':DB},ensure_ascii=False,indent=2))
