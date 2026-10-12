"""Local-dev/MySQL integration check. Creates and removes its own repair fixture."""
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.request

project = Path(__file__).resolve().parents[1]
mysql = os.environ.get(
    "EAM_MYSQL_BIN",
    str(project.parent / ".local-runtime/mysql-8.4.6-macos15-arm64/bin/mysql"),
)
root = "http://127.0.0.1:8080"
record_id = time.time_ns() // 1000
order_no = f"TEST-CONC-{record_id}"


def sql(statement):
    env = dict(os.environ)
    env["MYSQL_PWD"] = env["DB_PASSWORD"]
    subprocess.run(
        [mysql, "--no-defaults", "--host=127.0.0.1", "--port=3306",
         "--user=" + env.get("DB_USERNAME", "eam"), "eam_demo", "-e", statement],
        env=env, check=True, capture_output=True,
    )


def http(path, body=None):
    request = urllib.request.Request(
        root + path,
        data=None if body is None else json.dumps(body).encode(),
        headers={"Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)


def read_record():
    status, payload = http("/api/v1/repairs?keyword=" + order_no)
    assert status == 200, (status, payload)
    records = [row for row in payload["data"] if row["id"] == str(record_id)]
    assert len(records) == 1, "Test fixture must be visible to local admin"
    return records[0]


status, identity = http("/api/v1/auth/me")
assert status == 200 and identity.get("developmentMode") is True, "Requires local-dev mode"
assert "撤回" in identity.get("permissions", {}).get("repair", []), "Requires repair cancellation permission"

try:
    sql(f"""INSERT INTO eam_repair_order
        (id, tenant_id, order_no, equipment_code, equipment_name,
         fault_at, fault_type, description, reporter, status, row_version)
        VALUES ({record_id}, 10001, '{order_no}', 'TEST-CONC', '并发测试记录',
         NOW(), '测试', '自动集成测试临时记录', '集成测试', '待派工', 0)""")
    before = read_record()
    endpoint = f"/api/v1/repairs/{record_id}/actions"
    status, payload = http(endpoint + "?rowVersion=1", {"action": "cancel"})
    assert status == 409 and payload["code"] == "DATA_CONFLICT", (status, payload)
    unchanged = read_record()
    assert unchanged["status"] == before["status"] and unchanged["rowVersion"] == 0
    status, payload = http(endpoint, {"action": "cancel"})
    assert status == 400, (status, payload)
    status, payload = http(endpoint + "?rowVersion=0", {"action": "cancel"})
    assert status == 200, (status, payload)
    updated = read_record()
    assert updated["status"] == "已撤销" and updated["rowVersion"] == 1, updated
    status, payload = http(endpoint + "?rowVersion=0", {"action": "cancel"})
    assert status == 409, (status, payload)
    assert read_record()["rowVersion"] == 1
    print("PASS: stale version 409; missing version 400; valid transition increments version; repeated stale operation rejected")
finally:
    sql(f"DELETE FROM eam_repair_order WHERE id={record_id} AND tenant_id=10001 AND order_no='{order_no}'")
    print("Test fixture removed")
