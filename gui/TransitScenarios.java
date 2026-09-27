import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * Scenario 1 (City Circle) is written in code below so you can see how a scenario is put
 * together. Every other scenario is a data file in the scenarios/ folder, read by
 * loader/ScenarioFile.java. The GUI lists every *.scenario file it finds there, so you can add
 * your own. Do not edit scenarios 1 to 4: the viva uses them, and each shows a check code.
 */
public final class TransitScenarios {
    private TransitScenarios() {}

    public static final String CITY_ID = "city";
    private static final List<String> problems = new ArrayList<>();

    public static Path directory() {
        return Path.of(System.getProperty("transit.scenarios", "scenarios"));
    }

    public static List<String> ids() {
        List<String> ids = new ArrayList<>();
        ids.add(CITY_ID);
        for (ScenarioFile data : files()) {
            if (!data.id().equals(CITY_ID)) ids.add(data.id());
        }
        return ids;
    }

    public static List<String> problems() {
        return List.copyOf(problems);
    }

    public static TransitScenario byId(String id) {
        if (CITY_ID.equals(id)) return cityCircle();
        for (ScenarioFile data : files()) {
            if (data.id().equals(id)) return fromFile(data);
        }
        throw new IllegalArgumentException("unknown scenario: " + id);
    }

    private static List<ScenarioFile> files() {
        problems.clear();
        return ScenarioFile.readAll(directory(), problems);
    }

    public static TransitScenario fromFile(ScenarioFile data) {
        Builder b = new Builder(data.id(), data.name(), data.focus(), data.start());
        for (ScenarioFile.Stop stop : data.stops()) {
            b.stop(stop.name(), stop.x(), stop.y(), mode(stop.mode()));
        }
        for (ScenarioFile.VehicleSpec vehicle : data.vehicles()) {
            b.vehicle(newVehicle(vehicle), vehicle.stop(), mode(vehicle.mode()));
        }
        Map<String, Passenger> passengers = new HashMap<>();
        Map<String, String> labels = new HashMap<>();
        for (ScenarioFile.PassengerSpec spec : data.passengers()) {
            if (spec.kind().equals("adult")) {
                passengers.put(spec.key(), new AdultPassenger(spec.name()));
                labels.put(spec.key(), "adult");
            } else {
                passengers.put(spec.key(),
                    new ConcessionPassenger(spec.name(), spec.concessionType(), spec.age()));
                labels.put(spec.key(), "concession " + spec.concessionType() + ", age " + spec.age());
            }
        }
        for (ScenarioFile.Booking booking : data.bookings()) {
            b.book(booking.step(), passengers.get(booking.passengerKey()),
                labels.get(booking.passengerKey()), booking.route());
        }
        return b.build();
    }

    private static ServiceMode mode(String text) {
        try {
            return ServiceMode.valueOf(text);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("mode must be BUS, TRAIN or TRAM: " + text);
        }
    }

    private static Vehicle newVehicle(ScenarioFile.VehicleSpec spec) {
        try {
            return Class.forName(spec.className()).asSubclass(Vehicle.class)
                .getConstructor(String.class).newInstance(spec.id());
        } catch (ClassCastException failure) {
            throw new IllegalArgumentException(spec.className() + " is not a Vehicle");
        } catch (ReflectiveOperationException failure) {
            throw new IllegalArgumentException("cannot create vehicle " + spec.className()
                + "(\"" + spec.id() + "\")", failure);
        }
    }

    public static TransitScenario cityCircle() {
        Builder b = new Builder("city", "1. City Circle - normal day",
            "One adult makes one train trip on 2 January (a normal day): the full fare path "
                + "and the Train's move-then-dwell pattern.", 24);
        b.stop("Central", 5, 18, ServiceMode.TRAIN)
         .stop("Town Hall", 11, 14, ServiceMode.TRAIN)
         .stop("Wynyard", 17, 10, ServiceMode.TRAIN)
         .stop("Circular Quay", 23, 6, ServiceMode.TRAIN);
        b.vehicle(new Train("City 1"), "Central", ServiceMode.TRAIN);
        b.book(0, new AdultPassenger("Passenger 1"), "adult",
            List.of("Central", "Town Hall", "Wynyard", "Circular Quay"));
        return b.build();
    }

    private static final class Builder {
        private final String id;
        private final String name;
        private final String focus;
        private final int startTick;
        private final TransitApp system;
        private final Map<String, TransitScenario.Point> points = new HashMap<>();
        private final List<TransitScenario.StopPlacement> stops = new ArrayList<>();
        private final List<TransitScenario.VehicleStart> vehicles = new ArrayList<>();
        private final List<TransitScenario.BookingCommand> bookings = new ArrayList<>();

        Builder(String id, String name, String focus, int startTick) {
            this.id = id;
            this.name = name;
            this.focus = focus;
            this.startTick = startTick;
            this.system = new TransitApp(startTick);
        }

        Builder stop(String stopName, int x, int y, ServiceMode mode) {
            TransitScenario.Point point = new TransitScenario.Point(x, y);
            points.put(stopName, point);
            stops.add(new TransitScenario.StopPlacement(stopName, point, mode));
            return this;
        }

        Builder vehicle(Vehicle vehicle, String stopName, ServiceMode mode) {
            system.addVehicle(vehicle);
            vehicles.add(new TransitScenario.VehicleStart(vehicle, stopName, mode));
            return this;
        }

        Builder book(int releaseStep, Passenger passenger, String label, List<String> route) {
            int distance = 0;
            for (int i = 1; i < route.size(); i++) {
                distance += points.get(route.get(i - 1)).manhattanDistance(points.get(route.get(i)));
            }
            Trip trip = new Trip(route.get(0), route.get(route.size() - 1), distance);
            bookings.add(new TransitScenario.BookingCommand(releaseStep, passenger, label, route,
                trip));
            return this;
        }

        TransitScenario build() {
            return new TransitScenario(id, name, focus, startTick, system, stops, vehicles,
                bookings);
        }
    }
}
