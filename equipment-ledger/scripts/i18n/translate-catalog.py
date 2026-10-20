# coding: utf-8
"""Build-time translation drafts of static product messages only. Never send business records.
The checked-in en-draft.json is a cache; domain glossary is applied afterwards and tests check placeholders.
"""
import pathlib,json,re,urllib.request,urllib.parse,concurrent.futures,time
root=pathlib.Path.cwd();source=json.loads((root/'src/i18n/source.json').read_text());components=json.loads((root/'scripts/i18n/component-translations.json').read_text());cachepath=root/'scripts/i18n/en-draft.json';cache=json.loads(cachepath.read_text()) if cachepath.exists() else {}
manual={'设备台账':'Equipment Register','设备编号':'Equipment Code','设备名称':'Equipment Name','设备分类':'Equipment Category','设备状态':'Equipment Status','使用状态':'Usage Status','所属部门':'Department','所属公司':'Company','负责人':'Responsible Person','位置':'Location','料号':'Item Number','物料名称':'Material Name','规格型号':'Model / Specification','单位':'Unit','正常':'Normal','带病运行':'Running with Fault','故障':'Fault','在用':'In Use','闲置':'Idle','停用':'Disabled','待派工':'Awaiting Assignment','待接单':'Awaiting Acceptance','处理中':'In Progress','待验收':'Awaiting Inspection','已完成':'Completed','待执行':'Pending Execution','执行中':'In Progress','待审核':'Awaiting Review','草稿':'Draft','已取消':'Cancelled','已作废':'Voided','待出库':'Pending Issue','已出库':'Issued','部分出库':'Partially Issued','已冲销':'Reversed','出库管理':'Outbound Management','入库管理':'Inbound Management','保养管理':'Maintenance','保养标准':'Maintenance Standards','保养计划':'Maintenance Plans','保养任务':'Maintenance Tasks','维修工单':'Repair Work Orders','维修':'Repair','保养':'Maintenance','巡检':'Inspection','备件':'Spare Parts','设备BOM':'Equipment BOM','日常点检任务':'Daily Check Tasks','周期巡检任务':'Periodic Inspection Tasks','领料申请':'Material Requisitions','任务用料':'Task Materials','关联异常':'Related Anomalies','首页':'Home','工作台':'Work Center','个人设置':'Personal Settings','显示语言':'Display Language','系统管理':'System Administration','业务配置':'Business Configuration','扩展信息':'Additional Information','说明':'Description','保存':'Save','取消':'Cancel','关闭':'Close','新增':'Add','编辑':'Edit','删除':'Delete','确定':'Confirm','通过':'Accept','打回':'Reject','操作':'Actions','查看':'View','查看全部':'View All','返回':'Back','导入':'Import','导出':'Export','下载导入模板':'Download Import Template','刷新':'Refresh','清空':'Clear','全部':'All','全部设备':'All Equipment','登录':'Sign In','退出':'Sign Out','用户名':'Username','密码':'Password','是否':'Yes / No','是':'Yes','否':'No','语言管理':'Languages','简体中文':'Simplified Chinese','繁體中文':'Traditional Chinese','品牌':'Brand','序号':'No.','设备档案':'Equipment Record','概览':'Overview','基本信息':'Basic Information','动态':'Activity','待办':'To-dos','提醒':'Notifications','统计截至':'Statistics As Of','请选择':'Select','查询':'Search','重置':'Reset','添加筛选':'Add Filter','表单布局':'Form Layout','数量':'Quantity','金额':'Amount','单价':'Unit Price'}
manual.update({'启用': 'Enable', '停用': 'Disable', '恢复内置': 'Restore Built-in', '翻译覆盖率': 'Coverage', '语言编码': 'Language Code', '语言名称': 'Language', '更新时间': 'Updated At', '个角色': 'roles', '角色': 'Roles', '主题皮肤': 'Theme', '条': 'items', '共': 'Total', '确认导入': 'Confirm Import', '下载语言包模板': 'Download Language Pack Template', '名称': 'Name', '姓名': 'Name', '账号': 'Account', '部门': 'Department'})
cache.update(manual)
values=list(dict.fromkeys(v for k,v in source.items() if not k.startswith(('component.','calendar.')) and v not in cache and re.search('[\u3400-\u9fff]',v)))
batches=[];batch=[];size=0
for v in values:
 if size+len(v)>800 and batch:batches.append(batch);batch=[];size=0
 batch.append(v);size+=len(v)+1
if batch:batches.append(batch)
def translate(batch):
 safe=[]
 for value in batch:
  safe.append(re.sub(r'\{([a-zA-Z0-9_]+)\}',lambda m:'ZXPH'+m[1]+'PHZX',value).replace('\n',' ZXNEWLINEZX '))
 q='\n'.join(safe)
 url='https://clients5.google.com/translate_a/t?'+urllib.parse.urlencode({'client':'dict-chrome-ex','sl':'zh-CN','tl':'en','dt':'t','q':q})
 for attempt in range(4):
  try:
   data=json.load(urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'Mozilla/5.0'}),timeout=30));out=data[0];lines=out.split('\n')
   while len(lines)>len(batch) and not lines[-1].strip():lines.pop()
   if len(lines)!=len(batch):
    if len(batch)>1:
     result={}
     for item in batch:result.update(translate([item]))
     return result
    lines=[out]
   result={}
   for original,value in zip(batch,lines):
    value=re.sub(r'ZXPH\s*([a-zA-Z0-9_]+)\s*PHZX',lambda m:'{'+m[1]+'}',value,flags=re.I).replace('ZXNEWLINEZX','\n')
    if set(re.findall(r'\{\w+\}',original))!=set(re.findall(r'\{\w+\}',value)):raise ValueError('placeholder mismatch')
    result[original]=value.strip()
   return result
  except Exception:
   if attempt==3:raise
   time.sleep(attempt+1)
print('translation batches',len(batches),'messages',len(values),flush=True)
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as ex:
 jobs=[ex.submit(translate,batch) for batch in batches]
 for i,future in enumerate(concurrent.futures.as_completed(jobs)):
  result=future.result()
  cache.update(result);cachepath.write_text(json.dumps(cache,ensure_ascii=False,indent=2)+'\n')
  if i%10==0:print('completed batches',i+1,flush=True)
cache.update(manual);cachepath.write_text(json.dumps(cache,ensure_ascii=False,indent=2)+'\n')
print('complete',len(cache),flush=True)
