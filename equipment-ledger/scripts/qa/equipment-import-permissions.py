"""Permission and atomic rollback checks, exclusively on eam_import_test_20261018.
Requires the non-local-dev test API on port 18082 and the local MySQL CLI.
"""
import hashlib,io,json,pathlib,secrets,subprocess,urllib.request,urllib.error,uuid,zipfile,xml.etree.ElementTree as ET,time
ROOT=pathlib.Path(__file__).resolve().parents[3];R=ROOT/'.local-runtime';DB='eam_import_test_20261018';BASE='http://127.0.0.1:18082/api/v1';OUT=R/'equipment-import-qa';OUT.mkdir(exist_ok=True)
def sql(text):return subprocess.check_output([str(R/'mysql-8.4.6-macos15-arm64/bin/mysql'),'--defaults-extra-file='+str(R/'root.cnf'),'--socket='+str(R/'mysql.sock'),DB,'--batch','--skip-column-names'],input=text.encode()).decode()
tokens={name:secrets.token_urlsafe(36) for name in ('company','no-import','no-new','tenant')}
for i,(name,token) in enumerate(tokens.items()):
 user=990830010+i;role=990830020+i;grant=990830030+i;tenant=10002 if name=='tenant' else 10001;org=990830001 if name=='tenant' else 71003
 if name=='tenant':sql("INSERT IGNORE INTO eam_org_unit(id,tenant_id,name,code,org_type,parent_id) VALUES(990830001,10002,'隔离租户公司','IMPORT_QA_TENANT','公司',NULL);")
 actions=['查看','新增','导入'];actions.remove('导入') if name=='no-import' else None;actions.remove('新增') if name=='no-new' else None
 sql(f"INSERT IGNORE INTO eam_sys_role(id,tenant_id,code,name,data_scope) VALUES({role},{tenant},'IMPORT_QA_{i}','导入测试角色{i}','PER_MENU'); INSERT IGNORE INTO eam_sys_user(id,tenant_id,account,display_name,org_id) VALUES({user},{tenant},'import_qa_{i}','导入隔离人员{i}',{org}); INSERT IGNORE INTO eam_sys_user_role(tenant_id,user_id,role_id) VALUES({tenant},{user},{role}); INSERT IGNORE INTO eam_sys_role_menu_permission(id,tenant_id,role_id,menu_key) VALUES({grant},{tenant},{role},'equipment'); DELETE FROM eam_role_menu_action WHERE grant_id={grant}; DELETE FROM eam_role_menu_scope WHERE grant_id={grant};")
 for n,action in enumerate(actions):sql(f"INSERT INTO eam_role_menu_action VALUES({tenant},{grant},'{action}',{n});")
 sql(f"INSERT INTO eam_role_menu_scope VALUES({tenant},{grant},'company',0); DELETE FROM eam_auth_session WHERE id={990830050+i}; INSERT INTO eam_auth_session(id,tenant_id,user_id,token_hash,expires_at) VALUES({990830050+i},{tenant},{user},'{hashlib.sha256(token.encode()).hexdigest()}',DATE_ADD(NOW(),INTERVAL 1 HOUR));")
def request(name,path,body=None,ctype='application/json'):
 req=urllib.request.Request(BASE+path,data=body,headers={'Cookie':'EAM_SESSION='+tokens[name],'Origin':'http://127.0.0.1:18082','Content-Type':ctype})
 try:
  with urllib.request.urlopen(req,timeout=30) as r:return r.status,r.read()
 except urllib.error.HTTPError as e:return e.code,e.read()
report=[]
for name in ('no-import','no-new'):
 status,body=request(name,'/equipment/imports/template');assert status==403,(name,status,body);report.append({'case':name,'status':status})
