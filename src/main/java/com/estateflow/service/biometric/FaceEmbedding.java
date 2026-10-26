package com.estateflow.service.biometric;

/** Normalized transient feature vector. Callers cannot mutate the stored values. */
public final class FaceEmbedding implements AutoCloseable {
    private final float[] values;
    public FaceEmbedding(float[] values) {
        if (values == null || values.length != 128) throw new IllegalArgumentException("Expected a 128-value SFace feature.");
        this.values = normalize(values);
    }
    float[] copyValues() { return values.clone(); }
    public static FaceEmbedding average(Iterable<FaceEmbedding> samples, double consistencyThreshold) {
        float[] sum = new float[128]; int count = 0;
        for (FaceEmbedding sample : samples) {
            if (sample == null) throw new IllegalArgumentException("A face sample is missing.");
            sum = add(sum, sample.values); count++;
        }
        if (count < 2) throw new IllegalArgumentException("At least two samples are required.");
        for (int i = 0; i < sum.length; i++) sum[i] /= count;
        float[] average = normalize(sum);
        try {
            for (FaceEmbedding sample : samples) if (cosine(average, sample.values) < consistencyThreshold)
                throw new IllegalArgumentException("Face samples were inconsistent; try again in steady lighting.");
            return new FaceEmbedding(average);
        } finally { java.util.Arrays.fill(average,0f); java.util.Arrays.fill(sum,0f); }
    }
    public double similarity(FaceEmbedding other) { return cosine(values, other.values); }
    private static float[] add(float[] a, float[] b) { for (int i=0;i<a.length;i++) a[i]+=b[i]; return a; }
    private static float[] normalize(float[] in) {
        float[] out = in.clone(); double norm=0;
        for(float x:out) { if (!Float.isFinite(x)) throw new IllegalArgumentException("Invalid face feature."); norm += (double)x*x; }
        if (norm < 1e-12) throw new IllegalArgumentException("Invalid face feature.");
        double scale=1/Math.sqrt(norm); for(int i=0;i<out.length;i++) out[i]=(float)(out[i]*scale); return out;
    }
    private static double cosine(float[] a,float[] b) { double s=0; for(int i=0;i<a.length;i++) s+=(double)a[i]*b[i]; return s; }
    @Override public String toString() { return "FaceEmbedding[redacted]"; }
    @Override public void close() { java.util.Arrays.fill(values, 0f); }
}
