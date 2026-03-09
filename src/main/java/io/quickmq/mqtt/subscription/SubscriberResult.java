package io.quickmq.mqtt.subscription;

import io.netty.channel.Channel;
import io.netty.util.Recycler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 订阅查询结果（Netty Recycler 池化对象）。
 * <p>
 * 使用 Netty {@link Recycler} 管理生命周期：
 * <ul>
 *   <li>同线程 acquire/close 走 thread-local 快速路径，零竞争</li>
 *   <li>跨线程回收也安全（WeakOrderQueue 延迟回收）</li>
 *   <li>容量自动收敛，无需手动配置池大小</li>
 * </ul>
 *
 * <pre>{@code
 * try (SubscriberResult r = subscriptionStore.findSubscribers(topic)) {
 *     for (int i = 0; i < r.size(); i++) {
 *         SubscriptionEntry e = r.get(i);
 *     }
 * }
 * }</pre>
 */
public final class SubscriberResult implements AutoCloseable {

    private static final int INIT_CAPACITY = 64;

    private static final Recycler<SubscriberResult> RECYCLER = new Recycler<>() {
        @Override
        protected SubscriberResult newObject(Handle<SubscriberResult> handle) {
            return new SubscriberResult(handle);
        }
    };

    private final Recycler.Handle<SubscriberResult> handle;
    final Set<Channel> seen;
    final List<SubscriptionEntry> entries;

    private SubscriberResult(Recycler.Handle<SubscriberResult> handle) {
        this.handle = handle;
        this.seen = new HashSet<>(INIT_CAPACITY);
        this.entries = new ArrayList<>(INIT_CAPACITY);
    }

    static SubscriberResult acquire() {
        return RECYCLER.get();
    }

    public int size() {
        return entries.size();
    }

    public SubscriptionEntry get(int index) {
        return entries.get(index);
    }

    @Override
    public void close() {
        seen.clear();
        entries.clear();
        handle.recycle(this);
    }
}
