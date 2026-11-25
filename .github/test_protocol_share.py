#!/usr/bin/env python3
"""Compile the actual ProtocolShareHelper unchanged against small native-contract stubs.

No Android SDK, uploads, RPCs or real messages are used. Dialog dismissal, native
account-switch replacement and explicit Stars callbacks are driven by the tests.
The document stub's parameter declaration is extracted from this checkout, so a
wrong overload/parameter order fails javac. This is not an APK/device UI test.
Run: python3 .github/test_protocol_share.py
"""
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "TMessagesProj/src/main/java"
HELPER = JAVA / "com/Huanghun/protocol/maker/ProtocolShareHelper.java"
SENDER = JAVA / "org/telegram/messenger/SendMessagesHelper.java"

STUBS = {
    "android/content/Context.java": "package android.content; public class Context {}",
    "android/content/ContextWrapper.java": """package android.content;
public class ContextWrapper extends Context { final Context base;
public ContextWrapper(Context base){this.base=base;} public Context getBaseContext(){return base;} }""",
    "android/content/DialogInterface.java": """package android.content;
public interface DialogInterface { void dismiss();
interface OnDismissListener { void onDismiss(DialogInterface d); }
interface OnCancelListener { void onCancel(DialogInterface d); } }""",
    "android/app/Activity.java": """package android.app;
public class Activity extends android.content.Context { public boolean finishing, destroyed;
public boolean isFinishing(){return finishing;} public boolean isDestroyed(){return destroyed;} }""",
    "android/app/Dialog.java": """package android.app;
import android.content.DialogInterface;
public class Dialog implements DialogInterface {
public static Dialog lastShown; public static int shown; public boolean showing;
DialogInterface.OnDismissListener onDismiss;
public void setOnDismissListener(DialogInterface.OnDismissListener l){onDismiss=l;}
public void show(){showing=true;lastShown=this;shown++;}
public void dismiss(){boolean was=showing;showing=false;if(was && onDismiss!=null)onDismiss.onDismiss(this);}
public boolean isShowing(){return showing;}
} """,
    "android/os/Build.java": "package android.os; public class Build { public static class VERSION { public static int SDK_INT=35; } }",
    "android/os/Bundle.java": """package android.os; import java.util.*;
public class Bundle { final Map<String,Object> args=new HashMap<>();
public void putBoolean(String k,boolean v){args.put(k,v);} public void putInt(String k,int v){args.put(k,v);}
public boolean getBoolean(String k){return Boolean.TRUE.equals(args.get(k));}
public int getInt(String k){return (Integer)args.get(k);} }""",
    "android/text/SpannedString.java": """package android.text;
public final class SpannedString implements CharSequence { final String text;
public SpannedString(CharSequence s){text=s.toString();} public int length(){return text.length();}
public char charAt(int i){return text.charAt(i);} public CharSequence subSequence(int a,int b){return text.substring(a,b);}
public String toString(){return text;} }""",
    "android/net/Uri.java": "package android.net; public class Uri {}",
    "androidx/core/view/inputmethod/InputContentInfoCompat.java": "package androidx.core.view.inputmethod; public class InputContentInfoCompat {}",
    "org/telegram/tgnet/TLObject.java": "package org.telegram.tgnet; public class TLObject {}",
    "org/telegram/tgnet/TLRPC.java": """package org.telegram.tgnet;
public class TLRPC {
public static class Message extends TLObject { public int id; public Message(int id){this.id=id;} }
public static class MessageEntity extends TLObject {}
public static class User extends TLObject { public String name; public User(String n){name=n;} }
public static class Chat extends TLObject { public String title; public Chat(String n){title=n;} }
public static class TL_forumTopic extends TLObject { public String title; public Message topicStartMessage;
public TL_forumTopic(String title,int id){this.title=title;topicStartMessage=new Message(id);} }
} """,
    "org/telegram/tgnet/tl/TL_stories.java": "package org.telegram.tgnet.tl; public class TL_stories { public static class StoryItem {} }",
    "org/telegram/ui/ChatActivity.java": "package org.telegram.ui; public class ChatActivity { public static class ReplyQuote {} }",
    "org/telegram/messenger/MessageSuggestionParams.java": "package org.telegram.messenger; public class MessageSuggestionParams {}",
    "org/telegram/messenger/UserConfig.java": """package org.telegram.messenger;
public class UserConfig { public static final int MAX_ACCOUNT_COUNT=5; public static int selectedAccount;
static final UserConfig[] configs=new UserConfig[MAX_ACCOUNT_COUNT]; public boolean active;
public static UserConfig getInstance(int i){if(configs[i]==null)configs[i]=new UserConfig();return configs[i];}
public boolean isClientActivated(){return active;} }""",
    "org/telegram/messenger/AccountInstance.java": """package org.telegram.messenger;
public class AccountInstance { static final AccountInstance[] instances=new AccountInstance[5];
final int account; final MessagesController controller=new MessagesController();
AccountInstance(int a){account=a;} public int getCurrentAccount(){return account;}
public static AccountInstance getInstance(int a){if(instances[a]==null)instances[a]=new AccountInstance(a);return instances[a];}
public MessagesController getMessagesController(){return controller;} }""",
    "org/telegram/messenger/MessagesController.java": """package org.telegram.messenger;
import java.util.*; import org.telegram.tgnet.*;
public class MessagesController { public final Map<Long,TLObject> peers=new HashMap<>();
public final TopicsController topics=new TopicsController();
public TopicsController getTopicsController(){return topics;}
public TLObject getUserOrChat(long id){return peers.get(id);} }""",
    "org/telegram/messenger/TopicsController.java": """package org.telegram.messenger;
import java.util.*; import org.telegram.tgnet.TLRPC;
public class TopicsController { public final Map<String,TLRPC.TL_forumTopic> topics=new HashMap<>();
public TLRPC.TL_forumTopic findTopic(long chat,long topic){return topics.get(chat+":"+topic);} }""",
    "org/telegram/messenger/MessagesStorage.java": """package org.telegram.messenger; import java.util.*;
public class MessagesStorage { public static class TopicKey { public long dialogId,topicId;
public static TopicKey of(long d,long t){TopicKey k=new TopicKey();k.dialogId=d;k.topicId=t;return k;}
public boolean equals(Object o){return o instanceof TopicKey && ((TopicKey)o).dialogId==dialogId && ((TopicKey)o).topicId==topicId;}
public int hashCode(){return Objects.hash(dialogId,topicId);} } }""",
    "org/telegram/messenger/MessageObject.java": """package org.telegram.messenger;
import org.telegram.tgnet.TLRPC;
public class MessageObject { public boolean isTopicMainMessage; public final int account; public final TLRPC.Message messageOwner;
public MessageObject(int a,TLRPC.Message m,boolean x,boolean y){account=a;messageOwner=m;} }""",
    "org/telegram/messenger/MediaDataController.java": """package org.telegram.messenger;
import java.util.*; import org.telegram.tgnet.TLRPC;
public class MediaDataController { public static int account=-1;
public static MediaDataController getInstance(int a){account=a;return new MediaDataController();}
public ArrayList<TLRPC.MessageEntity> getEntities(CharSequence[] c,boolean x){
ArrayList<TLRPC.MessageEntity> e=new ArrayList<>();e.add(new TLRPC.MessageEntity());return e;} }""",
    "org/telegram/messenger/DialogObject.java": """package org.telegram.messenger; import org.telegram.tgnet.*;
public class DialogObject { public static String getDialogTitle(TLObject p){
if(p instanceof TLRPC.User)return ((TLRPC.User)p).name;
if(p instanceof TLRPC.Chat)return ((TLRPC.Chat)p).title;return "";} }""",
    "org/telegram/messenger/AndroidUtilities.java": """package org.telegram.messenger;
import android.app.Activity; import android.content.*;
public class AndroidUtilities { public static Activity findActivity(Context c){
if(c instanceof Activity)return (Activity)c;
if(c instanceof ContextWrapper)return findActivity(((ContextWrapper)c).getBaseContext());return null;} }""",
    "org/telegram/messenger/FileLog.java": """package org.telegram.messenger;
public class FileLog { public static Throwable last; public static void e(Throwable e){last=e;} }""",
    "org/telegram/messenger/Utilities.java": "package org.telegram.messenger; public class Utilities { public interface Callback<T> { void run(T t); } }",
    "org/telegram/ui/ActionBar/BaseFragment.java": """package org.telegram.ui.ActionBar;
import android.app.*; import org.telegram.ui.*; import org.telegram.messenger.UserConfig;
public class BaseFragment { public boolean isFinished,presentAllowed=true,showAllowed=true,presented;
public Activity activity; public Object layout=new Object(); int account=UserConfig.selectedAccount;
public BaseFragment(){} public BaseFragment(Activity a,int c){activity=a;account=c;}
public Activity getParentActivity(){return activity;} public Object getParentLayout(){return layout;}
public int getCurrentAccount(){return account;} public void setCurrentAccount(int a){if(presented)throw new IllegalStateException();account=a;}
public boolean presentFragment(BaseFragment f){if(!presentAllowed)return false;
f.activity=activity;f.layout=layout;f.presented=true;LaunchActivity.last=f;LaunchActivity.include=f;return true;}
public void finishFragment(){isFinished=true;}
public Dialog showDialog(Dialog d){return showDialog(d,null);}
public Dialog showDialog(Dialog d,Dialog.OnDismissListener l){if(!showAllowed)return null;d.setOnDismissListener(l);d.show();return d;}
} """,
    "org/telegram/ui/ActionBar/AlertDialog.java": """package org.telegram.ui.ActionBar;
import android.content.*;
public class AlertDialog extends android.app.Dialog { public CharSequence title,message;
public OnButtonClickListener positive,negative; public interface OnButtonClickListener { void onClick(AlertDialog d,int w); }
public void yes(){if(positive!=null)positive.onClick(this,1);dismiss();}
public void no(){if(negative!=null)negative.onClick(this,0);dismiss();}
public static class Builder { final AlertDialog d=new AlertDialog(); public Builder(Context c){}
public Builder setTitle(CharSequence s){d.title=s;return this;} public Builder setMessage(CharSequence s){d.message=s;return this;}
public Builder setPositiveButton(CharSequence s,OnButtonClickListener l){d.positive=l;return this;}
public Builder setNegativeButton(CharSequence s,OnButtonClickListener l){d.negative=l;return this;}
public AlertDialog create(){return d;} } }""",
    "org/telegram/ui/TopicsFragment.java": "package org.telegram.ui; public class TopicsFragment {}",
    "org/telegram/ui/DialogsActivity.java": """package org.telegram.ui;
import android.os.Bundle; import java.util.*; import org.telegram.messenger.*; import org.telegram.ui.ActionBar.*;
public class DialogsActivity extends BaseFragment { public static final int DIALOGS_TYPE_FORWARD=3;
public static DialogsActivity created; public final Bundle arguments; public DialogsActivityDelegate delegate; public boolean accountSet;
public DialogsActivity(Bundle b){arguments=b;created=this;} public void setCurrentAccount(int a){super.setCurrentAccount(a);accountSet=true;}
public void setDelegate(DialogsActivityDelegate d){delegate=d;}
public interface DialogsActivityDelegate { boolean didSelectDialogs(DialogsActivity f,ArrayList<MessagesStorage.TopicKey> peers,
CharSequence message,boolean param,boolean notify,int scheduleDate,int scheduleRepeatPeriod,TopicsFragment topicsFragment); }
public boolean pick(ArrayList<MessagesStorage.TopicKey> peers,CharSequence comment,boolean notify,int date,int repeat){
return delegate.didSelectDialogs(this,peers,comment,false,notify,date,repeat,null);}
} """,
    "org/telegram/ui/LaunchActivity.java": """package org.telegram.ui;
import org.telegram.ui.ActionBar.BaseFragment;
public class LaunchActivity extends android.app.Activity { public static BaseFragment include,last;
public static BaseFragment getLastFragmentIncludeMainTabs(){return include;}
public static BaseFragment getLastFragment(){return last;} }""",
    "org/telegram/ui/Components/AlertsCreator.java": """package org.telegram.ui.Components;
import java.util.*; import org.telegram.messenger.Utilities;
public class AlertsCreator { public static final Map<Long,Long> prices=new HashMap<>();
public static final Map<Long,Utilities.Callback<Long>> pending=new HashMap<>();
public static final List<String> calls=new ArrayList<>();
public static boolean ensurePaidMessageConfirmation(int currentAccount,long dialogId,int count,Utilities.Callback<Long> callback){
calls.add(currentAccount+":"+dialogId+":"+count);long price=prices.getOrDefault(dialogId,0L);
if(price<=0){callback.run(0L);return false;}pending.put(dialogId,callback);return true;}
public static void approve(long did){Utilities.Callback<Long> c=pending.remove(did);if(c==null)throw new AssertionError("no payment prompt");c.run(prices.get(did));}
} """,
}

