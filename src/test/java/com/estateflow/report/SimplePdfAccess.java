package com.estateflow.report;

/** Test bridge to the package-private escape helper via reflection-free duplication of the public contract. */
final class SimplePdfAccess {
    private SimplePdfAccess() {
    }

    static String escape(String value) {
        try {
            var m = Class.forName("com.estateflow.report.pdf.SimplePdf").getDeclaredMethod("escape", String.class);
            m.setAccessible(true);
            return (String) m.invoke(null, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
