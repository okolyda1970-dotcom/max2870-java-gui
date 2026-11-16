package com.max2870.controller;

import javax.swing.*;
import java.awt.*;

public class LevelPanel extends JPanel {
    private final int[] buf = new int[600];       // ~30 с при 20 отсчётах/с
    private int head = 0, count = 0;
    private int scaleMax = 100;
    private int maxHold = 0;
    private boolean holdOn = true;
    private long lastHoldMs = 0;
    private int tick = 0;
    private final Timer timer;

    private static final int BAR_W = 120;         // ширина столбика
    private static final int TOP = 30;
    private static final int BOT = 34;

    public LevelPanel() {
        setBackground(Color.BLACK);
        setMinimumSize(new Dimension(420, 240));
        timer = new Timer(50, e -> {
            tick++;
            // пик-холд держится 1.5 с, потом медленно сползает
            if (holdOn && maxHold > 0 && System.currentTimeMillis() - lastHoldMs > 1500) {
                maxHold = Math.max(0, maxHold - Math.max(1, maxHold / 60));
            }
            // авто-масштаб: раз в 0.5 с плавно подстраиваемся под максимум в буфере
            if (tick % 10 == 0 && count > 0) {
                int mx = 0;
                for (int i = 0; i < count; i++) {
                    int idx = (head - count + i + buf.length) % buf.length;
                    if (buf[idx] > mx) mx = buf[idx];
                }
                int target = Math.max(100, mx + mx / 8);
                scaleMax += (target - scaleMax) / 3;
                if (Math.abs(target - scaleMax) < 3) scaleMax = target;
            }
            repaint();
        });
        timer.start();
    }

    public void addLevel(int v) {
        if (v < 0) v = 0;
        buf[head] = v;
        head = (head + 1) % buf.length;
        if (count < buf.length) count++;
        if (v > maxHold) { maxHold = v; lastHoldMs = System.currentTimeMillis(); }
        if (v > scaleMax) scaleMax = v + v / 8;
    }

    public void clear() { head = 0; count = 0; maxHold = 0; scaleMax = 100; repaint(); }
    public void setHold(boolean on) { holdOn = on; if (!on) maxHold = 0; }
    public int getLast() { return count == 0 ? 0 : buf[(head - 1 + buf.length) % buf.length]; }
    public int getMaxHold() { return maxHold; }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        int barH = h - TOP - BOT;
        if (barH < 40) return;

        int sm = Math.max(50, scaleMax);
        int last = getLast();
        double frac = Math.min(1.0, last / (double) sm);
        double db = 20.0 * Math.log10(Math.max(1, last) / 2048.0);

        // ---------- заголовок ----------
        g2.setColor(Color.YELLOW);
        g2.setFont(new Font("Monospaced", Font.BOLD, 15));
        g2.drawString(String.format(java.util.Locale.US,
                "LEVEL %d   %.1f dBFS   HOLD %d   scale %d", last, db, maxHold, sm), 8, 20);

        // ---------- столбик ----------
        int bx = 12, bw = BAR_W - 24;
        g2.setColor(new Color(25, 25, 25));
        g2.fillRect(bx, TOP, bw, barH);
        g2.setColor(new Color(90, 90, 90));
        g2.drawRect(bx, TOP, bw, barH);

        int fh = (int) (frac * barH);
        if (fh > 0) {
            GradientPaint gp = new GradientPaint(
                    bx, TOP + barH, new Color(0, 200, 0),
                    bx, TOP, new Color(230, 40, 40));
            g2.setPaint(gp);
            g2.fillRect(bx + 1, TOP + barH - fh, bw - 1, fh);
        }

        // деления и подписи шкалы
        g2.setFont(new Font("Monospaced", Font.PLAIN, 9));
        for (int i = 0; i <= 8; i++) {
            int y = TOP + barH - i * barH / 8;
            g2.setColor(new Color(120, 120, 120));
            g2.drawLine(bx + bw, y, bx + bw + 5, y);
            g2.setColor(new Color(150, 150, 150));
            g2.drawString(String.valueOf(sm * i / 8), bx + bw + 8, y + 4);
        }

        // маркер пик-холда
        if (holdOn && maxHold > 0) {
            int hy = TOP + barH - (int) (Math.min(1.0, maxHold / (double) sm) * barH);
            g2.setColor(new Color(255, 140, 0));
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(bx, hy, bx + bw, hy);
        }

        // крупное число под столбиком
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.BOLD, 22));
        String s = String.valueOf(last);
        int sw = g2.getFontMetrics().stringWidth(s);
        g2.drawString(s, bx + (bw - sw) / 2, h - 10);

        // ---------- график истории ----------
        int gx = BAR_W + 34, gw = w - gx - 10;
        if (gw < 40) return;

        g2.setColor(new Color(35, 35, 35));
        for (int i = 1; i < 10; i++) g2.drawLine(gx + i * gw / 10, TOP, gx + i * gw / 10, TOP + barH);
        for (int i = 1; i < 8; i++) g2.drawLine(gx, TOP + i * barH / 8, gx + gw, TOP + i * barH / 8);
        g2.setColor(new Color(80, 80, 80));
        g2.drawRect(gx, TOP, gw, barH);

        if (count > 1) {
            if (holdOn && maxHold > 0) {
                int hy = TOP + barH - (int) (Math.min(1.0, maxHold / (double) sm) * barH);
                g2.setColor(new Color(255, 140, 0, 130));
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{6}, 0));
                g2.drawLine(gx, hy, gx + gw, hy);
            }
            g2.setColor(Color.GREEN);
            g2.setStroke(new BasicStroke(1.6f));
            Polygon p = new Polygon();
            for (int i = 0; i < count; i++) {
                int idx = (head - count + i + buf.length) % buf.length;
                int x = gx + (int) ((long) i * gw / (count - 1));
                int y = TOP + barH - (int) (Math.min(1.0, buf[idx] / (double) sm) * barH);
                p.addPoint(x, y);
            }
            g2.drawPolyline(p.xpoints, p.ypoints, p.npoints);
        }

        g2.setColor(new Color(160, 160, 160));
        g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g2.drawString(String.format(java.util.Locale.US, "окно %.1f с (20 отсч/с)", count * 0.05), gx + 4, h - 12);
    }
}