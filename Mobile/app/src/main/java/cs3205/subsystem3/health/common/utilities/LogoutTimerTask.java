package cs3205.subsystem3.health.common.utilities;

import android.content.Context;

import java.util.TimerTask;

import cs3205.subsystem3.health.common.miscellaneous.AppMessage;

/**
 * Created by danwen on 11/10/17.
 */

public class LogoutTimerTask extends TimerTask {
    private Context context;

    public LogoutTimerTask(Context context) {
        this.context = context;
    }

    @Override
    public void run() {
        //redirect user to login screen
        LogoutHelper.logout(context, AppMessage.TOAST_MESSAGE_SESSION_EXPIRED);
    }
}
