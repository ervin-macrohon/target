package abbot.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A simple thread factory to make the management of executor groups easier
 */
public class NamedThreadFactory implements ThreadFactory {
    
    private final AtomicInteger count = new AtomicInteger(1);
    private final String prefix;
    private final ThreadGroup group;

    public NamedThreadFactory(String name) {
        SecurityManager s = System.getSecurityManager();
        group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
        prefix = name  + "-thread-"; 
    }

    public Thread newThread(Runnable r) {
        Thread t = new Thread(group, r, prefix + count.getAndIncrement());
        return t;
    }
}
