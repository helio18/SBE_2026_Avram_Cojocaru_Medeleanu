package homework;

public class RegisteredSubscription {

    private final String subscriptionId;
    private final SubscriberNode subscriber;
    private final Subscription subscription;

    public RegisteredSubscription(String subscriptionId, SubscriberNode subscriber, Subscription subscription) {
        this.subscriptionId = subscriptionId;
        this.subscriber = subscriber;
        this.subscription = subscription;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public SubscriberNode getSubscriber() {
        return subscriber;
    }

    public String getSubscriberId() {
        return subscriber.getSubscriberId();
    }

    public Subscription getSubscription() {
        return subscription;
    }
}
