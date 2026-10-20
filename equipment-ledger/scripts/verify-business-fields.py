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
# This script intentionally requires a fresh clone, so it cannot silently alter existing test publications.
assert api('/business-config/objects/equipment')['publishedVersion']==0,'Use a fresh isolated QA clone'
original=query('/equipment',{'page':1,'size':1})['data'][0]
legacy_report=api('/repairs',{'equipmentCode':original['code'],'equipmentName':original['name'],'faultTypeId':api('/base-data/fault-types')['data'][0]['id'],'severity':'一般','description':'QA-旧报修','reporter':'QA','attachments':[]})['data']
options=[{'id':str(uuid.uuid4()),'name':name,'enabled':True,'sortOrder':i} for i,name in enumerate(['电工','钳工'])];option=options[0];option2=options[1]
power=field('额定功率','NUMBER');power['required']=True;power['min']='0';power['max']='9999'
boolean=field('是否备用','BOOLEAN');boolean['required']=True
trade=field('适用工种','CHOICE');trade['options']=copy.deepcopy(options)
multi=field('多选工种','MULTI_CHOICE');multi['options']=copy.deepcopy(options)
hidden=field('仅其他类别显示');hidden['visibleWhen']={'mode':'all','items':[{'field':'categoryId','operator':'eq','values':['-1']}]}
department=field('协作部门','DEPARTMENT');department['source']='department'
user=field('联络人','USER');user['source']='user'
text=field('客户信息');text['defaultValue']='新建设备默认值'
date=field('扩展日期','DATE');dt=field('扩展时间','DATETIME')
fields=[power,boolean,trade,multi,hidden,department,user,text,date,dt]
save_config('equipment',fields)
check('草稿不影响已发布字段',not api('/business-config/runtime/equipment')['configuration']['fields'])
state=publish('equipment');version=state['publishedVersion'];check('首次发布生成版本',version==1)
base={**original,'id':'','code':'QA-BIZ-001','name':'QA业务字段设备','images':[],'documents':[],'children':[],'relatedSpares':[],'customSchemaVersion':version,'customFields':{}}
before=sql("SELECT COUNT(*) FROM eam_equipment")
failed=api('/equipment',base,status=400)
check('必填错误定位到扩展字段','customFields.'+power['id'] in failed['fieldErrors'])
check('必填失败回滚主记录',sql("SELECT COUNT(*) FROM eam_equipment")==before)
values={power['id']:'0',boolean['id']:False,trade['id']:option['id'],multi['id']:[option['id'],option2['id']],date['id']:'2026-10-16',dt['id']:'2026-10-16T12:34:56',text['id']:'=SUM(1,2)'}
asset=api('/equipment',{**base,'customFields':values})['data']
check('零和否正常保存',float(asset['customFields'][power['id']])==0 and asset['customFields'][boolean['id']] is False)
check('选项保存ID及名称快照',asset['customFields'][trade['id']]==option['id'] and asset['customFieldLabels'][trade['id']]==option['name'])
check('日期时间按类型回填',asset['customFields'][dt['id']]=='2026-10-16T12:34:56')
error=update_equipment(asset,{**values,hidden['id']:'非法隐藏值'},status=400)
check('服务端拒绝隐藏字段修改','customFields.'+hidden['id'] in error['fieldErrors'])
update_equipment(asset,{**values,str(uuid.uuid4()):'伪造字段'},status=400);check('未知字段拒绝')
update_equipment(asset,{**values,date['id']:'bad'},status=400);check('错误日期拒绝')
for value in ['not-a-number','-1','10000']:
 update_equipment(asset,{**values,power['id']:value},status=400)
