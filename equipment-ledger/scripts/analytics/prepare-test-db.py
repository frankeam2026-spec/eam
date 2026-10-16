"""Clone the development database into a dedicated analytics acceptance database."""
import pathlib,subprocess
r=pathlib.Path(__file__).resolve().parents[3]/'.local-runtime'
mysql=r/'mysql-8.4.6-macos15-arm64/bin/mysql'
config='--defaults-extra-file='+str(r/'root.cnf')
opts=[config,'--socket='+str(r/'mysql.sock')]
db='eam_home_analytics_test_20261015'
subprocess.run([str(mysql),*opts],input=f"CREATE DATABASE IF NOT EXISTS {db} CHARACTER SET utf8mb4; GRANT ALL ON {db}.* TO 'eam'@'localhost';",text=True,check=True)
dump=subprocess.Popen([str(mysql.parent/'mysqldump'),*opts,'--single-transaction','--no-tablespaces','--set-gtid-purged=OFF','eam_demo'],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
load=subprocess.run([str(mysql),*opts,db],stdin=dump.stdout,capture_output=True)
dump.stdout.close();err=dump.communicate()[1]
assert dump.returncode==0,err.decode();assert load.returncode==0,load.stderr.decode()
print('Dedicated analytics database prepared')
