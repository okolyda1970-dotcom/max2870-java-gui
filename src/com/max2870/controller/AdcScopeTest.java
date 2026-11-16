package com.max2870.controller;

import com.fazecast.jSerialComm.SerialPort;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

public class AdcScopeTest extends JFrame {
    private SerialPort port;
    private OutputStream out;
    private InputStream in;
    private Thread reader;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile boolean streaming = false;

    private JComboBox<String> cbPorts;
    private JButton btnConn, btnStart, btnSave;
    private JSpinner spFs, spN, spFps;
    private JLabel lblInfo;
    private JTextArea log;
    private WavePanel wave;

    private short[] frame = new short[0];
    private double fs = 14000;

    public AdcScopeTest() {
        super("ADC Scope Test — проверка синусоиды");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);
        buildUi();
        refreshPorts();
    }

    private void buildUi() {
        setLayout(new BorderLayout(6, 6));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        top.add(new JLabel("Port:"));
        cbPorts = new JComboBox<>();
        cbPorts.setPreferredSize(new Dimension(130, 26));
        top.add(cbPorts);
        JButton bRef = new JButton("Refresh");
        bRef.addActionListener(e -> refreshPorts());
        top.add(bRef);
        btnConn = new JButton("Connect");
        btnConn.addActionListener(e -> toggleConn());
        top.add(btnConn);
        top.add(Box.createHorizontalStrut(15));
        top.add(new JLabel("fs (Hz):"));
        spFs = new JSpinner(new SpinnerNumberModel(14000, 1000, 17000, 500));
        spFs.setPreferredSize(new Dimension(85, 26));
        top.add(spFs);
        top.add(new JLabel("N:"));
        spN = new JSpinner(new SpinnerNumberModel(250, 20, 250, 10));
        spN.setPreferredSize(new Dimension(70, 26));
        top.add(spN);
        top.add(new JLabel("FPS:"));
        spFps = new JSpinner(new SpinnerNumberModel(10, 2, 20, 1));
        spFps.setPreferredSize(new Dimension(60, 26));
        top.add(spFps);
        btnStart = new JButton("▶ START");
        btnStart.setEnabled(false);
        btnStart.addActionListener(e -> toggleStream());
        top.add(btnStart);
        btnSave = new JButton("Save CSV");
        btnSave.setEnabled(false);
        btnSave.addActionListener(e -> saveCsv());
        top.add(btnSave);
        add(top, BorderLayout.NORTH);

        wave = new WavePanel();
        add(wave, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(4, 4));
        lblInfo = new JLabel(" — ");
        lblInfo.setFont(new Font("Monospaced", Font.BOLD, 13));
        lblInfo.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        bottom.add(lblInfo, BorderLayout.NORTH);
        log = new JTextArea(8, 60);
        log.setEditable(false);
        log.setFont(new Font("Monospaced", Font.PLAIN, 11));
        bottom.add(new JScrollPane(log), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    private void refreshPorts() {
        cbPorts.removeAllItems();
        for (SerialPort p : SerialPort.getCommPorts()) cbPorts.addItem(p.getSystemPortName());
    }

    private void toggleConn() {
        if (port != null && port.isOpen()) disconnect();
        else connect();
    }

    private void connect() {
        String name = (String) cbPorts.getSelectedItem();
        if (name == null) return;
        port = SerialPort.getCommPort(name);
        port.setComPortParameters(115200, 8, 1, SerialPort.NO_PARITY);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 100);
        if (!port.openPort()) { log("Не удалось открыть " + name); return; }
        try { port.clearDTR(); port.clearRTS(); } catch (Exception ignored) {}
        out = port.getOutputStream();
        in = port.getInputStream();
        running.set(true);
        startReader();
        btnConn.setText("Disconnect");
        btnStart.setEnabled(true);
        log("Connected " + name);
    }

    private void disconnect() {
        running.set(false);
        streaming = false;
        try { if (reader != null) reader.join(800); } catch (Exception ignored) {}
        try { port.clearDTR(); port.clearRTS(); } catch (Exception ignored) {}
        if (port != null) port.closePort();
        btnConn.setText("Connect");
        btnStart.setEnabled(false);
        btnStart.setText("▶ START");
        log("Disconnected");
    }

    private void toggleStream() {
        if (streaming) {
            try { out.write(0xDD); out.flush(); } catch (Exception e) { log("stop err: " + e); }
            streaming = false;
            btnStart.setText("▶ START");
            log(">> STOP");
        } else {
            fs = ((Number) spFs.getValue()).intValue();
            int n = ((Number) spN.getValue()).intValue();
            int fps = ((Number) spFps.getValue()).intValue();
            wave.fs = fs;
            sendText(String.format(java.util.Locale.US, "SCOPE_CFG:%d,%d,%d\n", (int) fs, n, 1000 / fps));
            try { Thread.sleep(120); } catch (Exception ignored) {}
            sendCtrl(-1.0f);
            streaming = true;
            btnStart.setText("⏹ STOP");
            btnSave.setEnabled(true);
            log(">> START fs=" + (int) fs + " N=" + n + " FPS=" + fps);
        }
    }

    private void sendText(String s) {
        try { out.write(s.getBytes()); out.flush(); } catch (Exception e) { log("tx err: " + e); }
    }

    private void sendCtrl(float freq) {
        try {
            ByteBuffer bb = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
            bb.put((byte) 0xAA);
            bb.putFloat(freq);
            for (int i = 5; i < 20; i++) bb.put((byte) 0);
            byte[] a = bb.array();
            long sum = 0;
            for (int i = 0; i < 20; i++) sum += a[i] & 0xFF;
            bb.putInt(20, (int) sum);
            out.write(a); out.flush();
        } catch (Exception e) { log("ctrl err: " + e); }
    }

    private void startReader() {
        reader = new Thread(() -> {
            byte[] buf = new byte[2048];
            byte[] pkt = new byte[1024];
            int idx = 0, need = 0;
            StringBuilder txt = new StringBuilder();
            while (running.get() && port.isOpen()) {
                try {
                    int av = in.available();
                    if (av <= 0) { Thread.sleep(3); continue; }
                    int rd = in.read(buf, 0, Math.min(av, buf.length));
                    for (int i = 0; i < rd; i++) {
                        byte b = buf[i];
                        if (streaming) {
                            if (idx == 0) {
                                if ((b & 0xFF) == 0xCC) pkt[idx++] = b;
                            } else if (idx == 1) {
                                int cnt = b & 0xFF;
                                need = 2 + cnt * 2;
                                if (cnt < 20 || cnt > 250 || need > pkt.length) { idx = 0; need = 0; continue; }
                                pkt[idx++] = b;
                            } else {
                                pkt[idx++] = b;
                                if (idx >= need) { handleFrame(pkt, need); idx = 0; need = 0; }
                            }
                        } else {
                            char c = (char) (b & 0xFF);
                            if (c == '\n' || c == '\r') {
                                if (txt.length() > 0) { log(txt.toString().trim()); txt.setLength(0); }
                            } else {
                                txt.append(c);
                                if (txt.length() > 200) txt.setLength(0);
                            }
                        }
                    }
                } catch (Exception e) {
                    if (running.get()) log("read err: " + e.getMessage());
                    break;
                }
            }
        }, "ADC-Reader");
        reader.setDaemon(true);
        reader.start();
    }

    private void handleFrame(byte[] raw, int len) {
        int cnt = raw[1] & 0xFF;
        short[] s = new short[cnt];
        int off = 2, bad = 0;
        for (int i = 0; i < cnt; i++) {
            if (off + 1 >= len) return;
            int lo = raw[off++] & 0xFF, hi = raw[off++] & 0xFF;
            int v = (hi << 8) | lo;
            if (v > 4095) bad++;
            s[i] = (short) v;
        }
        if (bad > cnt / 10) return;
        frame = s;
        analyze(s);
    }

    private void analyze(short[] s) {
        int mn = 4095, mx = 0;
        long sum = 0;
        for (short v : s) { int x = v & 0xFFFF; if (x < mn) mn = x; if (x > mx) mx = x; sum += x; }
        double mean = sum / (double) s.length;
        int pp = mx - mn;

        // частота по переходам через среднее (восходящие)
        int cross = 0;
        for (int i = 1; i < s.length; i++) {
            double a = (s[i - 1] & 0xFFFF) - mean, b = (s[i] & 0xFFFF) - mean;
            if (a <= 0 && b > 0) cross++;
        }
        double fZero = cross * fs / (s.length - 1);

        // FFT 512 с окном Ханна
        int N = 512;
        double[] re = new double[N], im = new double[N];
        for (int i = 0; i < N; i++) {
            double w = 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (N - 1));
            re[i] = (i < s.length ? ((s[i] & 0xFFFF) - mean) : 0) * w;
        }
        fft(re, im);
        int bi = 1; double bv = -1;
        for (int i = 1; i < N / 2; i++) {
            double m = re[i] * re[i] + im[i] * im[i];
            if (m > bv) { bv = m; bi = i; }
        }
        double fFft = bi * fs / N;

        final String info = String.format(java.util.Locale.US,
                "N=%d  pp=%d  DC=%.0f  min=%d max=%d  |  f(zero)=%.1f Hz   f(FFT)=%.1f Hz   fs=%.0f",
                s.length, pp, mean, mn, mx, fZero, fFft, fs);
        SwingUtilities.invokeLater(() -> {
            lblInfo.setText(info);
            wave.frame = s.clone();
            wave.mean = mean;
            wave.repaint();
        });
    }

    private void fft(double[] re, double[] im) {
        int n = re.length;
        int j = 0;
        for (int i = 0; i < n - 1; i++) {
            if (i < j) { double t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t; }
            int k = n >> 1;
            while (k >= 1 && j >= k) { j -= k; k >>= 1; }
            j += k;
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            double wr = Math.cos(ang), wi = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cr = 1, ci = 0;
                for (int k = 0; k < len / 2; k++) {
                    int u = i + k, v = i + k + len / 2;
                    double tr = cr * re[v] - ci * im[v];
                    double ti = cr * im[v] + ci * re[v];
                    re[v] = re[u] - tr; im[v] = im[u] - ti;
                    re[u] += tr; im[u] += ti;
                    double t = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr; cr = t;
                }
            }
        }
    }

    private void saveCsv() {
        if (frame.length == 0) return;
        JFileChooser fc = new JFileChooser();
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try (PrintWriter pw = new PrintWriter(fc.getSelectedFile())) {
            pw.println("index,time_us,adc");
            for (int i = 0; i < frame.length; i++) {
                pw.printf(java.util.Locale.US, "%d,%.2f,%d%n", i, i * 1e6 / fs, frame[i] & 0xFFFF);
            }
            log("Saved " + frame.length + " samples");
        } catch (Exception e) { log("save err: " + e); }
    }

    private void log(String s) {
        SwingUtilities.invokeLater(() -> {
            log.append(s + "\n");
            log.setCaretPosition(log.getDocument().getLength());
        });
    }

    private static class WavePanel extends JPanel {
        short[] frame = new short[0];
        double mean = 2048, fs = 14000;

        WavePanel() { setBackground(Color.BLACK); }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setColor(new Color(35, 35, 35));
            for (int i = 1; i < 10; i++) g2.drawLine(i * w / 10, 0, i * w / 10, h);
            for (int i = 1; i < 8; i++) g2.drawLine(0, i * h / 8, w, i * h / 8);
            g2.setColor(new Color(90, 90, 90));
            g2.drawLine(0, h / 2, w, h / 2);

            if (frame.length == 0) {
                g2.setColor(Color.GRAY);
                g2.drawString("нет кадров — нажмите START", w / 2 - 90, h / 2);
                return;
            }
            int mn = 4095, mx = 0;
            for (short v : frame) { int x = v & 0xFFFF; if (x < mn) mn = x; if (x > mx) mx = x; }
            double span = Math.max(20, mx - mn);
            double scale = (h * 0.8) / span;

            g2.setColor(Color.GREEN);
            g2.setStroke(new BasicStroke(1.8f));
            Polygon p = new Polygon();
            for (int i = 0; i < frame.length; i++) {
                int x = (int) ((long) i * w / (frame.length - 1));
                int y = (int) (h / 2 - ((frame[i] & 0xFFFF) - mean) * scale);
                p.addPoint(x, y);
            }
            g2.drawPolyline(p.xpoints, p.ypoints, p.npoints);

            g2.setColor(Color.YELLOW);
            g2.setFont(new Font("Monospaced", Font.BOLD, 12));
            g2.drawString(String.format(java.util.Locale.US, "окно %.2f мс | pp=%d | авто-масштаб",
                    frame.length / fs * 1000, mx - mn), 8, 16);
        }
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new AdcScopeTest().setVisible(true));
    }
}