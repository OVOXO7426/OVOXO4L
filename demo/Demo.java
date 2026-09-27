import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * With no argument, Demo runs the fixed story in main;
 * demo/expected-output.txt is its output. Given a scenario file, e.g.
 *     java -cp out Demo scenarios/2-bondi-buses.scenario
 * it runs that scenario without the GUI: the same bookings at the same
 * steps, then one tick per step.
 */
public class Demo {
    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            runScenario(ScenarioFile.read(Path.of(args[0])));
            return;
        }
        Trip toBondi = new Trip("Central", "Bondi Junction", 12);
        Trip toCentral = new Trip("Bondi Junction", "Central", 12);

        TransitSystem system = new TransitApp();
        Vehicle bus = new Bus("B1");
        Vehicle train = new Train("T1");
        Vehicle tram = new Tram("L1");
        system.addVehicle(bus);
        system.addVehicle(train);
        system.addVehicle(tram);

        Passenger ava = new AdultPassenger("Ava");
        Passenger ben = new ConcessionPassenger("Ben", "DV", 40);
        Passenger cal = new ConcessionPassenger("Cal", "A", 60);

        System.out.println("== day 0 (1 January) ==");
        printClock(system);
        print(system.book(ava, toBondi));
        print(system.book(ava, toCentral));
        print(system.book(ben, toBondi));

        System.out.println("== four ticks ==");
        for (int i = 0; i < 4; i++) {
            system.tick();
            System.out.println("  tick " + system.currentTick() + ": "
                    + describe(bus) + ", "
                    + describe(train) + ", " + describe(tram));
        }

        System.out.println("== day 1 ==");
        for (int i = 4; i < 24; i++) {
            system.tick();
        }
        printClock(system);
        print(system.book(ava, toBondi));
        print(system.book(ben, toBondi));
        print(system.book(cal, toBondi));

        System.out.println("== rejected ==");
        try {
            system.book(null, toBondi);
            System.out.println("  accepted (unexpected)");
        } catch (IllegalArgumentException rejected) {
            System.out.println("  null passenger rejected; tickets="
                    + system.tickets().size());
        }
        try {
            new ConcessionPassenger("Dee", "S", 20);
            System.out.println("  accepted (unexpected)");
        } catch (IllegalArgumentException rejected) {
            System.out.println("  concession type S rejected");
        }

        System.out.println("== totals ==");
        System.out.println("  tickets=" + system.tickets().size()
                + " revenue=" + system.revenueCents());
        List<Ticket> copy = system.tickets();
        copy.clear();
        System.out.println("  clearing a copy of tickets() leaves tickets="
                + system.tickets().size());
    }

    private static void runScenario(ScenarioFile data) throws Exception {
        TransitSystem system = new TransitApp(data.start());
        List<Vehicle> vehicles = new java.util.ArrayList<>();
        for (ScenarioFile.VehicleSpec spec : data.vehicles()) {
            Vehicle vehicle = (Vehicle) Class.forName(spec.className())
                .getConstructor(String.class).newInstance(spec.id());
            system.addVehicle(vehicle);
            vehicles.add(vehicle);
        }
        Map<String, Passenger> passengers = new LinkedHashMap<>();
        for (ScenarioFile.PassengerSpec spec : data.passengers()) {
            passengers.put(spec.key(), spec.kind().equals("adult")
                ? new AdultPassenger(spec.name())
                : new ConcessionPassenger(spec.name(), spec.concessionType(),
                        spec.age()));
        }
        int lastStep = 3;
        for (ScenarioFile.Booking booking : data.bookings()) {
            lastStep = Math.max(lastStep, booking.step());
        }

        System.out.println("== " + data.name() + " ==");
        for (int step = 0; step <= lastStep; step++) {
            boolean clockShown = false;
            for (ScenarioFile.Booking booking : data.bookings()) {
                if (booking.step() != step) continue;
                if (!clockShown) printClock(system);
                clockShown = true;
                List<String> route = booking.route();
                Trip trip = new Trip(route.get(0), route.get(route.size() - 1),
                    data.distance(booking));
                Passenger passenger = passengers.get(booking.passengerKey());
                try {
                    print(system.book(passenger, trip));
                } catch (IllegalArgumentException rejected) {
                    System.out.println("  " + passenger.name() + " rejected: "
                            + rejected.getMessage());
                }
            }
            system.tick();
            StringBuilder line =
                    new StringBuilder("  tick " + system.currentTick() + ":");
            for (Vehicle vehicle : vehicles) line.append(' ').append(describe(vehicle));
            System.out.println(line);
        }

        System.out.println("== totals ==");
        Map<Passenger, Long> paid = new IdentityHashMap<>();
        for (Ticket ticket : system.tickets()) {
            paid.merge(ticket.passenger(), ticket.finalFareCents(), Long::sum);
        }
        for (Passenger passenger : passengers.values()) {
            System.out.println("  " + passenger.name() + " paid "
                    + paid.getOrDefault(passenger, 0L));
        }
        System.out.println("  tickets=" + system.tickets().size()
                + " revenue=" + system.revenueCents());
    }

    private static void printClock(TransitSystem system) {
        System.out.println("  tick=" + system.currentTick() + " day=" + system.day()
                + " holiday=" + system.isHoliday());
    }

    private static void print(Ticket t) {
        System.out.println("  " + t.id() + " " + t.passenger().name() + " " + t.trip()
                + ": base " + t.baseFareCents() + ", day fare " + t.dayFareCents()
                + ", final " + t.finalFareCents());
    }

    private static String describe(Vehicle v) {
        return v.id() + "=" + (int) v.position() + " " + v.status();
    }
}
