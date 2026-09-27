import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads one scenario data file (*.scenario). This class only reads text: it creates no vehicles,
 * passengers or trips, so the GUI and Demo can each turn the data into objects their own way.
 */
public final class ScenarioFile {
    public static final String EXTENSION = ".scenario";

    public record Stop(String name, int x, int y, String mode) {}
    public record VehicleSpec(String className, String id, String stop, String mode) {}
    public record PassengerSpec(String key, String kind, String name, String concessionType,
                                int age) {}
    public record Booking(int step, String passengerKey, List<String> route) {}

    private final Path source;
    private String id;
    private String name;
    private String focus;
    private int start = -1;
    private final List<Stop> stops = new ArrayList<>();
    private final List<VehicleSpec> vehicles = new ArrayList<>();
    private final List<PassengerSpec> passengers = new ArrayList<>();
    private final List<Booking> bookings = new ArrayList<>();
    private final Map<String, Stop> stopsByName = new HashMap<>();
    private final Map<String, PassengerSpec> passengersByKey = new HashMap<>();

    private ScenarioFile(Path source) {
        this.source = source;
    }

    public static ScenarioFile read(Path file) throws IOException {
        ScenarioFile data = new ScenarioFile(file);
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            try {
                data.parseLine(line);
            } catch (RuntimeException failure) {
                throw new IllegalArgumentException(
                    file.getFileName() + " line " + (i + 1) + ": " + failure.getMessage());
            }
        }
        data.checkComplete();
        return data;
    }

    /** Reads every *.scenario file in a folder, in file-name order. Bad files go to problems. */
    public static List<ScenarioFile> readAll(Path directory, List<String> problems) {
        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(directory)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*" + EXTENSION)) {
                for (Path file : stream) files.add(file);
            } catch (IOException failure) {
                problems.add(directory + ": " + failure.getMessage());
            }
        } else {
            problems.add("scenario folder not found: " + directory.toAbsolutePath());
        }
        files.sort(null);
        List<ScenarioFile> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Path file : files) {
            try {
                ScenarioFile data = read(file);
                if (!ids.add(data.id())) {
                    problems.add(file.getFileName() + ": duplicate scenario id " + data.id());
                    continue;
                }
                result.add(data);
            } catch (IOException | RuntimeException failure) {
                problems.add(failure.getMessage());
            }
        }
        return result;
    }

    private void parseLine(String line) {
        int colon = line.indexOf(':');
        if (colon < 0) throw new IllegalArgumentException("expected 'key: value'");
        String key = line.substring(0, colon).strip();
        String value = line.substring(colon + 1).strip();
        switch (key) {
            case "id" -> id = text(value, "id");
            case "name" -> name = text(value, "name");
            case "focus" -> focus = text(value, "focus");
            case "start" -> start = number(value, "start");
            case "stop" -> addStop(fields(value, 4, 4));
            case "vehicle" -> addVehicle(fields(value, 4, 4));
            case "passenger" -> addPassenger(fields(value, 3, 5));
            case "book" -> addBooking(fields(value, 3, 3));
            default -> throw new IllegalArgumentException("unknown key: " + key);
        }
    }

    private void addStop(String[] f) {
        Stop stop = new Stop(text(f[0], "stop name"), number(f[1], "x"), number(f[2], "y"),
            text(f[3], "mode"));
        if (stopsByName.put(stop.name(), stop) != null) {
            throw new IllegalArgumentException("duplicate stop: " + stop.name());
        }
        stops.add(stop);
    }

    private void addVehicle(String[] f) {
        VehicleSpec vehicle = new VehicleSpec(text(f[0], "vehicle class"), text(f[1], "vehicle id"),
            knownStop(f[2]), text(f[3], "mode"));
        vehicles.add(vehicle);
    }

    private void addPassenger(String[] f) {
        String kind = text(f[1], "passenger kind");
        PassengerSpec passenger;
        if (kind.equals("adult") && f.length == 3) {
            passenger = new PassengerSpec(text(f[0], "passenger key"), kind, text(f[2], "name"),
                null, -1);
        } else if (kind.equals("concession") && f.length == 5) {
            passenger = new PassengerSpec(text(f[0], "passenger key"), kind, text(f[2], "name"),
                f[3].strip(), number(f[4], "age"));
        } else {
            throw new IllegalArgumentException(
                "expected 'key | adult | name' or 'key | concession | name | type | age'");
        }
        if (passengersByKey.put(passenger.key(), passenger) != null) {
            throw new IllegalArgumentException("duplicate passenger key: " + passenger.key());
        }
        passengers.add(passenger);
    }

    private void addBooking(String[] f) {
        int step = number(f[0], "step");
        if (step < 0) throw new IllegalArgumentException("step must be >= 0");
        String key = f[1].strip();
        if (!passengersByKey.containsKey(key)) {
            throw new IllegalArgumentException("unknown passenger key: " + key);
        }
        List<String> route = new ArrayList<>();
        for (String stop : f[2].split(">")) route.add(knownStop(stop));
        if (route.size() < 2) throw new IllegalArgumentException("a route needs two stops");
        bookings.add(new Booking(step, key, List.copyOf(route)));
    }

    private void checkComplete() {
        if (id == null || name == null || focus == null || start < 0) {
            throw new IllegalArgumentException(
                source.getFileName() + ": id, name, focus and start are required");
        }
    }

    private String knownStop(String value) {
        String stop = value.strip();
        if (!stopsByName.containsKey(stop)) throw new IllegalArgumentException("unknown stop: " + stop);
        return stop;
    }

    private static String[] fields(String value, int min, int max) {
        String[] parts = value.split("\\|", -1);
        if (parts.length < min || parts.length > max) {
            throw new IllegalArgumentException("expected " + min
                + (min == max ? "" : " to " + max) + " fields separated by |");
        }
        return parts;
    }

    private static String text(String value, String what) {
        String result = value.strip();
        if (result.isEmpty()) throw new IllegalArgumentException(what + " required");
        return result;
    }

    private static int number(String value, String what) {
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(what + " must be a whole number");
        }
    }

    /** The trip distance for a booking: the sum of horizontal and vertical steps along its route. */
    public int distance(Booking booking) {
        int total = 0;
        for (int i = 1; i < booking.route().size(); i++) {
            Stop from = stopsByName.get(booking.route().get(i - 1));
            Stop to = stopsByName.get(booking.route().get(i));
            total += Math.abs(from.x() - to.x()) + Math.abs(from.y() - to.y());
        }
        return total;
    }

    public PassengerSpec passenger(String key) { return passengersByKey.get(key); }

    public Path source() { return source; }
    public String id() { return id; }
    public String name() { return name; }
    public String focus() { return focus; }
    public int start() { return start; }
    public List<Stop> stops() { return List.copyOf(stops); }
    public List<VehicleSpec> vehicles() { return List.copyOf(vehicles); }
    public List<PassengerSpec> passengers() { return List.copyOf(passengers); }
    public List<Booking> bookings() { return List.copyOf(bookings); }
}
