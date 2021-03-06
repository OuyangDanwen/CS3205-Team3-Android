package cs3205.subsystem3.health.common.core;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Created by Yee on 09/28/17.
 */

public abstract class Timestamp {
    public static final String DATE_FORMAT = "yyyy-MM-dd_HH:mm:ss";
    public static final long EPOCH_DIFF = 86400000;

    /**
     * @return milliseconds since 1.1.1970 for today 0:00:00 local timezone
     */
    public static long getToday() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(System.currentTimeMillis());
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /**
     * @return milliseconds since 1.1.1970 for tomorrow 0:00:01 local timezone
     */
    public static long getTomorrow() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(System.currentTimeMillis());
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 1);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.DATE, 1);
        return c.getTimeInMillis();
    }

    public static long getEpochTimeStamp() {
        return System.currentTimeMillis() / 1000;
    }

    public static long getEpochTimeMillis(){
        return System.currentTimeMillis();
    }

    public static String getFormattedCurrentTimestamp(){
        return new SimpleDateFormat(DATE_FORMAT).format(Calendar.getInstance().getTime());
    }
}
