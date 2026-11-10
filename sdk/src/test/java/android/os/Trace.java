package android.os;

/**
 * Stands in for the platform's tracing in plain JVM unit tests. The Compose runtime marks every
 * composition with a trace section, and the Android stubs the tests run against throw from any
 * method; with this class ahead of them on the test classpath, a test can run a composition
 * without a device. See OnCardFormLeavingTest.
 */
public final class Trace {
    private Trace() {}

    public static void beginSection(String sectionName) {}

    public static void endSection() {}

    public static boolean isEnabled() {
        return false;
    }

    public static void beginAsyncSection(String methodName, int cookie) {}

    public static void endAsyncSection(String methodName, int cookie) {}

    public static void setCounter(String counterName, long counterValue) {}
}
