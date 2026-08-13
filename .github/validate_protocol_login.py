#!/usr/bin/env python3
"""Automates an end-to-end protocol-login validation on an Android emulator.

The authorized test ZIP is supplied at runtime by an encrypted Actions secret.
The script never prints the ZIP content or auth key.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path


def run(cmd: list[str], check: bool = True, capture: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(cmd, check=check, text=True, capture_output=capture)


def adb(*args: str, check: bool = True, capture: bool = True) -> subprocess.CompletedProcess[str]:
    return run(["adb", *args], check=check, capture=capture)


def screenshot(out_dir: Path, name: str) -> None:
    remote = f"/sdcard/{name}.png"
    adb("exec-out", "screencap", "-p", capture=False).stdout if False else None
    with (out_dir / f"{name}.png").open("wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], check=True, stdout=f)


def dump_ui(out_dir: Path, name: str) -> list[ET.Element]:
    remote = f"/sdcard/{name}.xml"
    adb("shell", "uiautomator", "dump", remote)
    content = adb("exec-out", "cat", remote).stdout
    (out_dir / f"{name}.xml").write_text(content, encoding="utf-8")
    return list(ET.fromstring(content).iter("node"))


def bounds_center(bounds: str) -> tuple[int, int]:
    values = [int(x) for x in re.findall(r"\d+", bounds)]
    if len(values) != 4:
        raise ValueError(f"unexpected bounds: {bounds}")
    left, top, right, bottom = values
    return ((left + right) // 2, (top + bottom) // 2)


def tap(x: int, y: int) -> None:
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(1.0)


def tap_text(out_dir: Path, dump_name: str, expected: str, timeout: int = 15) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        nodes = dump_ui(out_dir, dump_name)
        for node in nodes:
            label = node.attrib.get("text", "") or node.attrib.get("content-desc", "")
            if label == expected:
                x, y = bounds_center(node.attrib["bounds"])
                tap(x, y)
                return True
        time.sleep(1)
    return False


def contains_text(nodes: list[ET.Element], needle: str) -> bool:
    return any(
        needle.lower() in ((node.attrib.get("text", "") or node.attrib.get("content-desc", "")).lower())
        for node in nodes
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True)
    parser.add_argument("--session", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    apk = Path(args.apk)
    session = Path(args.session)

    adb("wait-for-device")
    adb("install", "-r", str(apk))
    adb("shell", "mkdir", "-p", "/sdcard/Download")
    adb("push", str(session), "/sdcard/Download/session.zip")
    adb("shell", "am", "force-stop", "com.Huanghun", check=False)
    adb("shell", "monkey", "-p", "com.Huanghun", "1")
    time.sleep(4)
    screenshot(out_dir, "01_login")

    # The login activity overflow menu remains in the conventional top-right position
    # across the Pixel 2 API 29 emulator image used by this workflow.
    tap(1018, 84)
    screenshot(out_dir, "02_overflow")
    if not tap_text(out_dir, "02_overflow_ui", "协议登录"):
        raise RuntimeError("PROTOCOL_LOGIN_MENU_NOT_FOUND")

    screenshot(out_dir, "03_protocol_options")
    if not tap_text(out_dir, "03_options_ui", "session登录"):
        raise RuntimeError("SESSION_LOGIN_OPTION_NOT_FOUND")

    screenshot(out_dir, "04_picker")
    if not tap_text(out_dir, "04_picker_ui", "session.zip"):
        raise RuntimeError("SESSION_FILE_NOT_FOUND_IN_PICKER")

    # The app itself enforces a 20-second verification timeout. Allow network and UI handoff time.
    time.sleep(30)
    screenshot(out_dir, "05_result")
    result_nodes = dump_ui(out_dir, "05_result_ui")
    login_visible = contains_text(result_nodes, "Your phone number") or contains_text(result_nodes, "你的手机号码")
    timeout_visible = contains_text(result_nodes, "协议登录超时")

    logcat = adb("logcat", "-d", "-v", "threadtime", check=False).stdout
    (out_dir / "logcat.txt").write_text(logcat, encoding="utf-8", errors="replace")
    activity = adb("shell", "dumpsys", "activity", "activities", check=False).stdout
    (out_dir / "activity.txt").write_text(activity, encoding="utf-8", errors="replace")

    if timeout_visible:
        raise RuntimeError("PROTOCOL_LOGIN_TIMEOUT")
    if login_visible:
        raise RuntimeError("LOGIN_SCREEN_STILL_VISIBLE")

    print("PROTOCOL_LOGIN_ANDROID_VALIDATED")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"PROTOCOL_LOGIN_VALIDATION_FAILURE: {exc}", file=sys.stderr)
        raise
