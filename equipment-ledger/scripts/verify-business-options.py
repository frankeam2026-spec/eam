#!/usr/bin/env python3
"""Field-owned options acceptance; writes only to eam_business_qa, port 8084."""
from pathlib import Path
import json, subprocess, secrets, hashlib, urllib.request, urllib.error, urllib.parse, copy, uuid, time
ROOT=Path(__file__).resolve().parents[1]; R=ROOT.parent/'.local-runtime'; DB='eam_business_qa'; BASE='http://127.0.0.1:8084/api/v1'
assert DB=='eam_business_qa' and ':8084/' in BASE
checks=[]
def sql(statement):
 return subprocess.run([str(R/'mysql-8.4.6-macos15-arm64/bin/mysql'),'--defaults-extra-file='+str(R/'root.cnf'),'--socket='+str(R/'mysql.sock'),'-N','-B',DB],input=statement,text=True,capture_output=True,check=True).stdout.strip()
token=secrets.token_urlsafe(36); digest=hashlib.sha256(token.encode()).hexdigest()
sql(f"INSERT INTO eam_auth_session(id,tenant_id,user_id,token_hash,expires_at) VALUES(989610161009,10001,73001,'{digest}',DATE_ADD(NOW(),INTERVAL 1 DAY)) ON DUPLICATE KEY UPDATE token_hash=VALUES(token_hash),expires_at=VALUES(expires_at)")
def api(path,body=None,method=None,status=200):
 req=urllib.request.Request(BASE+path,data=json.dumps(body,ensure_ascii=False).encode() if body is not None else None,method=method or ('POST' if body is not None else 'GET'),headers={'Cookie':'EAM_SESSION='+token,'Content-Type':'application/json','Origin':'http://127.0.0.1:8084'})
 try:
  with urllib.request.urlopen(req) as r: code=r.status; data=r.read()
 except urllib.error.HTTPError as e: code=e.code; data=e.read()
 try: result=json.loads(data) if data else None
 except Exception: result=data.decode('utf-8-sig')
 assert code==status,(path,code,result)
 return result
def check(name,condition=True):
 assert condition,name; checks.append(name);print('PASS',name,flush=True)
def save(config,status=200):
 c=api('/business-config/objects/equipment');return api('/business-config/objects/equipment/draft',{'configuration':config,'version':c['version']},'PUT',status)
def publish():
 c=api('/business-config/objects/equipment');return api('/business-config/objects/equipment/publish',{'version':c['version']})
def option(name):return dict(id=str(uuid.uuid4()),name=name,enabled=True,sortOrder=0)
def field(name,multiple=False):
 return dict(id=str(uuid.uuid4()),name=name,type='MULTI_CHOICE' if multiple else 'CHOICE',source='',options=[option('电工'),option('钳工')],enabled=True,required=False,group='补充信息',width='half',sortOrder=90,description='',placeholder='',maxLength=200,min='',max='',multiple=False,listable=True,filterable=True,exportable=True,visibleWhen={'mode':'all','items':[]},requiredWhen={'mode':'all','items':[]})
legacy=json.loads((R/'business-qa/results.json').read_text()); current=api('/business-config/objects/equipment'); config=copy.deepcopy(current['published']); old_version=current['publishedVersion']
old_field=next(f for f in config['fields'] if f['id']==legacy['fields']['trade']); old_id=old_field['options'][0]['id']
check('已有配置读取为字段内选项且保留ID',bool(old_field['options']))
run=str(int(time.time())); one=field('QA字段内单选'+run); many=field('QA字段内多选'+run,True); config['fields'] += [one,many]
for f in (one,many):
 for i,o in enumerate(f['options']):o['sortOrder']=i
