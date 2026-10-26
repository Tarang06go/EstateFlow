package com.estateflow.report.pdf;

/** The EstateFlow mark for PDFs: same 48-unit geometry as the on-screen logo (corners are square in PDF). */
public final class PdfLogo {

    static final float[] GREEN = {0.141f, 0.357f, 0.271f};
    static final float[] LIME = {0.569f, 0.863f, 0.412f};
    static final float[] WHITE = {1f, 1f, 1f};

    private PdfLogo() {
    }

    /** Draws the mark with its top-left corner at (x, yTop), {@code size} points wide. */
    public static void draw(SimplePdf pdf, float x, float yTop, float size, float[] tile, float[] roof, float[] body) {
        float s = size / 48f;
        pdf.fillRect(x, yTop, size, size, tile);
        pdf.fillPolygon(new float[]{x + 8 * s, x + 24 * s, x + 40 * s, x + 40 * s, x + 24 * s, x + 8 * s},
                new float[]{yTop + 23 * s, yTop + 9 * s, yTop + 23 * s, yTop + 27 * s, yTop + 14 * s, yTop + 27 * s}, roof);
        pdf.fillRect(x + 13 * s, yTop + 26 * s, 22 * s, 13 * s, body);
        pdf.fillRect(x + 21 * s, yTop + 31 * s, 6 * s, 8 * s, roof);
        pdf.fillRect(x + 16 * s, yTop + 29 * s, 3 * s, 3 * s, tile);
        pdf.fillRect(x + 29 * s, yTop + 29 * s, 3 * s, 3 * s, tile);
    }

    /** Mark for a dark header band: lime tile, deep-green building. */
    public static void drawOnDark(SimplePdf pdf, float x, float yTop, float size) {
        draw(pdf, x, yTop, size, LIME, GREEN, WHITE);
    }
}
