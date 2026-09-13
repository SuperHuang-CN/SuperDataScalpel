package cn.superhuang.data.scalpel.business.dsh.service;

import cn.superhuang.data.scalpel.business.dsh.config.DshProperties;
import cn.superhuang.data.scalpel.business.dsh.security.DshLoginIdentity;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;
import java.net.http.WebSocket;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import jakarta.annotation.PreDestroy;
@Service
public class DshEventService {
    private final DshBridgeClient bridge;
    private final DshService dsh;
    private final DshIdentityService identity;
    private final DshBindingService bindings;
    private final DshProperties properties;
    private final ObjectMapper mapper;
    private final Set<Connection> connections=ConcurrentHashMap.newKeySet();
    private final Semaphore permits=new Semaphore(32);
    private final ExecutorService senders=Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("dsh-events-",0).factory());
    public DshEventService(DshBridgeClient b,DshService s,DshIdentityService i,DshProperties p,ObjectMapper m,DshBindingService bindings) { bridge=b;dsh=s;identity=i;properties=p;mapper=m;this.bindings=bindings; }
    public SseEmitter subscribe(DshLoginIdentity login,UUID sessionId) {
        dsh.get(login.userId(),"/sessions/"+sessionId); // Ownership before stream headers.
        if(!permits.tryAcquire()) throw DshProblems.error(503,"DSH_EVENT_CAPACITY","事件连接数量已达到上限。");
        var c=new Connection(login);connections.add(c);
        try { c.socket=bridge.connect(login.userId(),sessionId,c).get(properties.getConnectTimeout().toMillis()+1000,TimeUnit.MILLISECONDS); }
        catch(Exception e) { c.close();throw DshProblems.error(502,"DSH_EVENTS_UNAVAILABLE","DSH 事件连接不可用，请重新读取会话状态。"); }
        senders.submit(c::drain);return c.emitter;
    }
    /** Reconciles DSH leases even when browsers are disconnected. */
    @Scheduled(fixedDelay = 5000, scheduler = "dshLifecycleScheduler")
    public void reconcile() {
        if(!properties.getEnabled()) return;
        Set<UUID> revoked=new HashSet<>();
        for(var c:connections) {
            try {
                boolean enabled=identity.enabled(c.login.userId());
                if(!enabled) revoked.add(c.login.userId());
                if(!c.login.expiresAt().isAfter(Instant.now()) || !enabled) c.fail("DSH_USER_UNAVAILABLE");
                else if(System.nanoTime()-c.lastHeartbeat>=TimeUnit.SECONDS.toNanos(15)) { c.lastHeartbeat=System.nanoTime();c.offer("",true); }
            } catch(RuntimeException e) { c.fail("DSH_AUTHORITY_UNAVAILABLE"); }
        }
        try {
            bindings.reconcileManagedStates();
            var users=bridge.get("/bridge/v3/active-users");List<String> enabled=new ArrayList<>();
            for(var user:users.path("userIds")) {UUID id=UUID.fromString(user.asText());if(!revoked.contains(id)&&identity.enabled(id)) enabled.add(id.toString());}
            bridge.post("/bridge/v3/actions/renew-leases",Map.of("userIds",enabled));
        } catch(RuntimeException ignored) { /* Plugin leases expire closed when Admin cannot confirm identities. */ }
    }
    @PreDestroy public void stop() { for(var c:connections)c.close();senders.shutdownNow(); }
    private final class Connection implements WebSocket.Listener {
        private final DshLoginIdentity login;
        private final SseEmitter emitter=new SseEmitter(0L);
        private final BlockingQueue<Frame> queue=new LinkedBlockingQueue<>(256);
        private final AtomicInteger bytes=new AtomicInteger();
        private final AtomicBoolean closed=new AtomicBoolean();
        private final StringBuilder fragments=new StringBuilder();
        private volatile WebSocket socket;
        private volatile long lastHeartbeat=System.nanoTime();
        private final AtomicBoolean ending=new AtomicBoolean();
        Connection(DshLoginIdentity login) {
            this.login=login;emitter.onCompletion(this::close);emitter.onTimeout(this::close);emitter.onError(e->close());
        }
        void offer(String text,boolean heartbeat) {
            if(closed.get() || ending.get())return;
            int size=text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if(bytes.addAndGet(size)>properties.getEventBufferBytes() || !queue.offer(new Frame(text,size,heartbeat,false))) { bytes.addAndGet(-size);fail("DSH_EVENT_BUFFER_EXCEEDED"); }
        }
        void drain() {
            try {
                while(!closed.get()) {
                    Frame f=queue.poll(1,TimeUnit.SECONDS);if(f==null)continue;bytes.addAndGet(-f.bytes());
                    if(f.heartbeat()) emitter.send(SseEmitter.event().comment("heartbeat"));
                    else { var event=mapper.readTree(f.text());emitter.send(SseEmitter.event().name(event.path("type").asText("message")).data(event)); }
                    if(f.terminal()) { close();return; }
                }
            } catch(Exception e) { close(); }
        }
        void fail(String code) {
            if(closed.get() || !ending.compareAndSet(false,true)) return;
            if(socket!=null) socket.abort();
            queue.clear();bytes.set(0);
            String text=mapper.writeValueAsString(Map.of("type","connection.failed","data",Map.of("code",code)));
            queue.offer(new Frame(text,0,false,true));
            // A stalled consumer cannot retain a revoked connection indefinitely.
            CompletableFuture.delayedExecutor(1,TimeUnit.SECONDS).execute(this::close);
        }
        void close() {
            if(!closed.compareAndSet(false,true))return;
            connections.remove(this);permits.release();queue.clear();if(socket!=null)socket.abort();emitter.complete();
        }
        @Override public void onOpen(WebSocket ws) { socket=ws;if(closed.get())ws.abort();else ws.request(1); }
        @Override public CompletionStage<?> onText(WebSocket ws,CharSequence value,boolean last) {
            if(fragments.length()+value.length()>properties.getEventBufferBytes()) {fail("DSH_EVENT_BUFFER_EXCEEDED");return CompletableFuture.completedFuture(null);}
            fragments.append(value);if(last) {offer(fragments.toString(),false);fragments.setLength(0);}ws.request(1);return CompletableFuture.completedFuture(null);
        }
        @Override public CompletionStage<?> onBinary(WebSocket ws,java.nio.ByteBuffer value,boolean last) { fail("DSH_EVENTS_INVALID");return CompletableFuture.completedFuture(null); }
        @Override public CompletionStage<?> onPing(WebSocket ws,java.nio.ByteBuffer message) { ws.request(1);return ws.sendPong(message); }
        @Override public CompletionStage<?> onClose(WebSocket ws,int status,String reason) { fail("DSH_EVENTS_DISCONNECTED");return CompletableFuture.completedFuture(null); }
        @Override public void onError(WebSocket ws,Throwable e) { fail("DSH_EVENTS_DISCONNECTED"); }
    }
    private record Frame(String text,int bytes,boolean heartbeat,boolean terminal) {}
}
