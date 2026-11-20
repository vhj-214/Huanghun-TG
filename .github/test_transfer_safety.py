#!/usr/bin/env python3
"""Source-level guards for MTProto transfer scheduling invariants."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
UPLOAD = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/FileUploadOperation.java"
CONNECTIONS = ROOT / "TMessagesProj/jni/tgnet/ConnectionsManager.cpp"


def java_block(source: str, marker: str) -> str:
    start = source.index(marker)
    opening = source.index("{", start)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[start:index + 1]
    raise AssertionError(f"Unclosed Java declaration: {marker}")


class TransferSafetyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.upload = UPLOAD.read_text(encoding="utf-8")
        cls.start_request = java_block(cls.upload, "private void startUploadRequest()")
        cls.connections = CONNECTIONS.read_text(encoding="utf-8")

    def test_upload_startup_is_bounded_by_initialized_window(self):
        start = java_block(self.upload, "public void start()")
        self.assertLess(start.index("startUploadRequest();"), start.index("a < maxRequestsCount"))
        self.assertIn("currentUploadRequetsCount >= maxRequestsCount", self.start_request)
        self.assertIn("NekoConfig.uploadBoost.Bool() && !isEncrypted", self.start_request)
        self.assertIn("maxUploadingBoostKBytes", self.start_request)

    def test_encrypted_iv_pool_tracks_the_real_request_limit(self):
        self.assertIn("freeRequestIvs = new ArrayList<>(maxRequestsCount)", self.start_request)
        self.assertIn("a < maxRequestsCount; a++", self.start_request)
        self.assertIn("if (maxRequestsCount > 0 && currentUploadRequetsCount >= maxRequestsCount)", self.start_request)
        self.assertIn("!isEncrypted ? maxUploadingBoostKBytes : maxUploadingKBytes", self.start_request)

    def test_resume_chunk_size_and_actual_byte_offset_are_persisted(self):
        self.assertIn('preferences.getInt(fileKey + "_chunk", 0)', self.start_request)
        self.assertIn('editor.putInt(fileKey + "_chunk", uploadChunkSize)', self.upload)
        self.assertIn("requestBytesOffset = stream.getFilePointer()", self.start_request)
        self.assertIn("final long currentRequestBytesOffset = requestBytesOffset", self.upload)
        self.assertNotIn("currentRequestPartNum + currentRequestBytes", self.upload)

    def test_native_download_counts_update_shared_maps(self):
        self.assertNotIn("auto map = request->isCancelRequest()", self.connections)
        self.assertGreaterEqual(self.connections.count("auto &map = request->isCancelRequest()"), 2)
        self.assertIn("map[datacenterId] = currentCount + 1;", self.connections)


if __name__ == "__main__":
    unittest.main()
