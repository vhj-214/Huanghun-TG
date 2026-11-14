#!/usr/bin/env python3
"""Source-level guard for the dynamic wallpaper first-frame fallback.

This is not an Android UI test; it checks that static wallpaper remains visible
until TextureView receives a real decoded frame.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "TMessagesProj/src/main/java/tw/nekomimi/nekogram/helpers/DynamicVideoWallpaperHelper.java"
CHAT = ROOT / "TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java"
DIALOGS = ROOT / "TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java"
PROFILE = ROOT / "TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java"


class DynamicWallpaperFallbackRegression(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SOURCE.read_text(encoding="utf-8")
        cls.chat = CHAT.read_text(encoding="utf-8")
        cls.dialogs = DIALOGS.read_text(encoding="utf-8")
        cls.profile = PROFILE.read_text(encoding="utf-8")

    def test_real_pages_use_the_shared_dynamic_wallpaper_player(self):
        self.assertIn("DynamicVideoWallpaperHelper.attach(wallpaperRoot, wallpaperAnchor", self.chat)
        self.assertIn("dynamicVideoWallpaperPlayer.suppressViewAlpha(contentView.backgroundView);", self.chat)
        self.assertIn("DynamicVideoWallpaperHelper.attach(\n                    wallpaperRoot", self.dialogs)
        self.assertIn("DynamicVideoWallpaperHelper.attach(contentView, context, currentAccount, getDialogId())", self.profile)
        self.assertIn("return attachMulti(parent, contentAnchor, context, account, dialogId);", self.source)

    def test_attach_records_fallback_without_hiding_it(self):
        self.assertIn("player.suppressViewAlpha(background);", self.source)
        self.assertIn("player.suppressViewAlpha(originalBackground);", self.source)
        self.assertNotIn("background.setAlpha(0f);", self.source)
        self.assertNotIn("originalBackground.setAlpha(0f);", self.source)

    def test_prepare_restores_static_background_before_async_decode(self):
        prepare = self.source.split("private void prepare(SurfaceTexture surfaceTexture)", 1)[1]
        prepare = prepare.split("private void configureVideoBuffer()", 1)[0]
        self.assertLess(prepare.index("restoreSuppressedAlphas();"), prepare.index("mediaPlayer.prepareAsync();"))
        self.assertIn("textureView.setAlpha(0f);", prepare)

    def test_texture_fades_in_only_after_a_decoded_frame(self):
        self.assertIn("if (released || !mediaPrepared || firstFrameRendered)", self.source)
        self.assertIn("public void onSurfaceTextureUpdated(SurfaceTexture surface)", self.source)
        callback = self.source.split("public void onSurfaceTextureUpdated(SurfaceTexture surface)", 1)[1]
        self.assertIn("firstFrameRendered = true;", callback)
        self.assertIn("textureView.animate().alpha(.96f)", callback)
        self.assertIn("state.view.setAlpha(0f);", callback)

    def test_release_restores_original_background_alpha(self):
        release = self.source.split("public void release()", 1)[1]
        release = release.split("private static final class SuppressedBackground", 1)[0]
        self.assertIn("restoreSuppressedAlphas();", release)


if __name__ == "__main__":
    unittest.main(verbosity=2)
