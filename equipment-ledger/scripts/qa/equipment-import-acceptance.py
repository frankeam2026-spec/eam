"""Equipment import acceptance. Mutating calls are restricted to an explicitly isolated endpoint.
Usage: python3 scripts/qa/equipment-import-acceptance.py --url http://127.0.0.1:18081 --isolated-database eam_import_test_20261018 --output /absolute/report-directory
Uses only the Python standard library. The server must use the named disposable DB; never point at the normal local server.
"""
import argparse, concurrent.futures, hashlib, io, json, pathlib, time, urllib.request, urllib.error, uuid, zipfile, subprocess, xml.etree.ElementTree as ET
P=argparse.ArgumentParser();P.add_argument('--url',required=True);P.add_argument('--isolated-database',required=True);P.add_argument('--output',required=True);A=P.parse_args()
assert A.isolated_database.startswith('eam_import_test_') and ':18081' in A.url, 'Only the dedicated isolated import test server is allowed'
OUT=pathlib.Path(A.output);OUT.mkdir(parents=True,exist_ok=True)
NS='http://schemas.openxmlformats.org/spreadsheetml/2006/main';ET.register_namespace('',NS)
def req(path,body=None,content='application/json',raw=False):
 r=urllib.request.Request(A.url+'/api/v1'+path,data=body,headers={'Content-Type':content})
 try:
  with urllib.request.urlopen(r,timeout=120) as res:b=res.read()
  return b if raw else json.loads(b)
 except urllib.error.HTTPError as e:raise AssertionError(f'{path}: HTTP {e.code} {e.read().decode()}')
def text(c):return ''.join(c.itertext()) if c.find('{'+NS+'}is') is not None else c.findtext('{'+NS+'}v','')
def sheet(blob,name):
 with zipfile.ZipFile(io.BytesIO(blob)) as z:return ET.fromstring(z.read(name))
def rows(blob,name):return [[text(c) for c in r] for r in sheet(blob,name).find('{'+NS+'}sheetData')]
# Each run starts with disabled prior custom test fields; identities/history are retained.
current=req('/business-config/objects/equipment')
if any(f.get('enabled',True) for f in current['draft']['fields']):
    configuration=current['draft']
    for old_field in configuration['fields']:old_field['enabled']=False
    request=urllib.request.Request(A.url+'/api/v1/business-config/objects/equipment/draft',data=json.dumps({'version':current['version'],'configuration':configuration}).encode(),headers={'Content-Type':'application/json'},method='PUT')
    with urllib.request.urlopen(request) as response:saved=json.load(response)
    req('/business-config/objects/equipment/publish',json.dumps({'version':saved['version']}).encode())
template=req('/equipment/imports/template',raw=True);(OUT/'template.xlsx').write_bytes(template)
print('TEMPLATE',len(template),flush=True)
base_rows=rows(template,'xl/worksheets/sheet1.xml');keys=base_rows[0];keys[0]=keys[0].split('|',2)[2]
choices={}
for row in rows(template,'xl/worksheets/sheet3.xml')[1:]:
 if len(row)>=2:choices.setdefault(row[0],[]).append(row[1])
(OUT/'choices.json').write_text(json.dumps(choices,ensure_ascii=False,indent=2))
print('CHOICES',json.dumps(choices,ensure_ascii=False)[:1500],flush=True)
def xlsx(data,formula=None):
 with zipfile.ZipFile(io.BytesIO(template)) as z:
  original={n:z.read(n) for n in z.namelist()}
 xml=ET.fromstring(original['xl/worksheets/sheet1.xml']);sd=xml.find('{'+NS+'}sheetData')
 for old in list(sd)[2:]:sd.remove(old)
 def letter(n):
  s='';n+=1
  while n:n,r=divmod(n-1,26);s=chr(65+r)+s
  return s
 for index,record in enumerate(data,3):
  r=ET.SubElement(sd,'{'+NS+'}row',r=str(index))
  for col,key in enumerate(keys):
   val=record.get(key,'');
   if val=='':continue
   c=ET.SubElement(r,'{'+NS+'}c',r=letter(col)+str(index),t='inlineStr')
   if formula and (index,key) in formula:
    del c.attrib['t'];ET.SubElement(c,'{'+NS+'}f').text=val;ET.SubElement(c,'{'+NS+'}v').text='0'
   else:ET.SubElement(ET.SubElement(c,'{'+NS+'}is'),'{'+NS+'}t').text=str(val)
 original['xl/worksheets/sheet1.xml']=ET.tostring(xml,encoding='utf-8',xml_declaration=True)
 buf=io.BytesIO()
 with zipfile.ZipFile(buf,'w',zipfile.ZIP_DEFLATED) as z:
  for name,data in original.items():z.writestr(name,data)
 return buf.getvalue()