SENDER_STUB = r"""package org.telegram.messenger;
import java.util.*; import android.net.Uri; import androidx.core.view.inputmethod.InputContentInfoCompat;
import org.telegram.tgnet.TLRPC; import org.telegram.tgnet.tl.TL_stories; import org.telegram.ui.ChatActivity;
public class SendMessagesHelper {
public static final List<Sent> sent=new ArrayList<>(); public static int failAfter=-1;
public static class Sent { public int account,date,repeat; public long did,stars; public boolean notify;
public ArrayList<String> paths,originals; public String caption,mime; public ArrayList<TLRPC.MessageEntity> entities;
public MessageObject reply,top; }
public static void prepareSendingDocuments(__SIGNATURE__) {
if(failAfter>=0 && sent.size()>=failAfter)throw new IllegalStateException("fake queue failure");
if(paths==originalPaths)throw new AssertionError("caller-owned lists aliased");
if(uris!=null || monoForumPeerId!=0 || suggestionParams!=null)throw new AssertionError("unexpected target or media");
Sent s=new Sent();s.account=accountInstance.getCurrentAccount();s.did=dialogId;
s.paths=new ArrayList<>(paths);s.originals=new ArrayList<>(originalPaths);s.caption=caption;s.mime=mime;
s.entities=captionEntities;s.reply=replyToMsg;s.top=replyToTopMsg;s.notify=notify;s.date=scheduleDate;
s.repeat=scheduleRepeatPeriod;s.stars=payStars;sent.add(s);
}
} """

