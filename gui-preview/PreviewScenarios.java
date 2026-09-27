import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/*
 * Scenario 1 is written in code; every other scenario is read from the scenarios/ folder by
 * loader/ScenarioFile.java, the same files the live GUI uses.
 */
public final class PreviewScenarios {
    private PreviewScenarios() {}

    public static List<PreviewScenario> all() {
        List<PreviewScenario> result = new ArrayList<>();
        result.add(cityCircle());
        List<String> problems = new ArrayList<>();
        Path directory = Path.of(System.getProperty("transit.scenarios", "scenarios"));
        for (ScenarioFile data : ScenarioFile.readAll(directory, problems)) {
            try {
                if (!data.id().equals("city")) result.add(fromFile(data));
            } catch (IllegalArgumentException failure) {
                problems.add(data.source().getFileName() + ": " + failure.getMessage());
            }
        }
        for (String problem : problems) System.err.println("Scenario skipped: " + problem);
        return result;
    }

    public static PreviewScenario byId(String id) {
        for (PreviewScenario scenario : all()) {
            if (scenario.id().equals(id)) return scenario;
        }
        throw new IllegalArgumentException("unknown preview scenario: " + id);
    }

    static PreviewScenario fromFile(ScenarioFile data) {
        List<PreviewScenario.StopPlacement> stops = new ArrayList<>();
        for (ScenarioFile.Stop stop : data.stops()) {
            stops.add(stop(stop.name(), stop.x(), stop.y(), ServiceMode.valueOf(stop.mode())));
        }
        List<PreviewScenario.VehiclePlacement> vehicles = new ArrayList<>();
        for (ScenarioFile.VehicleSpec vehicle : data.vehicles()) {
            vehicles.add(vehicle(vehicle.id(), vehicle.stop(), ServiceMode.valueOf(vehicle.mode())));
        }
        List<PreviewScenario.Route> routes = new ArrayList<>();
        Set<List<String>> seen = new LinkedHashSet<>();
        for (ScenarioFile.Booking booking : data.bookings()) {
            List<String> route = booking.route();
            List<String> reversed = new ArrayList<>(route);
            Collections.reverse(reversed);
            if (seen.contains(route) || seen.contains(reversed)) continue;
            seen.add(route);
            ServiceMode mode = ServiceMode.TRAIN;
            for (ScenarioFile.Stop stop : data.stops()) {
                if (stop.name().equals(route.get(0))) mode = ServiceMode.valueOf(stop.mode());
            }
            routes.add(new PreviewScenario.Route(route, mode));
        }
        return new PreviewScenario(data.id(), data.name(), data.focus(), stops, vehicles, routes);
    }

    private static PreviewScenario cityCircle() {
        return new PreviewScenario(
            "city", "1. City Circle - normal day",
            "One adult makes one train trip on 2 January (a normal day): the full fare path "
                + "and the Train's move-then-dwell pattern.",
            List.of(
                stop("Central", 5, 18, ServiceMode.TRAIN),
                stop("Town Hall", 11, 14, ServiceMode.TRAIN),
                stop("Wynyard", 17, 10, ServiceMode.TRAIN),
                stop("Circular Quay", 23, 6, ServiceMode.TRAIN)),
            List.of(vehicle("City 1", "Central", ServiceMode.TRAIN)),
            List.of(route(ServiceMode.TRAIN,
                "Central", "Town Hall", "Wynyard", "Circular Quay")));
    }

    private static PreviewScenario.StopPlacement stop(
            String label, int x, int y, ServiceMode mode) {
        return new PreviewScenario.StopPlacement(
            label, new PreviewScenario.Point(x, y), mode);
    }

    private static PreviewScenario.VehiclePlacement vehicle(
            String label, String stopLabel, ServiceMode mode) {
        return new PreviewScenario.VehiclePlacement(label, stopLabel, mode);
    }

    private static PreviewScenario.Route route(ServiceMode mode, String... stopLabels) {
        return new PreviewScenario.Route(List.of(stopLabels), mode);
    }
}