def upload(blob,name='sample.xlsx'):
 boundary='eam'+uuid.uuid4().hex
 body=f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{name}"\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n'.encode()+blob+f'\r\n--{boundary}--\r\n'.encode()
 return req('/equipment/imports',body,'multipart/form-data; boundary='+boundary)
def wait(batch,limit=240):
 end=time.monotonic()+limit
 while time.monotonic()<end:
  v=req('/equipment/imports/'+batch['id'])
  if v['state'] not in ('VALIDATING','IMPORTING'):return v
  time.sleep(.3)
 raise AssertionError('Batch timed out: '+batch['id'])
def confirm(b,key=None):return req('/equipment/imports/'+b['id']+'/confirm',json.dumps({'rowVersion':b['rowVersion'],'idempotencyKey':key or str(uuid.uuid4())}).encode())
# Seed migrations supply candidate lists. Department/location pairing is explicit, never fuzzy.
valid={'name':'隔离测试空压机','department':next(x for x in choices['所属部门'] if '苏州' in x),'category':choices['设备类别'][0],'functionLocation':choices['位置'][0],'unit':choices['单位'][0],'state':'正常','useState':choices['使用状态'][0],'owner':choices['负责人账号'][0],'assetNo':'0000123','amount':'12.30','purchaseDate':'2026-10-18'}
report=[]
def record(name,data):report.append({'case':name,**data});(OUT/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print(name,json.dumps(data,ensure_ascii=False),flush=True)
prefix='IMPORT-QA-'+uuid.uuid4().hex[:8]
# Valid + parent-child, preserving leading zeroes and restoring pagination.
a={**valid,'code':prefix+'-P'};b={**valid,'code':prefix+'-C','parentCode':a['code']}
blob=xlsx([a,b]);(OUT/'valid.xlsx').write_bytes(blob);batch=wait(upload(blob));record('valid-preview',batch)
assert batch['state']=='READY', req('/equipment/imports/'+batch['id']+'/preview?errorsOnly=true')
key=str(uuid.uuid4());confirm(batch,key);done=wait(batch);assert done['state']=='SUCCEEDED',done
again=confirm(batch,key);assert again['successRows']==2
res=req('/equipment/imports/'+batch['id']+'/results?page=1&size=1');assert res['page']['total']==2 and len(res['data'])==1
all_rows=req('/equipment/imports/'+batch['id']+'/results?page=1&size=20')['data']
parent=next(r for r in all_rows if r['code']==a['code']);child=next(r for r in all_rows if r['code']==b['code'])
detail=req('/equipment/'+parent['id']);detail=detail.get('data',detail)
assert any(str(r.get('equipmentId'))==child['id'] for r in detail['children']),detail
assert detail['assetNo']=='0000123' and detail['amount']=='12.30',detail
record('parent-and-idempotency',done)
# Errors do not write. All failures from a single row should be available together.
bad={**valid,'code':a['code'],'name':'','unit':'不存在的单位','purchaseDate':'2026-02-30','owner':'不存在的账号'}
batch=wait(upload(xlsx([bad])));assert batch['state']=='INVALID',batch
preview=req('/equipment/imports/'+batch['id']+'/preview?errorsOnly=true');assert batch['errorCount']>=5,preview
raw=req('/equipment/imports/'+batch['id']+'/report',raw=True);(OUT/'errors.xlsx').write_bytes(raw)
record('multi-field-errors',{'batch':batch,'preview':preview})
# Error report is accepted again as the original template (helper columns are ignored).
roundtrip=wait(upload(raw));assert roundtrip['state']=='INVALID' and roundtrip['totalRows']==1
record('report-reupload',roundtrip)
# Formula, cycle, file duplicates.
for name,data,formula in [
 ('formula',[{**valid,'code':prefix+'-F','amount':'1+2'}],{(3,'amount')}),
 ('cycle',[{**valid,'code':prefix+'-A','parentCode':prefix+'-B'},{**valid,'code':prefix+'-B','parentCode':prefix+'-A'}],None),
 ('duplicate',[{**valid,'code':prefix+'-D'},{**valid,'code':prefix+'-D'}],None)]:
 batch=wait(upload(xlsx(data,formula)));assert batch['state']=='INVALID',batch;record(name,batch)
# Confirm-time change: another batch claims the same code after preview.
collision=xlsx([{**valid,'code':prefix+'-RACE'}]);first=wait(upload(collision));second=wait(upload(collision));assert first['state']==second['state']=='READY'
confirm(first);assert wait(first)['state']=='SUCCEEDED';confirm(second);assert wait(second)['state']=='INVALID';record('concurrent-duplicate-revalidation',wait(second))
def query_counts():
 runtime=pathlib.Path(__file__).resolve().parents[3]/'.local-runtime'
 if not (runtime/'root.cnf').exists():return None
 query="SELECT COALESCE(SUM(CASE WHEN DIGEST_TEXT LIKE 'SELECT %' THEN COUNT_STAR ELSE 0 END),0),COALESCE(SUM(COUNT_STAR),0) FROM performance_schema.events_statements_summary_by_digest WHERE SCHEMA_NAME='"+A.isolated_database+"';"
 try:
  raw=subprocess.check_output([str(runtime/'mysql-8.4.6-macos15-arm64/bin/mysql'),'--defaults-extra-file='+str(runtime/'root.cnf'),'--socket='+str(runtime/'mysql.sock'),'--batch','--skip-column-names','-e',query]).decode().strip().split('\t')
  return list(map(int,raw))
 except Exception:return None
# Large file: bounded asynchronous preview and atomic commit of 10,000 rows.
large=xlsx([{**valid,'code':prefix+'-'+str(i),'name':'隔离批量设备'+str(i),'parentCode':''} for i in range(10000)])
before_counts=query_counts();start=time.monotonic();batch=wait(upload(large),480);check_seconds=time.monotonic()-start;assert batch['state']=='READY',batch
start=time.monotonic();confirm(batch);done=wait(batch,600);commit_seconds=time.monotonic()-start;assert done['state']=='SUCCEEDED' and done['successRows']==10000,done
page=req('/equipment/imports/'+done['id']+'/results?page=500&size=20');assert page['page']['total']==10000 and len(page['data'])==20
after_counts=query_counts();record('10000-rows',{'batch':done,'fileBytes':len(large),'validationSeconds':round(check_seconds,3),'commitSeconds':round(commit_seconds,3),'schemaSelectStatements':after_counts[0]-before_counts[0] if before_counts and after_counts else None,'schemaAllStatements':after_counts[1]-before_counts[1] if before_counts and after_counts else None})
# Exact limit rejection.
extra=xlsx([{**valid,'code':prefix+'-OVER-'+str(i)} for i in range(10001)]);batch=wait(upload(extra));assert batch['state']=='FAILED' and '10000' in batch['message'];record('row-limit',batch)
print('ACCEPTANCE PASSED',flush=True)
# Additional transactional, Unicode-collation, numbering and custom-field coverage.
rollback=wait(upload(xlsx([{**valid,'code':prefix+'-ROLLBACK-1'},{**valid,'code':prefix+'-ROLLBACK-2','name':'隔离回滚触发'}])))
assert rollback['state']=='READY';confirm(rollback);failed=wait(rollback);assert failed['state']=='FAILED' and failed['successRows']==0;record('database-failure-rolls-back-whole-batch',failed)
# Same collation as the official table (case- and accent-insensitive).
accent=wait(upload(xlsx([{**valid,'code':prefix+'-Accent-E'},{**valid,'code':prefix+'-Accent-É'}])))
assert accent['state']=='INVALID' and accent['errorRows']==2;record('native-code-collation',accent)
auto=wait(upload(xlsx([{**valid,'code':'','name':'自动编号设备1'},{**valid,'code':'','name':'自动编号设备2'}])))
assert auto['state']=='READY' and auto['autoNumberRows']==2;confirm(auto);auto=wait(auto);assert auto['successRows']==2;record('automatic-numbering',auto)
# Configuration changes happen only in this isolated database.
def put(path,body):
 r=urllib.request.Request(A.url+'/api/v1'+path,data=json.dumps(body).encode(),headers={'Content-Type':'application/json'},method='PUT')
 with urllib.request.urlopen(r,timeout=60) as res:return json.load(res)
def field(name,type='TEXT',**extra):
 return {'id':str(uuid.uuid4()),'name':name,'type':type,'source':'','enabled':True,'required':False,'group':'扩展信息','width':'half','span':1,'sortOrder':0,'description':'','placeholder':'','maxLength':200,'min':'','max':'','multiple':False,'listable':True,'filterable':True,'exportable':True,'visibleWhen':{'mode':'all','items':[]},'requiredWhen':{'mode':'all','items':[]},**extra}
schema=req('/business-config/objects/equipment');fields=schema['draft']['fields']
for old in fields:old['enabled']=False
number=field('隔离功率'+prefix,'NUMBER',required=True,min='0');boolean=field('隔离是非'+prefix,'BOOLEAN',required=True)
option_ids=[str(uuid.uuid4()),str(uuid.uuid4())]
choice=field('隔离工种'+prefix,'CHOICE',required=True,options=[{'id':option_ids[0],'name':'电工','enabled':True,'sortOrder':0},{'id':option_ids[1],'name':'机修','enabled':True,'sortOrder':1}])
hidden=field('隔离条件文字'+prefix,visibleWhen={'mode':'all','items':[{'field':boolean['id'],'operator':'eq','values':['true']}]})
user=field('隔离协同人员'+prefix,'USER',source='user',multiple=True);department=field('隔离关联部门'+prefix,'DEPARTMENT',source='department')
new_fields=[number,boolean,choice,hidden,user,department]
for i,f in enumerate(new_fields):f['sortOrder']=len(fields)+i
saved=put('/business-config/objects/equipment/draft',{'version':schema['version'],'configuration':{'fields':fields+new_fields,'layout':{'columns':2,'maxWidth':0}}})
pub=req('/business-config/objects/equipment/publish',json.dumps({'version':saved['version']}).encode())
# Reusing an older template produces a configuration conflict and writes nothing.
old=wait(upload(xlsx([{**valid,'code':prefix+'-OLD'}])));assert old['state']=='FAILED' and '配置' in old['message'];record('old-template-conflict',old)
template=req('/equipment/imports/template',raw=True);(OUT/'custom-template.xlsx').write_bytes(template);keys=rows(template,'xl/worksheets/sheet1.xml')[0];keys[0]=keys[0].split('|',2)[2]
custom={**valid,'code':prefix+'-CUSTOM','custom:'+number['id']:'0','custom:'+boolean['id']:'否','custom:'+choice['id']:'电工','custom:'+user['id']:'admin；zhangjianguo','custom:'+department['id']:valid['department']}
batch=wait(upload(xlsx([custom])));assert batch['state']=='READY',req('/equipment/imports/'+batch['id']+'/preview?errorsOnly=true');confirm(batch);done=wait(batch);assert done['state']=='SUCCEEDED',done
row=req('/equipment/imports/'+done['id']+'/results')['data'][0];equipment=req('/equipment/'+row['id']);equipment=equipment.get('data',equipment)
assert str(equipment['customFields'][number['id']]) in ('0','0.000000','0.00');assert equipment['customFields'][boolean['id']] is False;assert equipment['customFields'][choice['id']]==option_ids[0]
assert len(equipment['customFields'][user['id']])==2 and equipment['customFields'][department['id']]
record('custom-values-and-reference-ids',{'batch':done,'equipmentId':row['id'],'customFields':equipment['customFields']})
for name,changes in [('hidden-value',{'custom:'+hidden['id']:'不应填写'}),('required-custom',{'custom:'+number['id']:''}),('bad-choice',{'custom:'+choice['id']:'不存在的工种'})]:
 batch=wait(upload(xlsx([{**custom,'code':prefix+'-'+name,**changes}])));assert batch['state']=='INVALID',batch;record(name,batch)
# Preview an up-to-date template, then publish a new version before confirmation.
conflict=wait(upload(xlsx([{**custom,'code':prefix+'-CONFIG-RACE'}])));assert conflict['state']=='READY'
schema=req('/business-config/objects/equipment');saved=put('/business-config/objects/equipment/draft',{'version':schema['version'],'configuration':schema['draft']});req('/business-config/objects/equipment/publish',json.dumps({'version':saved['version']}).encode());confirm(conflict);done=wait(conflict);assert done['state']=='FAILED' and '配置' in done['message'];record('confirm-configuration-conflict',done)
print('EXTENDED ACCEPTANCE PASSED',flush=True)
