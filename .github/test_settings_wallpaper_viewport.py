#!/usr/bin/env python3
"""Exercise the actual wallpaper viewport Java code with small Android drawing stubs.

This is a geometry regression test, not a substitute for screenshots on an Android
emulator or device. Requires Python 3 and a JDK (the project already uses Java 21).
"""

from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
BASE_FRAGMENT = ROOT / "TMessagesProj/src/main/java/org/telegram/ui/ActionBar/BaseFragment.java"
TWO_STEP = ROOT / "TMessagesProj/src/main/java/org/telegram/ui/TwoStepVerificationActivity.java"
AUTO_DELETE = ROOT / "TMessagesProj/src/main/java/org/telegram/ui/AutoDeleteMessagesActivity.java"
SECTIONS = ROOT / "TMessagesProj/src/main/java/org/telegram/ui/Components/SectionsScrollView.java"


def java_block(source: str, marker: str) -> str:
    """Extract a declaration with balanced braces from the production source."""
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


HARNESS = r"""
public class ViewportRegression {
    @interface NonNull {}
    @interface Nullable {}
    interface ViewParent {}
    static class Matrix {
        float dx, dy;
        void mapPoints(float[] points) { points[0] += dx; points[1] += dy; }
    }
    static final class PixelFormat { static final int TRANSLUCENT = -3; }
    static class ColorFilter {}
    static class Rect {
        int left, top, right, bottom;
        Rect() {}
        Rect(int l, int t, int r, int b) { left=l; top=t; right=r; bottom=b; }
        boolean isEmpty() { return left >= right || top >= bottom; }
        int width() { return right-left; }
        int height() { return bottom-top; }
    }
    static class Canvas {
        int x, y, drawnLeft, drawnTop, drawnRight, drawnBottom;
        java.util.ArrayList<int[]> stack = new java.util.ArrayList<>();
        int save() { stack.add(new int[]{x, y}); return stack.size(); }
        void translate(float dx, float dy) { x += (int)dx; y += (int)dy; }
        void restoreToCount(int count) {
            int[] previous = stack.get(count-1); x = previous[0]; y = previous[1];
            while (stack.size() >= count) stack.remove(stack.size()-1);
        }
        void drawRect(int l, int t, int r, int b) {
            drawnLeft=x+l; drawnTop=y+t; drawnRight=x+r; drawnBottom=y+b;
        }
    }
    static abstract class Drawable {
        Rect bounds = new Rect();
        Rect getBounds() { return bounds; }
        Rect copyBounds() { return new Rect(bounds.left, bounds.top, bounds.right, bounds.bottom); }
        void setBounds(int l, int t, int r, int b) { bounds = new Rect(l, t, r, b); }
        void setBounds(Rect r) { setBounds(r.left, r.top, r.right, r.bottom); }
        void invalidateSelf() {}
        public abstract void draw(Canvas c);
        public abstract void setAlpha(int alpha);
        public abstract void setColorFilter(ColorFilter filter);
        public abstract int getOpacity();
    }
    static class TestWallpaper extends Drawable {
        final boolean ignoreOrigin;
        TestWallpaper(boolean ignoreOrigin) { this.ignoreOrigin = ignoreOrigin; }
        public void draw(Canvas c) {
            Rect b = getBounds();
            if (ignoreOrigin) c.drawRect(0, 0, b.width(), b.height());
            else c.drawRect(b.left, b.top, b.right, b.bottom);
        }
        public void setAlpha(int alpha) {}
        public void setColorFilter(ColorFilter filter) {}
        public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
    static class View implements ViewParent {
        View parent;
        int width, height, localX, localY, translationX, translationY;
        int scrollX, scrollY;
        View(int width, int height, View parent) { this.width=width; this.height=height; this.parent=parent; }
        ViewParent getParent() { return parent; }
        View getRootView() { View v=this; while (v.parent != null) v=v.parent; return v; }
        int getWidth() { return width; }
        int getHeight() { return height; }
        int getLeft() { return localX; }
        int getTop() { return localY; }
        float getTranslationX() { return translationX; }
        float getTranslationY() { return translationY; }
        int getScrollX() { return scrollX; }
        int getScrollY() { return scrollY; }
        void transformMatrixToGlobal(Matrix matrix) {
            for (View v=this; v!=null; v=v.parent) {
                matrix.dx += v.localX + v.translationX;
                matrix.dy += v.localY + v.translationY;
                if (v.parent != null) {
                    matrix.dx -= v.parent.scrollX;
                    matrix.dy -= v.parent.scrollY;
                }
            }
        }
        void transformMatrixToLocal(Matrix matrix) {
            Matrix global = new Matrix();
            transformMatrixToGlobal(global);
            matrix.dx -= global.dx;
            matrix.dy -= global.dy;
        }
        void getLocationInWindow(int[] location) {
            int x=0, y=0;
            for (View v=this; v!=null; v=v.parent) {
                x += v.localX + v.translationX; y += v.localY + v.translationY;
            }
            location[0]=x; location[1]=y;
        }
    }
    static class NavigationLayout {
        View view;
        NavigationLayout(View view) { this.view=view; }
        View getView() { return view; }
    }
    View fragmentView, actionBar;
    NavigationLayout parentLayout;

    __FIND_VIEWPORT__
    __DRAWABLE__

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    static Canvas draw(View target, View viewport, boolean ignoreOrigin) {
        TestWallpaper wallpaper = new TestWallpaper(ignoreOrigin);
        wallpaper.setBounds(8, 9, 10, 11);
        WallpaperViewportDrawable drawable = new WallpaperViewportDrawable(wallpaper, wallpaper, target, viewport);
        drawable.setBounds(0, 0, target.width, target.height);
        Canvas canvas = new Canvas();
        drawable.draw(canvas);
        check(canvas.x == 0 && canvas.y == 0, "canvas state not restored");
        Rect restored = wallpaper.getBounds();
        check(restored.left == 8 && restored.top == 9 && restored.right == 10 && restored.bottom == 11,
                "wallpaper bounds not restored");
        return canvas;
    }
    static void scenario(int width, int height, int leftInset, int rightInset, int barHeight, int translation, boolean ignoreOrigin) {
        View navigation = new View(width, height, null);
        View page = new View(width, height, navigation);
        page.translationX = translation;
        View bar = new View(width-leftInset-rightInset, barHeight, page);
        bar.localX = leftInset;
        View body = new View(bar.width, height-barHeight, page);
        body.localX = leftInset; body.localY = barHeight;
        ViewportRegression fragment = new ViewportRegression();
        fragment.parentLayout = new NavigationLayout(navigation);
        fragment.fragmentView = body; fragment.actionBar = bar;
        View viewport = fragment.findSettingsWallpaperViewport();
        check(viewport == page, "expected the shared moving page container");
        Canvas top = draw(bar, viewport, ignoreOrigin);
        Canvas content = draw(body, viewport, ignoreOrigin);
        check(top.drawnLeft <= 0 && top.drawnRight >= bar.width,
                "wallpaper does not fill title bar at translation="+translation);
        check(top.drawnTop <= 0 && top.drawnBottom >= bar.height, "title bar height not covered");
        check(content.drawnLeft == top.drawnLeft, "horizontal wallpaper seam");
        check(content.drawnTop + barHeight == top.drawnTop, "vertical wallpaper seam");
        check(content.drawnRight >= body.width && content.drawnBottom >= body.height,
                "body not covered after inset or resize");
        if (translation > rightInset) {
            Canvas oldReference = draw(bar, navigation, false);
            check(oldReference.drawnRight < bar.width,
                    "test must detect the original moving-target/fixed-viewport crop");
        }
    }
    public static void main(String[] args) {
        for (boolean originIgnored : new boolean[]{false, true}) {
            for (int translation : new int[]{-150, 0, 39, 250}) {
                scenario(576, 1280, 0, 0, 135, translation, originIgnored);
                scenario(1080, 2400, 18, 32, 180, translation, originIgnored);
                scenario(1920, 1080, 80, 40, 90, translation, originIgnored);
                scenario(480, 820, 0, 16, 70, translation, originIgnored);
            }
        }
        View nav = new View(400, 900, null);
        View p1 = new View(400, 900, nav), p2 = new View(400, 900, nav);
        ViewportRegression fragment = new ViewportRegression();
        fragment.parentLayout = new NavigationLayout(nav);
        fragment.fragmentView = new View(400, 800, p1);
        fragment.actionBar = new View(400, 100, p2);
        check(fragment.findSettingsWallpaperViewport() == nav, "different parents should use navigation fallback");
        fragment.parentLayout = null;
        check(fragment.findSettingsWallpaperViewport() == nav, "missing navigation should use root fallback");
        fragment.actionBar = null;
        check(fragment.findSettingsWallpaperViewport() == p1, "pages without an action bar should use their parent");
        View nestedBar = new View(400, 100, fragment.fragmentView);
        fragment.actionBar = nestedBar;
        check(fragment.findSettingsWallpaperViewport() == fragment.fragmentView,
                "nested action bar should use the shared fragment root");
        fragment.fragmentView.scrollY = 20;
        nestedBar.localY = 35;
        Canvas nested = draw(nestedBar, fragment.fragmentView, true);
        check(nested.drawnTop == -15, "nested scroll offset was not included");
        System.out.println("PASS: viewport selection, moving containers, bounds, inset, rotation and resize geometry");
    }
}
"""


