package edu.cmu.cs214.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.booking.domain.Booking;
import edu.cmu.cs214.booking.domain.Room;
import edu.cmu.cs214.booking.domain.TimeInterval;
import edu.cmu.cs214.booking.domain.User;
import edu.cmu.cs214.booking.repo.InMemoryBookingStore;
import java.util.List;
import org.junit.jupiter.api.Test;

class CancelBookingTest {

    private final Room roomA = new Room("A", "Alpha", 10);
    private final Room roomB = new Room("B", "Beta", 4);
    private final User alice = new User("u1", "Alice");
    private final User bob = new User("u2", "Bob");
    private final User carol = new User("u3", "Carol");
    private final User dave = new User("u4", "Dave");

    /**
     * Core invariant: no room ever holds two confirmed bookings whose intervals
     * overlap. Checked after every cancellation below.
     */
    private static void assertNoOverlap(List<Booking> bookings) {
        for (int i = 0; i < bookings.size(); i++) {
            for (int j = i + 1; j < bookings.size(); j++) {
                if (bookings.get(i).interval().overlaps(bookings.get(j).interval())) {
                    throw new AssertionError("overlapping bookings in the same room: "
                            + bookings.get(i) + " and " + bookings.get(j));
                }
            }
        }
    }

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

    // S3: Alice books room A over [600, 660); Bob is waitlisted for [630, 700).
    // Cancelling Alice's booking promotes Bob: room A then holds exactly one
    // booking, Bob's, over [630, 700), and Bob is off the waitlist.
    @Test
    void cancelPromotesNonConflictingWaitlistedUser() {
        InMemoryBookingStore store = new InMemoryBookingStore();
        BookingService svc = new BookingService(store);
        BookingResult rAlice = svc.book(roomA, alice, new TimeInterval(600, 660));
        assertInstanceOf(BookingResult.Confirmed.class, rAlice);
        String bookingId = ((BookingResult.Confirmed) rAlice).booking().id();
        BookingResult rBob = svc.book(roomA, bob, new TimeInterval(630, 700));
        assertInstanceOf(BookingResult.Waitlisted.class, rBob);

        svc.cancelBooking(bookingId);

        List<Booking> bookings = svc.listBookings(roomA);
        assertEquals(1, bookings.size());
        assertEquals(bob, bookings.get(0).user());
        assertEquals(new TimeInterval(630, 700), bookings.get(0).interval());
        assertTrue(store.waitlistForRoom(roomA).isEmpty());
    }

    // S4: first-come-first-served, at most one promotion. Alice holds
    // [600,720); Bob waits for [600,660) (first) and Carol for [660,700).
    // Cancelling Alice promotes Bob only; Carol keeps waiting.
    @Test
    void cancelPromotesTheEarliestWaiterAndOnlyOne() {
        InMemoryBookingStore store = new InMemoryBookingStore();
        BookingService svc = new BookingService(store);
        BookingResult rAlice = svc.book(roomA, alice, new TimeInterval(600, 720));
        String bookingId = ((BookingResult.Confirmed) rAlice).booking().id();
        assertInstanceOf(BookingResult.Waitlisted.class,
                svc.book(roomA, bob, new TimeInterval(600, 660)));
        assertInstanceOf(BookingResult.Waitlisted.class,
                svc.book(roomA, carol, new TimeInterval(660, 700)));

        svc.cancelBooking(bookingId);

        List<Booking> bookings = svc.listBookings(roomA);
        assertEquals(1, bookings.size());
        assertEquals(bob, bookings.get(0).user(), "the earliest waiter wins");
        assertEquals(new TimeInterval(600, 660), bookings.get(0).interval());
        assertEquals(1, store.waitlistForRoom(roomA).size(), "at most one promotion");
        assertEquals(carol, store.waitlistForRoom(roomA).get(0).user());
        assertNoOverlap(bookings);
    }

    // S5: a waiter who still conflicts must be skipped. Alice holds [600,660)
    // and Dave holds [700,760); Bob waits for [630,730) (first) and Carol for
    // [610,650). Cancelling Alice skips Bob (still overlaps Dave) and promotes
    // Carol — promoting Bob would break the invariant.
    @Test
    void cancelSkipsAWaiterWhoStillConflicts() {
        InMemoryBookingStore store = new InMemoryBookingStore();
        BookingService svc = new BookingService(store);
        BookingResult rAlice = svc.book(roomA, alice, new TimeInterval(600, 660));
        String bookingId = ((BookingResult.Confirmed) rAlice).booking().id();
        assertInstanceOf(BookingResult.Confirmed.class,
                svc.book(roomA, dave, new TimeInterval(700, 760)));
        assertInstanceOf(BookingResult.Waitlisted.class,
                svc.book(roomA, bob, new TimeInterval(630, 730)));
        assertInstanceOf(BookingResult.Waitlisted.class,
                svc.book(roomA, carol, new TimeInterval(610, 650)));

        svc.cancelBooking(bookingId);

        List<Booking> bookings = svc.listBookings(roomA);
        assertEquals(2, bookings.size(), "Dave plus the promoted Carol");
        assertTrue(bookings.stream().anyMatch(b -> b.user().equals(carol)));
        assertTrue(bookings.stream().anyMatch(b -> b.user().equals(dave)));
        assertEquals(1, store.waitlistForRoom(roomA).size());
        assertEquals(bob, store.waitlistForRoom(roomA).get(0).user(), "Bob is skipped, still waiting");
        assertNoOverlap(bookings);
    }

    // S6: promotion never crosses rooms. Alice holds room A while room B has
    // its own waiter; cancelling Alice must leave room B untouched.
    @Test
    void cancelDoesNotPromoteWaitersFromAnotherRoom() {
        InMemoryBookingStore store = new InMemoryBookingStore();
        BookingService svc = new BookingService(store);
        BookingResult rAlice = svc.book(roomA, alice, new TimeInterval(600, 660));
        String bookingId = ((BookingResult.Confirmed) rAlice).booking().id();
        assertInstanceOf(BookingResult.Confirmed.class,
                svc.book(roomB, bob, new TimeInterval(600, 660)));
        assertInstanceOf(BookingResult.Waitlisted.class,
                svc.book(roomB, carol, new TimeInterval(600, 660)));

        svc.cancelBooking(bookingId);

        assertTrue(svc.listBookings(roomA).isEmpty());
        assertEquals(1, svc.listBookings(roomB).size(), "room B is untouched");
        assertEquals(1, store.waitlistForRoom(roomB).size(), "room B's waiter is not promoted");
        assertNoOverlap(svc.listBookings(roomB));
    }
}
