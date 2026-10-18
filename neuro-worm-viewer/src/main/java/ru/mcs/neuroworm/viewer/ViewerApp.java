package ru.mcs.neuroworm.viewer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import ru.mcs.neuroworm.core.ConnectomeLoader;
import ru.mcs.neuroworm.core.Neuron;
import ru.mcs.neuroworm.core.NeuronType;
import ru.mcs.neuroworm.world.*;

public final class ViewerApp extends Application {
    private static final double ARENA = 200, SCALE = 3.0;
    private static final int COLS = 20, CELL = 16;
    private static final boolean SALT = "salt".equals(System.getProperty("scene", "chemotaxis"));

    private record Frame(double x, double y, double heading, int tick, double distance, String info) { }

    private final ConcurrentLinkedQueue<Integer> spikes = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<double[]> path = new ConcurrentLinkedQueue<>();
    private final AtomicReference<Frame> frame = new AtomicReference<>();
    private final java.util.Set<String> lesions = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile double speedFactor = 1.0;
    private volatile boolean paused, resetRequested, restartRequested, trainRequested, training;
    private volatile Scenario scenario;
    private volatile String hoverName = "";
    private double[] offsets;
    private long seed = 1;
    private Thread simThread;

    private final List<double[]> trail = new ArrayList<>();
    private double[] glow;
    private int[] cellOf;
    private Neuron[] byCell;
    private Color[] baseColor;
    private boolean[] matched = new boolean[0];
    private WritableImage fieldImage;

    @Override
    public void init() {
        Logger.getLogger(ConnectomeLoader.class.getName()).setLevel(Level.SEVERE);
        offsets = SALT ? SaltScenario.settle() : ChemotaxisScenario.settle();
        scenario = newScenario();
    }

    private Scenario newScenario() {
        Scenario s = SALT ? SaltScenario.create(offsets, seed++)
                : new ChemotaxisScenario(offsets, seed++, java.util.Set.of(), EfficiencyEvolution.EVOLVED);
        s.sim().setSpikeListener((t, i) -> {
            if (!training) spikes.add(i);
        });
        for (Neuron nn : s.net().neurons()) nn.lesioned = lesions.contains(nn.name);
        return s;
    }

    private void simLoop() {
        long next = System.nanoTime();
        int counter = 0;
        String info = "";
        while (!Thread.currentThread().isInterrupted()) {
            if (resetRequested) {
                resetRequested = false;
                scenario = newScenario();
                path.add(new double[] {Double.NaN});
            }
            if (restartRequested) {
                restartRequested = false;
                if (scenario instanceof SaltScenario ss) ss.restartRun(seed++);
                path.add(new double[] {Double.NaN});
            }
            if (trainRequested) {
                trainRequested = false;
                if (scenario instanceof SaltScenario ss) {
                    training = true;
                    ss.train(3, seed);
                    seed += 10;
                    training = false;
                }
                path.add(new double[] {Double.NaN});
            }
            if (paused) {
                LockSupport.parkNanos(20_000_000L);
                next = System.nanoTime();
                continue;
            }
            Scenario s = scenario;
            s.step();
            Worm w = s.worm();
            path.add(new double[] {w.x, w.y});
            if (counter++ % 50 == 0) info = s.info();
            frame.set(new Frame(w.x, w.y, w.heading, s.sim().tick(), s.distanceToTarget(), info));
            next += (long) (1e9 / (100.0 * speedFactor));
            long wait = next - System.nanoTime();
            if (wait > 0) LockSupport.parkNanos(wait);
            else if (wait < -100_000_000L) next = System.nanoTime();
        }
    }

    private int cellAt(double x, double y) {
        int c = (int) (y / CELL) * COLS + (int) (x / CELL);
        return c >= 0 && c < byCell.length ? c : -1;
    }

    private void setLesion(String name, boolean on) {
        if (on) lesions.add(name);
        else lesions.remove(name);
        Neuron live = scenario.net().get(name);
        if (live != null) live.lesioned = on;
    }

    private int updateMatches(String query) {
        matched = new boolean[byCell.length];
        int count = 0;
        for (String raw : query.split("[,;\\s]+")) {
            String token = raw.trim().toUpperCase();
            if (token.isEmpty()) continue;
            for (int c = 0; c < byCell.length; c++) {
                if (!matched[c] && byCell[c].name.toUpperCase().startsWith(token)) {
                    matched[c] = true;
                    count++;
                }
            }
        }
        return count;
    }

