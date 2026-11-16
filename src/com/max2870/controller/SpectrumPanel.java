package com.max2870.controller;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class SpectrumPanel extends JPanel {
    public interface FrequencyPickListener { void onFrequencyPicked(double freqMHz); }

    private List<double[]> sweepDataPoints;
    private double[] fftSpectrum;
    private boolean showFft = false;
    private final List<Double> markers = new ArrayList<>();
    private final Timer repaintTimer;

    private double minFreq = 900.0, maxFreq = 1100.0;
    private boolean rangeFixed = false;
    private double floorDb = 30.0, topDb = 66.0;
    private double sampleRate = 40000.0;
    private FrequencyPickListener pickListener;

    private static final double DB_FULL = 20.0 * Math.log10(4096.0);
    private static final int MARGIN = 34;
    private static final int FFT_N = 512;

    public SpectrumPanel() {
        setBackground(Color.BLACK);
        setMinimumSize(new Dimension(200, 220));
        setToolTipText("Клик по АЧХ: привязка к ближайшему пику + установка частоты (+IF)");
        repaintTimer = new Timer(50, e -> repaint());
        repaintTimer.start();
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { handleClick(e.getX()); }
        });
    }

    public void setFrequencyPickListener(FrequencyPickListener l) { pickListener = l; }
    public void setRange(double a, double b) { minFreq = a; maxFreq = b; rangeFixed = true; }
    public void setShowFft(boolean on) { showFft = on; repaint(); }
    public void setSampleRate(double fs) { sampleRate = Math.max(1, fs); }
    public void addMarker(double f) { markers.add(f); repaint(); }
    public void clearMarkers() { markers.clear(); repaint(); }
    public void setFloorDb(double v) { floorDb = Math.max(0, Math.min(v, topDb - 6)); repaint(); }
    public void setTopDb(double v) { topDb = Math.max(floorDb + 6, Math.min(v, DB_FULL)); repaint(); }
    public double getFloorDb() { return floorDb; }
    public double getTopDb() { return topDb; }

    public void setData(List<double[]> points) {
        this.sweepDataPoints = points;
        if (!rangeFixed && points != null && !points.isEmpty()) {
            minFreq = points.get(0)[0];
            maxFreq = points.get(points.size() - 1)[0];
        }
        repaint();
    }

    public void clearData() { sweepDataPoints = null; markers.clear(); repaint(); }

    // FFT больше НЕ стирает АЧХ
    public void updateSpectrum(double[] spectrum) { this.fftSpectrum = spectrum; if (showFft) repaint(); }

    private static double toDb(int level) { return 20.0 * Math.log10(Math.max(1, level) + 1.0); }

    private void handleClick(int x) {
        if (showFft || sweepDataPoints == null || sweepDataPoints.isEmpty()) return;
        int plotW = getWidth() - 2 * MARGIN;
        if (plotW < 10 || x < MARGIN || x > MARGIN + plotW) return;
        double fClick = minFreq + (x - MARGIN) / (double) plotW * (maxFreq - minFreq);

        int best = 0; double bestD = Double.MAX_VALUE;
        for (int i = 0; i < sweepDataPoints.size(); i++) {
            double d = Math.abs(sweepDataPoints.get(i)[0] - fClick);
            if (d < bestD) { bestD = d; best = i; }
        }
        int lo = Math.max(0, best - 3), hi = Math.min(sweepDataPoints.size() - 1, best + 3);
        int peak = best; double lvl = -1;
        for (int i = lo; i <= hi; i++) {
            if (sweepDataPoints.get(i)[1] > lvl) { lvl = sweepDataPoints.get(i)[1]; peak = i; }
        }
        double f = sweepDataPoints.get(peak)[0];
        markers.add(f);
        repaint();
        if (pickListener != null) pickListener.onFrequencyPicked(f);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        int pw = w - 2 * MARGIN, ph = h - 2 * MARGIN;
        if (pw < 10 || ph < 10) return;
        drawAxes(g2, MARGIN, MARGIN, pw, ph);

        if (showFft && fftSpectrum != null && fftSpectrum.length > 0) {
            drawFFTBars(g2, MARGIN, MARGIN, pw, ph);
            int bi = 1; double bv = -1e9;
            for (int i = 1; i < fftSpectrum.length; i++) if (fftSpectrum[i] > bv) { bv = fftSpectrum[i]; bi = i; }
            g2.setColor(Color.YELLOW);
            g2.setFont(new Font("Monospaced", Font.BOLD, 11));
            g2.drawString(String.format(java.util.Locale.US,
                    "FFT of ADC beat: peak bin %d = %.0f Hz (fs %.0f, Nyquist %.0f)",
                    bi, bi * sampleRate / FFT_N, sampleRate, sampleRate / 2), MARGIN, MARGIN - 6);
            return;
        }

        if (sweepDataPoints != null && !sweepDataPoints.isEmpty()) {
            double span = Math.max(1e-9, maxFreq - minFreq);
            double win = Math.max(1e-9, topDb - floorDb);

            g2.setFont(new Font("Monospaced", Font.PLAIN, 9));
            int step = (win > 40) ? 12 : 6;
            for (int db = (int) Math.ceil(floorDb / step) * step; db < topDb; db += step) {
                int y = MARGIN + ph - (int) ((db - floorDb) / win * ph);
                g2.setColor(new Color(40, 40, 40)); g2.drawLine(MARGIN, y, MARGIN + pw, y);
                g2.setColor(new Color(110, 110, 110)); g2.drawString(db + "dB", 2, y + 4);
            }

            g2.setColor(Color.CYAN);
            g2.setStroke(new BasicStroke(2f));
            Polygon poly = new Polygon();
            int peakIdx = 0; double peakLvl = -1;
            for (int i = 0; i < sweepDataPoints.size(); i++) {
                double[] p = sweepDataPoints.get(i);
                double xn = (p[0] - minFreq) / span; if (xn < 0) xn = 0; if (xn > 1) xn = 1;
                double yn = (toDb((int) p[1]) - floorDb) / win; if (yn < 0) yn = 0; if (yn > 1) yn = 1;
                poly.addPoint(MARGIN + (int) (xn * pw), MARGIN + ph - (int) (yn * ph));
                if (p[1] > peakLvl) { peakLvl = p[1]; peakIdx = i; }
            }
            if (poly.npoints > 1) g2.drawPolyline(poly.xpoints, poly.ypoints, poly.npoints);

            double[] last = sweepDataPoints.get(sweepDataPoints.size() - 1);
            int cx = MARGIN + (int) (((last[0] - minFreq) / span) * pw);
            g2.setColor(new Color(255, 255, 0, 110));
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine(cx, MARGIN, cx, MARGIN + ph);

            double[] pk = sweepDataPoints.get(peakIdx);
            int px = MARGIN + (int) (((pk[0] - minFreq) / span) * pw);
            double pyn = (toDb((int) pk[1]) - floorDb) / win; if (pyn < 0) pyn = 0; if (pyn > 1) pyn = 1;
            g2.setColor(Color.RED);
            g2.fillOval(px - 4, MARGIN + ph - (int) (pyn * ph) - 4, 9, 9);
            g2.setColor(Color.YELLOW);
            g2.setFont(new Font("Monospaced", Font.BOLD, 11));
            g2.drawString(String.format(java.util.Locale.US,
                    "PEAK %.3f MHz lvl=%d  (click = snap to peak)", pk[0], (int) pk[1]), MARGIN + 4, MARGIN + 12);

            g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
            int n = 0;
            for (double f : markers) {
                int kx = MARGIN + (int) (((f - minFreq) / span) * pw);
                g2.setColor(new Color(255, 255, 255, 150));
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4}, 0));
                g2.drawLine(kx, MARGIN, kx, MARGIN + ph);
                g2.setColor(Color.WHITE);
                g2.drawString(String.format(java.util.Locale.US, "#%d %.3f", ++n, f),
                        Math.min(kx + 3, MARGIN + pw - 70), MARGIN + 24 + n * 11);
            }
        } else {
            g2.setColor(Color.GRAY);
            g2.drawString("No Data. Run Sweep or Start Scope.", w / 2 - 95, h / 2);
        }
    }

    private void drawFFTBars(Graphics2D g2, int mx, int my, int pw, int ph) {
        int numBins = Math.min(100, fftSpectrum.length);
        float bw = (float) pw / numBins;
        g2.setColor(new Color(0, 200, 255));
        for (int i = 0; i < numBins; i++) {
            double db = Math.max(-80, Math.min(0, fftSpectrum[i]));
            int bh = (int) ((db + 80) / 80.0 * ph);
            g2.fillRect(mx + (int) (i * bw), my + ph - bh, (int) Math.ceil(bw) - 1, bh);
        }
    }

    private void drawAxes(Graphics2D g2, int mx, int my, int pw, int ph) {
        g2.setColor(new Color(60, 60, 60));
        g2.drawLine(mx, my, mx, my + ph);
        g2.drawLine(mx, my + ph, mx + pw, my + ph);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
        if (!showFft && sweepDataPoints != null && !sweepDataPoints.isEmpty()) {
            g2.drawString(String.format(java.util.Locale.US, "%.2f", minFreq), mx, my + ph + 15);
            g2.drawString(String.format(java.util.Locale.US, "%.2f MHz", maxFreq), mx + pw - 70, my + ph + 15);
            g2.drawString("dB", mx - 26, my + 10);
        } else {
            g2.drawString("FFT bin", mx, my + ph + 15);
            g2.drawString("dB", mx - 26, my + 10);
        }
    }
}