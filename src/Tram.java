public class Tram extends Vehicle {
    // Kept per object so every tram follows its own approach-then-run cycle.
    private boolean approaching;

    public Tram(String id) {
        super(id);
    }

    @Override
    public void advance() {
        if (approaching) {
            position += 2;
            status = VehicleStatus.IN_SERVICE;
        } else {
            position += 1;
            status = VehicleStatus.APPROACHING;
        }
        approaching = !approaching;
    }
}
