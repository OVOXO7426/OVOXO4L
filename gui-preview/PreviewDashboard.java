import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.Hashtable;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;

/** Final-like static shell shown while the student's backend is incomplete. */
public final class PreviewDashboard extends JPanel {
    private final PreviewCanvas canvas;
    private final JTextArea fareBreakdown = new JTextArea();
    private final JTextArea totals = new JTextArea();
    private final JTextArea eventLog = new JTextArea();
    private final String status;
    private PreviewScenario scenario;

    public PreviewDashboard(String status) {
        super(new BorderLayout(10, 10));
        this.status = status == null || status.isBlank()
            ? "Backend did not compile; see terminal diagnostics."
            : status.trim();
        scenario = PreviewScenarios.byId("city");
        canvas = new PreviewCanvas(scenario);
        buildInterface();
        refreshEvidence();
    }

    private void buildInterface() {
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JLabel heading = new JLabel("Assignment 2 Fare Time - preview (backend not running)");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 18f));
        add(heading, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);

        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setPreferredSize(new Dimension(350, 660));
        side.add(sectionLabel("Scenarios (1-4 are used in the viva)"));
        for (PreviewScenario preview : PreviewScenarios.all()) {
            addScenarioButton(side, preview.name(), "scenario-" + preview.id(), preview.id(),
                preview.description());
        }
        side.add(Box.createVerticalStrut(10));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        controls.setAlignmentX(Component.LEFT_ALIGNMENT);
        controls.add(disabledButton("Play", "play"));
        controls.add(disabledButton("Pause", "pause"));
        controls.add(disabledButton("Reset", "reset"));
        side.add(controls);
        side.add(Box.createVerticalStrut(6));

        JSlider speedSlider = new JSlider(0, 3, 0);
        speedSlider.setName("speed-slider");
        speedSlider.setMajorTickSpacing(1);
        speedSlider.setSnapToTicks(true);
        speedSlider.setPaintTicks(true);
        speedSlider.setPaintLabels(true);
        Hashtable<Integer, JLabel> labels = new Hashtable<>();
        labels.put(0, new JLabel("Paused"));
        labels.put(1, new JLabel("1x"));
        labels.put(2, new JLabel("2x"));
        labels.put(3, new JLabel("4x"));
        speedSlider.setLabelTable(labels);
        speedSlider.setEnabled(false);
        speedSlider.setAlignmentX(Component.LEFT_ALIGNMENT);
        speedSlider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 55));
        side.add(sectionLabel("Playback speed"));
        side.add(speedSlider);
        side.add(Box.createVerticalStrut(8));

        configureEvidenceArea(fareBreakdown, "fare-breakdown", 7);
        configureEvidenceArea(totals, "totals", 4);
        configureEvidenceArea(eventLog, "event-log", 10);
        side.add(sectionLabel("Ticket fare stages"));
        side.add(new JScrollPane(fareBreakdown));
        side.add(Box.createVerticalStrut(8));
        side.add(sectionLabel("Scenario totals"));
        side.add(totals);
        side.add(Box.createVerticalStrut(8));
        side.add(sectionLabel("Event log"));
        side.add(new JScrollPane(eventLog));
        add(side, BorderLayout.EAST);
    }

    private void addScenarioButton(JPanel side, String text, String name, String id,
                                   String tooltip) {
        JButton button = new JButton(text);
        button.setName(name);
        button.setToolTipText(tooltip);
        button.addActionListener(event -> loadScenario(id));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(button);
        side.add(Box.createVerticalStrut(4));
    }

    private JButton disabledButton(String text, String name) {
        JButton button = new JButton(text);
        button.setName(name);
        button.setEnabled(false);
        return button;
    }

    private JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private void configureEvidenceArea(JTextArea area, String name, int rows) {
        area.setName(name);
        area.setRows(rows);
        area.setColumns(27);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    }

    public void loadScenario(String id) {
        scenario = PreviewScenarios.byId(id);
        canvas.setScenario(scenario);
    }

    private void refreshEvidence() {
        fareBreakdown.setText(
            "Backend implementation required\n"
            + "Implement and compile the backend to display ticket fares and discounts.");
        totals.setText("Unavailable in preview mode.");
        eventLog.setText(
            "PREVIEW MODE: no backend operations are running.\n"
            + status + "\n"
            + "Run python3 run_gui.py again after changing src/.");
    }

    PreviewScenario scenario() { return scenario; }
    PreviewCanvas canvas() { return canvas; }

    String visibleEvidenceText() {
        return fareBreakdown.getText() + "\n" + totals.getText() + "\n" + eventLog.getText();
    }
}
