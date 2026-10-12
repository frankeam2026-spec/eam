#!/usr/bin/env python3
"""Acceptance for inventory list update. Writes only eam_outbound_test on API 8081."""
import hashlib, json, pathlib, re, secrets, subprocess, urllib.request, urllib.parse, urllib.error
ROOT = pathlib.Path(__file__).resolve().parents[1]
RUNTIME = ROOT.parent / '.local-runtime'
DB = 'eam_outbound_test'
BASE = 'http://127.0.0.1:8081/api/v1'
assert DB == 'eam_outbound_test' and ':8081/' in BASE
suffix=secrets.token_hex(4)
token=secrets.token_urlsafe(32)
uid=998101;rid=998201;wid=998901;bid=998902

def sql(statement):
    result=subprocess.run([str(RUNTIME/'mysql-8.4.6-macos15-arm64/bin/mysql'),'--defaults-extra-file='+str(RUNTIME/'root.cnf'),'--socket='+str(RUNTIME/'mysql.sock'),'--batch','--skip-column-names',DB],input=statement,text=True,capture_output=True,check=True)
    return result.stdout.strip()
# Fixed test account is confined to the isolated database.
sql(f"""
INSERT INTO eam_base_item(id,tenant_id,item_type,code,name,status) VALUES(998801,10001,'UNIT','QA-LIST-UNIT','QA 件',1),(998802,10001,'MATERIAL_CATEGORY','QA-LIST-CATEGORY','QA 测试',1) ON DUPLICATE KEY UPDATE status=1;
INSERT INTO eam_sys_user(id,tenant_id,account,display_name,org_id) VALUES({uid},10001,'qa-inventory-lists','QA 列表操作人',71003) ON DUPLICATE KEY UPDATE display_name='QA 列表操作人';
INSERT INTO eam_sys_role(id,tenant_id,code,name,data_scope) VALUES({rid},10001,'QA-INVENTORY-LISTS','QA 列表权限','PER_MENU') ON DUPLICATE KEY UPDATE name='QA 列表权限';
INSERT IGNORE INTO eam_sys_user_role(tenant_id,user_id,role_id) VALUES(10001,{uid},{rid});
INSERT INTO eam_auth_session(id,tenant_id,user_id,token_hash,expires_at) VALUES(998301,10001,{uid},'{hashlib.sha256(token.encode()).hexdigest()}',DATE_ADD(NOW(),INTERVAL 1 DAY)) ON DUPLICATE KEY UPDATE token_hash=VALUES(token_hash),expires_at=VALUES(expires_at);
INSERT INTO inv_warehouse(id,tenant_id,company_id,code,name) VALUES({wid},10001,71002,'QA-LIST-W','QA 列表验证仓') ON DUPLICATE KEY UPDATE name='QA 列表验证仓';
INSERT INTO inv_bin(id,tenant_id,warehouse_id,code,name) VALUES({bid},10001,{wid},'QA-LIST-B','QA 库位') ON DUPLICATE KEY UPDATE name='QA 库位';
""")
for i,(menu,actions) in enumerate({'inventoryMaterial':['查看','新增','编辑','查看金额','编辑参考价'],'inventoryReceipt':['查看','新增','编辑','入库','导出','查看金额']}.items()):
    scopes=['all'] if menu=='inventoryMaterial' else ['warehouse']
    orgs=[] if menu=='inventoryMaterial' else [wid]
    sql(f"INSERT INTO eam_sys_role_menu_permission(id,tenant_id,role_id,menu_key,actions_json,data_scopes_json,org_ids_json) VALUES({998401+i},10001,{rid},'{menu}','{json.dumps(actions,ensure_ascii=False)}','{json.dumps(scopes)}','{json.dumps(orgs)}') ON DUPLICATE KEY UPDATE actions_json=VALUES(actions_json),data_scopes_json=VALUES(data_scopes_json),org_ids_json=VALUES(org_ids_json);")

def call(method,path,body=None):
    req=urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body).encode(),method=method,headers={'Cookie':'EAM_SESSION='+token,'Content-Type':'application/json','Origin':'http://127.0.0.1:8081'})
    try:
        with urllib.request.urlopen(req) as response:return json.load(response)
    except urllib.error.HTTPError as error:
        raise AssertionError(error.read().decode()) from None

report=[]
def check(name,ok):
    assert ok,name
    report.append(name);print('PASS',name)
materials=[]
for n in range(3):
    materials.append(call('POST','/inventory/masters/materials',{'code':'','name':f'QA-{suffix}-{n}','unit':'QA 件','model':'QA-LIST-'+suffix,'category':'QA 测试','usageType':'消耗型','batchEnabled':False,'expiryEnabled':False,'enabled':True,'referencePrice':'1.25','remark':'隔离验收'})['data'])
check('new material code uses WL plus at least five digits',all(re.fullmatch(r'WL\d{5,}',m['code']) for m in materials))
check('material code sequence increases without month',int(materials[1]['code'][2:])==int(materials[0]['code'][2:])+1)
q=urllib.parse.quote('QA-LIST-'+suffix)
p1=call('GET',f'/inventory/receipts/materials?q={q}&page=1&size=2')
p2=call('GET',f'/inventory/receipts/materials?q={q}&page=2&size=2')
check('selection endpoint searches model and paginates',p1['page']['total']==3 and len(p1['data'])==2 and len(p2['data'])==1)
check('selection endpoint never exposes prices',all('referencePrice' not in m for m in p1['data']))
check('receipt choices no longer downloads entire material catalogue',call('GET','/inventory/receipts/choices')['data']['materials']==[])
receipt=call('POST','/inventory/receipts',{'warehouseId':str(wid),'supplier':'QA-'+suffix,'remark':'隔离验证','lines':[{'materialId':m['id'],'binId':str(bid),'quantity':'2','unitPrice':'1.25','allocatedFee':'0','taxRate':'0','currency':'CNY'} for m in materials]})['data']
found=call('GET','/inventory/receipts?'+urllib.parse.urlencode({'q':receipt['code'],'filters':json.dumps({'actorName':'QA 列表操作人'})}))['data']
check('receipt actor name available and filterable',len(found)==1 and found[0]['actorName']=='QA 列表操作人')
exported=call('GET','/inventory/receipts/export?'+urllib.parse.urlencode({'q':receipt['code'],'filters':json.dumps({'actorName':'QA 列表操作人'})}))['data']
check('receipt export includes operation actor',len(exported)==1 and exported[0]['actorName']=='QA 列表操作人')
confirmed=call('POST',f"/inventory/receipts/{receipt['id']}/confirm",{'rowVersion':receipt['rowVersion'],'idempotencyKey':secrets.token_hex(12)})['data']
check('new multi-material receipt still confirms correctly',confirmed['status']=='已入库' and len(confirmed['lines'])==3)
out=ROOT/'artifacts/qa/inventory-list-update';out.mkdir(parents=True,exist_ok=True)
(out/'api-results.json').write_text(json.dumps({'database':DB,'passed':len(report),'checks':report},ensure_ascii=False,indent=2))
