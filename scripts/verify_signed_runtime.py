"""Exercise the unmodified, non-debuggable production APK on a disposable emulator.

Root is used only to seed synthetic legacy settings and inspect fixture ciphertext.
No test component, diagnostic logger, private key or instrumentation is in the APK.
"""
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = "com.woojik.aircallai.release"
COMPONENT = PACKAGE + "/com.woojik.aircallai.ui.MainActivity"
DATA = "/data/user/0/" + PACKAGE
RESULTS = Path("signed-runtime/results")
MESSAGE = "signed-upgrade-fixture"
FAKE_KEY = "test-only-not-a-real-api-key"
checks = []


def adb(*args, check=True):
    return subprocess.run(["adb", *map(str, args)], text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                          check=check, timeout=60).stdout.strip()


def record(check, **details):
    checks.append({"check": check, **details})
    (RESULTS / "checks.json").write_text(json.dumps(checks, indent=2) + "\n")
    print(check, flush=True)


def snapshot():
    # UiAutomation can transiently fail while a Compose tree/window is changing.
    adb("shell", "rm", "-f", "/data/local/tmp/signed-ui.xml")
    adb("shell", "uiautomator", "dump", "/data/local/tmp/signed-ui.xml")
    adb("pull", "/data/local/tmp/signed-ui.xml", RESULTS / "ui.xml")
    return ET.parse(RESULTS / "ui.xml").getroot()


def nodes(root):
    return [node for node in root.iter("node") if node.get("package") == PACKAGE]


def wait_ui(predicate, label, timeout=35):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            root = snapshot()
            if predicate(root):
                assert adb("shell", "pidof", PACKAGE, check=False), "App process exited"
                return root
        except (subprocess.CalledProcessError, ET.ParseError, FileNotFoundError):
            pass
        time.sleep(1)
    raise AssertionError("Production UI did not render: " + label)


def has(root, text):
    return any(node.get("text") == text or node.get("content-desc") == text for node in nodes(root))


