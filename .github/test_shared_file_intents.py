#!/usr/bin/env python3
"""Compile the production share-URI helper against tiny Android API stubs.

This is a JVM contract test only; provider permission behavior still needs a device.
Run: python3 .github/test_shared_file_intents.py
"""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "TMessagesProj/src/main/java"
HELPER = JAVA / "org/telegram/messenger/SharedFileUriHelper.java"
LAUNCH = JAVA / "org/telegram/ui/LaunchActivity.java"

STUBS = {
    "android/os/Parcelable.java": "package android.os; public interface Parcelable {}",
    "android/os/Bundle.java": """package android.os;
import java.util.*;
public class Bundle { private final Map<String,Object> values=new HashMap<>();
public Object get(String k){return values.get(k);} public void put(String k,Object v){values.put(k,v);} }""",
    "android/net/Uri.java": """package android.net;
import android.os.Parcelable;
public final class Uri implements Parcelable { private final String value;
private Uri(String v){value=v;} public static Uri parse(String s){if(s==null)throw new NullPointerException();return new Uri(s);}
public String getScheme(){int i=value.indexOf(':');return i<0?null:value.substring(0,i);}
public String toString(){return value;} public boolean equals(Object o){return o instanceof Uri && value.equals(o.toString());}
public int hashCode(){return value.hashCode();} }""",
    "android/content/ClipData.java": """package android.content;
import java.util.*; import android.net.Uri;
public class ClipData { private final List<Item> items=new ArrayList<>(); public void addItem(Item i){items.add(i);}
public int getItemCount(){return items.size();} public Item getItemAt(int i){return items.get(i);}
public static class Item { private final Uri uri; private final boolean fail;
public Item(Uri u){this(u,false);} public Item(Uri u,boolean f){uri=u;fail=f;}
public Uri getUri(){if(fail)throw new SecurityException("revoked clip item");return uri;} } }""",
    "android/content/Intent.java": """package android.content;
import android.os.Bundle; import android.net.Uri;
public class Intent { public static final String EXTRA_STREAM="android.intent.extra.STREAM";
private Bundle extras; private ClipData clip; private Uri data;
public Bundle getExtras(){return extras;} public void setExtras(Bundle b){extras=b;}
public ClipData getClipData(){return clip;} public void setClipData(ClipData c){clip=c;}
public Uri getData(){return data;} public void setData(Uri u){data=u;} }""",
    "SharedUriRegression.java": """import java.util.*; import android.content.*; import android.net.Uri; import android.os.Bundle;
import org.telegram.messenger.SharedFileUriHelper;
public class SharedUriRegression {
static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
static void eq(Object a,Object b,String m){if(!Objects.equals(a,b))throw new AssertionError(m+": "+a+" != "+b);}
static Uri u(String s){return Uri.parse(s);} static ArrayList<Uri> collect(Intent i){return SharedFileUriHelper.collect(i);}
public static void main(String[] x){
 String test=x[0];
 switch(test){
 case "single_extra_stream": {Intent i=new Intent();Bundle b=new Bundle();b.put(Intent.EXTRA_STREAM,u("content://provider/archive.zip"));i.setExtras(b);
 eq(collect(i).toString(),Arrays.asList(u("content://provider/archive.zip")).toString(),"single URI");break;}
 case "multiple_extra_stream_mixed": {Intent i=new Intent();Bundle b=new Bundle();ArrayList<Object> values=new ArrayList<>();
 values.add(u("content://provider/app.apk"));values.add("content://provider/document.pdf");values.add("https://example.invalid/not-a-file");values.add(null);
 b.put(Intent.EXTRA_STREAM,values);i.setExtras(b);eq(collect(i).size(),2,"valid APK and document retained; malformed entries skipped");break;}
 case "clipdata_fallback_and_dedup": {Intent i=new Intent();ClipData c=new ClipData();c.addItem(new ClipData.Item(u("content://provider/file.txt")));
 c.addItem(new ClipData.Item(u("content://provider/broken"),true));c.addItem(new ClipData.Item(u("content://provider/other.zip")));i.setClipData(c);
 Bundle b=new Bundle();b.put(Intent.EXTRA_STREAM,new ArrayList<>(Arrays.asList(u("content://provider/file.txt"))));i.setExtras(b);
 ArrayList<Uri> got=collect(i);eq(got.size(),2,"ClipData contributes every readable distinct URI");eq(got.get(1).toString(),"content://provider/other.zip","malformed item does not stop later files");break;}
 case "data_fallback": {Intent i=new Intent();i.setData(u("file:///storage/shared/report.pdf"));eq(collect(i).size(),1,"data URI fallback");break;}
 case "empty_and_bad_scheme": {Intent i=new Intent();Bundle b=new Bundle();b.put(Intent.EXTRA_STREAM,Arrays.asList("","https://example.invalid/file"));i.setExtras(b);check(collect(i)==null,"no valid content/file URI returns null");check(collect(null)==null,"null Intent returns null");break;}
 case "zip_apk_document_unknown_mime": {String[] types={"application/zip","application/vnd.android.package-archive","application/pdf","application/octet-stream","application/x-unknown",null,"*/*"};
 for(String mime:types)check(!SharedFileUriHelper.isPhotoOrVideoMimeType(mime),"non-media is kept in document path: "+mime);
 check(SharedFileUriHelper.isPhotoOrVideoMimeType("image/png"),"image classified as media");check(SharedFileUriHelper.isPhotoOrVideoMimeType("video/mp4"),"video classified as media");break;}
 default:throw new AssertionError("unknown case "+test);
 }
 System.out.println("OK "+test);
} }""",
}

