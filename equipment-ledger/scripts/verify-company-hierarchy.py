#!/usr/bin/env python3
"""Isolated organization/permission acceptance. Never writes eam_demo.
Requires migrated eam_org_hierarchy_test and normal auth backend on 8081.
"""
import json, hashlib, secrets, subprocess, pathlib, urllib.request, urllib.error, time
from concurrent.futures import ThreadPoolExecutor
R=pathlib.Path(__file__).resolve().parents[2]/'.local-runtime'
DB='eam_org_hierarchy_test';BASE='http://127.0.0.1:8081';assert DB.endswith('_test')
def sql(s):
 return subprocess.run([str(R/'mysql-8.4.6-macos15-arm64/bin/mysql'),'--defaults-extra-file='+str(R/'root.cnf'),'--socket='+str(R/'mysql.sock'),'-N','-B',DB],input=s,text=True,capture_output=True,check=True).stdout.strip()
tokens={};checks=[]
def call(actor,path,body=None,method=None,status=200):
 h={'Origin':BASE,'Content-Type':'application/json'}
 if actor in tokens:h['Cookie']='EAM_SESSION='+tokens[actor]
 req=urllib.request.Request(BASE+'/api/v1'+path,headers=h,data=None if body is None else json.dumps(body).encode(),method=method or ('POST' if body is not None else 'GET'))
 try:
  with urllib.request.urlopen(req) as r:actual=r.status;data=json.load(r)
 except urllib.error.HTTPError as e:actual=e.code;data=json.loads(e.read())
 assert actual==status,(path,actual,data)
 return data
# Tokens are temporary test-only credentials, never printed.
def identity(actor,uid,org,scope=None,ids=[]):
 token=secrets.token_urlsafe(36);tokens[actor]=token
 sql(f"INSERT INTO eam_sys_user(id,tenant_id,account,display_name,org_id,status) VALUES({uid},10001,'QA-ORG-{actor}','QA-ORG-{actor}',{org},1) ON DUPLICATE KEY UPDATE org_id=VALUES(org_id),status=1;")
 sql(f"INSERT INTO eam_auth_session(id,tenant_id,user_id,token_hash,expires_at) VALUES({uid},10001,{uid},'{hashlib.sha256(token.encode()).hexdigest()}',DATE_ADD(NOW(),INTERVAL 1 HOUR)) ON DUPLICATE KEY UPDATE token_hash=VALUES(token_hash),expires_at=VALUES(expires_at);")
 if scope:
  sql(f"INSERT INTO eam_sys_role(id,tenant_id,code,name,status,data_scope) VALUES({uid},10001,'QA-ORG-{actor}','QA-ORG-{actor}',1,'PER_MENU') ON DUPLICATE KEY UPDATE status=1;")
  sql(f"INSERT IGNORE INTO eam_sys_user_role(tenant_id,user_id,role_id) VALUES(10001,{uid},{uid});")
  for j,menu in enumerate(['equipment','org','user','inventoryWarehouse','inventoryReceipt','inventoryOutbound','inventoryQuery']):
   actions=['查看']
   sql(f"INSERT INTO eam_sys_role_menu_permission(id,tenant_id,role_id,menu_key,actions_json,data_scopes_json,org_ids_json) VALUES({uid*10+j},10001,{uid},'{menu}','{json.dumps(actions,ensure_ascii=False)}','{json.dumps([scope])}','{json.dumps(ids)}') ON DUPLICATE KEY UPDATE data_scopes_json=VALUES(data_scopes_json),org_ids_json=VALUES(org_ids_json);")
def check(name,ok=True):
 assert ok,name
 checks.append(name);print('PASS',name)
