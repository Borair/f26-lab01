package edu.cmu.cs214.booking.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.booking.domain.Room;
import edu.cmu.cs214.booking.domain.TimeInterval;
import edu.cmu.cs214.booking.domain.User;
import edu.cmu.cs214.booking.repo.InMemoryBookingStore;
import org.junit.jupiter.api.Test;

/**
 * Exposes the defect in the agent-generated {@code isAvailable}: it re-implements
 * the overlap rule by checking only whether an existing booking's start falls
 * inside the requested interval, which is NOT equivalent to
 * {@code TimeInterval.overlaps} (a.start < b.end && b.start < a.end).
 */
class BookingServiceIsAvailableTest {

    private final Room roomA = new Room("A", "Alpha", 10);
    private final User alice = new User("u1", "Alice");

    private BookingService newServiceWith(TimeInterval booked) {
        BookingService svc = new BookingService(new InMemoryBookingStore());
        svc.book(roomA, alice, booked);
        return svc;
    }

    @Test
    void isAvailableIsFalseWhenExistingBookingFullyCoversTheInterval() {
        BookingService svc = newServiceWith(new TimeInterval(600, 700));
        // [600,700) fully contains [630,660): the slot is NOT available.
        assertFalse(svc.isAvailable(roomA, new TimeInterval(630, 660)));
    }

    @Test
    void isAvailableIsFalseWhenExistingBookingStartsBeforeAndEndsInsideTheInterval() {
        BookingService svc = newServiceWith(new TimeInterval(600, 660));
        // [600,660) overlaps [630,700) on [630,660): the slot is NOT available.
        assertFalse(svc.isAvailable(roomA, new TimeInterval(630, 700)));
    }

    @Test
    void isAvailableIsFalseWhenExistingBookingStartsInsideTheInterval() {
        BookingService svc = newServiceWith(new TimeInterval(630, 700));
        // start of the existing booking falls inside [600,660): the slot is NOT available.
        assertFalse(svc.isAvailable(roomA, new TimeInterval(600, 660)));
    }

    @Test
    void isAvailableIsTrueForBackToBackIntervals() {
        BookingService svc = newServiceWith(new TimeInterval(600, 660));
        // Half-open intervals [600,660) and [660,700) do not overlap.
        assertTrue(svc.isAvailable(roomA, new TimeInterval(660, 700)));
    }
}
