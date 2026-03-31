package homework;

import java.util.concurrent.atomic.AtomicLong;

public class SubscriberNode {

    private final String subscriberId;
    private final AtomicLong receivedNotifications;

    public SubscriberNode(String subscriberId) {
        this.subscriberId = subscriberId;
        this.receivedNotifications = new AtomicLong();
    }

    public String getSubscriberId() {
        return subscriberId;
    }

    public long receiveNotification(Publication publication, String brokerId, long emitNanoTime) {
        receivedNotifications.incrementAndGet();
        return System.nanoTime() - emitNanoTime;
    }

    public long getReceivedNotifications() {
        return receivedNotifications.get();
    }
}
