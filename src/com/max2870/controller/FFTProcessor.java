package com.max2870.controller;

public class FFTProcessor {
    private final double[] real;
    private final double[] imag;
    private final int n;

    public FFTProcessor(int size) {
        this.n = size;
        this.real = new double[size];
        this.imag = new double[size];
    }

    public double[] computeSpectrum(short[] samples) {
        if (samples == null || samples.length == 0) return new double[0];
        long sum = 0;
        for (short s : samples) sum += (s & 0xFFFF);
        double mean = sum / (double) samples.length;
        for (int i = 0; i < n; i++) {
            real[i] = (i < samples.length) ? ((samples[i] & 0xFFFF) - mean) : 0.0;
            imag[i] = 0.0;
        }
        applyWindow(real);
        fft(real, imag, false);
        int halfN = n / 2;
        double[] spectrum = new double[halfN];
        for (int i = 0; i < halfN; i++) {
            double mag = Math.sqrt(real[i] * real[i] + imag[i] * imag[i]);
            spectrum[i] = (mag > 0) ? 20.0 * Math.log10(mag / 2048.0) : -100.0;
        }
        return spectrum;
    }
    private void applyWindow(double[] data) {
        int len = data.length;
        for (int i = 0; i < len; i++) {
            data[i] *= 0.54 - 0.46 * Math.cos(2.0 * Math.PI * i / (len - 1));
        }
    }

    private void fft(double[] re, double[] im, boolean inverse) {
        int N = re.length;
        if (N <= 1) return;
        int levels = (int) (Math.log(N) / Math.log(2));
        int j = 0;
        for (int i = 0; i < N - 1; i++) {
            if (i < j) { swap(re, i, j); swap(im, i, j); }
            int k = N >> 1;
            while (k >= 1 && j >= k) { j -= k; k >>= 1; }
            j += k;
        }
        for (int l = 1; l <= levels; l++) {
            int m = 1 << l, mh = m >> 1;
            double angle = -Math.PI / mh;
            if (inverse) angle = -angle;
            double wr = Math.cos(angle), wi = Math.sin(angle);
            for (int i = 0; i < N; i += m) {
                double trr = 1.0, tri = 0.0;
                for (int k = 0; k < mh; k++) {
                    int u = i + k, v = i + k + mh;
                    double txr = trr * re[v] - tri * im[v];
                    double txi = trr * im[v] + tri * re[v];
                    re[v] = re[u] - txr; im[v] = im[u] - txi;
                    re[u] = re[u] + txr; im[u] = im[u] + txi;
                    double temp = trr * wr - tri * wi;
                    tri = trr * wi + tri * wr;
                    trr = temp;
                }
            }
        }
        if (inverse) for (int i = 0; i < N; i++) { re[i] /= N; im[i] /= N; }
    }

    private void swap(double[] arr, int i, int j) {
        double t = arr[i]; arr[i] = arr[j]; arr[j] = t;
    }
}