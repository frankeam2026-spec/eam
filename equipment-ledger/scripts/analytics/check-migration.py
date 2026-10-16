"""Run V51 against isolated, prefixed table copies, leaving local business tables untouched."""
import os,re,subprocess,pathlib
root=pathlib.Path(__file__).resolve().parents[2]
mysql=root.parent/'.local-runtime/mysql-8.4.6-macos15-arm64/bin/mysql'
env={**os.environ,'MYSQL_PWD':os.environ['DB_PASSWORD']}
args=[str(mysql),'-h127.0.0.1','-u',os.environ['DB_USERNAME'],'--default-character-set=utf8mb4','--batch','--skip-column-names','eam_layer_backend_test_20261014']
def sql(s):
 r=subprocess.run(args,input=s,text=True,capture_output=True,env=env)
 if r.returncode:raise RuntimeError(r.stderr)
 return r.stdout.strip()
text=(root/'backend/src/main/resources/db/migration/V51__home_analytics_facts.sql').read_text()
tables=sorted(set(re.findall(r'\b(?:eam|inv)_[a-z_]+\b',text)))
created=[]
try:
 for t in tables:
  target='home51_'+t
  sql(f'DROP TABLE IF EXISTS {target}; CREATE TABLE {target} LIKE eam_demo.{t};')
  created.append(target)
  cols=sql(f"SELECT COLUMN_NAME FROM information_schema.columns WHERE TABLE_SCHEMA='eam_demo' AND TABLE_NAME='{t}' AND GENERATION_EXPRESSION='' ORDER BY ORDINAL_POSITION").splitlines()
  columns=','.join('`'+c+'`' for c in cols)
  sql(f'INSERT INTO {target} ({columns}) SELECT {columns} FROM eam_demo.{t};')
 # Reconstruct the V50 columns on copies when the source DB has already migrated.
 columns={
  'eam_home_template':['overview_draft'],
  'eam_repair_order':['equipment_id','company_org_id','fault_type_id','fault_cause_id','accepted_at','accepted_owner_id','acceptance_score'],
  'eam_equipment_anomaly':['company_org_id','department_org_id'],
  'inv_usage':['actor_id','attribution','cost_amount','currency'],
 }
 indexes={'eam_repair_order':['ix_repair_report_analytics','ix_repair_accept_analytics'],'eam_equipment_anomaly':['ix_anomaly_analysis'],'inv_usage':['ix_usage_analytics'],'eam_repair_event':['ix_repair_event_analysis'],'eam_maintenance_event':['ix_maintenance_event_analysis'],'eam_inspection_event':['ix_inspection_event_analysis']}
 for table,names in indexes.items():
  for name in names:
   if sql(f"SELECT COUNT(*) FROM information_schema.statistics WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='home51_{table}' AND INDEX_NAME='{name}'")!='0':sql(f'ALTER TABLE home51_{table} DROP INDEX {name}')
 for table,names in columns.items():
  for name in names:
   if sql(f"SELECT COUNT(*) FROM information_schema.columns WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='home51_{table}' AND COLUMN_NAME='{name}'")!='0':sql(f'ALTER TABLE home51_{table} DROP COLUMN {name}')
 script=re.sub(r'\b(?:eam|inv)_[a-z_]+\b',lambda m:'home51_'+m.group(),text)
 sql(script)
 updates=';'.join(q for q in script.split(';') if re.search(r'^\s*(?:--[^\n]*\n\s*)*UPDATE\b',q,re.I))+';'
 fingerprint=sql("SELECT GROUP_CONCAT(CONCAT(id,':',COALESCE(cost_amount,'NULL'),':',COALESCE(currency,'NULL')) ORDER BY id) FROM home51_inv_usage")
 sql(updates)
 assert fingerprint==sql("SELECT GROUP_CONCAT(CONCAT(id,':',COALESCE(cost_amount,'NULL'),':',COALESCE(currency,'NULL')) ORDER BY id) FROM home51_inv_usage"),'Backfill must be repeatable'
 print('V51 isolated migration and repeated backfill passed:',len(tables),'tables copied')
 for t in ['eam_repair_order','inv_usage']:
  fields='COUNT(*),SUM(accepted_at IS NULL)' if t=='eam_repair_order' else 'COUNT(*),SUM(cost_amount IS NULL),SUM(currency IS NULL)'
  print(t,sql('SELECT '+fields+' FROM home51_'+t))
finally:
 for t in reversed(created):sql('DROP TABLE '+t)
