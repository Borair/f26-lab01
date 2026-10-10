package edu.cmu.cs214.booking.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.booking.domain.Booking;
import edu.cmu.cs214.booking.domain.Room;
import edu.cmu.cs214.booking.domain.TimeInterval;
import edu.cmu.cs214.booking.domain.User;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Regression tests for {@link InMemoryBookingStore}. */
class InMemoryBookingStoreTest {

    private final Room roomA = new Room("A", "Alpha", 10);
    private final Room roomB = new Room("B", "Beta", 4);
    private final User alice = new User("u1", "Alice");
    private final User bob = new User("u2", "Bob");

    @Test
    void bookingsForRoomReturnsOnlyThatRoomsBookings() {
        InMemoryBookingStore store = new InMemoryBookingStore();
        store.addBooking(new Booking("b1", roomA, alice, new TimeInterval(600, 660)));
        store.addBooking(new Booking("b2", roomB, bob, new TimeInterval(600, 660)));

        List<Booking> roomABookings = store.bookingsForRoom(roomA);
        assertEquals(1, roomABookings.size());
        assertTrue(roomABookings.stream().allMatch(b -> b.room().id().equals(roomA.id())));

        List<Booking> roomBBookings = store.bookingsForRoom(roomB);
        assertEquals(1, roomBBookings.size());
        assertTrue(roomBBookings.stream().allMatch(b -> b.room().id().equals(roomB.id())));
    }
}
