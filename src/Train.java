public class Train extends Vehicle {
    // Kept per object so every train follows its own move-then-dwell cycle.
    private boolean dwelling;

    public Train(String id) {
        super(id);
    }

    @Override
    public void advance() {
        if (dwelling) {
            status = VehicleStatus.IN_SERVICE;
        } else {
            position += 4;
            status = VehicleStatus.AT_STOP;
        }
        dwelling = !dwelling;
    }
}
