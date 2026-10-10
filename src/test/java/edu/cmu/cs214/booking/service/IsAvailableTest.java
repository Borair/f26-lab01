package edu.cmu.cs214.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.booking.domain.Room;
import edu.cmu.cs214.booking.domain.TimeInterval;
import edu.cmu.cs214.booking.domain.User;
import edu.cmu.cs214.booking.repo.InMemoryBookingStore;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@code isAvailable} query proposed in changes/agent-attempt.patch.
 *
 * <p>The patch decides availability from the <em>start</em> of the existing
 * booking only:
 * {@code b.interval().start() >= interval.start() && b.interval().start() < interval.end()}
 * That only catches an existing booking that begins inside the queried window.
 * A booking that starts <em>before</em> the window and runs into it is missed,
 * so the method answers "free" for a slot that is actually taken. The failure
 * is invisible to the existing suite because nothing calls isAvailable.
 */
class IsAvailableTest {

    private final Room roomA = new Room("A", "Alpha", 10);
    private final User alice = new User("u1", "Alice");
    private final User bob = new User("u2", "Bob");

    private static BookingService newService() {
        return new BookingService(new InMemoryBookingStore());
    }

    // Case 3 of the analysis table: the existing booking starts before the
    // query and runs into it. [570,630) overlaps [600,660), so the room is busy.
    @Test
    void notAvailableWhenAnEarlierBookingRunsIntoTheRequest() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(570, 630));

        assertFalse(svc.isAvailable(roomA, new TimeInterval(600, 660)),
                "[570,630) overlaps [600,660): the room is not free");
    }

    // Case 4: the existing booking completely spans the query.
    @Test
    void notAvailableWhenAnEarlierBookingSpansTheRequest() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(540, 720));

        assertFalse(svc.isAvailable(roomA, new TimeInterval(600, 660)),
                "[540,720) covers [600,660): the room is not free");
    }

    // Boundary cases the patch happens to get right, pinned so a "fix" cannot
    // silently invert them.
    @Test
    void availableForBookingsThatDoNotOverlap() {
        assertTrue(newService().isAvailable(roomA, new TimeInterval(600, 660)),
                "no bookings at all: free");

        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(540, 600));
        assertTrue(svc.isAvailable(roomA, new TimeInterval(600, 660)),
                "[540,600) is back-to-back with [600,660): free");

        BookingService later = newService();
        later.book(roomA, alice, new TimeInterval(700, 760));
        assertTrue(later.isAvailable(roomA, new TimeInterval(600, 660)),
                "[700,760) starts after the query: free");
    }

    // isAvailable must agree with TimeInterval.overlaps, the single place the
    // conflict rule lives, and with what book() actually does afterwards.
    @Test
    void isAvailableAgreesWithBook() {
        // Every position relationship between an existing booking and the query.
        int[][] existing = {
            {480, 540}, // 1 completely before
            {540, 600}, // 2 back-to-back before
            {570, 630}, // 3 starts before, runs into the query
            {540, 720}, // 4 spans the query
            {610, 640}, // 5 inside the query
            {630, 720}, // 6 starts inside, runs out
            {600, 660}, // 7 identical
            {660, 720}, // 8 back-to-back after
            {700, 760}, // 9 completely after
        };
        TimeInterval query = new TimeInterval(600, 660);

        for (int[] e : existing) {
            TimeInterval taken = new TimeInterval(e[0], e[1]);
            InMemoryBookingStore store = new InMemoryBookingStore();
            BookingService svc = new BookingService(store);
            svc.book(roomA, alice, taken);

            boolean free = svc.isAvailable(roomA, query);
            boolean shouldBeFree = !taken.overlaps(query);
            String where = "existing=" + taken + " query=" + query;

            assertEquals(shouldBeFree, free,
                    "isAvailable() disagrees with TimeInterval.overlaps(): " + where);

            BookingResult actual = new BookingService(store).book(roomA, bob, query);
            if (free) {
                assertInstanceOf(BookingResult.Confirmed.class, actual,
                        "isAvailable() said free but book() did not confirm: " + where);
            } else {
                assertInstanceOf(BookingResult.Waitlisted.class, actual,
                        "isAvailable() said busy but book() confirmed: " + where);
            }
        }
    }
}