def click_any(labels, scroll=False):
    def found(root):
        if any(has(root, label) for label in labels):
            return True
        if scroll:
            width, height = map(int, re.findall(r"(\d+)x(\d+)", adb("shell", "wm", "size"))[-1])
            adb("shell", "input", "swipe", width // 2, height * 3 // 4,
                width // 2, height // 3, 300)
        return False
    root = wait_ui(found, "/".join(labels))
    node = next(node for node in nodes(root)
                if node.get("text") in labels or node.get("content-desc") in labels)
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    adb("shell", "input", "tap", (x1 + x2) // 2, (y1 + y2) // 2)


def click(label, scroll=False):
    click_any((label,), scroll=scroll)


def launch(screen=None):
    command = ["shell", "am", "start", "-W", "-a", "android.intent.action.MAIN",
               "-c", "android.intent.category.LAUNCHER", "-n", COMPONENT]
    if screen:
        command += ["--es", "aircall.screen", screen]
    adb(*command)


def chat_ui():
    return wait_ui(lambda root: has(root, "메시지 입력") or has(root, "메시지를 입력하세요"), "chat")


def models_ui(legacy=False):
    return wait_ui(lambda root: has(root, "GPU 가속") and
                   has(root, "로컬 모델" if legacy else "다운로드 후 적용해 주세요."), "models")


def reopened_main_ui():
    # MainActivity.onNewIntent deliberately opens a fresh chat for launcher intents.
    # Process restoration can retain models or receive that new launcher intent.
    return wait_ui(lambda root: has(root, "메시지 입력") or
                   (has(root, "GPU 가속") and has(root, "다운로드 후 적용해 주세요.")),
                   "interactive chat or models after process death")


def read_fixture(relative):
    # All files belong to this freshly created emulator fixture, never a real account.
    return subprocess.check_output(["adb", "exec-out", "cat", DATA + "/" + relative], timeout=30)


def wait_file(relative):
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        try:
            result = read_fixture(relative)
            if result:
                return result
        except subprocess.CalledProcessError:
            pass
        time.sleep(0.5)
    raise AssertionError("Fixture was not saved: " + relative)


def seed_files(legacy=False):
    uid = adb("shell", "stat", "-c", "%u", DATA)
    assert uid.isdigit() and int(uid) >= 10000
    theme = '<int name="theme_mode" value="17" />' if legacy else '<string name="theme_mode">dark</string>'
    gpu = '<boolean name="local_use_gpu" value="false" />' if legacy else '<string name="local_use_gpu">false</string>'
    content = '<map><string name="minimum_age_14_v1">accepted</string>' + theme + gpu + (
        '<string name="ai_provider_mode">local</string>'
        '<string name="local_model_id">upgrade-fixture</string></map>')
    fixtures = {"shared_prefs/aircall_settings.xml": content,
                "files/models/upgrade-fixture.litertlm": "test-only-model-bytes",
                "files/tools/notes.txt": "test-only-note"}
    for relative, value in fixtures.items():
        source = RESULTS / (Path(relative).name + ".fixture")
        source.write_text(value)
        destination = DATA + "/" + relative
        adb("shell", "mkdir", "-p", str(Path(destination).parent))
        adb("push", source, destination)
        adb("shell", "chown", uid + ":" + uid, str(Path(destination).parent), destination)
        adb("shell", "chmod", "600", destination)
    adb("shell", "chown", "-R", uid + ":" + uid, DATA + "/files", DATA + "/shared_prefs")
    adb("shell", "restorecon", "-RF", DATA)


def seed_chat_and_key():
    chat_ui()
    click_any(("메시지 입력", "메시지를 입력하세요"))
    adb("shell", "input", "text", MESSAGE)
    click_any(("메시지 전송", "전송"))
    wait_ui(lambda root: has(root, MESSAGE) and not has(root, "생각하고 있어요"), "saved test message")
    encrypted = wait_file("files/chats/rooms.enc")
    assert MESSAGE.encode() not in encrypted, "Chat fixture must be encrypted"
    adb("shell", "input", "keyevent", "KEYCODE_BACK")  # dismiss the keyboard
    click_any(("메뉴 열기", "메뉴"))
    click("설정")
    click("AI 및 모델")
    click("API 키", scroll=True)
    adb("shell", "input", "text", FAKE_KEY)
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    click("키 저장", scroll=True)
    encrypted_key = wait_file("files/credentials/cloud_ai.bin")
    assert FAKE_KEY.encode() not in encrypted_key, "Credential fixture must be encrypted"
    record("Production UI saved encrypted chat and synthetic API key")
    return encrypted_key


def select_saved_chat():
    chat_ui()
    click_any(("메뉴 열기", "메뉴"))
    click(MESSAGE)
    wait_ui(lambda root: has(root, MESSAGE) and has(root, "나"), "decrypted saved chat")


def saved_stopped_activity(state, component):
    # Android 35/36 prints two spaces after Hist and uses state=, not mState=.
    # Inspect only the target's Hist record, never a neighboring launcher's state.
    blocks = re.split(r"(?=^\s*\* Hist\s+#)", state, flags=re.M)
    for block in blocks:
        header = re.match(r"\s*\* Hist\s+#\d+: ActivityRecord\{[^\n]+", block)
        if header and component in header[0]:
            if (re.search(r"\b(?:mState|state)=STOPPED\b", block) and
                    re.search(r"\b(?:haveState|mHaveState)=true\b", block) and
                    "finishing=true" not in block):
                return True
    return False


def background_and_kill(label):
    previous_pid = adb("shell", "pidof", PACKAGE)
    assert re.fullmatch(r"\d+", previous_pid)
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    deadline = time.monotonic() + 15
    saved = False
    while time.monotonic() < deadline:
        state = adb("shell", "dumpsys", "activity", "activities")
        (RESULTS / (label + "-activity.txt")).write_text(state)
        saved = saved_stopped_activity(state, COMPONENT)
        if saved:
            break
        time.sleep(1)
    if not saved:
        print(state, flush=True)
        raise AssertionError("Android must stop and save the real production activity before process death")
    adb("shell", "kill", "-9", previous_pid)
    for _ in range(15):
        if not adb("shell", "pidof", PACKAGE, check=False):
            record("Real production process death with saved activity state: " + label)
            return previous_pid
        time.sleep(0.2)
    raise AssertionError("Production process was not killed")


def require_no_crash(label):
    crash = adb("logcat", "-d", "-b", "crash")
    (RESULTS / (label + "-crashes.txt")).write_text(crash)
    assert PACKAGE not in crash, "Production crash found in Android crash buffer"


def main():
    RESULTS.mkdir(parents=True, exist_ok=True)
    assert adb("shell", "getprop", "ro.kernel.qemu") == "1", "Disposable emulator required"
    # The emulator's post-boot configuration can race adbd's root restart.
    # Retry only this setup transition; never retry app writes or UI assertions.
    for _ in range(10):
        try:
            adb("wait-for-device")
            adb("root", check=False)
            adb("wait-for-device")
            if adb("shell", "id", "-u") == "0":
                break
        except subprocess.CalledProcessError as failure:
            print("Emulator adbd reconnect: " + (failure.stdout or ""), flush=True)
        time.sleep(1)
    assert adb("shell", "id", "-u") == "0", "Rooted test emulator required for synthetic fixtures"
    baseline = next(Path("signed-runtime/baseline").glob("*.apk"))
    current = next(Path("signed-runtime/current").glob("*.apk"))
    adb("install", baseline)
    adb("shell", "pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS")
    seed_files()
    launch()
    baseline_healthy = True
    try:
        chat_ui()
    except AssertionError:
        baseline_healthy = False
        (RESULTS / "baseline-crashes.txt").write_text(adb("logcat", "-d", "-b", "crash"))
        (RESULTS / "baseline-logcat.txt").write_text(adb("logcat", "-d"))
        (RESULTS / "baseline-activities.txt").write_text(adb("shell", "dumpsys", "activity", "activities"))
        if (RESULTS / "ui.xml").exists():
            (RESULTS / "baseline-ui.xml").write_bytes((RESULTS / "ui.xml").read_bytes())
        record("Older production UI failed to render; updating its retained files", baselineUiHealthy=False)
    if baseline_healthy:
        seed_chat_and_key()
        adb("shell", "am", "force-stop", PACKAGE)
        launch("models")
        try:
            models_ui(legacy=True)
            background_and_kill("before-version-update")
        except AssertionError:
            (RESULTS / "baseline-models-crashes.txt").write_text(adb("logcat", "-d", "-b", "crash"))
            (RESULTS / "baseline-models-logcat.txt").write_text(adb("logcat", "-d"))
            record("Older production models screen failed the saved-state check; retaining encrypted files for update")
            adb("shell", "am", "force-stop", PACKAGE)
    else:
        adb("shell", "am", "force-stop", PACKAGE)
    retained = {relative: read_fixture(relative) for relative in (
        "shared_prefs/aircall_settings.xml", "files/models/upgrade-fixture.litertlm", "files/tools/notes.txt")}
    if baseline_healthy:
        retained.update({relative: read_fixture(relative) for relative in (
            "files/chats/rooms.enc", "files/credentials/cloud_ai.bin")})
    adb("install", "-r", current)
    for relative, value in retained.items():
        assert read_fixture(relative) == value, "In-place APK update changed fixture: " + relative
    record("Higher-version signed update preserved all seeded files", encryptedOldData=baseline_healthy)
    adb("logcat", "-c")
    adb("shell", "am", "force-stop", PACKAGE)
    launch()
    chat_ui()
    if baseline_healthy:
        select_saved_chat()
    else:
        seed_chat_and_key()
    require_no_crash("after-version-update")
    record("Updated production chat rendered and encrypted history reopened")
    # Also replace a data-bearing current APK before the clean-install comparison.
    adb("shell", "am", "force-stop", PACKAGE)
    chat_before = read_fixture("files/chats/rooms.enc")
    key_before = read_fixture("files/credentials/cloud_ai.bin")
    adb("install", "-r", current)
    assert read_fixture("files/chats/rooms.enc") == chat_before
    assert read_fixture("files/credentials/cloud_ai.bin") == key_before
    launch()
    select_saved_chat()
    record("Same signed APK replacement retained ciphertext and reopened the saved conversation")
    for index in range(3):
        adb("shell", "am", "force-stop", PACKAGE)
        launch("models")
        models_ui()
        old_pid = background_and_kill("restore-" + str(index))
        launch()
        root = reopened_main_ui()
        assert adb("shell", "pidof", PACKAGE) != old_pid
        record("Production main screen reopened in a new process", iteration=index + 1,
               renderedScreen="models" if has(root, "GPU 가속") else "chat")
    adb("shell", "am", "force-stop", PACKAGE)
    seed_files(legacy=True)
    adb("shell", "mkdir", "-p", DATA + "/files/credentials/oauth.gmail.account.bin")
    uid = adb("shell", "stat", "-c", "%u", DATA)
    adb("shell", "chown", uid + ":" + uid, DATA + "/files/credentials/oauth.gmail.account.bin")
    adb("shell", "restorecon", "-RF", DATA)
    launch()
    select_saved_chat()
    assert read_fixture("files/credentials/cloud_ai.bin") == key_before
    require_no_crash("legacy-and-restore")
    record("Legacy setting types and unreadable account fixture did not terminate production")
    # Uninstall is deliberately confined to the final clean-install comparison on this emulator.
    adb("uninstall", PACKAGE)
    adb("install", current)
    adb("shell", "pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS")
    adb("logcat", "-c")
    launch()
    click("만 14세 이상입니다")
    chat_ui()
    require_no_crash("clean-install")
    record("Clean production install rendered onboarding and chat without a crash")
    print("PASS: actual privately signed production runtime", flush=True)


if __name__ == "__main__":
    try:
        main()
    finally:
        RESULTS.mkdir(parents=True, exist_ok=True)
        for name, command in (("final-logcat.txt", ("logcat", "-d")),
                              ("final-activities.txt", ("shell", "dumpsys", "activity", "activities"))):
            (RESULTS / name).write_text(adb(*command, check=False))
        print("Final emulator crash buffer:\n" + adb("logcat", "-d", "-b", "crash", check=False), flush=True)
        if (RESULTS / "ui.xml").exists():
            root = ET.parse(RESULTS / "ui.xml").getroot()
            print("Final emulator UI controls: " + json.dumps([
                {"text": node.get("text", ""), "description": node.get("content-desc", "")}
                for node in nodes(root) if node.get("text") or node.get("content-desc")
            ], ensure_ascii=False), flush=True)
