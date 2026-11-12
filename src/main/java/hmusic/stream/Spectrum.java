package hmusic.stream;

/**
 * Analyse spectrale (FFT) d'un bloc d'échantillons audio,
 * regroupée en bandes de fréquences logarithmiques pour un affichage « égaliseur ».
 */
public final class Spectrum {

    public static final int SIZE = 1024;

    private Spectrum() {}

    /**
     * @param samples    SIZE échantillons mono entre -1 et 1
     * @param sampleRate fréquence d'échantillonnage (ex. 44100)
     * @param bands      nombre de bandes souhaitées
     * @return niveau de chaque bande entre 0 et 1
     */
    public static float[] bands(float[] samples, int sampleRate, int bands) {
        double[] re = new double[SIZE];
        double[] im = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            double hann = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (SIZE - 1)); // fenêtre de Hann
            re[i] = samples[i] * hann;
        }
        fft(re, im);

        float[] out = new float[bands];
        double minF = 40, maxF = Math.min(16000, sampleRate / 2.0);
        for (int b = 0; b < bands; b++) {
            double f0 = minF * Math.pow(maxF / minF, b / (double) bands);
            double f1 = minF * Math.pow(maxF / minF, (b + 1) / (double) bands);
            int i0 = Math.max(1, (int) Math.floor(f0 * SIZE / sampleRate));
            int i1 = Math.max(i0 + 1, (int) Math.ceil(f1 * SIZE / sampleRate));
            double peak = 0;
            for (int i = i0; i < i1 && i < SIZE / 2; i++) peak = Math.max(peak, Math.hypot(re[i], im[i]));
            double db = 20 * Math.log10(peak / (SIZE / 4.0) + 1e-9); // ~ -90 … 0 dB
            out[b] = (float) Math.max(0, Math.min(1, (db + 60) / 60));
        }
        return out;
    }

    /** FFT itérative radix-2 (Cooley-Tukey), en place. */
    private static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                double t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            double wr = Math.cos(ang), wi = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cr = 1, ci = 0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k, b = i + k + len / 2;
                    double xr = re[b] * cr - im[b] * ci;
                    double xi = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - xr; im[b] = im[a] - xi;
                    re[a] += xr; im[a] += xi;
                    double ncr = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = ncr;
                }
            }
        }
    }
}