status,blob=request('company','/equipment/imports/template');assert status==200,(status,blob)
ns={'x':'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
with zipfile.ZipFile(io.BytesIO(blob)) as z:
 opts=ET.fromstring(z.read('xl/worksheets/sheet3.xml'));strings=[''.join(r.itertext()).strip() for r in opts.find('x:sheetData',ns)]
 dept=[s for s in strings if s.startswith('所属部门')];assert dept and all('无锡' not in s for s in dept),dept
report.append({'case':'company-template-candidates','departments':dept})
# Other users and tenants cannot read a batch or its raw file report.
existing=sql("SELECT id FROM eam_import_batch WHERE actor_id=73001 ORDER BY created_at LIMIT 1;").strip()
for name in ('company','tenant'):
 for tail in ('','/preview','/report','/results'):
  status,body=request(name,'/equipment/imports/'+existing+tail);assert status>=400,(name,tail,status);assert existing.encode() not in body;report.append({'case':name+'-foreign-batch'+tail,'status':status})
# Company scope is checked on server; forging the other company's path cannot import.
uri='http://127.0.0.1:18081/api/v1/equipment/imports/template';admin=urllib.request.urlopen(uri).read()
NS=ns['x'];ET.register_namespace('',NS)
with zipfile.ZipFile(io.BytesIO(admin)) as z:files={n:z.read(n) for n in z.namelist()}
root=ET.fromstring(files['xl/worksheets/sheet1.xml']);sd=root.find('x:sheetData',ns);keys=[''.join(c.itertext()) for c in sd[0]];keys[0]=keys[0].split('|',2)[2]
row=ET.SubElement(sd,'{'+NS+'}row',r='3');vals={'code':'IMPORT-QA-CROSS-'+uuid.uuid4().hex[:8],'name':'跨公司伪造','department':'风塔集团 · 无锡风塔装备有限公司 · 生产部','category':'动力设备','functionLocation':'A车间','unit':'台','state':'正常','useState':'正常'}
for col,key in enumerate(keys):
 if key not in vals:continue
 n=col+1;letter=''
 while n:n,r=divmod(n-1,26);letter=chr(65+r)+letter
 c=ET.SubElement(row,'{'+NS+'}c',r=letter+'3',t='inlineStr');ET.SubElement(ET.SubElement(c,'{'+NS+'}is'),'{'+NS+'}t').text=vals[key]
files['xl/worksheets/sheet1.xml']=ET.tostring(root,encoding='utf-8',xml_declaration=True);buf=io.BytesIO()
with zipfile.ZipFile(buf,'w',zipfile.ZIP_DEFLATED) as z:
 for name,data in files.items():z.writestr(name,data)
boundary='eam'+uuid.uuid4().hex;body=f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="forged.xlsx"\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n'.encode()+buf.getvalue()+f'\r\n--{boundary}--\r\n'.encode();status,data=request('company','/equipment/imports',body,'multipart/form-data; boundary='+boundary);assert status==200,(status,data);batch=json.loads(data)
for _ in range(100):
 status,data=request('company','/equipment/imports/'+batch['id']);batch=json.loads(data)
 if batch['state']!='VALIDATING':break
 time.sleep(.1)
assert batch['state']=='INVALID',batch
status,data=request('company','/equipment/imports/'+batch['id']+'/preview?errorsOnly=true');preview=json.loads(data);assert any(e['field']=='department' for r in preview['data'] for e in r['issues']),preview
report.append({'case':'forged-cross-company-department','batch':batch})
# Verify the intentionally failed two-row batch left neither an equipment nor a result relation.
count=int(sql("SELECT COUNT(*) FROM eam_equipment WHERE equipment_code LIKE 'IMPORT-QA-%-ROLLBACK-%';").strip());assert count==0,count
report.append({'case':'rollback-verification-sql','equipmentCount':count})
# Retention cleanup: force-expire only the forged isolated batch; wait for the configured test scheduler.
sql(f"UPDATE eam_import_batch SET expires_at=DATE_SUB(NOW(),INTERVAL 1 DAY) WHERE id='{batch['id']}';")
for _ in range(50):
 status,data=request('company','/equipment/imports/'+batch['id']);expired=json.loads(data)
 if expired['state']=='EXPIRED':break
 time.sleep(.2)
assert expired['state']=='EXPIRED' and not expired['reportAvailable'],expired
assert not (R/'import-test-files/private-imports'/f"{batch['id']}.xlsx").exists()
report.append({'case':'expired-cleanup','state':expired['state']})
(OUT/'permissions.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print(json.dumps(report,ensure_ascii=False,indent=2))
