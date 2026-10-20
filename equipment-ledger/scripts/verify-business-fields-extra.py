#!/usr/bin/env python3
"""Business field acceptance. Mutations are restricted to eam_business_qa on port 8084."""
from pathlib import Path
import json,subprocess,secrets,hashlib,urllib.request,urllib.error,urllib.parse,uuid,concurrent.futures,copy
ROOT=Path(__file__).resolve().parents[1];R=ROOT.parent/'.local-runtime';DB='eam_business_qa';BASE='http://127.0.0.1:8084/api/v1'
assert DB=='eam_business_qa' and ':8084/' in BASE
checks=[]
def sql(statement):
 p=subprocess.run([str(R/'mysql-8.4.6-macos15-arm64/bin/mysql'),'--defaults-extra-file='+str(R/'root.cnf'),'--socket='+str(R/'mysql.sock'),'-N','-B',DB],input=statement,text=True,capture_output=True,check=True);return p.stdout.strip()
def session(uid,key):
 token=secrets.token_urlsafe(36);digest=hashlib.sha256(token.encode()).hexdigest();sql(f"INSERT INTO eam_auth_session(id,tenant_id,user_id,token_hash,expires_at) VALUES({key},10001,{uid},'{digest}',DATE_ADD(NOW(),INTERVAL 1 DAY)) ON DUPLICATE KEY UPDATE token_hash=VALUES(token_hash),expires_at=VALUES(expires_at)");return token
TOKEN=session(73001,989610161001)
def api(path,body=None,method=None,status=200,token=None,raw=False):
 req=urllib.request.Request(BASE+path,data=json.dumps(body,ensure_ascii=False).encode() if body is not None else None,method=method or ('POST' if body is not None else 'GET'),headers={'Cookie':'EAM_SESSION='+(token or TOKEN),'Content-Type':'application/json','Origin':'http://127.0.0.1:8084'})
 try:
  with urllib.request.urlopen(req) as response:code=response.status;payload=response.read()
 except urllib.error.HTTPError as e:code=e.code;payload=e.read()
 try:result=json.loads(payload) if payload else None
 except Exception:result=payload.decode('utf-8-sig')
 if status is not None:assert code==status,(path,code,result)
 return (code,result) if status is None else result

def check(name,condition=True):
 assert condition,name;checks.append(name);print('PASS',name,flush=True)
def field(name,type='TEXT',**extra):
 return dict(id=str(uuid.uuid4()),name=name,type=type,source='',enabled=True,required=False,group='补充信息',width='half',sortOrder=0,description='',placeholder='',maxLength=200,min='',max='',multiple=False,listable=True,filterable=True,exportable=True,visibleWhen={'mode':'all','items':[]},requiredWhen={'mode':'all','items':[]},**extra)
def save_config(obj,fields):
 current=api('/business-config/objects/'+obj);return api('/business-config/objects/'+obj+'/draft',{'configuration':{'fields':fields},'version':current['version']},'PUT')
def publish(obj):
 current=api('/business-config/objects/'+obj);return api('/business-config/objects/'+obj+'/publish',{'version':current['version']})
def update_equipment(record,values=None,version=None,status=200):
 body=copy.deepcopy(record)
 if values is not None:body['customFields']=values
 if version is not None:body['customSchemaVersion']=version
 return api('/equipment/'+record['id']+'?rowVersion='+str(record['rowVersion']),body,'PUT',status)
def query(path,params):return api(path+'?'+urllib.parse.urlencode(params))
result=json.loads((R/'business-qa/results.json').read_text());ids=result['fields'];asset=api('/equipment/'+result['equipmentId'])['data']
# Concurrency uses the same record version, and must not split core/extension writes.
def edit_race(value):return update_equipment(asset,{**asset['customFields'],ids['power']:value},status=None)[0]
with concurrent.futures.ThreadPoolExecutor(2) as pool:codes=list(pool.map(edit_race,['10','20']))
check('设备并发保存仅一次成功',sorted(codes)==[200,409]);asset=api('/equipment/'+asset['id'])['data']
# Real paging over more than one matching record, not filtering only the first page.
for i in range(3):
 body={**asset,'id':'','code':'QA-BIZ-PAGE-'+str(i),'name':'QA分页设备'+str(i),'customFields':{**asset['customFields'],ids['power']:str(30+i)}}
 api('/equipment',body)
