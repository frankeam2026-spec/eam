"""Create browser fixtures from the isolated 18081 API. Read-only; requires acceptance test seed data."""
import urllib.request,json,io,zipfile,xml.etree.ElementTree as E,pathlib,uuid
B='http://127.0.0.1:18081/api/v1'
def get(p):return urllib.request.urlopen(B+p).read()
blob=get('/equipment/imports/template');config=json.loads(get('/business-config/runtime/equipment'))['configuration']
N='http://schemas.openxmlformats.org/spreadsheetml/2006/main';E.register_namespace('',N)
with zipfile.ZipFile(io.BytesIO(blob)) as z:files={n:z.read(n) for n in z.namelist()}
root=E.fromstring(files['xl/worksheets/sheet1.xml']);sd=root.find('{'+N+'}sheetData');keys=[''.join(c.itertext()).strip() for c in sd[0]];keys[0]=keys[0].split('|',2)[2]
data={'code':'UI-IMPORT-'+uuid.uuid4().hex[:8],'name':'界面验收设备','department':'风塔集团 · 苏州风塔精密制造有限公司 · 机加工一部','category':'动力设备','functionLocation':'A车间','unit':'台','state':'正常','useState':'正常','owner':'admin','assetNo':'00099'}
for f in config['fields']:
 if not f.get('enabled',True):continue
 v={'NUMBER':'0','BOOLEAN':'否','USER':'admin','DEPARTMENT':data['department']}.get(f['type'],'')
 if f['type'] in ('CHOICE','MULTI_CHOICE'):v=next(o['name'] for o in f['options'] if o['enabled'])
 if v:data['custom:'+f['id']]=v
out=pathlib.Path(__file__).resolve().parents[3]/'.local-runtime/equipment-import-qa'
out.mkdir(parents=True,exist_ok=True)
for name,values in [('ui-valid.xlsx',data),('ui-errors.xlsx',{**data,'name':'','unit':'没有这个单位','purchaseDate':'2026-02-30'})]:
 tree=E.fromstring(files['xl/worksheets/sheet1.xml']);sheet=tree.find('{'+N+'}sheetData');r=E.SubElement(sheet,'{'+N+'}row',r='3')
 for i,k in enumerate(keys):
  if not values.get(k):continue
  n=i+1;col=''
  while n:n,rem=divmod(n-1,26);col=chr(65+rem)+col
  c=E.SubElement(r,'{'+N+'}c',r=col+'3',t='inlineStr');E.SubElement(E.SubElement(c,'{'+N+'}is'),'{'+N+'}t').text=values[k]
 with zipfile.ZipFile(out/name,'w',zipfile.ZIP_DEFLATED) as z:
  for p,b in files.items():z.writestr(p,E.tostring(tree,encoding='utf-8',xml_declaration=True) if p=='xl/worksheets/sheet1.xml' else b)
print(data['code'])
