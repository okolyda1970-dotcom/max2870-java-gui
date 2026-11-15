package com.max2870.controller;

public class FFTProcessor {

    private double[] real;
    private double[] imag;
    private int n; // Размер FFT (степень двойки)

    public FFTProcessor(int size) {
        this.n = size;
        this.real = new double[size];
        this.imag = new double[size];
    }

    /**
     * Выполняет FFT над входными данными.
     * @param samples Входной массив коротких целых чисел (ADC values)
     * @return Массив амплитуд (Magnitude) в дБ
     */
    public double[] computeSpectrum(short[] samples) {
        if (samples.length == 0) return new double[0];

        // 1. Подготовка данных: центрирование вокруг 0
        for (int i = 0; i < n; i++) {
            if (i < samples.length) {
                real[i] = (samples[i] & 0xFFFF) - 2048.0;
            } else {
                real[i] = 0.0; // Zero-padding
            }
            imag[i] = 0.0;
        }

        // 2. Применение окна Хэмминга
        applyWindow(real);

        // 3. Собственно FFT
        fft(real, imag, false); // false = forward transform

        // 4. Расчет амплитудного спектра (только первая половина до Nyquist)
        int halfN = n / 2;
        double[] spectrum = new double[halfN];
        
        for (int i = 0; i < halfN; i++) {
            double mag = Math.sqrt(real[i]*real[i] + imag[i]*imag[i]);
            
            // Перевод в dBFS
            if (mag > 0) {
                spectrum[i] = 20.0 * Math.log10(mag / 2048.0);
            } else {
                spectrum[i] = -100.0; 
            }
        }
        
        return spectrum;
    }

    private void applyWindow(double[] data) {
        int len = data.length;
        for (int i = 0; i < len; i++) {
            // Hamming window: w(n) = 0.54 - 0.46 * cos(2*pi*n/(N-1))
            double w = 0.54 - 0.46 * Math.cos(2.0 * Math.PI * i / (len - 1));
            data[i] *= w;
        }
    }

    // Итеративный алгоритм Cooley-Tukey Radix-2 FFT
    private void fft(double[] re, double[] im, boolean inverse) {
        int N = re.length;
        if (N <= 1) return;

        int levels = (int)(Math.log(N) / Math.log(2));
        
        // --- Шаг 1: Bit-Reversal Permutation ---
        int j = 0;
        for (int i = 0; i < N - 1; i++) {
            if (i < j) {
                swap(re, i, j);
                swap(im, i, j);
            }
            int k = N >> 1;
            while (k >= 1 && j >= k) {
                j -= k;
                k >>= 1;
            }
            j += k;
        }

        // --- Шаг 2: Compute FFT stages ---
        for (int l = 1; l <= levels; l++) {
            int m = 1 << l;       // Size of current sub-transform
            int mh = m >> 1;      // Half size
            
            // Angle increment for twiddle factors
            double angle = -Math.PI / mh; 
            if (inverse) angle = -angle;

            double wr = Math.cos(angle);
            double wi = Math.sin(angle);

            for (int i = 0; i < N; i += m) {
                double trr = 1.0, tri = 0.0; // Twiddle factor accumulator
                
                // IMPORTANT FIX: Renamed inner loop variable from 'j' to 'k' 
                // to avoid conflict with the outer 'j' used in bit-reversal above.
                for (int k = 0; k < mh; k++) {
                    int u = i + k;
                    int v = i + k + mh;
                    
                    double txr = trr * re[v] - tri * im[v];
                    double txi = trr * im[v] + tri * re[v];
                    
                    re[v] = re[u] - txr;
                    im[v] = im[u] - txi;
                    re[u] = re[u] + txr;
                    im[u] = im[u] + txi;
                    
                    // Update twiddle factor: W^(k+1) = W^k * W^1
                    double temp = trr * wr - tri * wi;
                    tri = trr * wi + tri * wr;
                    trr = temp;
                }
            }
        }
        
        if (inverse) {
            for (int i = 0; i < N; i++) {
                re[i] /= N;
                im[i] /= N;
            }
        }
    }
    
    private void swap(double[] arr, int i, int j) {
        double temp = arr[i];
        arr[i] = arr[j];
        arr[j] = temp;
    }
}