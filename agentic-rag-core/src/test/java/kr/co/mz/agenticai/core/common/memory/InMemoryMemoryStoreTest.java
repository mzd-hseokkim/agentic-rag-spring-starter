package kr.co.mz.agenticai.core.common.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class InMemoryMemoryStoreTest {

    private final InMemoryMemoryStore store = new InMemoryMemoryStore();

    @Test
    void appendAndReadBackInChronologicalOrder() {
        store.append("c1", MemoryRecord.user("hello"));
        store.append("c1", MemoryRecord.assistant("hi"));
        store.append("c1", MemoryRecord.user("how are you"));

        assertThat(store.history("c1", 10))
                .extracting(MemoryRecord::content)
                .containsExactly("hello", "hi", "how are you");
    }

    @Test
    void historyReturnsMostRecentEntriesWhenCapped() {
        IntStream.range(0, 5).forEach(i -> store.append("c1", MemoryRecord.user("m" + i)));

        assertThat(store.history("c1", 2))
                .extracting(MemoryRecord::content)
                .containsExactly("m3", "m4");
    }

    @Test
    void historyForUnknownConversationIsEmpty() {
        assertThat(store.history("missing", 10)).isEmpty();
    }

    @Test
    void zeroOrNegativeMaxMessagesYieldsEmpty() {
        store.append("c1", MemoryRecord.user("hi"));
        assertThat(store.history("c1", 0)).isEmpty();
        assertThat(store.history("c1", -1)).isEmpty();
    }

    @Test
    void clearRemovesConversation() {
        store.append("c1", MemoryRecord.user("hi"));
        store.clear("c1");
        assertThat(store.history("c1", 10)).isEmpty();
    }

    @Test
    void conversationsAreIsolated() {
        store.append("c1", MemoryRecord.user("a"));
        store.append("c2", MemoryRecord.user("b"));

        assertThat(store.history("c1", 10)).extracting(MemoryRecord::content).containsExactly("a");
        assertThat(store.history("c2", 10)).extracting(MemoryRecord::content).containsExactly("b");
    }

    @Test
    void concurrentAppendsToSameConversationAreAllRetained() throws Exception {
        int threads = 8;
        int perThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            IntStream.range(0, threads).forEach(t -> pool.submit(() -> {
                start.await();
                for (int i = 0; i < perThread; i++) {
                    store.append("c1", MemoryRecord.user("t" + t + "-" + i));
                }
                return null;
            }));
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
        List<MemoryRecord> history = store.history("c1", Integer.MAX_VALUE);
        assertThat(history).hasSize(threads * perThread);
    }
}