HARNESS = r"""
import java.io.*; import java.nio.file.*; import java.util.*;
import android.app.*; import android.content.*; import android.os.Bundle;
import com.Huanghun.protocol.maker.ProtocolShareHelper;
import org.telegram.messenger.*; import org.telegram.messenger.MessagesStorage.TopicKey;
import org.telegram.tgnet.TLRPC; import org.telegram.ui.*; import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.AlertsCreator;
public class ProtocolShareRegression {
static Activity activity=new LaunchActivity(); static BaseFragment host=new BaseFragment(activity,1);
static File directory; static File a,b; static ArrayList<File> files;
static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
static void eq(Object a,Object b,String why){check(Objects.equals(a,b),why+": "+a+" != "+b);}
static void active(int a){UserConfig.getInstance(a).active=true;}
static File file(String name,byte[] bytes)throws Exception {File f=new File(directory,name);Files.write(f.toPath(),bytes);return f;}
static ArrayList<TopicKey> peers(TopicKey... p){return new ArrayList<>(Arrays.asList(p));}
static TopicKey peer(long did){return TopicKey.of(did,0);}
static AlertDialog alert(){check(Dialog.lastShown instanceof AlertDialog,"expected native alert");return (AlertDialog)Dialog.lastShown;}
static DialogsActivity open(){check(ProtocolShareHelper.share(host,files),"picker presented");return DialogsActivity.created;}
static void pick(DialogsActivity p,ArrayList<TopicKey> peers){check(!p.pick(peers,null,true,0,0),"delegate must remain installed");}
static void zero(){eq(SendMessagesHelper.sent.size(),0,"no documents queued");}
static void ready()throws Exception {
 directory=Files.createTempDirectory("protocol-generated-results-").toFile();
 a=file("account.zip",new byte[]{80,75,3,4,1});b=file("passkey.ZIP",new byte[]{80,75,3,4,2});
 files=new ArrayList<>(Arrays.asList(a,b));active(1);UserConfig.selectedAccount=0;
 LaunchActivity.include=host;LaunchActivity.last=host;
 AccountInstance.getInstance(1).getMessagesController().peers.put(7L,new TLRPC.User("Alice"));
 AccountInstance.getInstance(1).getMessagesController().peers.put(-9L,new TLRPC.Chat("Group"));
 AccountInstance.getInstance(1).getMessagesController().peers.put(-10L,new TLRPC.Chat("Channel"));
 AccountInstance.getInstance(1).getMessagesController().topics.topics.put("9:33",new TLRPC.TL_forumTopic("Private topic",333));
}
static void run(String test)throws Exception {
 switch(test){
 case "picker_flags": {DialogsActivity p=open();check(p.accountSet && p.presented,"set account BEFORE present");eq(p.getCurrentAccount(),1,"preferred account");
 check(p.arguments.getBoolean("onlySelect") && p.arguments.getBoolean("checkCanWrite") && p.arguments.getBoolean("allowGlobalSearch") && p.arguments.getBoolean("allowSwitchAccount"),"normal picker options");eq(p.arguments.getInt("dialogsType"),3,"forward multi picker");zero();break;}
 case "exact_files_and_recipients": {DialogsActivity p=open();files.clear();files.add(file("not-generated.zip",new byte[]{1}));
 TopicKey alice=peer(7);ArrayList<TopicKey> recipients=peers(alice,peer(-9),peer(-10));
 StringBuilder caption=new StringBuilder("my comment");check(!p.pick(recipients,caption,false,123,456),"false while confirming");
 AlertDialog d=alert();zero();check(d.message.toString().contains("Alice") && d.message.toString().contains("Group") && d.message.toString().contains("Channel") && d.message.toString().contains("（3）"),"chosen peer names/count");
 check(d.message.toString().contains("account.zip") && d.message.toString().contains("passkey.ZIP") && d.message.toString().contains("私钥") && d.message.toString().contains("登录"),"archive disclosure");
 recipients.clear();alice.dialogId=88;caption.append(" mutated");UserConfig.selectedAccount=4;d.yes();
 eq(SendMessagesHelper.sent.size(),3,"each exact selected dialog");long[] ids={7,-9,-10};
 for(int i=0;i<3;i++){SendMessagesHelper.Sent s=SendMessagesHelper.sent.get(i);eq(s.did,ids[i],"immutable peers");eq(s.account,1,"picker account not selectedAccount");
 eq(s.paths,Arrays.asList(a.getCanonicalPath(),b.getCanonicalPath()),"generated archives only");eq(s.originals,s.paths,"original names");
 eq(s.caption,"my comment","comment snapshot");eq(s.mime,"application/zip","ZIP MIME");check(!s.notify,"silent preserved");eq(s.date,123,"schedule date");eq(s.repeat,456,"repeat");check(s.entities!=null,"caption entities");}
 check(p.isFinished,"close only after queued");break;}
 case "cancel_then_retry": {DialogsActivity p=open();pick(p,peers(peer(7)));alert().no();zero();check(!p.isFinished && p.delegate!=null,"cancel retains picker");pick(p,peers(peer(-9)));alert().yes();eq(SendMessagesHelper.sent.size(),1,"only later recipient");eq(SendMessagesHelper.sent.get(0).did,-9L,"no cancelled recipient");break;}
 case "dismiss_then_retry": {DialogsActivity p=open();pick(p,peers(peer(7)));alert().dismiss();zero();pick(p,peers(peer(-10)));alert().yes();eq(SendMessagesHelper.sent.size(),1,"dismiss resets busy");break;}
 case "no_recipients": {DialogsActivity p=open();int count=Dialog.shown;pick(p,null);pick(p,new ArrayList<>());zero();eq(Dialog.shown,count,"no confirmation for empty peers");check(!p.isFinished,"no closure");break;}
 case "double_taps": {DialogsActivity p=open();pick(p,peers(peer(7)));AlertDialog d=alert();int n=Dialog.shown;
 pick(p,peers(peer(-9)));eq(Dialog.shown,n,"one confirmation while busy");zero();d.yes();d.positive.onClick(d,1);pick(p,peers(peer(-9)));
 eq(SendMessagesHelper.sent.size(),1,"exactly one queue after positive double tap");eq(SendMessagesHelper.sent.get(0).did,7L,"initial selection only");break;}
 case "topic": {DialogsActivity p=open();pick(p,peers(TopicKey.of(-9,33)));check(alert().message.toString().contains("Private topic"),"topic in warning");alert().yes();
 SendMessagesHelper.Sent s=SendMessagesHelper.sent.get(0);eq(s.did,-9L,"exact forum dialog");check(s.reply!=null && s.top==s.reply && s.reply.isTopicMainMessage,"native topic reply and top");eq(s.reply.messageOwner.id,333,"topic start message");eq(s.reply.account,1,"topic account");break;}
 case "missing_topic": {DialogsActivity p=open();pick(p,peers(peer(7),TopicKey.of(-9,99)));zero();check(alert().message.toString().contains("话题"),"clear unavailable topic error");check(alert().positive==null,"not a send confirmation");check(!p.isFinished,"picker retained");break;}
 case "topic_lost_after_confirm": {DialogsActivity p=open();pick(p,peers(peer(7),TopicKey.of(-9,33)));AlertDialog d=alert();AccountInstance.getInstance(1).getMessagesController().topics.topics.clear();d.yes();zero();check(!p.isFinished,"no silent fallback");break;}
 case "missing_file_after_confirm": {DialogsActivity p=open();pick(p,peers(peer(7),peer(-9)));AlertDialog d=alert();check(b.delete(),"delete generated archive");d.yes();zero();check(!p.isFinished,"no partial mismatch");break;}
 case "changed_file_after_confirm": {DialogsActivity p=open();pick(p,peers(peer(7)));AlertDialog d=alert();Files.write(a.toPath(),new byte[]{1,2});d.yes();zero();break;}
 case "invalid_files": {List<List<File>> bad=new ArrayList<>();bad.add(null);bad.add(Collections.emptyList());bad.add(Arrays.asList((File)null));bad.add(Arrays.asList(new File(directory,"missing.zip")));
 bad.add(Arrays.asList(file("empty.zip",new byte[0])));bad.add(Arrays.asList(file("secret.session",new byte[]{1})));bad.add(Arrays.asList(directory));bad.add(Arrays.asList(a,a));bad.add(Arrays.asList(a,new File(directory,"missing2.zip")));
 for(List<File> f:bad){check(!ProtocolShareHelper.share(host,f),"reject entire invalid list");check(DialogsActivity.created==null,"no picker for invalid result");zero();}break;}
 case "unreadable_file": {File unreadable=file("locked.zip",new byte[]{1});check(unreadable.setReadable(false,false),"remove read permission");
 if(!unreadable.canRead()){check(!ProtocolShareHelper.share(host,Arrays.asList(unreadable)),"unreadable archive rejected");zero();}unreadable.setReadable(true,false);break;}
 case "no_account": {UserConfig.getInstance(1).active=false;check(!ProtocolShareHelper.share(host,files),"login required not picker success");
 check(alert().title.toString().contains("登录") && alert().message.toString().contains("登录"),"explicit login alert");check(DialogsActivity.created==null,"no account no picker");zero();break;}
 case "selected_account_fallback": {UserConfig.getInstance(1).active=false;active(3);UserConfig.selectedAccount=3;eq(open().getCurrentAccount(),3,"selected activated account");break;}
 case "first_account_fallback": {UserConfig.getInstance(1).active=false;active(2);active(4);UserConfig.selectedAccount=0;eq(open().getCurrentAccount(),2,"first activated account");break;}
 case "context_same_activity": {check(ProtocolShareHelper.share(new ContextWrapper(new ContextWrapper(activity)),files),"unwrap same Activity");eq(DialogsActivity.created.getCurrentAccount(),1,"host account");break;}
 case "context_other_activity": {check(!ProtocolShareHelper.share(new Activity(),files),"cannot use other Activity navigation host");check(!ProtocolShareHelper.share(new Context(),files),"no global fallback for application context");check(!ProtocolShareHelper.share((Context)null,files),"null context");zero();break;}
 case "host_fallback": {BaseFragment detached=new BaseFragment(activity,1);detached.layout=null;LaunchActivity.include=new BaseFragment(new Activity(),0);LaunchActivity.last=host;
 check(ProtocolShareHelper.share(detached,files),"fallback to last same Activity");eq(DialogsActivity.created.getCurrentAccount(),1,"preferred fragment account retained");break;}
 case "null_fragment_fallback": {check(ProtocolShareHelper.share((BaseFragment)null,files),"fallback include main tabs");eq(DialogsActivity.created.getCurrentAccount(),1,"fallback host account");break;}
 case "dead_hosts": {host.isFinished=true;check(!ProtocolShareHelper.share(host,files),"finished fragment");host.isFinished=false;activity.finishing=true;
 check(!ProtocolShareHelper.share(host,files),"finishing Activity");activity.finishing=false;activity.destroyed=true;check(!ProtocolShareHelper.share(activity,files),"destroyed Activity");zero();break;}
 case "present_failure": {host.presentAllowed=false;check(!ProtocolShareHelper.share(host,files),"true only on presentation");zero();break;}
 case "confirmation_show_failure": {DialogsActivity p=open();p.showAllowed=false;pick(p,peers(peer(7)));zero();p.showAllowed=true;pick(p,peers(peer(-9)));alert().yes();eq(SendMessagesHelper.sent.size(),1,"failed dialog releases busy");break;}
 case "account_logout_before_send": {DialogsActivity p=open();pick(p,peers(peer(7)));AlertDialog d=alert();UserConfig.getInstance(1).active=false;d.yes();zero();check(!p.isFinished,"logout no queue");break;}
 case "picker_destroyed_before_send": {DialogsActivity p=open();pick(p,peers(peer(7)));AlertDialog d=alert();p.isFinished=true;d.yes();zero();break;}
 case "native_account_switch_replacement": {DialogsActivity old=open();active(2);UserConfig.selectedAccount=2;
 DialogsActivity replacement=new DialogsActivity(old.arguments);replacement.setCurrentAccount(2);replacement.setDelegate(old.delegate);old.isFinished=true;
 host.presentFragment(replacement);pick(replacement,peers(peer(22)));zero();alert().yes();eq(SendMessagesHelper.sent.size(),1,"replacement picker sends");
 eq(SendMessagesHelper.sent.get(0).account,2,"new native picker account");eq(SendMessagesHelper.sent.get(0).did,22L,"new selected recipient");check(replacement.isFinished,"close new picker only");break;}
 case "picker_other_activity": {DialogsActivity p=open();DialogsActivity foreign=new DialogsActivity(p.arguments);foreign.activity=new Activity();foreign.setDelegate(p.delegate);
 int shown=Dialog.shown;pick(foreign,peers(peer(7)));zero();eq(Dialog.shown,shown,"reject foreign Activity replacement");break;}
 case "paid_all_approvals_before_queue": {AlertsCreator.prices.put(7L,5L);AlertsCreator.prices.put(-9L,8L);DialogsActivity p=open();pick(p,peers(peer(7),peer(-9),peer(-10)));
 zero();eq(AlertsCreator.calls.size(),0,"no fee prompt before security positive");alert().yes();zero();eq(AlertsCreator.calls.size(),1,"one native prompt at a time");
 AlertsCreator.approve(7);zero();AlertsCreator.approve(-9);eq(SendMessagesHelper.sent.size(),3,"queue after all approvals");eq(SendMessagesHelper.sent.get(0).stars,5L,"confirmed Stars price");eq(SendMessagesHelper.sent.get(1).stars,8L,"second Stars price");
 check(AlertsCreator.calls.contains("1:7:2") && AlertsCreator.calls.contains("1:-9:2"),"archive payment counts");break;}
 case "paid_cancel_retry_invalidates_old_callback": {AlertsCreator.prices.put(7L,5L);DialogsActivity p=open();pick(p,peers(peer(7)));alert().yes();zero();
 Utilities.Callback<Long> old=AlertsCreator.pending.get(7L);pick(p,peers(peer(-9)));zero();old.run(5L);zero();alert().yes();eq(SendMessagesHelper.sent.size(),1,"only fresh selection sends");eq(SendMessagesHelper.sent.get(0).did,-9L,"not cancelled paid recipient");old.run(5L);eq(SendMessagesHelper.sent.size(),1,"late callback no duplicate");break;}
 case "paid_files_revalidated": {AlertsCreator.prices.put(7L,5L);DialogsActivity p=open();pick(p,peers(peer(7),peer(-9)));alert().yes();check(b.delete(),"delete during payment wait");AlertsCreator.approve(7);zero();check(!p.isFinished,"paid wait no partial files");break;}
 case "paid_same_dialog_topics_count": {AlertsCreator.prices.put(-9L,4L);DialogsActivity p=open();pick(p,peers(peer(-9),TopicKey.of(-9,33)));alert().yes();zero();
 eq(AlertsCreator.calls,Arrays.asList("1:-9:4"),"one fee confirmation for both exact topic targets");AlertsCreator.approve(-9);eq(SendMessagesHelper.sent.size(),2,"preserve both TopicKeys");eq(SendMessagesHelper.sent.get(1).stars,4L,"topic fee");check(SendMessagesHelper.sent.get(1).reply!=null,"topic target preserved");break;}
 case "duplicate_peer_deduplicated": {DialogsActivity p=open();pick(p,peers(peer(7),peer(7)));alert().yes();eq(SendMessagesHelper.sent.size(),1,"no duplicate selected key");break;}
 case "invalid_recipient": {DialogsActivity p=open();pick(p,peers(peer(7),peer(0)));zero();check(alert().positive==null,"invalid peer not send dialog");pick(p,peers((TopicKey)null));zero();break;}
 case "queue_failure_no_duplicate_retry": {DialogsActivity p=open();SendMessagesHelper.failAfter=1;pick(p,peers(peer(7),peer(-9)));alert().yes();eq(SendMessagesHelper.sent.size(),1,"first accepted by fake queue");
 check(!p.isFinished,"not close when later queue fails");check(alert().message.toString().contains("部分"),"honest partial queue status");pick(p,peers(peer(7)));eq(SendMessagesHelper.sent.size(),1,"no duplicates after partial queue");break;}
 default:throw new AssertionError("Unknown test: "+test);
 }
}
public static void main(String[] args)throws Exception {ready();try{run(args[0]);System.out.println("OK "+args[0]);}
 finally{Files.walk(directory.toPath()).sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException e){throw new RuntimeException(e);}});} }
}
"""

