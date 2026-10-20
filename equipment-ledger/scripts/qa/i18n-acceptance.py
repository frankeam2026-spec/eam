# coding: utf-8
"""Mutating acceptance is restricted to the dedicated isolated API at 18083."""
import urllib.request,urllib.error,json,pathlib,uuid,copy
B='http://127.0.0.1:18083/api/v1';OUT=pathlib.Path(__file__).resolve().parents[3]/'.local-runtime/i18n-qa';OUT.mkdir(parents=True,exist_ok=True)
results=[]
def req(path,method='GET',body=None,locale='en-US',status=200,raw=False):
 r=urllib.request.Request(B+path,data=None if body is None else json.dumps(body,ensure_ascii=False).encode(),method=method,headers={'Content-Type':'application/json','Accept-Language':locale,'Origin':'http://127.0.0.1:18083'})
 try:
  with urllib.request.urlopen(r,timeout=60) as response:data=response.read();actual=response.status
 except urllib.error.HTTPError as e:data=e.read();actual=e.code
 if actual!=status:raise AssertionError((path,actual,data[:800]))
 return data if raw else json.loads(data)
def record(name,details=True):results.append({'case':name,'result':details});print(name,'PASS',flush=True);(OUT/'api.json').write_text(json.dumps(results,ensure_ascii=False,indent=2))
assert B.startswith('http://127.0.0.1:18083/')
catalog=req('/i18n/languages')['data'];assert {x['locale'] for x in catalog}>={'zh-CN','zh-TW','en-US'};record('builtin-languages')
resources={loc:req('/i18n/resources/'+loc)['data'] for loc in ['zh-CN','zh-TW','en-US']}
assert len(resources['en-US']['messages'])>2000
for loc,r in resources.items():assert r['locale']==loc
assert resources['en-US']['messages']['language.field.displayLanguage']=='Display Language';assert resources['zh-TW']['messages']['language.field.displayLanguage']=='顯示語言';record('builtin-resources-and-components',len(resources['en-US']['messages']))
settings=req('/auth/settings')['data'];theme=settings['theme']
r=req('/auth/settings/locale','PATCH',{'locale':'en-US'})['data'];assert r['effectiveLocale']=='en-US'
assert req('/auth/settings')['data']['theme']==theme;record('save-locale-preserves-theme')
req('/auth/settings','PUT',{'theme':theme});assert req('/auth/settings')['data']['locale']=='en-US';record('save-theme-preserves-locale')
error=req('/auth/settings/locale','PATCH',{'locale':'not-enabled'},status=400);assert '语言' not in error['message'] and 'unavailable' in error['message'].lower();record('localized-api-error',error)
error=req('/auth/settings/locale','PATCH',{'locale':None},status=400);assert 'locale' in error.get('fieldErrors',{});record('null-locale-field-error')
# Missing core fields must still report stable field identifiers with translated messages.
error=req('/equipment','POST',{},status=400);assert error.get('fieldErrors') and not any(v.startswith('{') for v in error['fieldErrors'].values());record('localized-field-errors',error)
fr={'formatVersion':1,'locale':'fr-FR','nativeName':'Français','direction':'ltr','messages':{'language.field.displayLanguage':"Langue d’affichage",'language.manager.menu':'Langues','unknown.demo':'ignored'}}
report=req('/system/languages/imports/validate','POST',fr)['data'];assert report['valid'] and report['unknown']==['unknown.demo'] and report['missing'];record('partial-package-and-unknown-keys',{'accepted':report['accepted'],'missing':len(report['missing'])})
rows=req('/system/languages')['data'];version=next((r['rowVersion'] for r in rows if r['locale']=='fr-FR'),0)
req('/system/languages/fr-FR','PUT',{'rowVersion':version,'package':fr});rows=req('/system/languages')['data'];version=next(r['rowVersion'] for r in rows if r['locale']=='fr-FR')
req('/system/languages/fr-FR/state','PATCH',{'rowVersion':version,'enabled':True});r=req('/i18n/resources/fr-FR')['data'];assert r['messages']['language.field.displayLanguage']=="Langue d’affichage";assert r['messages']['language.manager.cancel']=='取消';record('publish-enable-french-and-chinese-fallback')
req('/system/languages/fr-FR','PUT',{'rowVersion':version,'package':fr},status=409);record('stale-package-version-rejected')
req('/auth/settings/locale','PATCH',{'locale':'fr-FR'});rows=req('/system/languages')['data'];version=next(r['rowVersion'] for r in rows if r['locale']=='fr-FR')
req('/system/languages/fr-FR/state','PATCH',{'rowVersion':version,'enabled':False});r=req('/auth/settings')['data'];assert r['locale']=='fr-FR' and r['effectiveLocale']=='zh-CN';record('disabled-preference-retained-with-fallback')
req('/system/languages/zh-CN/state','PATCH',{'rowVersion':0,'enabled':False},status=400);record('builtin-disable-rejected')
placeholder=next(k for k,v in resources['zh-CN']['messages'].items() if '{p0}' in v)
bad=copy.deepcopy(fr);bad['messages']={placeholder:'No parameter'};assert not req('/system/languages/imports/validate','POST',bad)['data']['valid'];record('placeholder-mismatch-rejected')
for title,payload in [('prototype',{'__proto__.polluted':'yes'}),('html',{'language.field.displayLanguage':'<script>alert(1)</script>'})]:
 bad=copy.deepcopy(fr);bad['messages']=payload;assert not req('/system/languages/imports/validate','POST',bad)['data']['valid'];record(title+'-rejected')
bad=copy.deepcopy(fr);bad['locale']='ar-SA';bad['direction']='rtl';assert not req('/system/languages/imports/validate','POST',bad)['data']['valid'];record('rtl-deferred-explicitly')
for title,payload in [('unclosed-parameter',{'language.field.displayLanguage':'Display {oops'}),('broken-calendar',{'calendar.months':'January|February'})]:
 bad=copy.deepcopy(fr);bad['messages']=payload;assert not req('/system/languages/imports/validate','POST',bad)['data']['valid'];record(title+'-rejected')
for locale in ['zh-CN','zh-TW','en-US']:
 data=req('/equipment/imports/template',locale=locale,raw=True);assert data[:2]==b'PK';(OUT/('template-'+locale+'.xlsx')).write_bytes(data)
record('three-language-import-templates')
# Restore a built-in override without affecting business records.
zh={'formatVersion':1,'locale':'zh-TW','nativeName':'繁體中文','direction':'ltr','messages':{'language.field.displayLanguage':'測試語言'}}
rows=req('/system/languages')['data'];version=next(r['rowVersion'] for r in rows if r['locale']=='zh-TW')
req('/system/languages/zh-TW','PUT',{'rowVersion':version,'package':zh});assert req('/i18n/resources/zh-TW')['data']['messages']['language.field.displayLanguage']=='測試語言'
rows=req('/system/languages')['data'];version=next(r['rowVersion'] for r in rows if r['locale']=='zh-TW');req('/system/languages/zh-TW/restore','POST',{'rowVersion':version});assert req('/i18n/resources/zh-TW')['data']['messages']['language.field.displayLanguage']=='顯示語言';record('builtin-override-and-restore')
req('/auth/settings/locale','PATCH',{'locale':'zh-CN'})
record('all-scenarios-complete')
