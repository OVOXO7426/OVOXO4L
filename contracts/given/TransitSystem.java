// SUPPLIED - do not modify or submit.
import java.util.List;

public interface TransitSystem {
    void addVehicle(Vehicle vehicle);

    Ticket book(Passenger passenger, Trip trip);

    void tick();

    int currentTick();

    int day();

    boolean isHoliday();

    List<Ticket> tickets();

    long revenueCents();
}
