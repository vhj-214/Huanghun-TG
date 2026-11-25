#!/usr/bin/env python3
"""Regression checks for the embedded protocol result UI (not an Android device test)."""
from pathlib import Path
import re
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]
MAKER = ROOT / "TMessagesProj/src/main/java/com/Huanghun/protocol/maker"
HTML = ROOT / "TMessagesProj/src/main/assets/huanghun/protocol_ui.html"


def java_block(source, marker):
    start = source.index(marker)
    opening = source.index("{", start)
    depth = 0
    for index in range(opening, len(source)):
        depth += (source[index] == "{") - (source[index] == "}")
        if depth == 0:
            return source[start:index + 1]
    raise AssertionError(marker)


NODE_STUB = r"""
const assert = require('assert');
const elements = {};
function element(id) {
  if (!elements[id]) elements[id] = {
    id, style: {}, textContent: '', innerHTML: '', offsetHeight: 10,
    classList: {toggle() {}}, querySelector() {return element(id + '-code');}
  };
  return elements[id];
}
element('result-share').style.display = 'none';
global.window = global;
global.document = {
  body: element('body'), getElementById: element,
  querySelector() {return element('scroll');}, addEventListener() {}
};
global.requestAnimationFrame = () => {};
let shares = 0, shareArguments = null;
window.Huanghun = {share() {shares++; shareArguments = arguments.length;}};
"""


class ProtocolShareUiTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.html = HTML.read_text()
        cls.web = (MAKER / "ProtocolWebUi.java").read_text()
        cls.build = (MAKER / "ProtocolBuildUi.java").read_text()
        cls.js = re.search(r"<script>(.*?)</script>", cls.html, re.S).group(1)

    def run_js(self, tests):
        result = subprocess.run(["node", "-"], input=NODE_STUB + self.js + tests,
                                text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_success_share_calls_native_without_paths_or_recipients(self):
        self.run_js(r"""
HH.result({files: [{name: 'session.zip', size: 100}, {name: 'Passkey.zip', size: 200}], canShare: true});
assert.equal(element('result-share').style.display, '');
HH.share();
assert.equal(shares, 1);
assert.equal(shareArguments, 0);
assert.ok(element('result-files').innerHTML.includes('session.zip'));
assert.ok(element('result-files').innerHTML.includes('Passkey.zip'));
""")

    def test_empty_or_unshareable_results_never_share(self):
        self.run_js(r"""
HH.result({files: [], canShare: false}); HH.share();
assert.equal(shares, 0);
HH.result({files: [{name: 'session.zip', size: 100}], canShare: false}); HH.share();
assert.equal(shares, 0);
""")

    def test_busy_and_error_hide_previous_share_button(self):
        self.run_js(r"""
HH.result({files: [{name: 'session.zip'}]}); HH.busy('working', ''); HH.share();
assert.equal(shares, 0);
HH.result({files: [{name: 'session.zip'}]}); HH.error('failed'); HH.share();
assert.equal(shares, 0);
""")

    def test_result_names_are_escaped_and_missing_bridge_shows_error(self):
        self.run_js(r"""
HH.result({files: [{name: '<img src=x onerror=alert(1)>.zip'}]});
assert.ok(!element('result-files').innerHTML.includes('<img'));
assert.ok(element('result-files').innerHTML.includes('&lt;img'));
window.Huanghun = {}; HH.share();
assert.ok(element('error-text').textContent.includes('分享桥接不可用'));
assert.equal(shares, 0);
""")

    def test_every_protocol_completion_uses_common_result_ui(self):
        for marker in ['private static void exportLocalAccount(',
                       'private static void convertSelectedFile(',
                       'private static void createExternalPasskey(']:
            self.assertIn('showResult(owner,', java_block(self.build, marker))
        external = (MAKER / 'ExternalAccountLogin.java').read_text()
        self.assertIn('ProtocolBuildUi.showResult(owner,',
                      java_block(external, 'private static void extractAndExport('))
        result = java_block(self.build, 'public static void showResult(')
        self.assertIn('ProtocolWebUi.presentResult(', result)
        self.assertIn('setNeutralButton("分享"', result)
        self.assertIn('ProtocolShareHelper.share(context, shareFiles)', result)
        self.assertIn('收件人可能使用它登录账号', result)

    def test_native_bridge_only_uses_live_generated_file_snapshot(self):
        bridge = java_block(self.web, 'public void share()')
        self.assertIn('AndroidUtilities.runOnUIThread', bridge)
        self.assertIn('current != ProtocolWebUi.this', bridge)
        self.assertIn('released', bridge)
        self.assertIn('resultFiles.isEmpty()', bridge)
        self.assertIn('new ArrayList<>(resultFiles)', bridge)
        self.assertIn('ProtocolShareHelper.share(fragment,', bridge)
        self.assertNotIn('sendMessage(', bridge)
        self.assertNotIn('prepareSendingDocuments(', bridge)
        for marker in ['public static boolean presentError(',
                       'public static void showBusy(', 'private void dismiss()']:
            self.assertIn('resultFiles.clear()', java_block(self.web, marker))
        self.assertRegex(self.html, r'id="result-share"[^>]+onclick="HH.share\(\)"')
        self.assertIn('请仅分享给可信对象', self.html)


if __name__ == '__main__':
    unittest.main(verbosity=2)