# Admin uses an existing role in isolated migrated seed DB.
identity('admin',996000,71003)
sql('INSERT IGNORE INTO eam_sys_user_role(tenant_id,user_id,role_id) SELECT 10001,996000,role_id FROM eam_sys_user_role WHERE tenant_id=10001 AND user_id=73001;')
root=call('admin','/system/organizations')['data'][0]
check('legacy group preserved as display identity',root['id']=='71001' and root['type']=='集团')
check('legacy group is canonical company in database',sql('SELECT org_type FROM eam_org_unit WHERE id=71001')=='公司')
seq=str(int(time.time()))
def org(code,type,parent=None):
 return call('admin','/system/organizations',{'name':'QA-ORG-'+code,'code':'QA-ORG-'+seq+'-'+code,'type':type,'parentId':parent,'status':1})['data']
sub=org('sub','公司',71002);dept=org('dept','部门',int(sub['id']));child=org('child','部门',int(dept['id']));hq=org('hq','部门',71001)
check('company supports subsidiary and nested departments')
call('admin','/system/organizations',{'name':'QA-ORG-invalid','code':seq+'-invalid','type':'公司','parentId':int(dept['id'])},status=400)
check('company under department rejected')
call('admin','/system/organizations',{'name':'QA-ORG-root','code':seq+'-root','type':'公司'},status=400)
check('second root company rejected')
cycle={**sub,'parentId':sub['id'],'status':1}
call('admin','/system/organizations/'+sub['id'],cycle,method='PUT',status=400)
check('self cycle rejected')
call('admin','/system/organizations/'+dept['id'],{**dept,'parentId':'71006','status':1},method='PUT',status=400)
check('department cannot silently change company ownership')
# Existing department-bound assets continue to use their closest company.
companies=[71001,71002,int(sub['id']),71006];departments=[int(hq['id']),71003,int(child['id']),71007]
for i,(company,department) in enumerate(zip(companies,departments)):
 sql(f"INSERT INTO eam_equipment(id,tenant_id,equipment_code,name,category,company,department,company_org_id,department_org_id) VALUES({996100+i},10001,'QA-ORG-EQ-{i}','QA-ORG-EQ-{i}','测试','测试','测试',{company},{department}) ON DUPLICATE KEY UPDATE company_org_id=VALUES(company_org_id),department_org_id=VALUES(department_org_id);")
 sql(f"INSERT INTO inv_warehouse(id,tenant_id,company_id,code,name) VALUES({996200+i},10001,{company},'QA-ORG-WH-{i}','QA-ORG-WH-{i}') ON DUPLICATE KEY UPDATE company_id=VALUES(company_id);")
identity('own',996010,71003,'company');identity('tree',996011,71003,'companyTree');identity('selected',996012,71003,'companies',[71001,71006]);identity('hq',996013,int(hq['id']),'company');identity('depttree',996014,int(dept['id']),'departmentTree')
def eq(actor):return {r['code'] for r in call(actor,'/equipment?keyword=QA-ORG-EQ')['data']}
check('own company excludes subsidiary',eq('own')=={'QA-ORG-EQ-1'})
check('company tree includes subsidiary excludes sibling',eq('tree')=={'QA-ORG-EQ-1','QA-ORG-EQ-2'})
check('specified companies exact without cascade',eq('selected')=={'QA-ORG-EQ-0','QA-ORG-EQ-3'})
check('headquarters own scope excludes subsidiaries',eq('hq')=={'QA-ORG-EQ-0'})
check('department tree includes nested department',eq('depttree')=={'QA-ORG-EQ-2'})
call('own','/equipment/996102',status=403);check('direct detail respects scope')
for endpoint in ['receipts/choices','outbounds/choices']:
 for actor,expect in [('own',{'996201'}),('tree',{'996201','996202'}),('hq',{'996200'}),('selected',{'996200','996203'})]:
  rows=call(actor,'/inventory/'+endpoint)['data']['warehouses'];ids={w['id'] for w in rows if str(w['id']).startswith('9962')}
  check(actor+' '+endpoint,ids==expect)