    private String matchedNames() {
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (int c = 0; c < matched.length; c++) {
            if (!matched[c]) continue;
            if (shown++ < 6) sb.append(sb.length() > 0 ? ", " : "").append(byCell[c].name);
        }
        return shown > 6 ? sb + ", ..." : sb.toString();
    }

    @Override
    public void start(Stage stage) {
        List<Neuron> ordered = new ArrayList<>(scenario.net().neurons());
        ordered.sort(Comparator.comparing((Neuron nn) -> nn.type).thenComparing(nn -> nn.name));
        int n = ordered.size();
        cellOf = new int[n];
        byCell = new Neuron[n];
        baseColor = new Color[n];
        glow = new double[n];
        matched = new boolean[n];
        for (int c = 0; c < n; c++) {
            Neuron nr = ordered.get(c);
            byCell[c] = nr;
            cellOf[nr.index] = c;
            baseColor[c] = nr.type == NeuronType.SENSORY ? Color.web("#1f4e8c")
                    : nr.type == NeuronType.MOTOR ? Color.web("#8c1f1f") : Color.web("#3a3a3a");
        }
        fieldImage = new WritableImage((int) ARENA, (int) ARENA);
        for (int x = 0; x < ARENA; x++) {
            for (int y = 0; y < ARENA; y++) {
                double conc = Math.min(1, scenario.fieldAt(x + 0.5, y + 0.5));
                fieldImage.getPixelWriter().setColor(x, y, SALT
                        ? Color.rgb(0, (int) (90 * conc), (int) (200 * conc))
                        : Color.rgb(0, (int) (170 * conc), 0));
            }
        }

        Canvas arenaCanvas = new Canvas(ARENA * SCALE, ARENA * SCALE);
        int rows = (int) Math.ceil(n / (double) COLS);
        Canvas gridCanvas = new Canvas(COLS * CELL, rows * CELL);

        gridCanvas.setOnMouseClicked(e -> {
            int c = cellAt(e.getX(), e.getY());
            if (c < 0) return;
            String name = byCell[c].name;
            setLesion(name, !lesions.contains(name));
        });
        gridCanvas.setOnMouseMoved(e -> {
            int c = cellAt(e.getX(), e.getY());
            hoverName = c < 0 ? "" : byCell[c].name + " (" + byCell[c].type + ")";
        });
        gridCanvas.setOnMouseExited(e -> hoverName = "");

        Button pause = new Button("Pause");
        pause.setOnAction(e -> {
            paused = !paused;
            pause.setText(paused ? "Resume" : "Pause");
        });
        Button reset = new Button(SALT ? "Reset (forget)" : "Reset");
        reset.setOnAction(e -> resetRequested = true);
        Button clear = new Button("Clear lesions");
        clear.setOnAction(e -> {
            for (String name : new ArrayList<>(lesions)) setLesion(name, false);
        });
        Slider speed = new Slider(0.1, 50, 1);
        speed.valueProperty().addListener((o, a, b) -> speedFactor = b.doubleValue());
        Label speedLabel = new Label();
        speedLabel.textProperty().bind(speed.valueProperty().asString("x%.1f"));
        Label status = new Label();
        HBox controls = new HBox(10, pause, reset);
        if (SALT) {
            Button train = new Button("Train (salt + reward)");
            train.setOnAction(e -> trainRequested = true);
            Button restart = new Button("Restart run");
            restart.setOnAction(e -> restartRequested = true);
            controls.getChildren().addAll(train, restart);
        }
        controls.getChildren().addAll(clear, new Label("Speed"), speed, speedLabel, status);

        TextField search = new TextField();
        search.setPromptText("AWC, AIY, ...");
        search.setPrefWidth(200);
        Label matchLabel = new Label("0 found");
        search.textProperty().addListener((o, a, b) -> {
            int cnt = updateMatches(b);
            matchLabel.setText(cnt + " found" + (cnt > 0 ? ": " + matchedNames() : ""));
        });
        Button lesionBtn = new Button("Lesion");
        lesionBtn.setOnAction(e -> {
            for (int c = 0; c < matched.length; c++) if (matched[c]) setLesion(byCell[c].name, true);
        });
        Button restoreBtn = new Button("Restore");
        restoreBtn.setOnAction(e -> {
            for (int c = 0; c < matched.length; c++) if (matched[c]) setLesion(byCell[c].name, false);
        });
        HBox searchRow = new HBox(10, new Label("Neuron:"), search, lesionBtn, restoreBtn, matchLabel);

        BorderPane root = new BorderPane(arenaCanvas);
        root.setRight(gridCanvas);
        root.setBottom(new VBox(6, controls, searchRow));
        stage.setScene(new Scene(root));
        stage.setTitle(SALT ? "neuro-worm: salt conditioning" : "neuro-worm");
        stage.show();

        GraphicsContext ag = arenaCanvas.getGraphicsContext2D();
        GraphicsContext gg = gridCanvas.getGraphicsContext2D();
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                drain();
                drawArena(ag);
                drawGrid(gg);
                Frame f = frame.get();
                if (f != null) {
                    status.setText(String.format("%stick %d | distance %.1f | %s | %s%s",
                            training ? "TRAINING... " : "", f.tick(), f.distance(), f.info(), hoverName,
                            lesions.isEmpty() ? "" : " | lesions: " + String.join(",", new java.util.TreeSet<>(lesions))));
                }
            }
        }.start();

        simThread = new Thread(this::simLoop, "sim");
        simThread.setDaemon(true);
        simThread.start();
    }

    private void drain() {
        Integer i;
        while ((i = spikes.poll()) != null) glow[cellOf[i]] = 1.0;
        double[] p;
        while ((p = path.poll()) != null) {
            if (Double.isNaN(p[0])) trail.clear();
            else trail.add(p);
        }
        if (trail.size() > 6000) trail.subList(0, 2000).clear();
    }

    private void drawArena(GraphicsContext g) {
        Scenario s = scenario;
        g.drawImage(fieldImage, 0, 0, ARENA * SCALE, ARENA * SCALE);
        g.setFill(SALT ? Color.CYAN : Color.LIMEGREEN);
        g.fillOval((s.targetX() - 4) * SCALE, (s.targetY() - 4) * SCALE, 8 * SCALE, 8 * SCALE);
        g.setStroke(Color.GOLD);
        g.setLineWidth(1.2);
        for (int k = 1; k < trail.size(); k++) {
            double[] a = trail.get(k - 1), b = trail.get(k);
            if (Math.abs(a[0] - b[0]) < ARENA / 2 && Math.abs(a[1] - b[1]) < ARENA / 2) {
                g.strokeLine(a[0] * SCALE, a[1] * SCALE, b[0] * SCALE, b[1] * SCALE);
            }
        }
        Frame f = frame.get();
        if (f != null) {
            g.setFill(Color.WHITE);
            g.fillOval((f.x() - 2) * SCALE, (f.y() - 2) * SCALE, 4 * SCALE, 4 * SCALE);
            g.setStroke(Color.RED);
            g.setLineWidth(2);
            g.strokeLine(f.x() * SCALE, f.y() * SCALE,
                    (f.x() + 6 * Math.cos(f.heading())) * SCALE, (f.y() + 6 * Math.sin(f.heading())) * SCALE);
        }
    }

    private void drawGrid(GraphicsContext g) {
        g.setFill(Color.BLACK);
        g.fillRect(0, 0, COLS * CELL, Math.ceil(glow.length / (double) COLS) * CELL);
        for (int c = 0; c < glow.length; c++) {
            double x = (c % COLS) * CELL, y = (c / COLS) * CELL;
            if (lesions.contains(byCell[c].name)) {
                g.setFill(Color.BLACK);
                g.fillRect(x + 1, y + 1, CELL - 2, CELL - 2);
                g.setStroke(Color.RED);
                g.setLineWidth(2);
                g.strokeLine(x + 3, y + 3, x + CELL - 3, y + CELL - 3);
                g.strokeLine(x + CELL - 3, y + 3, x + 3, y + CELL - 3);
                glow[c] = 0;
            } else {
                g.setFill(baseColor[c].interpolate(Color.WHITE, glow[c]));
                g.fillRect(x + 1, y + 1, CELL - 2, CELL - 2);
                glow[c] *= 0.7;
            }
            if (c < matched.length && matched[c]) {
                g.setStroke(Color.GOLD);
                g.setLineWidth(2);
                g.strokeRect(x + 1, y + 1, CELL - 2, CELL - 2);
            }
        }
    }

    @Override
    public void stop() {
        if (simThread != null) simThread.interrupt();
    }

    public static void main(String[] args) { launch(args); }
}