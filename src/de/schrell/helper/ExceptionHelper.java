package de.schrell.helper;

import java.io.PrintWriter;
import java.io.StringWriter;

public class ExceptionHelper {

    public static String getStackTrace(final Throwable e) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

}
