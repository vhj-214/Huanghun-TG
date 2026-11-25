#!/usr/bin/env python3
"""Compile extracted production paging/filter methods with Java source-launcher stubs."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
src = (root / 'TMessagesProj/src/main/java/org/telegram/ui/FilteredSearchView.java').read_text()

def block(marker):
    start = src.index(marker)
    opening = src.index('{', start)
    depth = 0
    for i in range(opening, len(src)):
        depth += (src[i] == '{') - (src[i] == '}')
        if depth == 0:
            return src[start:i + 1]
    raise AssertionError(marker)

methods = '\n'.join(block(m) for m in [
    'public void setMessageSearchOptions(',
    'private boolean isMessageSearchFilteringEnabled()',
    'private void clearSearchResults()',
    'private void rebuildMessageSearchResults()',
    'private void loadMoreFilteredMessages()',
    'public void messagesDeleted(',
])
search = block('public void search(')
# Remove presentation-only UI, preserving real response bookkeeping and auto-pagination.
start = search.index('                    if (messages.isEmpty()) {')
end = search.rindex('                    adapter.notifyDataSetChanged();')
search = search[:start] + search[end:]
helper = (root / 'TMessagesProj/src/main/java/org/telegram/messenger/MessageSearchFilter.java').read_text()
helper = helper[helper.index('/**'):].replace('public final class MessageSearchFilter', 'final class MessageSearchFilter')

fields = '''
ArrayList<MessageObject> messages = new ArrayList<>();
SparseArray<MessageObject> messagesById = new SparseArray<>();
ArrayList<String> sections = new ArrayList<>();
HashMap<String, ArrayList<MessageObject>> sectionArrays = new HashMap<>();
ArrayList<MessageObject> rawMessages = new ArrayList<>();
HashSet<MessageHashId> rawMessageIds = new HashSet<>();
MessageObject lastRawMessage;
int rawMessagesLoaded, nextSearchRate, requestIndex, searchRequestToken, searchRequestAccount, totalCount, photoViewerClassGuid;
boolean messageSearchOptionsEnabled, exactMessageSearch, messageSearchAutoLoadBlocked, isLoading, endReached, firstLoading = true;
boolean deduplicateMessageSearch = true, loadingMoreFilteredMessages;
int messageSearchPageTarget = 20;
String lastMessagesSearchString, lastSearchFilterQueryString, currentSearchString, currentDataQuery;
FiltersView.MediaFilterData currentSearchFilter;
long currentSearchDialogId, currentSearchCommunityId, currentSearchMaxDate, currentSearchMinDate;
boolean currentIncludeFolder;
Runnable searchRunnable;
Adapter adapter = new Adapter();
EmptyView emptyView = new EmptyView();
RecyclerListView recyclerListView = new RecyclerListView();
LoadingView loadingView = new LoadingView();
ArrayList<Object> localTipChats = new ArrayList<>();
ArrayList<FiltersView.DateData> localTipDates = new ArrayList<>();
Delegate delegate;
'''
classes = r'''
class SparseArray<T> extends HashMap<Integer,T> {}
class View { static final int VISIBLE=0, GONE=8; void setAlpha(float a){} void setVisibility(int v){} }
class Text extends View { void setText(String s){} }
class EmptyView extends View { Text title=new Text(), subtitle=new Text(); void showProgress(boolean... b){} }
class LoadingView { void setViewType(int t){} }
class Adapter { int notifications; void notifyDataSetChanged(){notifications++;} }
class RecyclerListView { View getPinnedHeader(){return null;} }
class Delegate { void updateFiltersView(boolean a, Object b, Object c, boolean d){} }
class FlickerLoadingView { static final int DIALOG_TYPE=0, PHOTOS_TYPE=1, FILES_TYPE=2, AUDIO_TYPE=3, LINKS_TYPE=4; }
class R { static class string { static int SearchEmptyViewTitle2=0, SearchEmptyViewFilteredSubtitle2=1; } }
class LocaleController { static String getString(int r){return "";} }
class TextUtils { static boolean isEmpty(String s){return s==null || s.isEmpty();} }
class AndroidUtilities {
    static ArrayDeque<Runnable> queue=new ArrayDeque<>();
    static void runOnUIThread(Runnable r){queue.add(r);}
    static void runOnUIThread(Runnable r,int delay){queue.add(r);}
    static void cancelRunOnUIThread(Runnable r){queue.remove(r);}
    static void drain(){int n=0; while(!queue.isEmpty()){if(++n>100)throw new AssertionError("UI loop");queue.remove().run();}}
}
class UserConfig { static int selectedAccount=0; }
class ChatObject { static boolean isChannel(long id,int a){return id>=100;} }
class MessageObject {
    TLRPC.Message messageOwner;
    String monthKey;
    MessageObject(int a,TLRPC.Message m,boolean b,boolean c){messageOwner=m;monthKey=m.month;}
    int getId(){return messageOwner.id;}
    long getDialogId(){return getPeerId(messageOwner.peer_id);}
    void setQuery(String s){}
    static long getPeerId(TLRPC.Peer p){return p==null?0:p.id;}
}
class FiltersView {
    static final int FILTER_TYPE_FILES=0,FILTER_TYPE_MEDIA=1,FILTER_TYPE_LINKS=2,FILTER_TYPE_MUSIC=3,FILTER_TYPE_VOICE=4;
    static class MediaFilterData { int filterType; Object filter=new Object(); MediaFilterData(int t){filterType=t;} }
    static class DateData {}
    static void fillTipDates(String s,ArrayList<DateData> d){}
}
class TLMethod<T> {}
class TLRPC {
    static class Peer { long id; Peer(long i){id=i;} }
    static class TL_inputPeerEmpty extends Peer { TL_inputPeerEmpty(){super(0);} }
    static class TL_inputMessagesFilterEmpty {}
    static class TL_error {}
    static class Message { int id; Peer peer_id,from_id; String message,month="a"; }
    static class messages_Messages {
        int count,next_rate;
        ArrayList<Message> messages=new ArrayList<>();
        ArrayList<Object> users=new ArrayList<>(),chats=new ArrayList<>();
    }
    static class TL_messages_search extends TLMethod<messages_Messages> {
        String q; int limit,min_date,max_date,offset_id; Object filter; Peer peer;
    }
    static class TL_messages_searchGlobal extends TLMethod<messages_Messages> {
        String q; int limit,min_date,max_date,offset_id,offset_rate,flags,folder_id; Object filter,community; Peer offset_peer;
    }
}
class MessagesController {
    static MessagesController instance=new MessagesController();
    static MessagesController getInstance(int a){return instance;}
    PeerPlaceholder placeholder;
    TLRPC.Peer getInputPeer(long id){return new TLRPC.Peer(id);}
    Object getInputChannel(long id){return new Object();}
    void putUsers(Object a,boolean b){} void putChats(Object a,boolean b){}
    static class PeerPlaceholder {}
}
class AccountInstance {
    static AccountInstance getInstance(int a){return new AccountInstance();}
    MessagesController getMessagesController(){return MessagesController.instance;}
}
class MessagesStorage {
    static MessagesStorage getInstance(int a){return new MessagesStorage();}
    void localSearch(int a,String b,Object c,Object d,Object e,Object f,int g){}
    void putUsersAndChats(Object a,Object b,boolean c,boolean d){}
}
class PhotoViewer {
    static PhotoViewer getInstance(){return new PhotoViewer();}
    boolean isVisible(){return false;}
    void addPhoto(MessageObject m,int guid){}
}
class ConnectionsManager {
    interface Callback { void run(TLRPC.messages_Messages r,TLRPC.TL_error e); }
    static class Pending { int token; TLMethod<TLRPC.messages_Messages> request; Callback callback; boolean cancelled; }
    static ConnectionsManager instance=new ConnectionsManager();
    static ConnectionsManager getInstance(int a){return instance;}
    ArrayList<Pending> pending=new ArrayList<>(); int nextToken=1;
    int sendRequestTyped(TLMethod<TLRPC.messages_Messages> req,Callback cb){
        if(pending.stream().anyMatch(p->!p.cancelled))throw new AssertionError("Concurrent requests");
        Pending p=new Pending(); p.token=nextToken++; p.request=req; p.callback=cb; pending.add(p); return p.token;
    }
    void cancelRequest(int token,boolean notify){for(Pending p:pending)if(p.token==token)p.cancelled=true;}
    Pending active(){return pending.stream().filter(p->!p.cancelled).findFirst().orElseThrow();}
    void complete(Pending p,TLRPC.messages_Messages result,boolean error){pending.remove(p);p.callback.run(result,error?new TLRPC.TL_error():null);AndroidUtilities.drain();}
    void reset(){pending.clear();AndroidUtilities.queue.clear();}
}
'''
# A generic list only needed by the local-search branch.
classes = classes.replace('static class TL_error {}', 'static class User {}\n    static class TL_error {}')
tests = r'''
static int checks;
static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
static FilteredSearchHarness fresh(){ConnectionsManager.instance.reset();return new FilteredSearchHarness();}
static TLRPC.Message msg(int id,long dialog,long from,String text){TLRPC.Message m=new TLRPC.Message();m.id=id;m.peer_id=new TLRPC.Peer(dialog);m.from_id=from==0?null:new TLRPC.Peer(from);m.message=text;return m;}
static TLRPC.messages_Messages page(int total,int rate,TLRPC.Message... messages){TLRPC.messages_Messages p=new TLRPC.messages_Messages();p.count=total;p.next_rate=rate;p.messages.addAll(Arrays.asList(messages));return p;}
static TLRPC.Message[] rows(int first,int last,long dialog,String text){ArrayList<TLRPC.Message> rows=new ArrayList<>();for(int i=first;i<=last;i++)rows.add(msg(i,dialog,i,text));return rows.toArray(new TLRPC.Message[0]);}
void start(long dialog,String query){search(dialog,0,0,0,null,false,query,true);AndroidUtilities.drain();}
static void respond(TLRPC.messages_Messages page){ConnectionsManager c=ConnectionsManager.instance;c.complete(c.active(),page,false);}
public static void main(String[] args){
    FilteredSearchHarness v=fresh();
    v.start(-10,"你好");
    respond(page(2,0,msg(1,-10,7,"你好啊"),msg(2,-10,7,"你好")));
    check(v.messages.size()==2,"default view has no client filters");
    v.setMessageSearchOptions(true,false);
    check(v.messages.size()==1 && v.messages.get(0).getId()==1,"late API dedup keeps first raw hit");
    v.setMessageSearchOptions(true,true);
    check(v.messages.size()==1 && v.messages.get(0).getId()==2,"exact runs before sender dedup");
    v.setMessageSearchOptions(false,false);
    check(v.messages.size()==2 && v.rawMessages.size()==2,"toggle restores retained raw items");
    check(v.rawMessagesLoaded==2 && v.lastRawMessage.getId()==2,"toggle preserves raw count and cursor");
    check(v.sections.size()==1 && v.sectionArrays.get("a").size()==2 && v.messagesById.size()==2,"indices rebuilt");

    v=fresh();v.setMessageSearchOptions(true,true);v.start(-10,"你好");
    respond(page(8,0,msg(1,-10,7,"你好啊"),msg(2,-10,7,"你好"),msg(3,-10,7,"你好"),msg(4,-11,7,"你好"),msg(5,9,9,"你好"),msg(6,9,9,"你好"),msg(7,-100,0,"你好"),msg(8,-100,0,"你好")));
    check(v.messages.size()==5,"same dialog+sender collapses only groups/channels, private results survive");
    v.setMessageSearchOptions(false,true);check(v.messages.size()==7,"turn off dedup only");
    v.setMessageSearchOptions(false,false);check(v.messages.size()==8,"turn off exact restores suffix message");

    v=fresh();v.setMessageSearchOptions(false,true);v.start(-10,"你好");
    respond(page(60,0,rows(1,20,-10,"你好啊")));
    check(v.messages.isEmpty() && v.rawMessagesLoaded==20 && !v.endReached,"hidden page does not exhaust raw total");
    check(((TLRPC.TL_messages_search)ConnectionsManager.instance.active().request).offset_id==20,"empty visible page advances using raw cursor");
    respond(page(60,0,rows(21,40,-10,"你好")));
    check(v.messages.size()==20 && ConnectionsManager.instance.pending.isEmpty() && !v.endReached,"auto fill stops at 20 visible");
    v.setMessageSearchOptions(true,true);
    check(v.messages.size()==20,"toggle full list requires no extra page");
    v.search(-10,0,0,0,null,false,"你好",false);AndroidUtilities.drain();
    check(((TLRPC.TL_messages_search)ConnectionsManager.instance.active().request).offset_id==40,"next cursor is last raw, not filter survivor");
    v.setMessageSearchOptions(false,false);
    respond(page(60,0,rows(41,60,-10,"你好啊")));
    check(v.messages.size()==60 && v.endReached,"in-flight response uses latest options");

    v=fresh();v.setMessageSearchOptions(false,true);v.start(0,"你好");
    respond(page(100,25,rows(1,20,-100,"suffix")));
    TLRPC.TL_messages_searchGlobal global=(TLRPC.TL_messages_searchGlobal)ConnectionsManager.instance.active().request;
    check(global.offset_id==20 && global.offset_peer.id==-100 && global.offset_rate==25,"global raw offset includes peer and rate");
    respond(page(100,26,rows(20,39,-100,"suffix")));
    check(v.rawMessagesLoaded==39,"overlap IDs counted once");
    respond(page(100,26,rows(20,39,-100,"suffix")));
    check(v.endReached && ConnectionsManager.instance.pending.isEmpty(),"repeated page stops without loop");
    v.setMessageSearchOptions(false,false);check(v.messages.size()==39,"raw page overlap never duplicates visible hits");

    v=fresh();v.setMessageSearchOptions(false,true);v.start(-10,"你好");
    respond(page(100,0,rows(1,20,-10,"suffix")));
    respond(page(100,0));check(v.endReached && ConnectionsManager.instance.pending.isEmpty(),"empty page terminates despite stale server count");

    v=fresh();v.setMessageSearchOptions(false,true);v.start(-10,"你好");
    respond(page(100,0,rows(1,20,-10,"suffix")));
    ConnectionsManager c=ConnectionsManager.instance;c.complete(c.active(),null,true);
    check(v.messageSearchAutoLoadBlocked && !v.isLoading && c.pending.isEmpty(),"error blocks automated retries");
    v.setMessageSearchOptions(true,true);check(c.pending.isEmpty(),"toggle does not restart failed auto loop");
    v.search(-10,0,0,0,null,false,"你好",false);AndroidUtilities.drain();
    check(c.pending.size()==1,"explicit same query can retry error");
    c.complete(c.active(),null,false);check(!v.isLoading && v.messageSearchAutoLoadBlocked,"null response treated as error");

    v=fresh();v.setMessageSearchOptions(true,false);v.start(-10,"old");
    ConnectionsManager.Pending stale=ConnectionsManager.instance.active();
    v.search(-10,0,0,0,null,false,"old",false);AndroidUtilities.drain();
    check(ConnectionsManager.instance.pending.size()==1,"same query during flight is guarded");
    v.search(-10,0,0,0,null,false,"new",true);AndroidUtilities.drain();
    check(stale.cancelled && ConnectionsManager.instance.pending.size()==2,"new query cancels previous request");
    ConnectionsManager.instance.complete(stale,page(1,0,msg(1,-10,1,"old")),false);
    check(v.messages.isEmpty() && v.isLoading,"stale response cannot clear new loading state or raw data");
    respond(page(1,0,msg(2,-10,1,"new")));
    check(v.messages.size()==1 && v.messages.get(0).getId()==2,"new query response retained");

    v=fresh();v.setMessageSearchOptions(true,false);v.start(-100,"你好");
    respond(page(3,0,msg(1,-100,7,"你好"),msg(2,-100,7,"你好"),msg(3,-100,8,"你好")));
    v.messagesDeleted(100,new ArrayList<>(Arrays.asList(1,3)));
    check(v.messages.size()==1 && v.messages.get(0).getId()==2,"deleting winner promotes retained hidden hit");
    check(v.lastRawMessage.getId()==3 && v.rawMessageIds.contains(new MessageHashId(3,-100)),"delete preserves cursor and tombstone IDs");
    v.setMessageSearchOptions(false,false);check(v.messages.size()==1,"deleted hidden messages never reappear on toggle");
    check(v.sectionArrays.get("a").size()==1 && v.messagesById.size()==1,"delete rebuilds all indices");

    v=fresh();v.setMessageSearchOptions(true,true);
    FiltersView.MediaFilterData media=new FiltersView.MediaFilterData(FiltersView.FILTER_TYPE_MEDIA);
    v.search(-10,0,0,0,media,false,"你好",true);AndroidUtilities.drain();
    respond(page(2,0,msg(1,-10,7,"suffix"),msg(2,-10,7,"suffix")));
    check(v.messages.size()==2,"API never filters MEDIA view");

    v=fresh();v.setMessageSearchOptions(false,true);v.start(-10,"你好");
    respond(page(80,0,rows(1,20,-10,"你好")));
    v.search(-10,0,0,0,null,false,"你好",false);AndroidUtilities.drain();
    check(v.messageSearchPageTarget==40,"manual continuation requests next 20 visible hits");
    respond(page(80,0,rows(21,40,-10,"suffix")));
    check(ConnectionsManager.instance.pending.size()==1 && ((TLRPC.TL_messages_search)ConnectionsManager.instance.active().request).offset_id==40,"filtered-away continuation page keeps scanning");
    respond(page(80,0,rows(41,60,-10,"你好")));
    check(v.messages.size()==40 && ConnectionsManager.instance.pending.isEmpty(),"continuation stops when new visible target filled");

    v=fresh();v.setMessageSearchOptions(true,false);v.start(-10,"A");
    stale=ConnectionsManager.instance.active();
    v.search(-10,0,0,0,null,false,"B",true);
    v.search(-10,0,0,0,null,false,"A",true);
    AndroidUtilities.drain();
    check(((TLRPC.TL_messages_search)ConnectionsManager.instance.active().request).q.equals("A"),"rapid A-B-A debounce retains final query");
    ConnectionsManager.instance.complete(stale,page(1,0,msg(1,-10,7,"stale")),false);
    respond(page(1,0,msg(2,-10,7,"A")));
    check(v.messages.get(0).getId()==2,"rapid-query stale callback remains ignored");
    System.out.println("PASS: "+checks+" extracted Java integration checks");
}
'''

java = 'import java.util.*;\nclass FilteredSearchHarness {\n' + fields
java += block('Runnable clearCurrentResultsRunnable =') + ';\n'
java += block('public static class MessageHashId') + '\n' + methods + '\n' + search + '\n' + tests + '\n}\n' + helper + '\n' + classes
with tempfile.TemporaryDirectory(prefix='huanghun-filtered-search-') as temp:
    path = Path(temp) / 'FilteredSearchHarness.java'
    path.write_text(java)
    result = subprocess.run(['java', str(path)], capture_output=True, text=True)
print(result.stdout, end='')
print(result.stderr, end='')
raise SystemExit(result.returncode)
