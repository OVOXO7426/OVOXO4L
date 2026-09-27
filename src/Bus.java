public class Bus extends Vehicle {
    public Bus(String id) {
        super(id);
    }

    @Override
    public void advance() {
        position += 1;
        status = VehicleStatus.IN_SERVICE;
    }
}
