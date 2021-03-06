package cs3205.subsystem3.health.logic.step;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.support.annotation.Nullable;

import java.util.Date;

import cs3205.subsystem3.health.BuildConfig;
import cs3205.subsystem3.health.common.core.Timestamp;
import cs3205.subsystem3.health.common.logger.Log;
import cs3205.subsystem3.health.common.logger.Tag;
import cs3205.subsystem3.health.data.source.local.Repository;
import cs3205.subsystem3.health.data.source.local.StepsDB;
import cs3205.subsystem3.health.model.Steps;

import static cs3205.subsystem3.health.common.core.SharedPreferencesConstant.ACTION;
import static cs3205.subsystem3.health.common.core.SharedPreferencesConstant.ACTION_PAUSE;
import static cs3205.subsystem3.health.common.core.SharedPreferencesConstant.DIVISOR;
import static cs3205.subsystem3.health.common.core.SharedPreferencesConstant.EVENT_TIMESTAMP;
import static cs3205.subsystem3.health.common.core.SharedPreferencesConstant.FILENAME;
import static cs3205.subsystem3.health.common.core.SharedPreferencesConstant.OFFSET;
import static cs3205.subsystem3.health.common.core.SharedPreferencesConstant.PAUSE_COUNT;
import static cs3205.subsystem3.health.common.core.SharedPreferencesConstant.STEPS;
import static cs3205.subsystem3.health.common.core.SharedPreferencesConstant.STEPS_STOPPED;
import static cs3205.subsystem3.health.common.core.SharedPreferencesConstant.SYSTEM_TIMESTAMP;
import static cs3205.subsystem3.health.logic.step.StepsUtil.updateSteps;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Created by Yee on 09/28/17.
 */

public class StepSensorService extends Service implements SensorEventListener {

    public static final String TAG = Tag.STEP_SENSOR;

    private final static int NOTIFICATION_ID = 1;
    private final static long MICROSECONDS_IN_ONE_MINUTE = 60000000;
    private final static long SAVE_OFFSET_TIME = AlarmManager.INTERVAL_HOUR;
    private final static int SAVE_OFFSET_STEPS = 500;

    private static int steps;
    private static int lastSaveSteps;
    private static long lastSaveTime;

    private String sessionName = "session";
    private Steps data;
    private long divisor = 0;
    private long offset = 0;

    public final static String ACTION_UPDATE_NOTIFICATION = "updateNotificationState";

    private void reRegisterSensor() {
        if (BuildConfig.DEBUG) Log.i(TAG, "re-register sensor listener");
        SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        try {
            sm.unregisterListener(this);
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG, e.getMessage(), e);
            e.printStackTrace();
        }

