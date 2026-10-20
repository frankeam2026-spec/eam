#!/usr/bin/env python3
"""Layout API acceptance, restricted to the existing isolated QA database."""
from pathlib import Path
import json, subprocess, secrets, hashlib, urllib.request, urllib.error, copy
ROOT=Path(__file__).resolve().parents[1]; R=ROOT.parent/'.local-runtime'; DB='eam_business_qa'; BASE='http://127.0.0.1:8084/api/v1'
assert DB=='eam_business_qa' and ':8084/' in BASE
checks=[]
def sql(statement):
 return subprocess.run([str(R/'mysql-8.4.6-macos15-arm64/bin/mysql'),'--defaults-extra-file='+str(R/'root.cnf'),'--socket='+str(R/'mysql.sock'),'-N','-B',DB],input=statement,text=True,capture_output=True,check=True).stdout.strip()
token=secrets.token_urlsafe(36); digest=hashlib.sha256(token.encode()).hexdigest()
sql(f"INSERT INTO eam_auth_session(id,tenant_id,user_id,token_hash,expires_at) VALUES(989610161008,10001,73001,'{digest}',DATE_ADD(NOW(),INTERVAL 1 DAY)) ON DUPLICATE KEY UPDATE token_hash=VALUES(token_hash),expires_at=VALUES(expires_at)")
def api(path,body=None,method=None,status=200):
 req=urllib.request.Request(BASE+path,data=json.dumps(body,ensure_ascii=False).encode() if body is not None else None,method=method or ('POST' if body is not None else 'GET'),headers={'Cookie':'EAM_SESSION='+token,'Content-Type':'application/json','Origin':'http://127.0.0.1:8084'})
 try:
  with urllib.request.urlopen(req) as r: code=r.status; data=r.read()
 except urllib.error.HTTPError as e: code=e.code; data=e.read()
 result=json.loads(data) if data else None
 assert code==status,(path,code,result)
 return result
def check(name,condition=True):
 assert condition,name; checks.append(name); print('PASS',name,flush=True)
for obj in ('equipment','repair_report'):
 before=api('/business-config/objects/'+obj); version=before['publishedVersion']; config=copy.deepcopy(before['draft'])
 values=sql("SELECT * FROM eam_business_value ORDER BY tenant_id,object_key,record_id,field_id,ordinal_no")
 config['layout']={'columns':4,'maxWidth':1200}
 for index,field in enumerate(config['fields']): field['span']=index%4+1
 draft=api('/business-config/objects/'+obj+'/draft',{'configuration':config,'version':before['version']},'PUT')
 check(obj+' 草稿布局不影响运行配置',api('/business-config/runtime/'+obj)['configuration']==before['published'])
 for invalid in ({'columns':5,'maxWidth':0},{'columns':0,'maxWidth':0},{'columns':'2','maxWidth':0},{'columns':4,'maxWidth':200},{'columns':4,'maxWidth':9999}):
  bad=copy.deepcopy(config);bad['layout']=invalid
  api('/business-config/objects/'+obj+'/draft',{'configuration':bad,'version':draft['version']},'PUT',400)
 if config['fields']:
  bad=copy.deepcopy(config);bad['fields'][0]['span']=5
  api('/business-config/objects/'+obj+'/draft',{'configuration':bad,'version':draft['version']},'PUT',400)
 check(obj+' 非法列数宽度占宽拒绝且版本不变',api('/business-config/objects/'+obj)['version']==draft['version'])
 published=api('/business-config/objects/'+obj+'/publish',{'version':draft['version']})
 runtime=api('/business-config/runtime/'+obj)['configuration']
 check(obj+' 发布后保留列数宽度与跨列',runtime==config)
 check(obj+' 布局变更不改变字段ID与已有值',set(f['id'] for f in runtime['fields'])==set(f['id'] for f in before['draft']['fields']) and values==sql("SELECT * FROM eam_business_value ORDER BY tenant_id,object_key,record_id,field_id,ordinal_no"))
 restored=api('/business-config/objects/'+obj+'/restore',{'target':version,'version':published['version']})
 check(obj+' 恢复历史仅改变草稿',api('/business-config/runtime/'+obj)['configuration']==config and restored['draft']==before['published'])
 api('/business-config/objects/'+obj+'/publish',{'version':restored['version']})
result=json.loads((R/'business-qa/results.json').read_text());result['layoutPassed']=checks;(R/'business-qa/results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2))
