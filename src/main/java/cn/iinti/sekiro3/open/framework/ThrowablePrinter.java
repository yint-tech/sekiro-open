package cn.iinti.sekiro3.open.framework;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class ThrowablePrinter {
    /**
     * Caption  for labeling causative exception stack traces
     */
    private static final String CAUSE_CAPTION = "Caused by: ";

    /**
     * Caption for labeling suppressed exception stack traces
     */
    private static final String SUPPRESSED_CAPTION = "Suppressed: ";



    public static void printStackTrace(Collection<String> out, Throwable throwable) {
        // Guard against malicious overrides of Throwable.equals by
        // using a Set with identity equality semantics.
        Set<Throwable> dejaVu =
                Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        dejaVu.add(throwable);


        // Print our stack trace
        //s.println(this);
        out.add(throwable.toString());
        StackTraceElement[] trace = throwable.getStackTrace();
        for (StackTraceElement traceElement : trace)
            out.add("\tat " + traceElement);

        // Print suppressed exceptions, if any
        for (Throwable se : throwable.getSuppressed())
            printEnclosedStackTrace(out, se, trace, SUPPRESSED_CAPTION, "\t", dejaVu);

        // Print cause, if any
        Throwable ourCause = throwable.getCause();
        if (ourCause != null)
            printEnclosedStackTrace(out, ourCause, trace, CAUSE_CAPTION, "", dejaVu);

    }

    /**
     * Print our stack trace as an enclosed exception for the specified
     * stack trace.
     */
    private static void printEnclosedStackTrace(Collection<String> out, Throwable throwable,
                                                StackTraceElement[] enclosingTrace,
                                                String caption,
                                                String prefix,
                                                Set<Throwable> dejaVu) {

        if (dejaVu.contains(throwable)) {
            out.add("\t[CIRCULAR REFERENCE:" + throwable + "]");
        } else {
            dejaVu.add(throwable);
            // Compute number of frames in common between this and enclosing trace
            StackTraceElement[] trace = throwable.getStackTrace();
            int m = trace.length - 1;
            int n = enclosingTrace.length - 1;
            while (m >= 0 && n >= 0 && trace[m].equals(enclosingTrace[n])) {
                m--;
                n--;
            }
            int framesInCommon = trace.length - 1 - m;

            // Print our stack trace
            out.add(prefix + caption + throwable);
            for (int i = 0; i <= m; i++)
                out.add(prefix + "\tat " + trace[i]);
            if (framesInCommon != 0)
                out.add(prefix + "\t... " + framesInCommon + " more");

            // Print suppressed exceptions, if any
            for (Throwable se : throwable.getSuppressed())
                printEnclosedStackTrace(out, se, trace, SUPPRESSED_CAPTION,
                        prefix + "\t", dejaVu);

            // Print cause, if any
            Throwable ourCause = throwable.getCause();
            if (ourCause != null)
                printEnclosedStackTrace(out, ourCause, trace, CAUSE_CAPTION, prefix, dejaVu);
        }
    }
}