        if (BuildConfig.DEBUG) {
            Log.i(TAG, "step sensors: " + sm.getSensorList(Sensor.TYPE_STEP_COUNTER).size());
            if (sm.getSensorList(Sensor.TYPE_STEP_COUNTER).size() < 1) return; // emulator
            Log.i(TAG, "default: " + sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER).getName());
        }

        // enable batching with delay of max 5 min
        sm.registerListener(this, sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER),
                SensorManager.SENSOR_DELAY_NORMAL, (int) (5 * MICROSECONDS_IN_ONE_MINUTE));
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.values[0] > Integer.MAX_VALUE) {
            if (BuildConfig.DEBUG)
                Log.i(TAG, "probably not a real value: " + sensorEvent.values[0]);
            return;
        } else {
            SharedPreferences prefs = getSharedPreferences(STEPS, Context.MODE_PRIVATE);
            getEventOffset(prefs, sensorEvent.timestamp, Timestamp.getEpochTimeMillis());

            steps = (int) sensorEvent.values[0];

            if (prefs.getBoolean(STEPS_STOPPED, false) == false) {
                long eventTimestamp = getTime(sensorEvent.timestamp);
                data = updateSteps(data, eventTimestamp);
                Log.i(TAG, " updatedSteps | " + data.toString());
                updateIfNecessary();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (BuildConfig.DEBUG) Log.i(TAG, sensor.getName() + " accuracy changed: " + accuracy);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG) Log.i(TAG, "StepSensorService onCreate");
        reRegisterSensor();
        //updateNotificationState();
        SharedPreferences prefs = getSharedPreferences(STEPS, Context.MODE_PRIVATE);
        retrieveSteps(prefs);
    }

    @Override
    public void onTaskRemoved(final Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        if (BuildConfig.DEBUG) Log.i(TAG, "sensor service task removed");

        SharedPreferences prefs = getSharedPreferences(STEPS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(STEPS_STOPPED, false)) {
            prefs.edit().putBoolean(STEPS_STOPPED, true).commit();
        }

        // Restart service in 500 ms
        /** ((AlarmManager) getSystemService(Context.ALARM_SERVICE))
         .set(AlarmManager.RTC, System.currentTimeMillis() + 500, PendingIntent
         .getService(this, 3, new Intent(this, StepSensorService.class), 0));
         */
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (BuildConfig.DEBUG) Log.i(TAG, "SensorListener onDestroy");
        try {
            SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
            sm.unregisterListener(this);
            SharedPreferences prefs = getSharedPreferences(STEPS, Context.MODE_PRIVATE);
            if (prefs.getBoolean(STEPS_STOPPED, false)) {
                prefs.edit().putBoolean(STEPS_STOPPED, true).commit();

                //save to file
                String filename = prefs.getString(FILENAME, String.valueOf(Timestamp.getEpochTimeMillis()));
                Repository.writeFile(getBaseContext(), getApplication().getFilesDir().getAbsolutePath(), filename, data);
            }
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG, e.getMessage(), e);
            e.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        SharedPreferences prefs = getSharedPreferences(STEPS, Context.MODE_PRIVATE);

        if (intent != null && ACTION_PAUSE.equals(intent.getStringExtra(ACTION))) {
            prefs.edit().putBoolean(STEPS_STOPPED, false).commit();
            if (BuildConfig.DEBUG)
                Log.i(TAG, "onStartCommand action: " + intent.getStringExtra(ACTION));
            if (steps == 0) {
                StepsDB db = new StepsDB(this);
                steps = db.getCurrentSteps();
                db.close();
            }
            if (prefs.contains(PAUSE_COUNT)) { // resume counting
                int difference = steps -
                        prefs.getInt(PAUSE_COUNT, steps); // number of steps taken during the pause
                StepsDB db = new StepsDB(this);
                db.addToLastEntry(-difference);
                db.close();
                prefs.edit().remove(PAUSE_COUNT).commit();
                //updateNotificationState();
            } else { // pause counting
                // cancel restart
                ((AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE))
                        .cancel(PendingIntent.getService(getApplicationContext(), 2,
                                new Intent(this, StepSensorService.class),
                                PendingIntent.FLAG_UPDATE_CURRENT));
                prefs.edit().putInt(PAUSE_COUNT, steps).commit();
                //updateNotificationState();
                stopSelf();
                return START_NOT_STICKY;
            }
        }

        if (intent != null && intent.getBooleanExtra(ACTION_UPDATE_NOTIFICATION, false)) {
            //updateNotificationState();
        } else {
            retrieveSteps(prefs);
            updateIfNecessary();
        }

        // restart service every hour to save the current step count
        ((AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE))
                .set(AlarmManager.RTC, Math.min(Timestamp.getTomorrow(),
                        System.currentTimeMillis() + AlarmManager.INTERVAL_HOUR), PendingIntent
                        .getService(getApplicationContext(), 2,
                                new Intent(this, StepSensorService.class),
                                PendingIntent.FLAG_UPDATE_CURRENT));

        return START_STICKY;
    }

    private void updateIfNecessary() {
        SharedPreferences prefs = getApplication().getSharedPreferences(STEPS, Context.MODE_PRIVATE);

        if (prefs.getBoolean(STEPS_STOPPED, false) == false) {
            if (steps > lastSaveSteps + SAVE_OFFSET_STEPS ||
                    (steps > 0 && System.currentTimeMillis() > lastSaveTime + SAVE_OFFSET_TIME)) {
                if (BuildConfig.DEBUG) Log.i(TAG,
                        "saving steps: steps=" + steps + " lastSave=" + lastSaveSteps +
                                " lastSaveTime=" + new Date(lastSaveTime));
                StepsDB db = new StepsDB(this);
                if (db.getSteps(Timestamp.getToday()) == Integer.MIN_VALUE) {
                    int pauseDifference = steps -
                            getSharedPreferences(STEPS, Context.MODE_PRIVATE)
                                    .getInt(PAUSE_COUNT, steps);
                    db.insertNewDay(Timestamp.getToday(), steps - pauseDifference);
                    if (pauseDifference > 0) {
                        // update pauseCount for the new day
                        getSharedPreferences(STEPS, Context.MODE_PRIVATE).edit()
                                .putInt(PAUSE_COUNT, steps).commit();
                    }
                }
                int prevSteps = db.getCurrentSteps();
                db.saveCurrentSteps(steps);
                db.close();
                lastSaveSteps = steps;
                lastSaveTime = System.currentTimeMillis();
                //updateNotificationState();
            }
        }
    }

    private void retrieveSteps(SharedPreferences prefs) {
        if (prefs.getBoolean(STEPS_STOPPED, false) == false) {
            String filename = prefs.getString(FILENAME, String.valueOf(Timestamp.getEpochTimeMillis()));
            prefs.edit().putString(FILENAME, filename);
            data = Repository.getFile(getApplication().getFilesDir().getAbsolutePath(), filename, sessionName);
        } else {
            data = new Steps(0, sessionName);
        }
    }

    private long getTime(long eventTimestamp) {
        long eventTimeMillis;
        if(divisor == 0) {
            eventTimeMillis = Timestamp.getEpochTimeMillis();
        } else {
            eventTimeMillis = (eventTimestamp / divisor) + offset;
        }
        return eventTimeMillis;
    }

    private void getEventOffset(SharedPreferences prefs, long nanoEventTimestamp, long sysTimeMillis) {
        if (!prefs.contains(OFFSET) && !prefs.contains(DIVISOR)) {
            if (!prefs.contains(EVENT_TIMESTAMP)) {
                prefs.edit().putLong(EVENT_TIMESTAMP, nanoEventTimestamp);
                prefs.edit().putLong(SYSTEM_TIMESTAMP, sysTimeMillis);
                return;
            }

            long event1TimeStamp = prefs.getLong(EVENT_TIMESTAMP, Timestamp.getEpochTimeMillis());
            long sysTimeMillis1TimeStamp = prefs.getLong(SYSTEM_TIMESTAMP, Timestamp.getEpochTimeMillis());

            long timestampDelta = nanoEventTimestamp - event1TimeStamp;
            long sysTimeDelta = sysTimeMillis - sysTimeMillis1TimeStamp;

            long divisor;
            long offset;
            if (timestampDelta/sysTimeDelta > 1000) { // in reality ~1 vs ~1,000,000
                // timestamps are in nanoseconds
                divisor = 1000000;
            } else {
                // timestamps are in milliseconds
                divisor = 1;
            }

            offset = sysTimeMillis1TimeStamp - (event1TimeStamp / divisor);

            prefs.edit().putLong(OFFSET, offset);
            prefs.edit().putLong(DIVISOR, divisor);
        } else {
            offset = prefs.getLong(OFFSET, 1000);
            divisor = prefs.getLong(DIVISOR, 1);
        }
    }
}
