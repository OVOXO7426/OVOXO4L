import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Starts the live GUI on a backend that compiles: java -cp out TransitGui */
public final class TransitGui {
    private TransitGui() {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Assignment 2 Fare Time");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(new TransitDashboard());
            frame.pack();
            frame.setLocationByPlatform(true);
            frame.setVisible(true);
        });
    }
}