conditions=[{'field':'custom:'+ids['power'],'operator':'range','values':['30','32']}]
r=query('/equipment',{'advanced':json.dumps(conditions),'page':2,'size':1,'sortBy':'custom:'+ids['power'],'direction':'asc'})
check('分页筛选覆盖全部匹配数据',r['page']['total']==3 and float(r['data'][0]['customFields'][ids['power']])==31)
# Conditional required/hide rules preserve values and do not allow hidden writes.
config=api('/business-config/objects/equipment');fields=config['draft']['fields']
conditional=field('条件必填信息');conditional['required']=True;conditional['visibleWhen']={'mode':'all','items':[{'field':ids['boolean'],'operator':'eq','values':['true']}]}
fields.append(conditional);save_config('equipment',fields);publish('equipment');asset=api('/equipment/'+asset['id'])['data']
update_equipment(asset,{**asset['customFields'],ids['boolean']:True},status=400);check('条件满足时执行必填')
asset=update_equipment(asset,{**asset['customFields'],ids['boolean']:True,conditional['id']:'历史保留值'})['data']
asset=update_equipment(asset,{ids['boolean']:False})['data'];check('隐藏字段保留原值',asset['customFields'][conditional['id']]=='历史保留值')
update_equipment(asset,{conditional['id']:'隐藏篡改'},status=400);check('隐藏状态禁止改写已有值')
r=query('/equipment',{'advanced':json.dumps([{'field':'custom:'+conditional['id'],'operator':'contains','values':['保留']}])});check('隐藏保留值仍可筛选',r['page']['total']==1)
# Explicit clears do not clear unspecified values.
text_field=next(f for f in fields if f['name']=='客户信息');asset=update_equipment(asset,{text_field['id']:None})['data'];check('明确清空只影响指定字段',text_field['id'] not in asset['customFields'] and ids['boolean'] in asset['customFields'])
# Disable only affects input, not historical display/query/export.
fields[0]['enabled']=False;save_config('equipment',fields);publish('equipment');asset=api('/equipment/'+asset['id'])['data']
update_equipment(asset,{ids['power']:'999'},status=400);check('停用字段不可改写')
r=query('/equipment',{'advanced':json.dumps([{'field':'custom:'+ids['power'],'operator':'range','values':['10','20']}])});check('停用字段历史值可查询',r['page']['total']>=1)
# Defaults apply on creation only, including API submissions.
new_values={ids['boolean']:False}
new_asset=api('/equipment',{**asset,'id':'','code':'QA-BIZ-DEFAULT','name':'QA默认值设备','customFields':new_values})['data'];check('默认值只在新增时应用',new_asset['customFields'].get(text_field['id'])=='新建设备默认值')
old=query('/equipment',{'keyword':'EQ-2026-0014','size':1})['data'][0]
old=update_equipment(old,{ids['boolean']:False})['data'];check('历史编辑不自动补默认值',text_field['id'] not in old['customFields'])
# A role limited to one department cannot configure fields, enumerate other users, or forge references.
uid=73003;role=989610161101
org=asset['departmentOrgId'];sql(f"UPDATE eam_sys_user SET org_id={org} WHERE id={uid} AND tenant_id=10001; DELETE FROM eam_sys_user_role WHERE tenant_id=10001 AND user_id={uid};")
# Clone only the role row shape, not the administrator permissions.
columns=sql('SHOW COLUMNS FROM eam_sys_role').splitlines();names=[line.split('\t')[0] for line in columns]
expressions=[]
for name in names:
 if name=='id':expressions.append(str(role))
 elif name=='code':expressions.append("'QA_BUSINESS_FIELDS'")
 elif name=='name':expressions.append("'QA业务字段部门用户'")
 elif name=='permissions_json':expressions.append("JSON_ARRAY()")
 elif name=='data_scope':expressions.append("'PER_MENU'")
 else:expressions.append('`'+name+'`')
sql('INSERT INTO eam_sys_role('+','.join('`'+name+'`' for name in names)+') SELECT '+','.join(expressions)+' FROM eam_sys_role WHERE id=72001')
sql(f"INSERT INTO eam_sys_user_role(tenant_id,user_id,role_id) VALUES(10001,{uid},{role}); INSERT INTO eam_sys_role_menu_permission(id,tenant_id,role_id,menu_key,actions_json,data_scopes_json,org_ids_json) VALUES(989610161102,10001,{role},'equipment',JSON_ARRAY('查看','新增','编辑','导出'),JSON_ARRAY('department'),JSON_ARRAY());")
limited=session(uid,989610161103)
api('/business-config/objects',status=403,token=limited);api('/business-config/objects/equipment/draft',{'configuration':{'fields':[]},'version':0},'PUT',403,limited);check('无配置权限不能查看或修改配置')
api('/business-config/runtime/equipment',token=limited);check('普通业务用户可读取已发布字段')
choices=query('/business-config/runtime/equipment/options',{'source':'department','page':1,'size':100}) # admin sees all
foreign=next(x for x in choices['data'] if x['id']!=org)
res=api('/business-config/runtime/equipment/options?source=department&page=1&size=100',token=limited)
check('部门选择按业务数据权限过滤',all(x['id']==org for x in res['data']))
body={**new_asset,'id':'','code':'QA-BIZ-FORBIDDEN','customFields':{ids['boolean']:False,ids['department']:foreign['id']}}
err=api('/equipment',body,status=400,token=limited);check('服务端拒绝伪造越权部门','customFields.'+ids['department'] in err['fieldErrors'])
foreign_user=sql(f"SELECT id FROM eam_sys_user WHERE tenant_id=10001 AND deleted=0 AND org_id<>{org} LIMIT 1").splitlines()[0]
body['customFields']={ids['boolean']:False,ids['user']:foreign_user};err=api('/equipment',body,status=400,token=limited);check('服务端拒绝伪造越权用户','customFields.'+ids['user'] in err['fieldErrors'])
api('/equipment?advanced='+urllib.parse.quote(json.dumps([{'field':'custom:'+ids['department'],'operator':'in','values':[foreign['id']]}])),status=400,token=limited);check('筛选不能借关联对象越权')
# Dictionary field cannot be reused to expose an unsupported built-in data source.
api('/business-config/runtime/equipment/options?source=base:LOCATION',status=400);check('选项接口拒绝未注册来源')
# Restore active sample configuration for UI inspection, without publishing test-only conditional requirements.
current=api('/business-config/objects/equipment');restored=api('/business-config/objects/equipment/restore',{'target':2,'version':current['version']});publish('equipment')
result['extraPassed']=checks;(R/'business-qa/results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));print('EXTRA TOTAL',len(checks),flush=True)