check('数值类型和上下限由服务端校验')
# Text, multi-choice and range predicates must apply to count, paging and CSV alike.
conditions=[{'field':'custom:'+power['id'],'operator':'range','values':['0','0']},{'field':'custom:'+multi['id'],'operator':'in','values':[option['id'],option2['id']]}]
result=query('/equipment',{'advanced':json.dumps(conditions),'page':1,'size':1})
check('区间和多选服务端分页不重复',result['page']['total']==1 and result['data'][0]['id']==asset['id'])
result=query('/equipment',{'advanced':json.dumps([{'field':'custom:'+text['id'],'operator':'contains','values':['sum']}]),'page':1,'size':10})
check('文本包含不区分大小写',result['page']['total']==1)
result=query('/equipment',{'advanced':json.dumps([{'field':'custom:'+date['id'],'operator':'range','values':['2026-10-16','2026-10-16']}]),'page':1,'size':10})
check('日期结束当天纳入',result['page']['total']==1)
query('/equipment',{'advanced':json.dumps(conditions),'sortBy':'custom:'+power['id'],'direction':'asc'})
check('数值扩展列服务端排序')
csv=query('/equipment/export',{'advanced':json.dumps(conditions),'columns':'code,custom:'+power['id']+',custom:'+text['id']})
check('导出同条件并防公式注入','QA-BIZ-001' in csv and "'=SUM" in csv and '额定功率' in csv)
# Option stop preserves original selection; assigning it to a new record is rejected.
trade['options'][0]['enabled']=False;multi['options'][0]['enabled']=False;save_config('equipment',fields);publish('equipment');asset=api('/equipment/'+asset['id'])['data']
asset=update_equipment(asset,values)['data'];check('停用选项历史值可原样保留',asset['customFields'][trade['id']]==option['id'])
api('/equipment',{**base,'code':'QA-BIZ-002','customFields':values,'customSchemaVersion':asset['customSchemaVersion']},status=400);check('停用选项不可新选')
invalid_options=copy.deepcopy(fields);next(f for f in invalid_options if f['id']==trade['id'])['options'].pop(0);cur=api('/business-config/objects/equipment');api('/business-config/objects/equipment/draft',{'configuration':{'fields':invalid_options},'version':cur['version']},'PUT',400);check('已发布选项不能删除')
# Metadata conflict must leave both core and extension rows untouched.
fields[0]['name']='额定功率（kW）';save_config('equipment',fields);publish('equipment')
update_equipment(asset,values,status=409);check('旧配置版本保存拒绝')
asset=api('/equipment/'+asset['id'])['data'];check('字段改名保留ID和值',power['id'] in asset['customFields'])
# Device copy carries allowed values and validates required fields under the current configuration.
copy_values={**asset['customFields'],trade['id']:option2['id'],multi['id']:[option2['id']]}
copy_asset=api('/equipment',{**base,'code':'QA-BIZ-003','name':'QA业务字段副本','customFields':copy_values,'customSchemaVersion':asset['customSchemaVersion']})['data']
check('复制设备保留扩展值',copy_asset['customFields'][boolean['id']] is False)
# Wrong business field type and cyclic rules must never be published.
current=api('/business-config/objects/equipment');invalid=copy.deepcopy(current['draft']);invalid['fields'][0]['type']='TEXT'
api('/business-config/objects/equipment/draft',{'configuration':invalid,'version':current['version']},'PUT',400);check('已发布字段类型不可改变')
a=field('循环A');b=field('循环B');a['visibleWhen']={'mode':'all','items':[{'field':b['id'],'operator':'eq','values':['a']}]};b['visibleWhen']={'mode':'all','items':[{'field':a['id'],'operator':'eq','values':['a']}]}
api('/business-config/objects/equipment/draft',{'configuration':{'fields':fields+[a,b]},'version':current['version']},'PUT',400);check('循环联动拒绝')
# Report integration and legacy orders: new report fields must not block workflow operations.
report_field=field('工种','CHOICE');report_field['options']=copy.deepcopy(options);report_field['required']=True;report_field['group']='报修信息'
save_config('repair_report',[report_field]);publish('repair_report')
rf={'equipmentCode':asset['code'],'equipmentName':asset['name'],'faultTypeId':api('/base-data/fault-types')['data'][0]['id'],'severity':'一般','description':'QA-工种报修','reporter':'QA','attachments':[],'customSchemaVersion':1,'customFields':{}}
api('/repairs',rf,status=400);check('报修新增执行自定义必填')
order=api('/repairs',{**rf,'customFields':{report_field['id']:option2['id']}})['data'];check('报修保存工种ID',order['customFields'][report_field['id']]==option2['id'])
res=query('/repairs',{'advanced':json.dumps([{'field':'custom:'+report_field['id'],'operator':'in','values':[option2['id']]}]),'page':1,'size':1})
check('维修列表数量和分页包含自定义条件',res['page']['total']==1 and res['data'][0]['id']==order['id'])
rcsv=query('/repairs/export',{'advanced':json.dumps([{'field':'custom:'+report_field['id'],'operator':'in','values':[option2['id']]}]),'columns':'orderNo,custom:'+report_field['id']})
check('维修导出包含工种快照',option2['name'] in rcsv and '工种' in rcsv)
legacy_body={**rf,'equipmentCode':original['code'],'equipmentName':original['name'],'customFields':{}}
api('/repairs/'+legacy_report['id']+'?rowVersion='+str(legacy_report['rowVersion']),legacy_body,'PUT',400);check('历史待派工编辑要求补齐')
legacy=api('/repairs/'+legacy_report['id']+'/workflow')['data']['order']
def action(row,name,**kwargs):return api('/repairs/'+row['id']+'/workflow?rowVersion='+str(row['rowVersion']),{'action':name,**kwargs})['data']
legacy=action(legacy,'dispatch',userIds=['73001']);legacy=action(legacy,'claim');check('历史工单派工接单不受新增报修必填阻断',legacy['status']=='处理中')
causes=api('/base-data/fault-causes')['data'];cause=next(x for x in causes if x['status']=='启用')
results=api('/repairs/'+legacy['id']+'/workflow')['data']['results'];resolved=next(x for x in results if x['resolved'])
legacy=action(legacy,'process',faultTypeId=legacy['faultTypeId'],faultCauseId=cause['id'],processingResultId=resolved['id'],repairMethod='自修',content='QA处理完成',prevention='',attachments=[],collaboratorIds=[])
legacy=action(legacy,'accept',score=5,comment='QA验收通过',attachments=[]);check('历史工单处理验收不受新增报修必填阻断',legacy['status']=='已完成')
# Restore creates a draft, no runtime rollback until publish.
cur=api('/business-config/objects/equipment');restored=api('/business-config/objects/equipment/restore',{'target':1,'version':cur['version']})
check('恢复历史版本仅修改草稿',restored['publishedVersion']==2 and restored['draft']['fields'][0]['name']=='额定功率')
# Race two configuration writers with one base revision.
cur=api('/business-config/objects/equipment')
def race(i):return api('/business-config/objects/equipment/draft',{'configuration':cur['draft'],'version':cur['version']},'PUT',None)[0]
with concurrent.futures.ThreadPoolExecutor(2) as pool:codes=list(pool.map(race,[1,2]))
check('并发草稿保存仅一个成功',sorted(codes)==[200,409])
# Keep useful stable examples in the isolated database for UI inspection.
active=api('/business-config/objects/equipment')['draft']['fields'];
for f in active:
 for o in f.get('options',[]):o['enabled']=True
save_config('equipment',active);publish('equipment')
report={'passed':checks,'database':DB,'equipmentId':asset['id'],'copyId':copy_asset['id'],'repairId':order['id'],'fields':{'power':power['id'],'boolean':boolean['id'],'trade':trade['id'],'multi':multi['id'],'department':department['id'],'user':user['id']}}
(R/'business-qa/results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
print('TOTAL',len(checks),flush=True)