CASES = (
    "picker_flags", "exact_files_and_recipients", "cancel_then_retry", "dismiss_then_retry",
    "no_recipients", "double_taps", "topic", "missing_topic", "topic_lost_after_confirm",
    "missing_file_after_confirm", "changed_file_after_confirm", "invalid_files", "unreadable_file",
    "no_account", "selected_account_fallback", "first_account_fallback", "context_same_activity",
    "context_other_activity", "host_fallback", "null_fragment_fallback", "dead_hosts", "present_failure",
    "confirmation_show_failure", "account_logout_before_send", "picker_destroyed_before_send",
    "native_account_switch_replacement", "picker_other_activity", "paid_all_approvals_before_queue",
    "paid_cancel_retry_invalidates_old_callback", "paid_files_revalidated", "paid_same_dialog_topics_count",
    "duplicate_peer_deduplicated", "invalid_recipient", "queue_failure_no_duplicate_retry",
)


class ProtocolShareTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not shutil.which("javac") or not shutil.which("java"):
            raise unittest.SkipTest("JDK javac/java is required")
        cls.temp = tempfile.TemporaryDirectory(prefix="protocol-share-java-")
        cls.addClassCleanup(cls.temp.cleanup)
        work = Path(cls.temp.name)
        sender = SENDER.read_text()
        signatures = re.findall(r"public static void prepareSendingDocuments\(([^\n]+)\)", sender)
        signatures = [s for s in signatures if "scheduleRepeatPeriod" in s and "pollSendParams" not in s]
        if len(signatures) != 1:
            raise AssertionError("Expected one exact full schedule-repeat document overload")
        stubs = dict(STUBS)
        stubs["org/telegram/messenger/SendMessagesHelper.java"] = SENDER_STUB.replace("__SIGNATURE__", signatures[0])
        stubs["com/Huanghun/protocol/maker/ProtocolShareHelper.java"] = HELPER.read_text()
        stubs["ProtocolShareRegression.java"] = HARNESS
        for path, content in stubs.items():
            target = work / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(content + "\n")
        cls.classes = work / "classes"
        subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(cls.classes),
                        *map(str, work.rglob("*.java"))], check=True, capture_output=True, text=True)

    def test_native_contracts_no_external_share_or_path_bridge(self):
        source = HELPER.read_text()
        for forbidden in ("ACTION_SEND", "createChooser", "FileProvider", "@JavascriptInterface", "startActivity", "UploadFile"):
            self.assertNotIn(forbidden, source)
        self.assertIn("public static boolean share(BaseFragment fragment, List<File> files)", source)
        self.assertIn("public static boolean share(Context context, List<File> files)", source)
        self.assertIn("AlertsCreator.ensurePaidMessageConfirmation(request.account", source)
        self.assertIn("AccountInstance.getInstance(picker.getCurrentAccount())", source)
        self.assertNotIn("AccountInstance.getInstance(UserConfig.selectedAccount)", source)
        dialogs = (JAVA / "org/telegram/ui/DialogsActivity.java").read_text()
        self.assertIn("if (res && resetDelegate)", dialogs)
        self.assertIn("dialogsActivity.setDelegate(oldDelegate)", dialogs)
        self.assertIn("allowGlobalSearch = arguments.getBoolean", dialogs)
        self.assertRegex(dialogs, r"boolean didSelectDialogs\(DialogsActivity fragment, ArrayList<MessagesStorage.TopicKey> dids, CharSequence message, boolean param, boolean notify, int scheduleDate, int scheduleRepeatPeriod, TopicsFragment topicsFragment\)")


def make_test(case):
    def test(self):
        result = subprocess.run(["java", "-cp", str(self.classes), "ProtocolShareRegression", case],
                                capture_output=True, text=True, timeout=20)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("OK " + case, result.stdout)
    return test


for name in CASES:
    setattr(ProtocolShareTest, "test_" + name, make_test(name))

if __name__ == "__main__":
    unittest.main(verbosity=2)
