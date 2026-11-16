package com.max2870.controller;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Max2870Gui extends JFrame implements EspConnection.ConnectionListener {
    private static final long serialVersionUID = 1L;
    private EspConnection connection;

    private JComboBox<String> comboPorts;
    private JButton btnConnect, btnRefresh, btnScopeToggle, btnMode;
    private JTextField txtCmd;

    private JTextField txtFreq;
    private JComboBox<Integer> cbPower, cbCpCurrent;
    private JComboBox<String> cbLdMode;
    private JButton btnApply;

    private JTextField txtSweepStart, txtSweepStop, txtSweepStep;
    private JButton btnSweepToggle;
    private boolean sweeping = false;
    private JSpinner spinIfHz, spinFsKhz, spinFps;
    private JSlider sldFloorDb, sldTopDb;
    private JLabel lblFloorDb, lblTopDb, lblBeat;
    private JCheckBox chkFftView, chkAutoZoom;
    private double lastPickedFreq = Double.NaN;
    private double actualFs = 14000;
    private double lastBeatHz = Double.NaN;
    private double tuneBaseLo, tuneBeatMHz;

    private JLabel lblStatus;
    private JTextArea textLog;
    private OscilloscopePanel scopePanel;
    private LevelPanel levelPanel;
    private SpectrumPanel spectrumPanel;
    private FFTProcessor fftProcessor;
    private JPanel viewCard;
    private CardLayout viewLayout;
    private boolean levelMode = false;

    private JSlider sldTimeDiv, sldVoltDiv, sldTrigger;
    private JSpinner spinPosX, spinPosY;
    private JButton btnPauseResume;
    private JCheckBox chkAutoScale;
    private JLabel lblBeatBig;

    private final List<double[]> sweepDataPoints = new ArrayList<>();

    private static final Font FIELD_FONT = new Font("Monospaced", Font.BOLD, 14);
    private static final Color BTN_OFF_BG = new Color(225, 225, 228);
    private static final Color BTN_OFF_FG = new Color(20, 20, 20);
    private static final Color BTN_ON_BG = new Color(0, 140, 0);
    private static final Color BTN_ON_FG = Color.WHITE;

    public Max2870Gui() {
        super("MAX2870 Controller & Scope/Spectrum via ESP32");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 1000);
        setMinimumSize(new Dimension(1080, 820));
        setLocationRelativeTo(null);
        connection = new EspConnection(this);
        fftProcessor = new FFTProcessor(512);
        initComponents();
        refreshPorts();
    }

    private JTextField makeFixedField(String value, int w, String tip) {
        JTextField f = new JTextField(value, 10);
        f.setFont(FIELD_FONT);
        f.setBackground(new Color(250, 250, 252));
        f.setForeground(new Color(20, 20, 40));
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(90, 120, 200), 1, true),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        Dimension d = new Dimension(w, 32);
        f.setPreferredSize(d);
        f.setMinimumSize(new Dimension(Math.max(80, w - 30), 28));
        f.setMaximumSize(d);
        f.setHorizontalAlignment(JTextField.RIGHT);
        f.setToolTipText(tip);
        return f;
    }

    private void styleToggle(JButton b, boolean on, String text) {
        b.setText(text);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setBackground(on ? BTN_ON_BG : BTN_OFF_BG);
        b.setForeground(on ? BTN_ON_FG : BTN_OFF_FG);
    }

    private void initComponents() {
        setLayout(new BorderLayout(5, 5));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        top.setBorder(new EmptyBorder(6, 8, 6, 8));
        top.add(new JLabel("COM Port:"));
        comboPorts = new JComboBox<>();
        comboPorts.setPreferredSize(new Dimension(130, 28));
        top.add(comboPorts);
        btnRefresh = new JButton("Refresh");
        btnRefresh.addActionListener(e -> refreshPorts());
        top.add(btnRefresh);
        btnConnect = new JButton("Connect");
        btnConnect.setBackground(new Color(0, 150, 0));
        btnConnect.setForeground(Color.WHITE);
        btnConnect.addActionListener(e -> toggleConnection());
        top.add(btnConnect);
        top.add(Box.createHorizontalStrut(12));
        btnScopeToggle = new JButton();
        styleToggle(btnScopeToggle, false, "▶ SCOPE ON");
        btnScopeToggle.setEnabled(false);
        btnScopeToggle.addActionListener(e -> toggleScope());
        top.add(btnScopeToggle);
        btnMode = new JButton("📈 LEVEL");
        btnMode.setEnabled(false);
        btnMode.addActionListener(e -> toggleMode());
        top.add(btnMode);
        top.add(Box.createHorizontalStrut(12));
        top.add(new JLabel("CMD:"));
        txtCmd = new JTextField("ADC_BENCH", 12);
        txtCmd.setPreferredSize(new Dimension(140, 28));
        txtCmd.addActionListener(e -> sendCmd());
        top.add(txtCmd);
        JButton btnCmd = new JButton("Send");
        btnCmd.addActionListener(e -> sendCmd());
        top.add(btnCmd);
        add(top, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(new EmptyBorder(6, 10, 6, 10));
        center.add(createManualFreqPanel());
        center.add(Box.createVerticalStrut(6));
        center.add(createAdvancedSettingsPanel());
        center.add(Box.createVerticalStrut(6));
        center.add(createSweepControlPanel());
        add(center, BorderLayout.CENTER);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        mainSplit.setResizeWeight(0.45);

        JPanel scopeArea = new JPanel(new BorderLayout(5, 5));
        lblStatus = new JLabel(" DISCONNECTED ");
        lblStatus.setOpaque(true);
        lblStatus.setBackground(Color.GRAY);
        lblStatus.setForeground(Color.WHITE);
        lblStatus.setHorizontalAlignment(SwingConstants.CENTER);
        lblStatus.setPreferredSize(new Dimension(0, 25));
        scopeArea.add(lblStatus, BorderLayout.NORTH);

        JSplitPane scopeInner = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        scopeInner.setResizeWeight(0.75);
        scopePanel = new OscilloscopePanel();
        scopePanel.setPreferredSize(new Dimension(400, 230));
        scopePanel.setSampleRate(14000);
        levelPanel = new LevelPanel();
        viewLayout = new CardLayout();
        viewCard = new JPanel(viewLayout);
        viewCard.add(scopePanel, "scope");
        viewCard.add(levelPanel, "level");
        scopeInner.setLeftComponent(viewCard);
        scopeInner.setRightComponent(createScopeControlPanel());
        scopeArea.add(scopeInner, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(5, 5));
        spectrumPanel = new SpectrumPanel();
        spectrumPanel.setPreferredSize(new Dimension(400, 280));
        spectrumPanel.setFrequencyPickListener(this::pickFrequencyFromGraph);
        bottom.add(spectrumPanel, BorderLayout.CENTER);
        textLog = new JTextArea();
        textLog.setEditable(false);
        textLog.setFont(new Font("Monospaced", Font.PLAIN, 10));
        JScrollPane logScroll = new JScrollPane(textLog);
        logScroll.setPreferredSize(new Dimension(0, 90));
        bottom.add(logScroll, BorderLayout.SOUTH);

        mainSplit.setTopComponent(scopeArea);
        mainSplit.setBottomComponent(bottom);
        add(mainSplit, BorderLayout.SOUTH);
    }

    private JPanel createManualFreqPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        p.add(new JLabel("Frequency (MHz):"));
        txtFreq = makeFixedField("1000.000000", 150, "Выходная частота MAX2870, МГц");
        p.add(txtFreq);
        p.add(createStepButton("-1 MHz", -1.0f));
        p.add(createStepButton("-100 kHz", -0.1f));
        p.add(createStepButton("-10 kHz", -0.01f));
        p.add(createStepButton("-1 kHz", -0.001f));
        p.add(Box.createHorizontalStrut(8));
        p.add(createStepButton("+1 kHz", +0.001f));
        p.add(createStepButton("+10 kHz", +0.01f));
        p.add(createStepButton("+100 kHz", +0.1f));
        p.add(createStepButton("+1 MHz", +1.0f));
        return p;
    }

    private JPanel createAdvancedSettingsPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 8));
        p.setBorder(BorderFactory.createTitledBorder("Advanced PLL Settings"));
        p.add(new JLabel("Output Power Code:"));
        cbPower = new JComboBox<>(new Integer[]{0, 1, 2, 3});
        cbPower.setSelectedItem(3);
        cbPower.setPreferredSize(new Dimension(60, 28));
        p.add(cbPower);
        p.add(new JLabel("CP Current Code:"));
        cbCpCurrent = new JComboBox<>();
        for (int i = 0; i <= 15; i++) cbCpCurrent.addItem(i);
        cbCpCurrent.setSelectedItem(4);
        cbCpCurrent.setPreferredSize(new Dimension(60, 28));
        p.add(cbCpCurrent);
        p.add(new JLabel("LD Pin Function:"));
        cbLdMode = new JComboBox<>(new String[]{
                "Low (Constant 0)", "Digital Lock Detect", "Analog Lock Detect", "High (Constant 1)"});
        cbLdMode.setSelectedIndex(1);
        cbLdMode.setPreferredSize(new Dimension(180, 28));
        p.add(cbLdMode);
        btnApply = new JButton("APPLY ALL SETTINGS");
        btnApply.setFont(btnApply.getFont().deriveFont(Font.BOLD));
        btnApply.setEnabled(false);
        btnApply.addActionListener(e -> sendAll());
        p.add(btnApply);
        return p;
    }

    private JPanel createSweepControlPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Frequency Sweep Analyzer"));

        JPanel params = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        params.add(new JLabel("Start (MHz):"));
        txtSweepStart = makeFixedField("990.00", 110, "Начальная частота скана");
        params.add(txtSweepStart);
        params.add(new JLabel("Stop (MHz):"));
        txtSweepStop = makeFixedField("1010.00", 110, "Конечная частота скана");
        params.add(txtSweepStop);
        params.add(new JLabel("Step (kHz):"));
        txtSweepStep = makeFixedField("100", 90, "Шаг скана, кГц");
        params.add(txtSweepStep);
        btnSweepToggle = new JButton("▶ RUN SWEEP");
        btnSweepToggle.setBackground(new Color(0, 100, 200));
        btnSweepToggle.setForeground(Color.WHITE);
        btnSweepToggle.setPreferredSize(new Dimension(170, 32));
        btnSweepToggle.setEnabled(false);
        btnSweepToggle.addActionListener(e -> { if (sweeping) stopSweep(); else startSweep(); });
        params.add(btnSweepToggle);
        panel.add(params, BorderLayout.NORTH);

        JPanel scale = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        scale.add(new JLabel("IF (Hz):"));
        spinIfHz = new JSpinner(new SpinnerNumberModel(300, 0, 20000, 100));
        spinIfHz.setPreferredSize(new Dimension(80, 26));
        spinIfHz.setToolTipText("LO = RF + IF");
        scale.add(spinIfHz);
        lblBeat = new JLabel("beat: —");
        lblBeat.setForeground(new Color(0, 100, 0));
        scale.add(lblBeat);
        spinIfHz.addChangeListener(e -> {
            if (connection != null && connection.isOpen() && !Double.isNaN(lastPickedFreq)) {
                applyPicked(lastPickedFreq);
            }
        });

        scale.add(new JLabel("fs (kHz):"));
        spinFsKhz = new JSpinner(new SpinnerNumberModel(14, 1, 100, 1));
        spinFsKhz.setPreferredSize(new Dimension(65, 26));
        scale.add(spinFsKhz);
        scale.add(new JLabel("FPS:"));
        spinFps = new JSpinner(new SpinnerNumberModel(10, 2, 20, 1));
        spinFps.setPreferredSize(new Dimension(60, 26));
        scale.add(spinFps);
        javax.swing.event.ChangeListener cfg = e -> sendScopeCfg();
        spinFsKhz.addChangeListener(cfg);
        spinFps.addChangeListener(cfg);

        scale.add(Box.createHorizontalStrut(10));
        scale.add(new JLabel("Floor dB:"));
        sldFloorDb = new JSlider(0, 60, 30);
        sldFloorDb.setPreferredSize(new Dimension(100, 24));
        lblFloorDb = new JLabel("30");
        sldFloorDb.addChangeListener(e -> {
            spectrumPanel.setFloorDb(sldFloorDb.getValue());
            lblFloorDb.setText(String.valueOf((int) spectrumPanel.getFloorDb()));
        });
        scale.add(sldFloorDb);
        scale.add(lblFloorDb);

        scale.add(new JLabel("Top dB:"));
        sldTopDb = new JSlider(40, 72, 66);
        sldTopDb.setPreferredSize(new Dimension(100, 24));
        lblTopDb = new JLabel("66");
        sldTopDb.addChangeListener(e -> {
            spectrumPanel.setTopDb(sldTopDb.getValue());
            lblTopDb.setText(String.valueOf((int) spectrumPanel.getTopDb()));
        });
        scale.add(sldTopDb);
        scale.add(lblTopDb);

        chkFftView = new JCheckBox("FFT view", false);
        chkFftView.addActionListener(e -> spectrumPanel.setShowFft(chkFftView.isSelected()));
        scale.add(chkFftView);
        chkAutoZoom = new JCheckBox("Auto zoom", true);
        chkAutoZoom.setToolTipText("После грубого скана — точный ±20 кГц шагом 100 Гц");
        scale.add(chkAutoZoom);
        JButton btnClearMarks = new JButton("Clear marks");
        btnClearMarks.addActionListener(e -> { spectrumPanel.clearMarkers(); levelPanel.clear(); });
        scale.add(btnClearMarks);
        panel.add(scale, BorderLayout.CENTER);

        java.awt.event.ActionListener enter = e -> { if (sweeping) stopSweep(); else startSweep(); };
        txtSweepStart.addActionListener(enter);
        txtSweepStop.addActionListener(enter);
        txtSweepStep.addActionListener(enter);
        return panel;
    }

    private JPanel createScopeControlPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("Scope / Level Controls"));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(2, 5, 2, 5);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;

        gc.gridx = 0; gc.gridy = 0; p.add(new JLabel("Time/Div:"), gc);
        gc.gridx = 1; sldTimeDiv = new JSlider(1, 100, 1);
        sldTimeDiv.addChangeListener(e -> scopePanel.setZoom(1.0 + sldTimeDiv.getValue() / 4.0));
        p.add(sldTimeDiv, gc);

        gc.gridx = 0; gc.gridy = 1; p.add(new JLabel("Volt/Div:"), gc);
        gc.gridx = 1; sldVoltDiv = new JSlider(5, 1000, 200);
        sldVoltDiv.addChangeListener(e -> scopePanel.setVoltPerDiv(sldVoltDiv.getValue()));
        p.add(sldVoltDiv, gc);

        gc.gridx = 0; gc.gridy = 2; p.add(new JLabel("Pos X:"), gc);
        gc.gridx = 1; spinPosX = new JSpinner(new SpinnerNumberModel(0, -500, 500, 1));
        spinPosX.addChangeListener(e -> scopePanel.setPosX((Integer) spinPosX.getValue()));
        p.add(spinPosX, gc);

        gc.gridx = 0; gc.gridy = 3; p.add(new JLabel("Pos Y:"), gc);
        gc.gridx = 1; spinPosY = new JSpinner(new SpinnerNumberModel(0, -500, 500, 1));
        spinPosY.addChangeListener(e -> scopePanel.setPosY((Integer) spinPosY.getValue()));
        p.add(spinPosY, gc);

        gc.gridx = 0; gc.gridy = 4; p.add(new JLabel("Trig Lvl:"), gc);
        gc.gridx = 1; sldTrigger = new JSlider(0, 4095, 2048);
        sldTrigger.addChangeListener(e -> scopePanel.setTriggerLevel(sldTrigger.getValue()));
        p.add(sldTrigger, gc);

        gc.gridx = 0; gc.gridy = 5; gc.gridwidth = 2;
        chkAutoScale = new JCheckBox("Auto Scale", false);
        chkAutoScale.addActionListener(e -> scopePanel.setAutoScale(chkAutoScale.isSelected()));
        p.add(chkAutoScale, gc);

        gc.gridy = 6;
        btnPauseResume = new JButton("⏸ PAUSE");
        btnPauseResume.setBackground(new Color(200, 150, 0));
        btnPauseResume.addActionListener(e -> togglePause());
        p.add(btnPauseResume, gc);

        gc.gridy = 7;
        lblBeatBig = new JLabel("BEAT: — Hz");
        lblBeatBig.setFont(new Font("Monospaced", Font.BOLD, 18));
        lblBeatBig.setForeground(new Color(0, 120, 0));
        p.add(lblBeatBig, gc);

        gc.gridy = 8;
        JButton btnAutoTune = new JButton("🎯 AUTO TUNE");
        btnAutoTune.setBackground(new Color(0, 120, 200));
        btnAutoTune.setForeground(Color.WHITE);
        btnAutoTune.addActionListener(e -> autoTune());
        p.add(btnAutoTune, gc);

        gc.gridy = 9;
        JCheckBox chkHold = new JCheckBox("Level max hold", true);
        chkHold.addActionListener(e -> levelPanel.setHold(chkHold.isSelected()));
        p.add(chkHold, gc);
        return p;
    }

    private JButton createStepButton(String label, float delta) {
        JButton b = new JButton(label);
        b.addActionListener(e -> {
            try {
                float cur = Float.parseFloat(txtFreq.getText().trim().replace(',', '.'));
                float nv = cur + delta;
                if (nv < 23.5f) nv = 23.5f;
                if (nv > 6000f) nv = 6000f;
                txtFreq.setText(String.format(java.util.Locale.US, "%.6f", nv));
                if (levelMode) sendAll();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Некорректное значение частоты!", "Ошибка", JOptionPane.WARNING_MESSAGE);
            }
        });
        return b;
    }

    private void togglePause() {
        boolean paused = scopePanel.isPaused();
        scopePanel.setPaused(!paused);
        btnPauseResume.setText(!paused ? "▶ RESUME" : "⏸ PAUSE");
        btnPauseResume.setBackground(!paused ? new Color(0, 150, 0) : new Color(200, 150, 0));
    }

    private void toggleMode() {
        if (!connection.isOpen()) return;
        levelMode = !levelMode;
        if (levelMode) {
            if (connection.isStreamingActive()) {
                connection.stopScopeStream();
                styleToggle(btnScopeToggle, false, "▶ SCOPE ON");
            }
            levelPanel.clear();
            connection.sendRawText("MONITOR_START\n");
            viewLayout.show(viewCard, "level");
            btnMode.setText("📉 SCOPE");
            notifyLog(">>> LEVEL MODE (амплитуда во времени)");
        } else {
            connection.sendRawText("MONITOR_STOP\n");
            viewLayout.show(viewCard, "scope");
            btnMode.setText("📈 LEVEL");
            notifyLog(">>> SCOPE MODE");
        }
    }

    private void toggleScope() {
        if (!connection.isOpen()) return;
        if (levelMode) toggleMode();
        if (connection.isStreamingActive()) {
            connection.stopScopeStream();
            styleToggle(btnScopeToggle, false, "▶ SCOPE ON");
        } else {
            sendScopeCfg();
            connection.startScopeStream();
            styleToggle(btnScopeToggle, true, "⏹ SCOPE OFF");
        }
    }

    private void sendScopeCfg() {
        if (!connection.isOpen()) return;
        int fs = ((Number) spinFsKhz.getValue()).intValue() * 1000;
        actualFs = fs;
        int fps = ((Number) spinFps.getValue()).intValue();
        int n = 250;
        if (n * 2 * fps > 9000) n = 9000 / (2 * fps);
        if (n < 20) n = 20;
        scopePanel.setSampleRate(fs);
        spectrumPanel.setSampleRate(fs);
        connection.sendRawText(String.format(java.util.Locale.US, "SCOPE_CFG:%d,%d,%d\n", fs, n, 1000 / fps));
    }

    private void sendCmd() {
        if (!connection.isOpen()) return;
        String c = txtCmd.getText().trim();
        if (c.isEmpty()) return;
        if (connection.isStreamingActive()) {
            connection.stopScopeStream();
            styleToggle(btnScopeToggle, false, "▶ SCOPE ON");
        }
        connection.sendRawText(c + "\n");
        notifyLog(">>> CMD: " + c);
    }

    private void resetSweepUi() {
        sweeping = false;
        btnSweepToggle.setText("▶ RUN SWEEP");
        btnSweepToggle.setBackground(new Color(0, 100, 200));
    }

    private void refreshPorts() {
        comboPorts.removeAllItems();
        for (String p : connection.getAvailablePorts()) comboPorts.addItem(p);
    }

    private void toggleConnection() { if (connection.isOpen()) disconnect(); else connect(); }

    private void connect() {
        String port = (String) comboPorts.getSelectedItem();
        if (port == null) {
            JOptionPane.showMessageDialog(this, "Выберите порт", "Ошибка", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (connection.openPort(port)) {
            btnConnect.setText("Disconnect");
            btnConnect.setBackground(new Color(200, 0, 0));
            btnApply.setEnabled(true);
            btnScopeToggle.setEnabled(true);
            btnSweepToggle.setEnabled(true);
            btnMode.setEnabled(true);
            setStatus(false, "UNLOCKED");
            sendAll();
            sendScopeCfg();
        } else {
            JOptionPane.showMessageDialog(this, "Не удалось подключиться", "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void disconnect() {
        connection.closePort();
        btnConnect.setText("Connect");
        btnConnect.setBackground(new Color(0, 150, 0));
        btnApply.setEnabled(false);
        btnScopeToggle.setEnabled(false);
        styleToggle(btnScopeToggle, false, "▶ SCOPE ON");
        btnSweepToggle.setEnabled(false);
        btnMode.setEnabled(false);
        btnMode.setText("📈 LEVEL");
        levelMode = false;
        viewLayout.show(viewCard, "scope");
        resetSweepUi();
        setStatus(false, "DISCONNECTED");
        lblStatus.setBackground(Color.GRAY);
    }

    private void sendAll() {
        if (!connection.isOpen()) return;
        try {
            float freq = Float.parseFloat(txtFreq.getText().trim().replace(',', '.'));
            int powerCode = (Integer) cbPower.getSelectedItem();
            int cpCode = (Integer) cbCpCurrent.getSelectedItem();
            int ldMode = cbLdMode.getSelectedIndex();
            connection.sendControlPacket(freq, cpCode, 0, 0, 6, 200, 0xFF, powerCode, 0, 0, 0, 1, (short) 0, ldMode);
            if (sweeping) { notifyLog(">>> SWEEP aborted by APPLY"); resetSweepUi(); }
            setStatus(false, "SENDING...");
            lblStatus.setBackground(Color.ORANGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Ошибка ввода:\n" + ex.getMessage(), "Ошибка", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void startSweep() {
        if (!connection.isOpen()) return;
        try {
            double start = Double.parseDouble(txtSweepStart.getText().trim().replace(',', '.'));
            double stop = Double.parseDouble(txtSweepStop.getText().trim().replace(',', '.'));
            double stepK = Double.parseDouble(txtSweepStep.getText().trim().replace(',', '.'));
            if (stop < start || stepK <= 0) throw new IllegalArgumentException("Stop >= Start и Step > 0 обязательны");
            if (connection.isStreamingActive()) {
                connection.stopScopeStream();
                styleToggle(btnScopeToggle, false, "▶ SCOPE ON");
            }
            if (levelMode) {
                connection.sendRawText("MONITOR_STOP\n");
                viewLayout.show(viewCard, "scope");
                levelMode = false;
                btnMode.setText("📈 LEVEL");
            }
            spectrumPanel.setRange(start, stop);
            sweepDataPoints.clear();
            spectrumPanel.clearData();
            String cmd = String.format(java.util.Locale.US, "SWEEP_START:%.3f,%.3f,%.3f\n", start, stop, stepK);
            connection.sendRawText(cmd);
            notifyLog(">>> SENT SWEEP COMMAND: " + cmd.trim());
            sweeping = true;
            btnSweepToggle.setText("⏹ STOP SWEEP");
            btnSweepToggle.setBackground(new Color(200, 50, 50));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Ошибка параметров свипа:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void stopSweep() {
        connection.sendRawText("SWEEP_STOP\n");
        notifyLog(">>> SWEEP STOPPED BY USER");
        resetSweepUi();
    }

    private void applyPicked(double pickedMHz) {
        double ifHz = ((Number) spinIfHz.getValue()).doubleValue();
        double tuned = pickedMHz + ifHz / 1000000.0;
        if (tuned < 23.5) tuned = 23.5;
        if (tuned > 6000.0) tuned = 6000.0;
        txtFreq.setText(String.format(java.util.Locale.US, "%.6f", tuned));
        notifyLog(String.format(java.util.Locale.US, ">>> RETUNE %.3f MHz +IF %.0f Hz -> LO %.6f MHz", pickedMHz, ifHz, tuned));
        sendAll();
    }

    private void pickFrequencyFromGraph(double freqMHz) {
        if (!connection.isOpen()) return;
        if (sweeping) { connection.sendRawText("SWEEP_STOP\n"); resetSweepUi(); }
        lastPickedFreq = freqMHz;
        applyPicked(freqMHz);
        if (!levelMode && !connection.isStreamingActive()) {
            sendScopeCfg();
            connection.startScopeStream();
            styleToggle(btnScopeToggle, true, "⏹ SCOPE OFF");
        }
    }

    private void autoTune() {
        if (!connection.isOpen() || Double.isNaN(lastBeatHz)) return;
        try {
            double lo = Double.parseDouble(txtFreq.getText().trim().replace(',', '.'));
            double ifHz = ((Number) spinIfHz.getValue()).doubleValue();
            tuneBaseLo = lo;
            tuneBeatMHz = lastBeatHz / 1000000.0;
            double tryA = lo + tuneBeatMHz + ifHz / 1000000.0;
            txtFreq.setText(String.format(java.util.Locale.US, "%.6f", tryA));
            notifyLog(String.format(java.util.Locale.US, ">>> AUTOTUNE try A: %.6f", tryA));
            sendAll();
            javax.swing.Timer t = new javax.swing.Timer(700, ev -> {
                ((javax.swing.Timer) ev.getSource()).stop();
                if (lastBeatHz > ifHz * 2.5) {
                    double tryB = tuneBaseLo - tuneBeatMHz + ifHz / 1000000.0;
                    txtFreq.setText(String.format(java.util.Locale.US, "%.6f", tryB));
                    notifyLog(String.format(java.util.Locale.US, ">>> AUTOTUNE try B: %.6f", tryB));
                    sendAll();
                }
            });
            t.setRepeats(false);
            t.start();
        } catch (Exception ex) {
            notifyLog("AUTOTUNE error: " + ex.getMessage());
        }
    }

    private void notifyLog(String message) {
        SwingUtilities.invokeLater(() -> {
            textLog.append(message + "\n");
            textLog.setCaretPosition(textLog.getDocument().getLength());
        });
    }

    private void setStatus(boolean locked, String text) {
        lblStatus.setText(" " + text + " ");
        lblStatus.setBackground(locked ? new Color(0, 180, 0) : new Color(220, 50, 50));
    }

    @Override
    public void onLogMessage(String message) {
        if (message.startsWith("LEVEL:")) {
            try {
                final int v = Integer.parseInt(message.substring(6).trim());
                SwingUtilities.invokeLater(() -> levelPanel.addLevel(v));
            } catch (Exception ignored) {}
            return;
        }
        if (message.startsWith("SCOPE_CFG ok:")) {
            try {
                int i = message.indexOf("fs=");
                int j = message.indexOf(' ', i);
                actualFs = Integer.parseInt(message.substring(i + 3, j));
                SwingUtilities.invokeLater(() -> {
                    scopePanel.setSampleRate(actualFs);
                    spectrumPanel.setSampleRate(actualFs);
                });
            } catch (Exception ignored) {}
        }
        if (message.startsWith("SCAN_RESULT:")) { handleScanResponse(message); return; }
        if (message.startsWith("SWEEP_PEAK:")) {
            try {
                String[] p = message.split(":")[1].split(",");
                final double f = Double.parseDouble(p[0]);
                SwingUtilities.invokeLater(() -> {
                    spectrumPanel.addMarker(f);
                    if (chkAutoZoom != null && chkAutoZoom.isSelected()) {
                        double stepK = Double.parseDouble(txtSweepStep.getText().trim().replace(',', '.'));
                        if (stepK > 0.5) {
                            javax.swing.Timer t = new javax.swing.Timer(400, ev -> {
                                ((javax.swing.Timer) ev.getSource()).stop();
                                txtSweepStart.setText(String.format(java.util.Locale.US, "%.3f", f - 0.02));
                                txtSweepStop.setText(String.format(java.util.Locale.US, "%.3f", f + 0.02));
                                txtSweepStep.setText("0.1");
                                notifyLog(">>> AUTO-ZOOM: fine sweep around " + f);
                                startSweep();
                            });
                            t.setRepeats(false);
                            t.start();
                        }
                    }
                });
            } catch (Exception ignored) {}
        }
        if (message.startsWith("SWEEP: done") || message.startsWith("SWEEP: stopped")) {
            SwingUtilities.invokeLater(this::resetSweepUi);
        }
        SwingUtilities.invokeLater(() -> {
            textLog.append(message + "\n");
            textLog.setCaretPosition(textLog.getDocument().getLength());
        });
    }

    private void handleScanResponse(String line) {
        try {
            String[] parts = line.split(":")[1].split(",");
            double freq = Double.parseDouble(parts[0]);
            int level = Integer.parseInt(parts[1]);
            synchronized (sweepDataPoints) { sweepDataPoints.add(new double[]{freq, level}); }
            SwingUtilities.invokeLater(() -> spectrumPanel.setData(sweepDataPoints));
        } catch (Exception ignored) {}
    }

    @Override
    public void onLockStatusChanged(boolean isLocked) {
        SwingUtilities.invokeLater(() -> setStatus(isLocked, isLocked ? "LOCKED" : "UNLOCKED"));
    }

    @Override
    public void onError(String error) {
        SwingUtilities.invokeLater(() -> textLog.append("[ERR] " + error + "\n"));
    }

    @Override
    public void onDataReceived(short[] samples) {
        SwingUtilities.invokeLater(() -> {
            scopePanel.updateData(samples);
            if (fftProcessor == null || samples.length == 0) return;
            double[] sp = fftProcessor.computeSpectrum(samples);
            spectrumPanel.updateSpectrum(sp);
            int bi = 3; double bv = -1e9;
            for (int i = 3; i < sp.length; i++) if (sp[i] > bv) { bv = sp[i]; bi = i; }
            double beat = bi * actualFs / 512.0;
            lastBeatHz = beat;
            if (lblBeatBig != null) {
                lblBeatBig.setText(String.format(java.util.Locale.US, "BEAT: %.0f Hz", beat));
                lblBeatBig.setForeground(beat < 1000 ? new Color(0, 150, 0) : new Color(200, 80, 0));
            }
            if (lblBeat != null) lblBeat.setText(String.format(java.util.Locale.US, "beat: %.0f Hz", beat));
        });
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new Max2870Gui().setVisible(true));
    }
}