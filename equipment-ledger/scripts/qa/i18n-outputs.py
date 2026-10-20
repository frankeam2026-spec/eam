"""Output checks on the isolated API; only validation-batch metadata is created, never equipment or print jobs."""
import json,urllib.request,pathlib,io,zipfile,xml.etree.ElementTree as ET
from pypdf import PdfReader
import subprocess
OUT=pathlib.Path('artifacts/qa/i18n');OUT.mkdir(parents=True,exist_ok=True);B='http://127.0.0.1:18083/api/v1'
def req(path,loc,body=None):
 r=urllib.request.Request(B+path,data=None if body is None else json.dumps(body,ensure_ascii=False).encode(),headers={'Content-Type':'application/json','Accept-Language':loc,'Origin':'http://127.0.0.1:18083'})
 return urllib.request.urlopen(r,timeout=60).read()
results=[]
for loc in ['zh-CN','zh-TW','en-US']:
 catalog=json.loads(req('/printing/catalog',loc))['data'];config=catalog['defaults'];template={'name':'QA preview','companyId':None,'enabled':True,'isDefault':False,'rowVersion':0,'config':config}
 data=req('/printing/templates/preview',loc,template);assert data.startswith(b'%PDF');reader=PdfReader(io.BytesIO(data));text='\n'.join(p.extract_text() for p in reader.pages);assert config['title'] in text,(loc,config['title'],text[:300]);(OUT/('print-'+loc+'.pdf')).write_bytes(data)
 subprocess.run(['/Users/frank/.cache/codex-runtimes/codex-primary-runtime/dependencies/bin/override/pdftoppm','-f','1','-singlefile','-r','96','-png',str(OUT/('print-'+loc+'.pdf')),str(OUT/('print-'+loc))],check=True)
 # Custom titles/labels are customer content, preserved in any language.
 config['title']='客户自定义标题 CUSTOMER TITLE';custom=req('/printing/templates/preview',loc,template);ct='\n'.join(p.extract_text() for p in PdfReader(io.BytesIO(custom)).pages);assert config['title'] in ct
 # Stable machine column keys and translated visible headings. Formula ranges must reference the actual localized sheet.
 xlsx=req('/equipment/imports/template',loc);ns={'s':'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
 with zipfile.ZipFile(io.BytesIO(xlsx)) as z:
  w=ET.fromstring(z.read('xl/workbook.xml'));names=[s.attrib['name'] for s in w.find('s:sheets',ns)];first=ET.fromstring(z.read('xl/worksheets/sheet1.xml'));rows=first.find('s:sheetData',ns);keys=[''.join(c.itertext()) for c in rows[0]];assert keys[0].endswith('|code') and any(k=='name' for k in keys)
  for name in w.findall('s:definedNames/s:definedName',ns):assert names[2] in name.text,(loc,names,name.text)
 results.append({'locale':loc,'templateSheets':names,'printPages':len(reader.pages),'customTitlePreserved':True,'stableImportColumns':True});print(loc,'PASS',flush=True)
# Query, not data writes: exports use translated system headings and original business names.
for path in ['/equipment/export?fields=code,name','/maintenance/tasks/export?fields=code,planName','/inspection/daily-tasks/export?fields=code,planName']:
 try:
  data=req(path,'en-US');header=data.decode('utf-8-sig').splitlines()[0];assert '编号' not in header and '计划名称' not in header;results.append({'export':path,'header':header})
 except urllib.error.HTTPError as e:
  raise AssertionError((path,e.code,e.read()[:300]))
# An English validation report must translate newly generated diagnostic reasons and suggestions.
import time,uuid,re
xlsx=req('/equipment/imports/template','en-US');ns={'s':'http://schemas.openxmlformats.org/spreadsheetml/2006/main'};NS=ns['s'];ET.register_namespace('',NS)
with zipfile.ZipFile(io.BytesIO(xlsx)) as z:files={name:z.read(name) for name in z.namelist()}
first=ET.fromstring(files['xl/worksheets/sheet1.xml']);data=first.find('s:sheetData',ns);keys=[''.join(c.itertext()) for c in data[0]];row=ET.SubElement(data,'{'+NS+'}row',r='3');index=keys.index('name');n=index+1;letter=''
while n:n,r=divmod(n-1,26);letter=chr(65+r)+letter
cell=ET.SubElement(row,'{'+NS+'}c',r=letter+'3',t='inlineStr');ET.SubElement(ET.SubElement(cell,'{'+NS+'}is'),'{'+NS+'}t').text='QA English report'
files['xl/worksheets/sheet1.xml']=ET.tostring(first,encoding='utf-8',xml_declaration=True);buf=io.BytesIO()
with zipfile.ZipFile(buf,'w',zipfile.ZIP_DEFLATED) as z:
 for name,blob in files.items():z.writestr(name,blob)
boundary='eam'+uuid.uuid4().hex;body=f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="english-report.xlsx"\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n'.encode()+buf.getvalue()+f'\r\n--{boundary}--\r\n'.encode()
r=urllib.request.Request(B+'/equipment/imports',data=body,headers={'Content-Type':'multipart/form-data; boundary='+boundary,'Accept-Language':'en-US','Origin':'http://127.0.0.1:18083'})
batch=json.load(urllib.request.urlopen(r));bid=batch['id']
for _ in range(100):
 batch=json.loads(req('/equipment/imports/'+bid,'en-US'))
 if batch['state']!='VALIDATING':break
 time.sleep(.1)
assert batch['state']=='INVALID',batch
preview=json.loads(req('/equipment/imports/'+bid+'/preview?errorsOnly=true','en-US'));issues=preview['data'][0]['issues'];system=[e for e in issues if not e['field'].startswith('custom:')];assert system and all(not re.search('[\u3400-\u9fff]',re.sub('带病运行|正常|故障','',e['reason']+e['suggestion'])) for e in system),system
(OUT/'validation-report-en-US.xlsx').write_bytes(req('/equipment/imports/'+bid+'/report','en-US'));results.append({'validationReport':'en-US','state':batch['state'],'systemIssues':system,'equipmentAdded':0});print('English validation report PASS',flush=True)
(OUT/'outputs.json').write_text(json.dumps(results,ensure_ascii=False,indent=2))
