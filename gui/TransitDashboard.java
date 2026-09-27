import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.Timer;

/** The supplied dashboard: scenario choice, playback controls, and backend evidence. */
public final class TransitDashboard extends JPanel {
    private static final int NORMAL_STEP_DELAY_MS = 200;
    private static final int ROAD_REFRESH_DELAY_MS = 100;
    private final Timer timer;
    private final Timer roadRefreshTimer;
    private final TransitCanvas canvas;
    private final JLabel heading = new JLabel();
    private final JLabel focus = new JLabel();
    private final JButton playButton = new JButton("Play");
    private final JButton pauseButton = new JButton("Pause");
    private final JSlider speedSlider = new JSlider(0, 3, 0);
    private final JTextArea clock = new JTextArea();
    private final JTextArea vehicles = new JTextArea();
    private final JComboBox<String> ticketChoice = new JComboBox<>();
    private final JTextArea fareStages = new JTextArea();
    private final JTextArea passengers = new JTextArea();
    private final JTextArea totals = new JTextArea();
    private final JTextArea eventLog = new JTextArea();
    private final JLabel checkCode = new JLabel();
    private TransitPlayback playback;
    private boolean updatingSlider;
    private boolean updatingTickets;

    public TransitDashboard() { this(TransitScenarios.cityCircle()); }

    TransitDashboard(TransitScenario initialScenario) {
        super(new BorderLayout(10, 10));
        if (initialScenario == null) throw new IllegalArgumentException("scenario required");
        playback = new TransitPlayback(initialScenario);
        canvas = new TransitCanvas(playback);
        timer = new Timer(NORMAL_STEP_DELAY_MS, event -> advanceTimer());
        timer.setCoalesce(true);
        roadRefreshTimer = new Timer(ROAD_REFRESH_DELAY_MS, event -> refreshRoadFade());
        roadRefreshTimer.setCoalesce(true);
        buildInterface();
        updateControlState();
        refreshPresentation();
    }

