#!/usr/bin/env python3
"""Source-only login glass regression checks (not Android runtime/UI tests).

Run from any directory:
    python3 .github/test_login_glass.py

The original login handlers are compared with the exact pre-fix commit. Git
history is required for the preservation checks; no credentials or account data
are read. Java/Kotlin compilation and real-device checks remain separate gates.
"""
from pathlib import Path
import re
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]
BASELINE = "e3524f50c"
JAVA = "TMessagesProj/src/main/java/org/telegram/ui/"
LOGIN = JAVA + "LoginActivity.java"
KEYBOARD = JAVA + "Components/CustomPhoneKeyboardView.java"
BACKGROUND = JAVA + "Components/LoginBackgroundLayout.java"


def text(path):
    return (ROOT / path).read_text(encoding="utf-8")


def original(path):
    return subprocess.run(["git", "show", f"{BASELINE}:{path}"], cwd=ROOT,
                          check=True, capture_output=True, text=True).stdout


def block(source, pattern):
    # Ignore Java comments and string/character literals, while retaining offsets.
    tokens = r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\''
    masked = re.sub(tokens, lambda m: " " * len(m.group()), source, flags=re.S)
    match = re.search(pattern, masked)
    if not match:
        raise AssertionError(f"Java block missing: {pattern}")
    opening = masked.index("{", match.start())
    depth = 0
    for i in range(opening, len(masked)):
        if masked[i] == "{":
            depth += 1
        elif masked[i] == "}":
            depth -= 1
            if depth == 0:
                return source[match.start():i + 1]
    raise AssertionError("Unbalanced Java block")


