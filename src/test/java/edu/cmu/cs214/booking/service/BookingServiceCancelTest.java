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

class BookingServiceCancelTest {

    private final Room roomA = new Room("A", "Alpha", 10);
    private final User alice = new User("u1", "Alice");
    private final User bob = new User("u2", "Bob");
    private final User carol = new User("u3", "Carol");
    private final User dave = new User("u4", "Dave");

    private BookingService newService() {
        return new BookingService(new InMemoryBookingStore());
    }

    private String confirmedId(BookingService svc, Room room, User user, TimeInterval interval) {
        BookingResult r = svc.book(room, user, interval);
        assertInstanceOf(BookingResult.Confirmed.class, r);
        return ((BookingResult.Confirmed) r).booking().id();
    }

    @Test
    void cancelPromotesWaitlistedUserWhoseSlotIsNowFree() {
        BookingService svc = newService();
        String aliceId = confirmedId(svc, roomA, alice, new TimeInterval(600, 660));
        BookingResult r = svc.book(roomA, bob, new TimeInterval(600, 660));
        assertInstanceOf(BookingResult.Waitlisted.class, r);

        svc.cancelBooking(aliceId);

        List<Booking> bookings = svc.listBookings(roomA);
        assertEquals(1, bookings.size());
        assertEquals(bob, bookings.get(0).user());
        assertEquals(new TimeInterval(600, 660), bookings.get(0).interval());
    }

    @Test
    void cancelWithUnknownIdDoesNothing() {
        BookingService svc = newService();
        String aliceId = confirmedId(svc, roomA, alice, new TimeInterval(600, 660));

        svc.cancelBooking("b-no-such-id");

        assertEquals(1, svc.listBookings(roomA).size());
        assertEquals(aliceId, svc.listBookings(roomA).get(0).id());
    }

    @Test
    void cancelPromotesAtMostOneUser() {
        BookingService svc = newService();
        String aliceId = confirmedId(svc, roomA, alice, new TimeInterval(600, 660));
        assertInstanceOf(BookingResult.Waitlisted.class, svc.book(roomA, bob, new TimeInterval(600, 660)));
        assertInstanceOf(BookingResult.Waitlisted.class, svc.book(roomA, carol, new TimeInterval(600, 660)));

        svc.cancelBooking(aliceId);

        List<Booking> bookings = svc.listBookings(roomA);
        assertEquals(1, bookings.size(), "only the earliest waiter should be promoted");
        assertEquals(bob, bookings.get(0).user());
    }

    @Test
    void cancelSkipsStillConflictingWaiterAndPromotesTheNextFit() {
        BookingService svc = newService();
        String aliceId = confirmedId(svc, roomA, alice, new TimeInterval(600, 660));
        confirmedId(svc, roomA, dave, new TimeInterval(660, 720));
        // Bob still overlaps Dave's booking after Alice is cancelled.
        assertInstanceOf(BookingResult.Waitlisted.class, svc.book(roomA, bob, new TimeInterval(610, 700)));
        // Carol is the next waiter and fits after Alice is cancelled.
        assertInstanceOf(BookingResult.Waitlisted.class, svc.book(roomA, carol, new TimeInterval(600, 630)));

        svc.cancelBooking(aliceId);

        List<Booking> bookings = svc.listBookings(roomA);
        assertEquals(2, bookings.size());
        assertTrue(bookings.stream().noneMatch(b -> b.user().equals(bob)), "still-conflicting waiter stays on the waitlist");
        assertTrue(bookings.stream().anyMatch(b -> b.user().equals(carol)), "next fit is promoted");
        assertTrue(bookings.stream().anyMatch(b -> b.user().equals(dave)), "unrelated booking is untouched");
    }
}