    private void buildInterface() {
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 18f));
        top.add(heading);
        top.add(focus);
        add(top, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);

        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.add(sectionLabel("Scenarios (1-4 are used in the viva)"));
        for (String id : TransitScenarios.ids()) addScenarioButton(side, id);
        for (String problem : TransitScenarios.problems()) {
            System.err.println("Scenario skipped: " + problem);
        }
        side.add(Box.createVerticalStrut(8));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        controls.setAlignmentX(Component.LEFT_ALIGNMENT);
        playButton.setName("play");
        playButton.addActionListener(event -> play());
        pauseButton.setName("pause");
        pauseButton.addActionListener(event -> pause());
        JButton step = namedButton("Step", "step");
        step.setToolTipText("Pause, then perform exactly one tick");
        step.addActionListener(event -> stepOnce());
        JButton reset = namedButton("Reset", "reset");
        reset.addActionListener(event -> reset());
        controls.add(playButton);
        controls.add(pauseButton);
        controls.add(step);
        controls.add(reset);
        side.add(controls);

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
        speedSlider.setAlignmentX(Component.LEFT_ALIGNMENT);
        speedSlider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 55));
        speedSlider.addChangeListener(event -> {
            if (!updatingSlider) selectSpeed(speedSlider.getValue(), true);
        });
        side.add(speedSlider);

        configureArea(clock, "clock", 2);
        configureArea(vehicles, "vehicles", 3);
        configureArea(fareStages, "fare-stages", 7);
        configureArea(passengers, "passengers", 7);
        configureArea(totals, "totals", 4);
        configureArea(eventLog, "event-log", 8);
        ticketChoice.setName("ticket-choice");
        ticketChoice.setAlignmentX(Component.LEFT_ALIGNMENT);
        ticketChoice.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        ticketChoice.addActionListener(event -> {
            if (!updatingTickets) showSelectedTicket();
        });

        addSection(side, "Clock", clock);
        addSection(side, "Vehicles (backend position and status)", vehicles);
        side.add(sectionLabel("Ticket fare stages"));
        side.add(ticketChoice);
        side.add(fareStages);
        side.add(Box.createVerticalStrut(6));
        addSection(side, "Passengers", new JScrollPane(passengers));
        addSection(side, "Totals", totals);
        addSection(side, "Event log", new JScrollPane(eventLog));
        checkCode.setName("check-code");
        checkCode.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(checkCode);

        JScrollPane sideScroll = new JScrollPane(side);
        sideScroll.setPreferredSize(new Dimension(400, 700));
        sideScroll.setBorder(BorderFactory.createEmptyBorder());
        add(sideScroll, BorderLayout.EAST);
    }

    private void addSection(JPanel side, String title, JComponent body) {
        side.add(sectionLabel(title));
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(body);
        side.add(Box.createVerticalStrut(6));
    }

    private void addScenarioButton(JPanel side, String id) {
        TransitScenario preview;
        try {
            preview = TransitScenarios.byId(id);
        } catch (IllegalArgumentException failure) {
            System.err.println("Scenario skipped: " + id + ": " + failure.getMessage());
            return;
        }
        JButton button = namedButton(preview.name(), "scenario-" + id);
        button.setToolTipText(preview.focus());
        button.addActionListener(event -> loadScenario(id));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(button);
        side.add(Box.createVerticalStrut(3));
    }

    private JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JButton namedButton(String text, String name) {
        JButton button = new JButton(text);
        button.setName(name);
        return button;
    }

    private void configureArea(JTextArea area, String name, int rows) {
        area.setName(name);
        area.setRows(rows);
        area.setColumns(34);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    public void loadScenario(String id) {
        TransitScenario scenario = TransitScenarios.byId(id);
        selectSpeed(0, false);
        roadRefreshTimer.stop();
        playback = new TransitPlayback(scenario);
        canvas.setPlayback(playback);
        updateControlState();
        refreshPresentation();
    }

    public void play() {
        if (playback.running()) return;
        selectSpeed(1, true);
    }

    public void pause() {
        selectSpeed(0, false);
        refreshPresentation();
        canvas.repaint();
    }

    /** Pauses playback and performs exactly one backend tick, for step-by-step explanation. */
    public void stepOnce() {
        selectSpeed(0, false);
        if (playback.complete() || !playback.errorMessage().isEmpty()) return;
        playback.start();
        playback.step(System.nanoTime());
        playback.pause();
        refreshPresentation();
        canvas.repaint();
        updateRoadRefreshTimer();
        updateControlState();
    }

    public void reset() { loadScenario(playback.scenario().id()); }

    public void setPlaybackMultiplier(int multiplier) {
        int value = switch (multiplier) {
            case 1 -> 1;
            case 2 -> 2;
            case 4 -> 3;
            default -> throw new IllegalArgumentException("speed must be 1, 2, or 4");
        };
        selectSpeed(value, true);
    }

    private void selectSpeed(int value, boolean startPlayback) {
        if (value < 0 || value > 3) {
            throw new IllegalArgumentException("slider speed must be between 0 and 3");
        }
        if (updatingSlider) return;
        if (value != 0 && (playback.complete() || !playback.errorMessage().isEmpty())) {
            value = 0;
        }
        updatingSlider = true;
        try {
            timer.stop();
            if (speedSlider.getValue() != value) speedSlider.setValue(value);
            if (value == 0) {
                playback.pause();
                updateRoadRefreshTimer();
            } else {
                roadRefreshTimer.stop();
                int delay = NORMAL_STEP_DELAY_MS >> (value - 1);
                timer.setInitialDelay(delay);
                timer.setDelay(delay);
                if (startPlayback) playback.start();
                if (playback.running()) timer.start();
            }
            updateControlState();
        } finally {
            updatingSlider = false;
        }
    }

    private void updateControlState() {
        boolean canPlay = !playback.running() && !playback.complete()
            && playback.errorMessage().isEmpty();
        playButton.setEnabled(canPlay);
        pauseButton.setEnabled(playback.running());
    }

    private void advanceTimer() {
        if (playback.running()) playback.step(System.nanoTime());
        refreshPresentation();
        canvas.repaint();
        if (playback.complete() || !playback.errorMessage().isEmpty()) {
            selectSpeed(0, false);
        } else {
            updateControlState();
        }
    }

    private void updateRoadRefreshTimer() {
        if (playback.hasFadingRoads(System.nanoTime())) {
            roadRefreshTimer.start();
        } else {
            roadRefreshTimer.stop();
        }
    }

    private void refreshRoadFade() {
        canvas.repaint();
        if (!playback.hasFadingRoads(System.nanoTime())) roadRefreshTimer.stop();
    }

    private void refreshPresentation() {
        TransitScenario scenario = playback.scenario();
        heading.setText(scenario.name());
        focus.setText(scenario.focus());
        checkCode.setText("Scenario check: " + scenario.checkCode());
        clock.setText("Tick " + playback.currentTick() + " (step " + playback.stepNumber() + ")\n"
                + "Day " + playback.day() + ": " + (playback.holiday() ? "public holiday" : "normal day")
                + (playback.complete() ? "  [complete]" : ""));

        StringBuilder vehicleText = new StringBuilder();
        for (TransitPlayback.VehicleView vehicle : playback.vehicles()) {
            if (vehicleText.length() > 0) vehicleText.append('\n');
            vehicleText.append(vehicle.mode()).append(' ').append(vehicle.label())
                .append(": position ").append((int) vehicle.position())
                .append(", ").append(vehicle.status());
        }
        vehicles.setText(vehicleText.toString());

        refreshTicketChoice();
        showSelectedTicket();

        List<String> summaries = playback.passengerSummaries();
        passengers.setText(summaries.isEmpty() ? "No tickets yet." : String.join("\n", summaries));
        totals.setText(
                "Tickets: " + playback.ticketCount() + "   Arrived: " + playback.arrivedCount() + "\n"
                + "Revenue: " + TransitPlayback.money(playback.revenueCents()) + "\n"
                + "Holiday savings: " + TransitPlayback.money(playback.holidaySavingsCents()) + "\n"
                + "Passenger savings: " + TransitPlayback.money(playback.passengerSavingsCents()));
        eventLog.setText(String.join("\n", playback.logLines()));
        eventLog.setCaretPosition(eventLog.getDocument().getLength());
    }

    private void refreshTicketChoice() {
        List<Ticket> tickets = playback.tickets();
        if (ticketChoice.getItemCount() == tickets.size()) return;
        boolean followLatest = ticketChoice.getSelectedIndex() < 0
                || ticketChoice.getSelectedIndex() == ticketChoice.getItemCount() - 1;
        updatingTickets = true;
        try {
            List<String> items = new ArrayList<>();
            for (Ticket ticket : tickets) items.add(ticket.id() + " - " + ticket.passenger().name());
            int keep = ticketChoice.getSelectedIndex();
            ticketChoice.removeAllItems();
            for (String item : items) ticketChoice.addItem(item);
            if (!items.isEmpty()) {
                ticketChoice.setSelectedIndex(followLatest || keep >= items.size()
                        ? items.size() - 1 : keep);
            }
        } finally {
            updatingTickets = false;
        }
    }

    private void showSelectedTicket() {
        int index = ticketChoice.getSelectedIndex();
        List<Ticket> tickets = playback.tickets();
        if (index < 0 || index >= tickets.size()) {
            fareStages.setText("No ticket yet. Press Play or Step.");
            return;
        }
        Ticket t = tickets.get(index);
        fareStages.setText(
                "Ticket " + t.id() + ", " + t.passenger().name() + "\n"
                + "Trip: " + t.trip() + "\n"
                + "Day " + t.day() + (t.holiday() ? " (public holiday)" : " (normal day)") + "\n"
                + "1. Base fare:        " + TransitPlayback.money(t.baseFareCents()) + "\n"
                + "2. Day fare:         " + TransitPlayback.money(t.dayFareCents())
                + "  [" + TransitPlayback.dayRule(t) + "]\n"
                + "3. Passenger fare:   " + TransitPlayback.money(t.finalFareCents())
                + "  [" + playback.labelOf(t.passenger()) + "]\n"
                + "Paid: " + TransitPlayback.money(t.finalFareCents()));
    }

    public TransitPlayback playback() { return playback; }
    public TransitCanvas canvas() { return canvas; }
    boolean animationRunning() { return timer.isRunning(); }
    int animationDelayMillis() { return timer.getDelay(); }

    /** All evidence text currently shown in the side panel. */
    public String visibleEvidenceText() {
        refreshPresentation();
        return clock.getText() + "\n" + vehicles.getText() + "\n" + fareStages.getText() + "\n"
                + passengers.getText() + "\n" + totals.getText() + "\n" + eventLog.getText()
                + "\n" + checkCode.getText();
    }

    /** Selects a ticket by its position in booking order. */
    public void selectTicket(int index) {
        refreshPresentation();
        ticketChoice.setSelectedIndex(index);
        showSelectedTicket();
    }
}