CASES = ("single_extra_stream", "multiple_extra_stream_mixed", "clipdata_fallback_and_dedup",
         "data_fallback", "empty_and_bad_scheme", "zip_apk_document_unknown_mime")


class SharedFileIntentTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not shutil.which("javac") or not shutil.which("java"):
            raise unittest.SkipTest("JDK javac/java is required")
        cls.temp = tempfile.TemporaryDirectory(prefix="shared-file-intents-")
        cls.addClassCleanup(cls.temp.cleanup)
        work = Path(cls.temp.name)
        stubs = dict(STUBS)
        stubs["org/telegram/messenger/SharedFileUriHelper.java"] = HELPER.read_text()
        for rel, content in stubs.items():
            target = work / rel
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(content + "\n")
        cls.classes = work / "classes"
        subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(cls.classes),
                        *map(str, work.rglob("*.java"))], check=True, capture_output=True, text=True)

    def test_launch_activity_keeps_failures_per_attachment(self):
        source = LAUNCH.read_text()
        start = source.index("private boolean handleSharedMultipleFiles(")
        end = source.index("/**\n     * Some file managers", start)
        method = source[start:end]
        self.assertIn("for (Uri uri : uris)", method)
        self.assertIn("catch (Throwable e)", method)
        self.assertIn("getContentResolver().getType(uri)", method)
        self.assertIn("SharedFileUriHelper.isPhotoOrVideoMimeType(itemType)", method)
        self.assertIn("MediaController.copyFileToCache(uri, \"file\")", method)
        self.assertIn("documentsUrisArray.add(uri)", method)
        self.assertNotIn("if (declaredType != null && (declaredType.startsWith(\"image/\")", method)
        self.assertIn("ACTION_SEND_MULTIPLE.equals(intent.getAction())", source)
        self.assertIn("handleSharedMultipleFiles(intent, intentAccount[0])", source)

    def test_single_share_uses_same_safe_uri_collector(self):
        source = LAUNCH.read_text()
        start = source.index("private static Uri getSharedFileUri(")
        end = source.index("@SuppressLint(\"Range\")", start)
        method = source[start:end]
        self.assertIn("SharedFileUriHelper.collect(intent)", method)
        self.assertIn("uris.get(0)", method)

    def test_invalid_or_revoked_uri_isolated_by_collector(self):
        source = HELPER.read_text()
        self.assertIn("A malformed item must not discard other shared files", source)
        self.assertIn("catch (Throwable ignored)", source)
        self.assertIn("appendUri(item.getUri(), result, seen)", source)
        self.assertIn("seen.add(key)", source)


def make_test(case):
    def test(self):
        result = subprocess.run(["java", "-cp", str(self.classes), "SharedUriRegression", case],
                                capture_output=True, text=True, timeout=20)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("OK " + case, result.stdout)
    return test


for name in CASES:
    setattr(SharedFileIntentTest, "test_" + name, make_test(name))

if __name__ == "__main__":
    unittest.main(verbosity=2)