class WallpaperViewportTest(unittest.TestCase):
    def test_viewport_geometry_and_drawable_coordinates(self):
        source = BASE_FRAGMENT.read_text(encoding="utf-8")
        selection = java_block(source, "private View findSettingsWallpaperViewport()")
        drawable = java_block(source, "private static final class WallpaperViewportDrawable extends Drawable")
        harness = HARNESS.replace("__FIND_VIEWPORT__", selection).replace("__DRAWABLE__", drawable)
        with tempfile.TemporaryDirectory(prefix="wallpaper-viewport-test-") as directory:
            path = Path(directory) / "ViewportRegression.java"
            path.write_text(harness, encoding="utf-8")
            subprocess.run(["javac", str(path)], check=True, capture_output=True, text=True)
            result = subprocess.run(["java", "-cp", directory, "ViewportRegression"],
                                    check=True, capture_output=True, text=True)
            self.assertIn("PASS:", result.stdout)

    def test_two_step_async_updates_preserve_wallpaper_and_state_tag(self):
        source = TWO_STEP.read_text(encoding="utf-8")
        update = java_block(source, "private void updatePageBackground(int colorKey)")
        rows = java_block(source, "private void updateRows()")
        self.assertNotIn("fragmentView.setBackgroundColor", rows)
        self.assertIn("updatePageBackground(Theme.key_windowBackgroundGray)", rows)
        self.assertIn("updatePageBackground(Theme.key_windowBackgroundWhite)", rows)
        harness = r"""
public class PasswordBackgroundRegression {
    static class View {
        int background, tag;
        boolean glass;
        void setBackgroundColor(int color) { background=color; }
        void setTag(int key) { tag=key; }
    }
    static class Theme { static int getColor(int key) { return key + 100; } }
    View fragmentView = new View();
    static boolean isInGlassPage(View view) { return view.glass; }
    __UPDATE__
    public static void main(String[] args) {
        PasswordBackgroundRegression f = new PasswordBackgroundRegression();
        f.updatePageBackground(1);
        if (f.fragmentView.background != 101 || f.fragmentView.tag != 1)
            throw new AssertionError("native fallback must remain intact");
        f.fragmentView.glass = true;
        f.fragmentView.background = 999; // installed wallpaper
        f.updatePageBackground(2); // delayed password response
        if (f.fragmentView.background != 999 || f.fragmentView.tag != 2)
            throw new AssertionError("delayed password response replaced wallpaper or lost its tag");
        f.updatePageBackground(1); // password change/loading state
        if (f.fragmentView.background != 999 || f.fragmentView.tag != 1)
            throw new AssertionError("state update replaced wallpaper or lost its tag");
        System.out.println("PASS: password state changes preserve glass and native fallback");
    }
}
""".replace("__UPDATE__", update)
        with tempfile.TemporaryDirectory(prefix="settings-password-test-") as directory:
            path = Path(directory) / "PasswordBackgroundRegression.java"
            path.write_text(harness, encoding="utf-8")
            subprocess.run(["javac", str(path)], check=True, capture_output=True, text=True)
            result = subprocess.run(["java", "-cp", directory, "PasswordBackgroundRegression"],
                                    check=True, capture_output=True, text=True)
            self.assertIn("PASS:", result.stdout)

    def test_auto_delete_keeps_ripple_without_opaque_selector_layer(self):
        source = AUTO_DELETE.read_text(encoding="utf-8")
        update = java_block(source, "private void updateItems()")
        self.assertIn("scrollView.setGlassSectionsEnabled(true)", source)
        self.assertIn("Theme.createSelectorDrawable(", update)
        self.assertIn("Theme.RIPPLE_MASK_ALL", update)
        self.assertNotIn("createSelectorWithBackgroundDrawable", update)
        section = SECTIONS.read_text(encoding="utf-8")
        self.assertIn("glassSectionsEnabled && BaseFragment.isInGlassPage(this)", section)
        self.assertIn("RecyclerListView.drawBackgroundRect", section)  # non-glass fallback


if __name__ == "__main__":
    unittest.main(verbosity=2)
