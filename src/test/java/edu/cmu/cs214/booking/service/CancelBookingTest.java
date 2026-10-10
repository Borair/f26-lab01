package edu.cmu.cs214.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.booking.domain.Room;
import edu.cmu.cs214.booking.domain.TimeInterval;
import edu.cmu.cs214.booking.domain.User;
import edu.cmu.cs214.booking.repo.InMemoryBookingStore;
import org.junit.jupiter.api.Test;

class CancelBookingTest {

    private final Room roomA = new Room("A", "Alpha", 10);
    private final User alice = new User("u1", "Alice");
    private final User bob = new User("u2", "Bob");

    // S1: Alice books room A over [600, 660) and then cancels; the room must
    // no longer hold any booking.
    @Test
    void cancelRemovesTheBooking() {
        BookingService svc = new BookingService(new InMemoryBookingStore());
        BookingResult r = svc.book(roomA, alice, new TimeInterval(600, 660));
        assertInstanceOf(BookingResult.Confirmed.class, r);
        String bookingId = ((BookingResult.Confirmed) r).booking().id();

        svc.cancelBooking(bookingId);

        assertTrue(svc.listBookings(roomA).isEmpty());
    }

    // S2: with one confirmed booking and one waitlisted user, cancelling an
    // id that does not exist must leave both untouched.
    @Test
    void cancelWithUnknownIdLeavesBookingsAndWaitlistUntouched() {
        InMemoryBookingStore store = new InMemoryBookingStore();
        BookingService svc = new BookingService(store);
        svc.book(roomA, alice, new TimeInterval(600, 660));
        BookingResult r = svc.book(roomA, bob, new TimeInterval(630, 700));
        assertInstanceOf(BookingResult.Waitlisted.class, r);

        svc.cancelBooking("no-such-booking");

        assertEquals(1, svc.listBookings(roomA).size());
        assertEquals(1, store.waitlistForRoom(roomA).size());
    }
}
