public class ConcessionPassenger extends Passenger {
    private static final String[] VALID_TYPES = {"A", "D", "V", "AD", "AV", "DV", "ADV"};
    private static final int AGED_MIN_AGE = 65;

    private final String concessionType;
    private final int age;

    public ConcessionPassenger(String name, String concessionType, int age) {
        super(name);
        if (!isValidType(concessionType)) {
            throw new IllegalArgumentException("invalid concession type: " + concessionType);
        }
        if (age < 0) {
            throw new IllegalArgumentException("age must not be negative: " + age);
        }
        this.concessionType = concessionType;
        this.age = age;
    }

    public String concessionType() {
        return concessionType;
    }

    public int age() {
        return age;
    }

    public boolean isEligible() {
        boolean aged = concessionType.contains("A") && age >= AGED_MIN_AGE;
        return aged || concessionType.contains("D") || concessionType.contains("V");
    }

    @Override
    public long applyDiscount(long fareCents) {
        if (isEligible()) {
            return fareCents / 2;
        }
        return fareCents;
    }

    // Exact match against the list rejects null, lower case, wrong order and repeats.
    private static boolean isValidType(String type) {
        for (String valid : VALID_TYPES) {
            if (valid.equals(type)) {
                return true;
            }
        }
        return false;
    }
}
