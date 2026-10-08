"""Check rendered app controls in adb's UI snapshot without printing user text."""
import argparse
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("snapshot")
parser.add_argument("--screen", choices=("models", "main"), required=True)
args = parser.parse_args()
root = ET.parse(args.snapshot).getroot()
texts = {node.get("text", "") for node in root.iter("node")
         if node.get("package") == "com.woojik.aircallai"}
models = {"다운로드 후 적용해 주세요.", "GPU 가속"} <= texts
chat = bool({"메시지 입력", "어떤 이야기를 나눌까요?", "새 채팅"} & texts)
if not (models if args.screen == "models" else (models or chat)):
    raise SystemExit(f"Expected {args.screen} controls are not rendered")
print(f"Verified rendered {args.screen} screen")
