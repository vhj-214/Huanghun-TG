# 实际生效点静态审查报告

基于最终工作树的调用链检查：
- [PASS] root uses LoginBackgroundLayout
- [PASS] fragment opts out of ActionBar white fallback
- [PASS] root gets wallpaper
- [PASS] cached wallpaper is nonblocking
- [PASS] wallpaper notification refreshes actual root
- [PASS] phone fields glass actual instances
- [PASS] protocol original border preserved
- [PASS] keyboard opt-in after all views created
- [PASS] keyboard input handlers untouched by helper
- [PASS] wallpaper source has bad-bitmap guard

说明：这是源码调用链和编译级验证，不是真机 UI 测试。
