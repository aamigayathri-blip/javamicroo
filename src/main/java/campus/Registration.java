package campus;

import java.time.Instant;

/** A student's seat in an event. */
public class Registration {
    private final long eventId;
    private final long userId;
    private final String registeredAt;

    public Registration(long eventId, long userId) {
        this.eventId = eventId;
        this.userId = userId;
        this.registeredAt = Instant.now().toString();
    }

    public long getEventId() { return eventId; }
    public long getUserId() { return userId; }
    public String getRegisteredAt() { return registeredAt; }
}