call('own','/inventory/outbounds/materials?warehouseId=996202',status=403);check('unauthorized warehouse picker denied')
call('tree','/inventory/outbounds/materials?warehouseId=996202');check('descendant warehouse picker allowed')
# Group can be a root company without changing identity/children.
rootForm={**root,'type':'公司','status':1,'parentId':None}
result=call('admin','/system/organizations/71001',rootForm,method='PUT');check('single company root display supported',result['data']['type']=='公司')
call('admin','/system/organizations/71001',{**rootForm,'type':'集团'},method='PUT');check('group display toggle preserves ID and data')
warehouse=call('admin','/inventory/masters/warehouses',{'companyId':'71001','name':'QA-ORG-HQ-'+seq,'code':'QA-ORG-HQ-'+seq,'allocationRule':'FIFO','enabled':True})
check('group-owned warehouse can be created')
call('admin','/system/organizations/'+hq['id'],method='DELETE',status=400);check('business referenced department cannot be deleted')
# Validate the public role/user save interfaces, not only seeded grants.
role=call('admin','/system/roles',{'code':'QA-ORG-ROLE-'+seq,'name':'QA-ORG-ROLE-'+seq,'status':1,'menuPermissions':{'equipment':{'actions':['查看'],'dataScopes':['companyTree','departmentTree'],'orgIds':[]},'inventoryOutbound':{'actions':['查看'],'dataScopes':['companies'],'orgIds':[71001,71006]}}})['data']
check('new scopes persist through role API',role['menuPermissions']['equipment']['dataScopes']==['companyTree','departmentTree'])
call('admin','/system/roles',{'code':'QA-ORG-INVALID-'+seq,'name':'invalid','menuPermissions':{'inventoryOutbound':{'actions':['查看'],'dataScopes':['companies'],'orgIds':[71003]}}},status=400)
check('department ID cannot be granted as company')
user=call('admin','/system/users',{'account':'QA-ORG-U-'+seq,'name':'QA-ORG-U-'+seq,'orgId':71001,'status':1,'password':'OrgTest-User-2026!','roleIds':[]})['data']
check('user can belong directly to company',user['orgId']=='71001')
asset=call('admin','/equipment',{'code':'QA-ORG-ASSET-'+seq,'name':'QA-ORG-ASSET-'+seq,'category':'测试','company':root['name'],'department':hq['name'],'departmentOrgId':hq['id'],'state':'正常','useState':'在用','unit':'台','functionLocation':'测试位置'})['data']
check('headquarters equipment creation resolves canonical company',sql('SELECT company_org_id FROM eam_equipment WHERE id='+asset['id'])=='71001')
call('admin','/system/organizations/'+child['id'],method='DELETE',status=400);check('equipment references block leaf department deletion')
free=org('free','部门',71001)
call('admin','/system/organizations/'+free['id'],method='DELETE');check('unused organization can be deleted')
# Two parallel opposite reparent operations: at most one succeeds, no cycle survives.
a=org('race-a','公司',71001);b=org('race-b','公司',71001)
def move(pair):
 node,parent=pair
 try:call('admin','/system/organizations/'+node['id'],{**node,'parentId':parent['id'],'status':1},method='PUT');return True
 except AssertionError as e:
  assert e.args[0][1] in (400,409),e
  return False
with ThreadPoolExecutor(2) as pool:results=list(pool.map(move,[(a,b),(b,a)]))
check('concurrent opposite moves prevent cycles',sum(results)==1)
check('organization changes audited',int(sql('SELECT COUNT(*) FROM eam_org_event WHERE tenant_id=10001'))>=8)
out=pathlib.Path(__file__).resolve().parents[1]/'artifacts/qa/company-hierarchy';out.mkdir(parents=True,exist_ok=True)
(out/'results.json').write_text(json.dumps({'passed':len(checks),'checks':checks,'database':DB},ensure_ascii=False,indent=2))
print('Passed',len(checks),'checks')