save(config); check('字段选项草稿不提前生效',not any(f['id']==one['id'] for f in api('/business-config/runtime/equipment')['configuration']['fields']))
pub=publish(); first_version=pub['publishedVersion']; check('发布保存字段独立选项',next(f for f in pub['published']['fields'] if f['id']==one['id'])['options']==one['options'])
api('/business-config/dictionaries',status=410);check('独立字典管理接口已移除')
asset=api('/equipment/'+legacy['equipmentId'])['data']; original=copy.deepcopy(asset)
asset['customFields']={legacy['fields']['power']:'10',legacy['fields']['boolean']:False}; asset['customSchemaVersion']=pub['publishedVersion']; asset['id']=''; asset['code']='QA-OPTIONS-'+str(int(time.time()));asset['name']='QA字段内选项设备';asset['customFields'][one['id']]=one['options'][0]['id'];asset['customFields'][many['id']]=[o['id'] for o in many['options']]
created=api('/equipment',asset)['data'];check('单选多选保存ID及快照',created['customFields'][one['id']]==one['options'][0]['id'] and created['customFieldLabels'][many['id']]=='电工、钳工')
source=urllib.parse.quote('field:'+one['id']); choices=api('/business-config/runtime/equipment/options?source='+source+'&q='+urllib.parse.quote('电'))
check('字段选项可搜索且字段间不混用',choices['total']==1 and choices['data'][0]['id']==one['options'][0]['id'])
config=copy.deepcopy(pub['published']); single=next(f for f in config['fields'] if f['id']==one['id']);single['options'][0]['name']='电气工';single['options'][1]['enabled']=False;single['options'].append(option('焊工'));single['options'][-1]['sortOrder']=2
legacy_single=next(f for f in config['fields'] if f['id']==old_field['id']);legacy_single['options'][0]['name']='QA独立改名'
other_before=next(f for f in pub['published']['fields'] if f['id']==legacy['fields']['multi']) if 'multi' in legacy['fields'] else None
save(config); before=api('/business-config/runtime/equipment/options?source='+source);check('编辑选项在发布前不影响录入',before['data'][0]['name']=='电工')
pub=publish(); after=api('/business-config/runtime/equipment/options?source='+source);check('发布后停用项不供新选',len(after['data'])==2 and all(o['id']!=single['options'][1]['id'] for o in after['data']))
check('多选字段选项保持独立',next(f for f in pub['published']['fields'] if f['id']==many['id'])['options'][0]['name']=='电工')
read=api('/equipment/'+created['id'])['data'];check('选项改名保留原记录ID及快照',read['customFields'][one['id']]==one['options'][0]['id'] and read['customFieldLabels'][one['id']]=='电工')
new=copy.deepcopy(asset);new['code']+='-STOP';new['customSchemaVersion']=pub['publishedVersion'];new['customFields'][one['id']]=single['options'][1]['id'];err=api('/equipment',new,status=400);check('服务端拒绝新选停用项','customFields.'+one['id'] in err['fieldErrors'])
new['customFields'][one['id']]=many['options'][0]['id'];api('/equipment',new,status=400);check('服务端拒绝其他字段的选项ID')
conditions=[{'field':'custom:'+many['id'],'operator':'in','values':[many['options'][1]['id']]}];res=api('/equipment?advanced='+urllib.parse.quote(json.dumps(conditions)));check('多选筛选覆盖已保存的真实ID',any(x['id']==created['id'] for x in res['data']))
bad=copy.deepcopy(pub['published']);next(f for f in bad['fields'] if f['id']==one['id'])['options'].pop(0);save(bad,400);check('服务端禁止删除已发布选项')
bad=copy.deepcopy(pub['published']);opts=next(f for f in bad['fields'] if f['id']==one['id'])['options'];opts[1]['name']=opts[0]['name'];save(bad,400);check('服务端禁止重复名称')
restored=api('/business-config/objects/equipment/restore',{'target':first_version,'version':pub['version']});restored_field=next(f for f in restored['draft']['fields'] if f['id']==one['id']);check('恢复历史保留后加选项并停用',any(o['id']==single['options'][-1]['id'] and not o['enabled'] for o in restored_field['options']))
# Leave the QA layout on the latest successful configuration for visual inspection.
save(config);publish()
(R/'business-qa/inline-options-results.json').write_text(json.dumps({'passed':checks,'fieldId':one['id'],'multiFieldId':many['id']},ensure_ascii=False,indent=2))
print('TOTAL',len(checks))
