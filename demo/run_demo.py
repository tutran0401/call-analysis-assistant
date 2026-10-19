"""Demo Scripts - 6 scenario của Final Demo (PROJECT_SPEC.md mục 10).

Gọi Chat API đang chạy (mặc định http://127.0.0.1:8080) với đúng file log của từng scenario, in kết luận và lưu
response JSON + report văn bản vào demo/out/. Chỉ dùng thư viện chuẩn của Python 3 - câu hỏi tiếng Việt được gửi
dạng UTF-8, tránh lỗi mất dấu của curl.exe trên Windows.

    python demo/run_demo.py [--base-url http://127.0.0.1:8080] [--data-root sample-data] [--only 1,6]

Cần: backend chạy profile web (`--spring.profiles.active=web`), Elasticsearch đã import signaling, sample-data/.
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.request
import uuid

SCENARIOS = [
    {"id": 1, "name": "Cuộc gọi bình thường", "expected": "SUCCESS, không cờ chất lượng",
     "call": "DE7DD314",
     "files": ["caller_endcall.log", "callee_endcall.log", "caller_webrtc.log", "callee_webrtc.log"],
     "questions": ["Phân tích cuộc gọi này giúp mình"]},
    {"id": 2, "name": "Chất lượng kém", "expected": "SUCCESS + cờ chất lượng, NETWORK_PACKET_LOSS",
     "call": "B5F19381", "files": "*",
     "questions": ["Vì sao bên nhận nghe bị rè?"]},
    {"id": 3, "name": "Lỗi thiết lập", "expected": "FAIL, SIGNALING_FAILURE",
     "call": "1B009D42", "files": ["caller_endcall.log", "caller_webrtc.log"],
     "questions": ["Vì sao cuộc gọi này không kết nối được?"]},
    {"id": 4, "name": "Lỗi media", "expected": "FAIL, ICE_FAILURE hoặc TURN_FAILURE",
     "call": "2D9057AA", "files": ["callee_endcall.log", "callee_webrtc.log"],
     "questions": ["Có lỗi kết nối media không?"]},
    {"id": 5, "name": "Thiếu dữ liệu", "expected": "UNKNOWN, nêu rõ file thiếu (xem demo/README.md)",
     "call": "DE7DD314", "files": ["caller_webrtc.log"],
     "questions": ["Phân tích cuộc gọi này giúp mình"]},
    {"id": 6, "name": "Nhất quán + sensitive data", "expected": "Cùng verdict ở 3 cách hỏi; không lộ JWT/phone/IP",
     "call": "DE7DD314",
     "files": ["caller_endcall.log", "callee_endcall.log", "caller_webrtc.log", "callee_webrtc.log"],
     "append": {"caller_webrtc.log": [
         "[999:001][1] (RTCLogging.mm:34): Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJkZW1vIn0.ZGVtby1zaWduYXR1cmUtdmFsdWU",
         "[999:002][1] (RTCLogging.mm:34): contact phone=0987654321 ip=113.161.45.67"]},
     "secrets": ["eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJkZW1vIn0.ZGVtby1zaWduYXR1cmUtdmFsdWU",
                 "0987654321", "113.161.45.67"],
     "questions": ["Phân tích cuộc gọi này giúp mình", "Cuộc gọi này có lỗi gì không?",
                   "Kiểm tra giúp mình log cuộc gọi này"]},
]


def find_call_dir(data_root, prefix):
    """Thư mục cuộc gọi có tên bắt đầu bằng prefix, ở bất kỳ nhóm nào dưới data_root (success/, fail/, for_test/...)."""
    for group in sorted(os.listdir(data_root)):
        base = os.path.join(data_root, group)
        if not os.path.isdir(base):
            continue
        for name in sorted(os.listdir(base)):
            if name.upper().startswith(prefix.upper()) and os.path.isdir(os.path.join(base, name)):
                return os.path.join(base, name)
    raise SystemExit(f"Không tìm thấy cuộc gọi {prefix} dưới {data_root}")


def multipart(fields, files):
    boundary = "----demo" + uuid.uuid4().hex
    body = bytearray()
    for name, value in fields:
        body += f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"\r\n\r\n".encode()
        body += value.encode("utf-8") + b"\r\n"
    for filename, content in files:
        body += (f"--{boundary}\r\nContent-Disposition: form-data; name=\"files\"; filename=\"{filename}\"\r\n"
                 "Content-Type: text/plain\r\n\r\n").encode()
        body += content + b"\r\n"
    body += f"--{boundary}--\r\n".encode()
    return bytes(body), f"multipart/form-data; boundary={boundary}"


def ask(base_url, question, files):
    body, content_type = multipart([("message", question)], files)
    request = urllib.request.Request(base_url + "/api/chat", data=body, headers={"Content-Type": content_type})
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return {"error": e.read().decode("utf-8", "replace"), "status": e.code}


def load_files(call_dir, scenario):
    names = scenario["files"]
    if names == "*":
        names = sorted(n for n in os.listdir(call_dir) if n.endswith(("_endcall.log", "_webrtc.log")))
    files = []
    for name in names:
        with open(os.path.join(call_dir, name), "rb") as f:
            content = f.read()
        extra = scenario.get("append", {}).get(name)
        if extra:
            content += ("\n" + "\n".join(extra) + "\n").encode("utf-8")
        files.append((name, content))
    return files


def main():
    # Console Windows mặc định cp1252 -> in tiếng Việt sẽ lỗi; ép UTF-8.
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--data-root", default="sample-data")
    parser.add_argument("--only", default="")
    parser.add_argument("--out", default=os.path.join(os.path.dirname(__file__), "out"))
    args = parser.parse_args()
    only = {int(x) for x in args.only.split(",") if x.strip()}
    os.makedirs(args.out, exist_ok=True)

    for scenario in SCENARIOS:
        if only and scenario["id"] not in only:
            continue
        call_dir = find_call_dir(args.data_root, scenario["call"])
        files = load_files(call_dir, scenario)
        print(f"\n=== Scenario {scenario['id']}: {scenario['name']} ===")
        print(f"Kỳ vọng: {scenario['expected']}")
        print(f"File: {', '.join(name for name, _ in files)}")
        verdicts = set()
        for index, question in enumerate(scenario["questions"], start=1):
            result = ask(args.base_url, question, files)
            path = os.path.join(args.out, f"scenario-{scenario['id']}-q{index}.json")
            with open(path, "w", encoding="utf-8") as f:
                json.dump(result, f, ensure_ascii=False, indent=2)
            report = result.get("report") or {}
            if "error" in result:
                print(f"  [{question}] LỖI {result.get('status')}: {result['error']}")
                continue
            verdicts.add((report.get("verdict"), report.get("issueCategory")))
            print(f"  [{question}] -> {report.get('verdict')} | cờ chất lượng: {report.get('qualityFlag')} | "
                  f"{report.get('issueCategory')} | độ tin cậy: {report.get('confidenceLevel')} | "
                  f"degraded: {result.get('degraded')} | {result.get('requestId')}")
            for limitation in report.get("dataLimitations", []):
                print(f"      giới hạn dữ liệu: {limitation}")
            with open(path.replace(".json", ".txt"), "w", encoding="utf-8") as f:
                f.write(result.get("renderedText", ""))
        if len(scenario["questions"]) > 1:
            print(f"  Nhất quán giữa các cách hỏi: {'CÓ' if len(verdicts) == 1 else 'KHÔNG'} {sorted(verdicts)}")
        for secret in scenario.get("secrets", []):
            leaked = [n for n in os.listdir(args.out)
                      if n.startswith(f"scenario-{scenario['id']}-")
                      and secret in open(os.path.join(args.out, n), encoding="utf-8").read()]
            print(f"  Giá trị nhạy cảm {secret[:12]}…: {'LỘ trong ' + ', '.join(leaked) if leaked else 'không lộ'}")
    print(f"\nĐã lưu response vào {os.path.abspath(args.out)}")


if __name__ == "__main__":
    sys.exit(main())
