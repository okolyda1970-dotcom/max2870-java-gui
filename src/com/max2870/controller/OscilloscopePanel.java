package com.max2870.controller;

import javax.swing.*;
import java.awt.*;

public class OscilloscopePanel extends JPanel {
    private short[] frame = new short[0];   // последний "снимок"
    private double sampleRate = 40000.0;    // Гц (синхронно с SCOPE_CFG)
    private double zoom = 1.0;              // 1 = весь кадр, 10 = десятая часть
    private int posX = 0, posY = 0;
    private int triggerLevel = 2048;
    private double voltPerDiv = 200.0;      // LSB на деление
    private boolean isPaused = false, autoScale = false;

    private static final int COLS = 10, ROWS = 8;
    private final Timer timer;

    public OscilloscopePanel() {
        setBackground(Color.BLACK);
        setMinimumSize(new Dimension(400, 220));
        timer = new Timer(50, e -> { if (!isPaused) { if (autoScale) doAutoScale(); repaint(); } });
        timer.start();
    }

    public void setSampleRate(double fs) { sampleRate = fs; repaint(); }
    public void setZoom(double z) { zoom = Math.max(1.0, Math.min(50.0, z)); }
    public void setPosX(int p) { posX = p; }
    public void setPosY(int p) { posY = p; }
    public void setTriggerLevel(int t) { triggerLevel = t; }
    public void setVoltPerDiv(double v) { voltPerDiv = Math.max(1, v); }
    public void setPaused(boolean p) { isPaused = p; }
    public boolean isPaused() { return isPaused; }
    public void setAutoScale(boolean a) { autoScale = a; }

    // Один пакет = один кадр, никакой склейки
    public void updateData(short[] samples) {
        if (isPaused || samples == null || samples.length == 0) return;
        frame = samples.clone();
    }

    private void doAutoScale() {
        if (frame.length == 0) return;
        int mn = 4095, mx = 0;
        for (short s : frame) { int v = s & 0xFFFF; if (v < mn) mn = v; if (v > mx) mx = v; }
        int range = Math.max(20, mx - mn);
        voltPerDiv = range / 6.0;
        triggerLevel = (mn + mx) / 2;
        posY = 0;
    }

    private int findTrigger(int need) {
        if (frame.length < need + 2) return 0;
        int limit = frame.length - need - 1;
        for (int i = 1; i < limit; i++) {
            int a = frame[i] & 0xFFFF, b = frame[i + 1] & 0xFFFF;
            if (a <= triggerLevel && b > triggerLevel) return i;
        }
        return 0;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        int cw = w / COLS, ch = h / ROWS;

        g2.setColor(new Color(35, 35, 35));
        for (int i = 1; i < COLS; i++) g2.drawLine(i * cw, 0, i * cw, h);
        for (int i = 1; i < ROWS; i++) g2.drawLine(0, i * ch, w, i * ch);
        g2.setColor(new Color(80, 80, 80));
        g2.drawLine(w / 2, 0, w / 2, h);
        g2.drawLine(0, h / 2, w, h / 2);

        if (frame.length == 0) {
            g2.setColor(Color.GRAY);
            g2.drawString("No frame yet — press SCOPE ON", w / 2 - 90, h / 2);
            return;
        }

        int show = (int) Math.max(20, frame.length / zoom);
        if (show > frame.length) show = frame.length;
        int start = findTrigger(show) + posX;
        if (start < 0) start = 0;
        if (start > frame.length - show) start = Math.max(0, frame.length - show);

        double pxPerLSB = ch / voltPerDiv;
        int cy = h / 2;

        // уровень триггера
        int ty = (int) (cy - (triggerLevel - 2048) * pxPerLSB) - posY;
        g2.setColor(new Color(255, 90, 90, 140));
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{5}, 0));
        g2.drawLine(0, ty, w, ty);

        //波形
        g2.setColor(Color.GREEN);
        g2.setStroke(new BasicStroke(1.6f));
        Polygon poly = new Polygon();
        for (int i = 0; i < show; i++) {
            int v = frame[start + i] & 0xFFFF;
            int x = (int) ((long) i * w / (show - 1));
            int y = (int) (cy - (v - 2048) * pxPerLSB) - posY;
            poly.addPoint(x, y);
        }
        if (poly.npoints > 1) g2.drawPolyline(poly.xpoints, poly.ypoints, poly.npoints);

        double msPerDiv = (show / (double) COLS) / sampleRate * 1000.0;
        g2.setColor(Color.YELLOW);
        g2.setFont(new Font("Monospaced", Font.BOLD, 11));
        g2.drawString(String.format(java.util.Locale.US,
                "%.3f ms/div | %.0f LSB/div | fs %.0f Hz | N=%d | TRIG %d%s",
                msPerDiv, voltPerDiv, sampleRate, frame.length, triggerLevel, isPaused ? " | PAUSE" : ""), 8, 16);
    }
}