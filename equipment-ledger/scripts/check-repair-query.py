"""MySQL/local-dev query regression; removes all dedicated fixtures in finally."""
import csv
import io
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request

project = Path(__file__).resolve().parents[1]
mysql = os.environ.get("EAM_MYSQL_BIN", str(project.parent / ".local-runtime/mysql-8.4.6-macos15-arm64/bin/mysql"))
root = "http://127.0.0.1:8080"
base_id = time.time_ns() // 1000
tag = f"TQ{base_id}"


def sql(statement):
    env = dict(os.environ)
    env["MYSQL_PWD"] = env["DB_PASSWORD"]
    subprocess.run([mysql, "--no-defaults", "--host=127.0.0.1", "--port=3306",
                    "--user=" + env.get("DB_USERNAME", "eam"), "eam_demo", "-e", statement],
                   env=env, check=True, capture_output=True)


def http(path, raw=False):
    try:
        with urllib.request.urlopen(root + path, timeout=10) as response:
            return response.status, response.read().decode("utf-8-sig") if raw else json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)


def query(**params):
    status, payload = http("/api/v1/repairs?" + urllib.parse.urlencode({"keyword": tag, **params}))
    assert status == 200, (status, payload)
    return payload


status, identity = http("/api/v1/auth/me")
assert status == 200 and identity.get("developmentMode") is True, "Requires local-dev mode"
assert "导出" in identity.get("permissions", {}).get("repair", []), "Requires repair export permission"
assert str(identity["data"]["tenantId"]) == "10001", "Requires local demo tenant"
user_id = int(identity["data"]["id"])
try:
    values = []
    states = ["处理中", "挂起", "待验收", "待派工", "已完成"]
    for index in range(26):
        tenant = 10001 if index < 25 else 20002
        state = states[min(index // 5, 4)]
        reporter = user_id if index < 24 else user_id + 999
        assignee = user_id if index < 10 else user_id + 999
        description = "=1+1" if index == 0 else "query test fixture"
        values.append(f"({base_id + index},{tenant},'{tag}-{index:02d}','TEST-QUERY','查询测试',NOW(),'测试','{description}','集成测试','{state}',{reporter},{assignee})")
    sql("INSERT INTO eam_repair_order (id,tenant_id,order_no,equipment_code,equipment_name,fault_at,fault_type,description,reporter,status,reporter_user_id,assignee_user_id) VALUES " + ",".join(values))
    first = query(page=1, size=10, sortBy="orderNo", direction="asc")
    second = query(page=2, size=10, sortBy="orderNo", direction="asc")
    third = query(page=3, size=10, sortBy="orderNo", direction="asc")
    assert first["page"]["total"] == 25 and [len(x["data"]) for x in (first, second, third)] == [10, 10, 5]
    ids = [row["id"] for result in (first, second, third) for row in result["data"]]
    assert len(set(ids)) == 25 and str(base_id + 25) not in ids
    assert first["counts"] == {"all": 25, "pending": 0, "owned": 10, "mine": 10, "accept": 5, "created": 24, "dispatch": 5}, first["counts"]
    for queue, count in first["counts"].items():
        assert query(queue=queue)["page"]["total"] == count
    filtered = query(filters=json.dumps({"status": "处理中"}))
    assert filtered["page"]["total"] == 5 and filtered["counts"]["mine"] == 5
    assert query(filters=json.dumps({"orderNo": tag + "-00"}))["page"]["total"] == 1
    advanced = json.dumps([{"field": "status", "operator": "in", "values": ["处理中", "挂起"]}, {"field": "faultAt", "operator": "range", "values": ["2020-01-01T00:00", "2099-12-31T23:59"]}])
    assert query(advanced=advanced)["page"]["total"] == 10
    assert query(advanced=advanced, filters=json.dumps({"status": "处理中"}))["page"]["total"] == 5
    assert query(advanced=json.dumps([{"field": "assignee", "operator": "empty", "values": []}]))["page"]["total"] == 25
    status, advanced_export = http("/api/v1/repairs/export?" + urllib.parse.urlencode({"keyword": tag, "advanced": advanced, "columns": "orderNo"}), raw=True)
    assert status == 200 and len(list(csv.reader(io.StringIO(advanced_export)))) == 11
    status, body = http("/api/v1/repairs?size=10000")
    assert status == 400, (status, body)
    status, body = http("/api/v1/repairs/export?columns=tenantId")
    assert status == 400 and body["code"] == "INVALID_ARGUMENT", (status, body)
    status, exported = http("/api/v1/repairs/export?" + urllib.parse.urlencode({"keyword": tag, "sortBy": "orderNo", "direction": "asc", "columns": "orderNo,description"}), raw=True)
    assert status == 200
    exported_rows = list(csv.reader(io.StringIO(exported)))
    assert len(exported_rows) == 26 and exported_rows[0] == ["工单编号", "故障描述"]
    assert exported_rows[1][1] == "'=1+1"
    batch_values = [f"({base_id + 26 + i},10001,'{tag}B-{i:03d}','TEST-QUERY','分批导出测试',NOW(),'测试','export batch fixture','集成测试','待派工',{user_id})" for i in range(501)]
    sql("INSERT INTO eam_repair_order (id,tenant_id,order_no,equipment_code,equipment_name,fault_at,fault_type,description,reporter,status,reporter_user_id) VALUES " + ",".join(batch_values))
    status, batch_export = http("/api/v1/repairs/export?" + urllib.parse.urlencode({"keyword": tag + "B", "sortBy": "orderNo", "direction": "asc", "columns": "orderNo"}), raw=True)
    batch_rows = list(csv.reader(io.StringIO(batch_export)))
    assert status == 200 and len(batch_rows) == 502 and len(set(row[0] for row in batch_rows[1:])) == 501
    print("PASS: server pagination, stable ordering, queues by user ID, column filters, tenant isolation, page limit and complete filtered export")
    print("PASS: 501-record export spans multiple batches without missing or duplicate records")
finally:
    sql(f"DELETE FROM eam_repair_order WHERE id BETWEEN {base_id} AND {base_id + 526} AND order_no LIKE '{tag}%'")
    print("Query test fixtures removed")
