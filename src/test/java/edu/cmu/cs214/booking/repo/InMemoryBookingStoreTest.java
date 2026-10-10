package edu.cmu.cs214.booking.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import edu.cmu.cs214.booking.domain.Booking;
import edu.cmu.cs214.booking.domain.Room;
import edu.cmu.cs214.booking.domain.TimeInterval;
import edu.cmu.cs214.booking.domain.User;
import org.junit.jupiter.api.Test;

/**
 * Regression tests pinned directly on the layer where the Milestone 1 defect
 * lived: {@code bookingsForRoom} used to hand back every booking in the store
 * instead of only the bookings of the requested room.
 */
class InMemoryBookingStoreTest {

    private final Room roomA = new Room("A", "Alpha", 10);
    private final Room roomB = new Room("B", "Beta", 4);
    private final User alice = new User("u1", "Alice");
    private final User bob = new User("u2", "Bob");

    private static Booking booking(String id, Room room, User user, TimeInterval interval) {
        return new Booking(id, room, user, interval);
    }

    @Test
    void bookingsForRoomIsScopedToASingleRoom() {
        BookingStore store = new InMemoryBookingStore();
        store.addBooking(booking("b1", roomA, alice, new TimeInterval(600, 660)));
        store.addBooking(booking("b2", roomB, bob, new TimeInterval(600, 660)));

        assertEquals(2, store.allBookings().size(), "the store still holds both bookings");
        assertEquals(1, store.bookingsForRoom(roomA).size());
        assertEquals(1, store.bookingsForRoom(roomB).size());
        assertEquals("b1", store.bookingsForRoom(roomA).get(0).id());
        assertEquals("b2", store.bookingsForRoom(roomB).get(0).id());
    }

    @Test
    void bookingsForRoomIgnoresOtherRoomsWaitingOnTheSameSlot() {
        BookingStore store = new InMemoryBookingStore();
        store.addBooking(booking("b1", roomA, alice, new TimeInterval(600, 660)));
        store.addBooking(booking("b2", roomB, bob, new TimeInterval(600, 660)));
        store.addBooking(booking("b3", roomB, bob, new TimeInterval(660, 720)));

        assertEquals(1, store.bookingsForRoom(roomA).size());
        assertEquals(2, store.bookingsForRoom(roomB).size());
        assertEquals(0, store.bookingsForRoom(new Room("C", "Gamma", 2)).size());
    }
}
