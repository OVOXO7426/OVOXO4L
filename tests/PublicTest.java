import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class PublicTest {
    static Passenger A() { return new AdultPassenger("adult"); }
    static Trip trip(int distance) { return new Trip("A", "B", distance); }
    static TransitSystem holiday() { return new TransitApp(0); }
    static TransitSystem normalDay() { return new TransitApp(24); }

    @Test
    @DisplayName("CLK03: tick() adds exactly one to the current tick")
    void clk03() {
        TransitSystem system = new TransitApp(7); system.tick();
        boolean __ok = system.currentTick() == 8;
        assertTrue(__ok, "tick() adds exactly one to the current tick");
    }

    @Test
    @DisplayName("HOL01: day 0 (1 January) is a holiday")
    void hol01() {
        assertTrue(new TransitApp().isHoliday(), "day 0 (1 January) is a holiday");
    }

    @Test
    @DisplayName("FARE01: an adult pays distance x 25 cents on a normal day")
    void fare01() {
        assertTrue(normalDay().book(A(), trip(4)).finalFareCents() == 100, "an adult pays distance x 25 cents on a normal day");
    }

    @Test
    @DisplayName("HFARE03: the second trip on the same holiday is free")
    void hfare03() {
        TransitSystem system = holiday(); Passenger sam = A();
        long first = system.book(sam, trip(4)).finalFareCents(); long second = system.book(sam, trip(4)).finalFareCents();
        boolean __ok = first == 50 && second == 0;
        assertTrue(__ok, "the second trip on the same holiday is free");
    }

    @Test
    @DisplayName("TIX03: clearing the list returned by tickets() does not change the system")
    void tix03() {
        TransitSystem system = normalDay(); system.book(A(), trip(1)); system.book(A(), trip(2));
        java.util.List<Ticket> copy = system.tickets(); copy.clear();
        boolean __ok = system.tickets().size() == 2;
        assertTrue(__ok, "clearing the list returned by tickets() does not change the system");
    }
}
