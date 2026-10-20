"""Only creates temporary identities/sessions in eam_i18n_test_20261019."""
import pathlib,subprocess,secrets,hashlib,json,urllib.request,urllib.error
R=pathlib.Path(__file__).resolve().parents[3]/'.local-runtime';DB='eam_i18n_test_20261019';B='http://127.0.0.1:18084/api/v1';out=[]
def sql(s):return subprocess.check_output([str(R/'mysql-8.4.6-macos15-arm64/bin/mysql'),'--defaults-extra-file='+str(R/'root.cnf'),'--socket='+str(R/'mysql.sock'),DB,'--batch','--skip-column-names'],input=s.encode()).decode()
tokens={name:secrets.token_urlsafe(36) for name in ['manager','ordinary','second']}
for i,(name,token) in enumerate(tokens.items()):
 user=991019100+i;role=991019110+i;grant=991019120+i
 sql(f"INSERT IGNORE INTO eam_sys_user(id,tenant_id,account,display_name,org_id) VALUES({user},10001,'i18n_qa_{i}','多语言隔离人员{i}',71003);INSERT IGNORE INTO eam_sys_role(id,tenant_id,code,name,data_scope) VALUES({role},10001,'I18N_QA_{i}','多语言隔离角色{i}','PER_MENU');INSERT IGNORE INTO eam_sys_user_role(tenant_id,user_id,role_id) VALUES(10001,{user},{role});DELETE FROM eam_auth_session WHERE id={991019130+i};INSERT INTO eam_auth_session(id,tenant_id,user_id,token_hash,expires_at) VALUES({991019130+i},10001,{user},'{hashlib.sha256(token.encode()).hexdigest()}',DATE_ADD(NOW(),INTERVAL 1 HOUR));")
 if name=='manager':sql(f"INSERT IGNORE INTO eam_sys_role_menu_permission(id,tenant_id,role_id,menu_key) VALUES({grant},10001,{role},'language');INSERT IGNORE INTO eam_role_menu_action VALUES(10001,{grant},'查看',0);INSERT IGNORE INTO eam_role_menu_scope VALUES(10001,{grant},'all',0);")
def req(name,path,method='GET',body=None,status=200):
 h={'Content-Type':'application/json','Accept-Language':'en-US','Origin':'http://127.0.0.1:18084'}
 if name:h['Cookie']='EAM_SESSION='+tokens[name]
 r=urllib.request.Request(B+path,data=None if body is None else json.dumps(body).encode(),headers=h,method=method)
 try:
  with urllib.request.urlopen(r) as response:code=response.status;data=response.read()
 except urllib.error.HTTPError as e:code=e.code;data=e.read()
 assert code==status,(path,code,data[:300]);return json.loads(data)
def record(case):out.append({'case':case,'passed':True});print(case,'PASS',flush=True)
req(None,'/i18n/languages');req(None,'/i18n/resources/en-US');req(None,'/system/languages',status=401);record('anonymous-only-public-translations')
for method,path,body in [('GET','/system/languages',None),('GET','/system/languages/template',None),('POST','/system/languages/imports/validate',{}),('PUT','/system/languages/fr-FR',{}),('PATCH','/system/languages/fr-FR/state',{}),('POST','/system/languages/zh-TW/restore',{})]:req('ordinary',path,method,body,403)
record('management-denied-on-every-entry')
req('manager','/system/languages');pack={'formatVersion':1,'locale':'fr-CA','nativeName':'Français (Canada)','direction':'ltr','messages':{'language.manager.menu':'Langues'}};req('manager','/system/languages/imports/validate','POST',pack);rows=req('manager','/system/languages')['data'];v=next((r['rowVersion'] for r in rows if r['locale']=='fr-CA'),0);req('manager','/system/languages/fr-CA','PUT',{'package':pack,'rowVersion':v});record('single-menu-permission-allows-management');assert req('manager','/system/languages/template?locale=fr-CA')['messages']==pack['messages'];record('partial-package-export-preserves-overrides')
req('ordinary','/auth/settings/locale','PATCH',{'locale':'en-US'});req('second','/auth/settings/locale','PATCH',{'locale':'zh-TW'});assert req('ordinary','/auth/settings')['data']['locale']=='en-US';assert req('second','/auth/settings')['data']['locale']=='zh-TW';record('preferences-isolated-between-accounts')
# Session re-issue emulates another device: saved preference still comes from the account.
old=tokens['ordinary'];tokens['ordinary']=secrets.token_urlsafe(36);sql(f"UPDATE eam_auth_session SET token_hash='{hashlib.sha256(tokens['ordinary'].encode()).hexdigest()}' WHERE id=991019131;");assert req('ordinary','/auth/settings')['data']['locale']=='en-US';record('preference-survives-new-session')
(R/'i18n-qa/permissions.json').write_text(json.dumps(out,indent=2))
# Remove the authentication sessions created by this script; keep isolated identities for reproducibility.
sql('DELETE FROM eam_auth_session WHERE id BETWEEN 991019130 AND 991019132;')
