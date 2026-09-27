import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Native Swing entry point shared by the live and static scaffold modes. */
public final class ScaffoldGuiLauncher {
    private static final String USAGE =
        "Usage: ScaffoldGuiLauncher (--live|--preview) [--status-file PATH]";

    private ScaffoldGuiLauncher() {}

    public static void main(String[] args) {
        Arguments parsed;
        try {
            parsed = parse(args);
        } catch (IllegalArgumentException failure) {
            System.err.println(USAGE);
            System.exit(2);
            return;
        }

        String status = "live".equals(parsed.mode)
            ? "Backend compiled; starting live mode."
            : "Preview mode: the backend is not loaded.";
        if (parsed.statusFile != null) {
            try {
                status = Files.readString(parsed.statusFile, StandardCharsets.UTF_8);
            } catch (IOException failure) {
                System.err.println("Could not read GUI status file: " + failure.getMessage());
                System.exit(2);
                return;
            }
        }

        String mode = parsed.mode;
        String shown = status;
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Assignment 2 Fare Time");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(createContent(mode, "TransitDashboard", shown));
            frame.pack();
            frame.setLocationByPlatform(true);
            frame.setVisible(true);
        });
    }

    static JPanel createContent(String mode, String dashboardClassName, String status) {
        if ("preview".equals(mode)) return new PreviewDashboard(status);
        if (!"live".equals(mode)) {
            throw new IllegalArgumentException("mode must be live or preview");
        }
        try {
            return Class.forName(dashboardClassName).asSubclass(JPanel.class)
                .getDeclaredConstructor().newInstance();
        } catch (InvocationTargetException failure) {
            VirtualMachineError fatal = fatalCause(failure);
            if (fatal != null) throw fatal;
            Throwable cause = rootCause(failure.getCause());
            cause.printStackTrace(System.err);
            return new PreviewDashboard(safeSummary(cause));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            VirtualMachineError fatal = fatalCause(failure);
            if (fatal != null) throw fatal;
            failure.printStackTrace(System.err);
            return new PreviewDashboard(safeSummary(rootCause(failure)));
        }
    }

    private static Throwable rootCause(Throwable failure) {
        if (failure == null) return new IllegalStateException("unknown startup failure");
        Throwable result = failure;
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        seen.add(result);
        while (result.getCause() != null && seen.add(result.getCause())) {
            result = result.getCause();
        }
        return result;
    }

    private static VirtualMachineError fatalCause(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = failure;
        while (current != null && seen.add(current)) {
            try {
                throw current;
            } catch (VirtualMachineError fatal) {
                return fatal;
            } catch (Throwable notFatal) {
                current = current.getCause();
            }
        }
        return null;
    }

    private static String safeSummary(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) return failure.getClass().getSimpleName();
        return failure.getClass().getSimpleName() + ": "
            + message.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static Arguments parse(String[] args) {
        if (args == null || (args.length != 1
                && (args.length != 3 || !"--status-file".equals(args[1])))) {
            throw new IllegalArgumentException("invalid arguments");
        }
        String mode = switch (args[0]) {
            case "--live" -> "live";
            case "--preview" -> "preview";
            default -> throw new IllegalArgumentException("invalid mode");
        };
        if (args.length == 1) return new Arguments(mode, null);
        if (args[2].isBlank()) throw new IllegalArgumentException("status file required");
        return new Arguments(mode, Path.of(args[2]));
    }

    private static final class Arguments {
        private final String mode;
        private final Path statusFile;

        Arguments(String mode, Path statusFile) {
            this.mode = mode;
            this.statusFile = statusFile;
        }
    }
}
