package cs3205.subsystem3.health;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import cs3205.subsystem3.health.common.activities.ActivityBase;
import cs3205.subsystem3.health.common.miscellaneous.AppMessage;
import cs3205.subsystem3.health.common.miscellaneous.Value;
import cs3205.subsystem3.health.common.utilities.LogoutHelper;
import cs3205.subsystem3.health.common.utilities.SessionManager;
import cs3205.subsystem3.health.ui.camera.CameraActivity;
import cs3205.subsystem3.health.ui.heartrate.HeartRateReaderActivity;
import cs3205.subsystem3.health.ui.login.LoginActivity;
import cs3205.subsystem3.health.ui.nfc.NFCReaderActivity;
import cs3205.subsystem3.health.ui.step.StepSensorFragment;

public class MainActivity extends ActivityBase implements NavigationView.OnNavigationItemSelectedListener {

    private boolean isOnCreate;
    private TextView textView;
    private String username;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_main);
        isOnCreate = true;

        Intent intent = new Intent(this, LoginActivity.class);
        startActivityForResult(intent, 77);
    }

    private void loggedIn() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        View headerView = navigationView.getHeaderView(0);
        textView = (TextView) headerView.findViewById(R.id.tv_nav_header);
        textView.append(username);

        initFrag();
    }

    private void initFrag() {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.fragment_placeholder, new StepSensorFragment());
        //ft.add(R.id.fragment_placeholder, new StepSensorFragment());
        ft.commit();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.main, menu);
        getMenuInflater().inflate(R.menu.logout, menu);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (SessionManager.isLogoutTimerSet()) {
            SessionManager.cancelLogoutTimer();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (SessionManager.isLogoutTimerSet()) {
            SessionManager.resetLogoutTimer(this);
        } else {
            if (!isOnCreate) {
                SessionManager.setLogoutTimer(this);
            }
        }
        isOnCreate = false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        switch (item.getItemId()) {
            case R.id.action_settings:
                return true;
            case R.id.logout:
                LogoutHelper.logout(this, AppMessage.TOAST_MESSAGE_LOGOUT_SUCCESS);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_camera) {
            // Handle the camera action
            startCamera();

        } else if (id == R.id.nav_heartrate) {
            startHeartReader();
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void startHeartReader() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            //ask for authorisation
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BODY_SENSORS}, 50);
        } else {
            Intent startHearRateReadingIntent = new Intent(getApplicationContext(), HeartRateReaderActivity.class);
            startActivity(startHearRateReadingIntent);
        }
    }

    private void startCamera() {
        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) | ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)) != PackageManager.PERMISSION_GRANTED) {
            //ask for authorisation
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 40);
        } else {
            Intent cameraIntent = new Intent(this, CameraActivity.class);
            startActivity(cameraIntent);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 77) {
            if (resultCode == RESULT_OK) {
                username = data.getStringExtra(Value.KEY_VALUE_LOGIN_INTENT_USERNAME);
                loggedIn();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 50:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission Granted
                    Intent startHeartRateReadingIntent = new Intent(getApplicationContext(), HeartRateReaderActivity.class);
                    startActivity(startHeartRateReadingIntent);

                } else {
                    // Permission Denied
                    Toast.makeText(MainActivity.this, AppMessage.TOAST_MESSAGE_BODY_SENSOR_PERMISSION_DENIED, Toast.LENGTH_SHORT)
                            .show();
                }
                break;
            case 51:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission Granted
                    Intent startNFCReadingIntent = new Intent(getApplicationContext(), NFCReaderActivity.class);
                    startActivity(startNFCReadingIntent);

                } else {
                    // Permission Denied
                    Toast.makeText(MainActivity.this, AppMessage.TOAST_MESSAGE_NFC_PERMISSION_DENIED, Toast.LENGTH_SHORT)
                            .show();
                }
                break;
            case 40:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    Intent cameraIntent = new Intent(this, CameraActivity.class);
                    startActivity(cameraIntent);
                } else {
                    Toast.makeText(MainActivity.this, AppMessage.TOAST_MESSAGE_EXTERNAL_STORAGE_PERMISSION_DENIED, Toast.LENGTH_SHORT).show();
                }
                break;

            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}
