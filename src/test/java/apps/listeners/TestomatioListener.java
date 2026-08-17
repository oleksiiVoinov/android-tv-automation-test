package apps.listeners;

import apps.tv.api.testomatio.TestomatioConfig;
import apps.tv.api.testomatio.TestomatioReporter;
import org.json.JSONObject;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.lang.reflect.Method;

/**
 * Reports every TestNG result into a Testomat.io Run.
 *
 * <p>Enabled with {@code -Dtestomatio=true} (see {@link TestomatioConfig}); completely inert otherwise,
 * so it is safe to keep the listener registered for all test tasks.
 *
 * <p>The Run is created once per JVM when the suite starts and is deliberately <b>left open</b> at the end —
 * the person who reviews the regression closes it in Testomat.io.
 */
public class TestomatioListener implements ISuiteListener, ITestListener {

    @Override
    public void onStart(ISuite suite) {
        TestomatioReporter.start(suite.getName());
    }

    @Override
    public void onFinish(ISuite suite) {
        TestomatioReporter reporter = TestomatioReporter.current();
        if (reporter != null) {
            reporter.complete();
        }
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        report(result, "passed");
    }

    @Override
    public void onTestFailure(ITestResult result) {
        report(result, "failed");
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        report(result, "skipped");
    }

    @Override
    public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
        report(result, "failed");
    }

    private void report(ITestResult result, String status) {
        TestomatioReporter reporter = TestomatioReporter.current();
        if (reporter == null) {
            return;
        }
        try {
            Method method = result.getMethod().getConstructorOrMethod().getMethod();
            String className = result.getTestClass().getName();
            String methodName = method.getName();
            int parameterCount = method.getParameterCount();

            String fallbackTitle = result.getMethod().getDescription() == null
                    || result.getMethod().getDescription().isBlank()
                    ? methodName
                    : result.getMethod().getDescription();

            Throwable error = result.getThrowable();
            String message = error == null ? null : String.valueOf(error.getMessage());
            String stack = error == null ? null : stackTrace(error);

            reporter.report(
                    className,
                    methodName,
                    parameterCount,
                    firstParameter(result),
                    fallbackTitle,
                    result.getTestContext().getName(),
                    status,
                    Math.max(0L, result.getEndMillis() - result.getStartMillis()),
                    message,
                    stack,
                    example(result));
        } catch (Exception e) {
            System.out.println("[testomatio] listener error: " + e);
        }
    }

    /**
     * First data-provider value as a string — used to pick the per-value case
     * (protocol name, Android version). Null for non data-driven tests.
     */
    private String firstParameter(ITestResult result) {
        Object[] parameters = result.getParameters();
        if (parameters == null || parameters.length == 0 || parameters[0] == null) {
            return null;
        }
        return String.valueOf(parameters[0]);
    }

    /** Data-provider parameters of the current invocation, so parallel data rows are distinguishable. */
    private JSONObject example(ITestResult result) {
        Object[] parameters = result.getParameters();
        if (parameters == null || parameters.length == 0) {
            return null;
        }
        JSONObject example = new JSONObject();
        for (int i = 0; i < parameters.length; i++) {
            example.put("param" + (i + 1), String.valueOf(parameters[i]));
        }
        return example;
    }

    private static String stackTrace(Throwable error) {
        StringBuilder builder = new StringBuilder(error.toString());
        for (StackTraceElement element : error.getStackTrace()) {
            builder.append("\n\tat ").append(element);
        }
        Throwable cause = error.getCause();
        if (cause != null && cause != error) {
            builder.append("\nCaused by: ").append(cause);
            for (StackTraceElement element : cause.getStackTrace()) {
                builder.append("\n\tat ").append(element);
            }
        }
        return builder.toString();
    }
}
