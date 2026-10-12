#!/usr/bin/env python3
"""Warehouse scope and stock picker checks; writes only eam_outbound_test, API 8081."""
import json, hashlib, secrets, subprocess, pathlib, urllib.request, urllib.error, urllib.parse
R=pathlib.Path(__file__).resolve().parents[2]/'.local-runtime'
DB='eam_outbound_test';BASE='http://127.0.0.1:8081/api/v1';assert DB!='eam_demo'
def sql(s):
 return subprocess.run([str(R/'mysql-8.4.6-macos15-arm64/bin/mysql'),'--defaults-extra-file='+str(R/'root.cnf'),'--socket='+str(R/'mysql.sock'),DB],input=s,text=True,capture_output=True,check=True).stdout
sql("""INSERT INTO inv_material(id,tenant_id,code,name,unit,usage_type,batch_enabled,expiry_enabled,enabled) VALUES(9939001,10001,'QA-SCOPE-STOCK','QA 权限库存物料','QA 件','消耗型',1,1,1) ON DUPLICATE KEY UPDATE enabled=1;
INSERT INTO inv_batch(id,tenant_id,material_id,code,expiry_date) VALUES(9939101,10001,9939001,'QA-SCOPE-VALID',DATE_ADD(CURRENT_DATE(),INTERVAL 30 DAY)),(9939102,10001,9939001,'QA-SCOPE-EXPIRED',DATE_SUB(CURRENT_DATE(),INTERVAL 1 DAY)),(9939103,10001,9939001,'QA-SCOPE-MISSING',NULL) ON DUPLICATE KEY UPDATE expiry_date=VALUES(expiry_date);
INSERT INTO inv_balance(id,tenant_id,company_id,warehouse_id,bin_id,material_id,batch_id,stock_status,quantity,reserved_quantity) VALUES(9939201,10001,71002,998901,998902,9939001,9939101,'可用',6.75,1.25),(9939202,10001,71002,998901,998902,9939001,9939101,'冻结',3,0),(9939203,10001,71002,998901,998902,9939001,9939102,'可用',2,0),(9939204,10001,71002,998901,998902,9939001,9939103,'可用',4,0),(9939205,10001,71006,995002,995013,9939001,9939101,'可用',99,0) ON DUPLICATE KEY UPDATE quantity=VALUES(quantity),reserved_quantity=VALUES(reserved_quantity);
""")
tokens={}
for i,actor in enumerate(['companyA','companyB','selectedB'],1):
 uid=993100+i;rid=993200+i;token=secrets.token_urlsafe(36);tokens[actor]=token
 org=71007 if actor=='companyB' else 71003
 sql(f"""INSERT INTO eam_sys_user(id,tenant_id,account,display_name,org_id) VALUES({uid},10001,'qa-scope-{actor}','QA {actor}',{org}) ON DUPLICATE KEY UPDATE org_id=VALUES(org_id);
INSERT INTO eam_sys_role(id,tenant_id,code,name,data_scope) VALUES({rid},10001,'QA-SCOPE-{actor}','QA {actor}','PER_MENU') ON DUPLICATE KEY UPDATE data_scope='PER_MENU';
INSERT IGNORE INTO eam_sys_user_role(tenant_id,user_id,role_id) VALUES(10001,{uid},{rid});
INSERT INTO eam_auth_session(id,tenant_id,user_id,token_hash,expires_at) VALUES({993300+i},10001,{uid},'{hashlib.sha256(token.encode()).hexdigest()}',DATE_ADD(NOW(),INTERVAL 1 DAY)) ON DUPLICATE KEY UPDATE token_hash=VALUES(token_hash),expires_at=VALUES(expires_at);
""")
 for j,menu in enumerate(['inventoryReceipt','inventoryOutbound','inventoryQuery','inventoryWarehouse']):
  scope=['all'] if menu=='inventoryWarehouse' else ['warehouse'] if actor=='selectedB' else ['company']
  ids=[995002] if actor=='selectedB' and menu!='inventoryWarehouse' else []
  actions=['查看','导出']
  sql(f"INSERT INTO eam_sys_role_menu_permission(id,tenant_id,role_id,menu_key,actions_json,data_scopes_json,org_ids_json) VALUES({993400+i*10+j},10001,{rid},'{menu}','{json.dumps(actions,ensure_ascii=False)}','{json.dumps(scope)}','{json.dumps(ids)}') ON DUPLICATE KEY UPDATE actions_json=VALUES(actions_json),data_scopes_json=VALUES(data_scopes_json),org_ids_json=VALUES(org_ids_json);")
def call(actor,path,status=200):
 req=urllib.request.Request(BASE+path,headers={'Cookie':'EAM_SESSION='+tokens[actor]})
 try:
  with urllib.request.urlopen(req) as r:actual=r.status;data=json.load(r)
 except urllib.error.HTTPError as e:actual=e.code;data=json.loads(e.read())
 assert actual==status,(actor,path,actual,data)
 return data
checks=[]
def check(name,ok):
 assert ok,name
 checks.append(name);print('PASS',name)
for actor,company in [('companyA','71002'),('companyB','71006')]:
 for endpoint in ['receipts/choices','outbounds/choices']:
  rows=call(actor,'/inventory/'+endpoint)['data']['warehouses']
  check(actor+' '+endpoint+' filters own company',bool(rows) and all(w['companyId']==company for w in rows))
 balances=call(actor,'/inventory/queries/balances?q=QA-SCOPE-STOCK')['data']
 check(actor+' inventory records stay in own company',bool(balances) and all(str(r['warehouseId'])!='995002' if actor=='companyA' else str(r['warehouseId'])=='995002' for r in balances))
for endpoint in ['receipts/choices','outbounds/choices']:
 rows=call('selectedB','/inventory/'+endpoint)['data']['warehouses']
 check('explicit warehouse '+endpoint+' sees only B',len(rows)==1 and str(rows[0]['id'])=='995002')
stock=call('companyA','/inventory/outbounds/materials?q=QA-SCOPE-STOCK&warehouseId=998901')['data'][0]
from decimal import Decimal as D
check('picker physical includes frozen and expired',D(stock['stockQuantity'])==D('15.75'))
check('picker reserved belongs to selected warehouse',D(stock['reservedQuantity'])==D('1.25'))
check('available excludes reserved frozen expired and missing expiry',D(stock['availableQuantity'])==D('5.50'))
check('cross-company warehouse stock request denied',bool(call('companyA','/inventory/outbounds/materials?warehouseId=995002',403).get('message')))
check('selected warehouse may be explicitly granted across company',D(call('selectedB','/inventory/outbounds/materials?q=QA-SCOPE-STOCK&warehouseId=995002')['data'][0]['stockQuantity'])==D('99'))
rows=call('selectedB','/inventory/queries/balances?q=QA-SCOPE-STOCK')['data']
check('selected warehouse limits inventory query',len(rows)==1 and str(rows[0]['warehouseId'])=='995002')
out=pathlib.Path(__file__).resolve().parents[1]/'artifacts/qa/warehouse-access';out.mkdir(parents=True,exist_ok=True)
(out/'results.json').write_text(json.dumps({'passed':len(checks),'checks':checks,'database':DB},ensure_ascii=False,indent=2))
