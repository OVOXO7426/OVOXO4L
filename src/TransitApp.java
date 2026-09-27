import java.util.ArrayList;
import java.util.List;

public class TransitApp implements TransitSystem {
    private static final int TICKS_PER_DAY = 24;
    private static final int DAYS_PER_YEAR = 365;
    private static final int[] HOLIDAY_DAYS_OF_YEAR = {0, 25, 114, 358, 359};
    private static final long CENTS_PER_DISTANCE = 25;

    private final List<Vehicle> vehicles = new ArrayList<>();
    private final List<Ticket> tickets = new ArrayList<>();
    private final FarePolicy normalFare = new NormalFare();
    private final FarePolicy holidayFare = new HolidayFare();
    private int tick;
    private long revenueCents;

    public TransitApp() {
        this(0);
    }

    public TransitApp(int startTick) {
        if (startTick < 0) {
            throw new IllegalArgumentException("start tick must not be negative: " + startTick);
        }
        tick = startTick;
    }

    @Override
    public void addVehicle(Vehicle vehicle) {
        if (vehicle == null) {
            throw new IllegalArgumentException("vehicle must not be null");
        }
        vehicles.add(vehicle);
    }

    @Override
    public Ticket book(Passenger passenger, Trip trip) {
        if (passenger == null || trip == null) {
            throw new IllegalArgumentException("passenger and trip must not be null");
        }
        int today = day();
        boolean holiday = isHoliday();
        long base = trip.distance() * CENTS_PER_DISTANCE;
        int earlier = countTickets(passenger, today);
        FarePolicy policy = holiday ? holidayFare : normalFare;

        long dayFare = policy.dayFare(base, earlier);
        if (dayFare < 0 || dayFare > base) {
            throw new IllegalArgumentException("day fare out of range: " + dayFare);
        }
        long fare = passenger.applyDiscount(dayFare);
        if (fare < 0 || fare > dayFare) {
            throw new IllegalArgumentException("passenger fare out of range: " + fare);
        }

        // Nothing above changes state, so a rejected booking leaves no trace.
        String id = "T" + (tickets.size() + 1);
        Ticket ticket = new Ticket(id, passenger, trip, today, holiday, base, dayFare, fare);
        tickets.add(ticket);
        revenueCents += fare;
        return ticket;
    }

    @Override
    public void tick() {
        tick++;
        for (Vehicle vehicle : vehicles) {
            vehicle.advance();
        }
    }

    @Override
    public int currentTick() {
        return tick;
    }

    @Override
    public int day() {
        return tick / TICKS_PER_DAY;
    }

    @Override
    public boolean isHoliday() {
        int dayOfYear = day() % DAYS_PER_YEAR;
        for (int holidayDay : HOLIDAY_DAYS_OF_YEAR) {
            if (holidayDay == dayOfYear) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<Ticket> tickets() {
        return new ArrayList<>(tickets);
    }

    @Override
    public long revenueCents() {
        return revenueCents;
    }

    // Same object, not same name: two passengers may share a name.
    private int countTickets(Passenger passenger, int day) {
        int count = 0;
        for (Ticket ticket : tickets) {
            if (ticket.passenger() == passenger && ticket.day() == day) {
                count++;
            }
        }
        return count;
    }
}