class LoginGlassRegression(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.login = text(LOGIN)
        cls.keyboard = text(KEYBOARD)
        cls.background = text(BACKGROUND)
        cls.original_login = original(LOGIN)
        cls.original_keyboard = original(KEYBOARD)

    def test_login_business_views_are_unchanged(self):
        # Includes phone formatting, country selection, network requests,
        # passkeys, verification, password, registration and payment handlers.
        names = re.findall(r"(?:public|private)(?:\s+final)?(?:\s+static)?\s+class\s+(\w+)",
                           self.original_login)
        names = [name for name in names if name != "LoginActivity"]
        self.assertGreaterEqual(len(names), 10)
        for name in names:
            with self.subTest(view=name):
                pattern = rf"\bclass\s+{name}\b"
                if name == "PhoneNumberConfirmView":
                    # Its blur snapshot deliberately uses the neutral glass
                    # source instead of the old opaque white fill.
                    continue
                self.assertEqual(block(self.original_login, pattern), block(self.login, pattern))

    def test_protocol_import_and_consent_are_unchanged(self):
        for method in ("showProtocolLoginOptions", "showProtocolLoginDisclaimer",
                       "onActivityResultFragment", "onRequestPermissionsResultFragment",
                       "loadCurrentState", "clearCurrentState", "putBundleToEditor",
                       "onBackPressed", "showKeyboard", "setCustomKeyboardVisible"):
            with self.subTest(method=method):
                pattern = rf"\b(?:public|private)(?:\s+static)?\s+[\w<>]+\s+{method}\s*\("
                self.assertEqual(block(self.original_login, pattern), block(self.login, pattern))

    def test_keyboard_constructor_and_input_are_unchanged(self):
        self.assertEqual(block(self.original_keyboard, r"public CustomPhoneKeyboardView\("),
                         block(self.keyboard, r"public CustomPhoneKeyboardView\("))
        for method in ("canScrollHorizontally", "setDispatchBackWhenEmpty",
                       "setupBackButtonDetector", "setViewToFindFocus", "checkFindEditText",
                       "setEditText", "onLayout", "onMeasure"):
            with self.subTest(method=method):
                pattern = rf"\b(?:public|private|protected)\s+[\w<>]+\s+{method}\s*\("
                self.assertEqual(block(self.original_keyboard, pattern), block(self.keyboard, pattern))
        self.assertEqual(block(self.original_keyboard, r"class NumberButtonView\b"),
                         block(self.keyboard, r"class NumberButtonView\b"))
        for runnable in ("onBackButton", "detectLongClick"):
            pattern = rf"private final Runnable {runnable}\s*="
            self.assertEqual(block(self.original_keyboard, pattern), block(self.keyboard, pattern))

    def test_no_global_theme_mutation_or_opaque_root_reset(self):
        self.assertNotIn("restoreOfficialLoginTheme", self.login)
        self.assertNotIn("Theme.applyTheme(", self.login)
        self.assertNotIn("Theme.selectedAutoNightType =", self.login)
        self.assertNotIn("fragmentView.setBackgroundColor(Theme.getColor", self.login)
        self.assertNotIn("sizeNotifierFrameLayout.setBackgroundColor(Theme.getColor", self.login)
        self.assertIn("setHasOwnBackground(true)", self.login)

    def test_neutral_glass_has_no_wallpaper_or_capture_recursion(self):
        self.assertIn("new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray))", self.background)
        self.assertNotIn("Theme.getCachedWallpaperNonBlocking()", self.background)
        self.assertNotIn("Theme.getCachedWallpaper()", self.background)
        for forbidden in ("RuntimeShader", "new RenderNode", "root.draw(",
                          "fragmentView.draw(", "DynamicVideoWallpaperHelper", ".recycle()"):
            self.assertNotIn(forbidden, self.background)
        self.assertIn("setDrawBackgroundBehindTopBars(true)", self.background)
        self.assertIn("getGlassFactory()", self.background)

    def test_damaged_wallpaper_and_loading_indicator_have_guards(self):
        refresh = block(self.background, r"public void refreshWallpaper\(")
        self.assertLess(refresh.index("new ColorDrawable"),
                        refresh.index("setBackgroundImage(surface, false)"))
        draw = block(self.background, r"protected boolean drawChild\(")
        self.assertIn("floatingButton.progressView.setProgressColor", self.login)
        self.assertIn("canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundGray))", self.login)
        self.assertNotIn("canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite))", self.login)
        self.assertIn("applyLoginGlass(phoneView.protocolLoginButton, 12, 0, true)", self.login)
        self.assertIn("new LayerDrawable(new Drawable[]{glassBackground, originalBackground})", self.login)

    def test_lifecycle_and_glass_subscriptions_are_paired(self):
        event = "NotificationCenter.didSetNewWallpapper"
        self.assertIn(f"addObserver(this, {event})", self.login)
        self.assertIn(f"removeObserver(this, {event})", self.login)
        self.assertIn("positionWatcher.shutdown()", self.background)
        self.assertIn("sizeNotifierFrameLayout.dispose()", self.login)
        self.assertIn("if (factory != null && glassFactory != factory)", self.keyboard)
        update = block(self.keyboard, r"public void updateColors\(")
        self.assertNotIn("factory.create(", update)
        self.assertIn("setPadding(left, top, right, bottom)", self.keyboard)
        self.assertIn("setPadding(left, top, right, bottom)", self.login)

    def test_other_keyboard_callers_remain_opt_in(self):
        default = block(self.keyboard, r"private static Drawable getButtonDrawable\(int index\)")
        self.assertIn("getButtonDrawable(index, false)", default)
        setter_calls = []
        for source in (ROOT / "TMessagesProj/src/main/java").rglob("*.java"):
            source_text = source.read_text(encoding="utf-8")
            variables = re.findall(r"\bCustomPhoneKeyboardView\s+(\w+)\s*[;=]", source_text)
            if any(re.search(rf"\b{variable}\.setGlassBackground\s*\(", source_text)
                   for variable in variables):
                setter_calls.append(source.name)
        self.assertEqual(setter_calls, ["LoginActivity.java"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
