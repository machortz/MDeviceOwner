package org.sample.mdeviceowner;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    static final String LOG_TAG = "silentinstallsample";

    Button mInstallBtn;
    Button mUninstallBtn;

    PackageInstaller mPackageInstaller;
    DevicePolicyManager mDevicePolicyManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

        mPackageInstaller = getPackageManager().getPackageInstaller();
        mDevicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        mInstallBtn = (Button) findViewById(R.id.install_btn);
        mUninstallBtn = (Button) findViewById(R.id.uninstall_btn);

        mInstallBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int sessionId = createSession();
                if (sessionId < 0) return;
                writeSession(sessionId);
                commitSession(sessionId);
            }
        });

        mUninstallBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, MainActivity.class);
                PendingIntent sender = PendingIntent.getActivity(MainActivity.this, 0, intent, 0);
                mPackageInstaller.uninstall("org.mozilla.firefox", sender.getIntentSender());
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mDevicePolicyManager.isDeviceOwnerApp(getPackageName())) {
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
            alertDialogBuilder.setMessage("Device Ownerになってください");
            alertDialogBuilder.setPositiveButton("終了する",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            MainActivity.this.finish();
                        }
                    });
            AlertDialog alertDialog = alertDialogBuilder.create();
            alertDialog.show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private int createSession() {
        final PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setInstallLocation(PackageInfo.INSTALL_LOCATION_INTERNAL_ONLY);

        PackageInstaller.SessionCallback sessionCallback = new
                PackageInstaller.SessionCallback() {
                    @Override
                    public void onCreated(int i) {
                        Log.v(LOG_TAG, "SessionCallback#onCreated : session id = " + i);
                    }

                    @Override
                    public void onBadgingChanged(int i) {

                        Log.v(LOG_TAG, "SessionCallback#onBadgingChanged : session id = " + i);
                    }

                    @Override
                    public void onActiveChanged(int i, boolean b) {
                        Log.v(LOG_TAG, "SessionCallback#onActiveChanged : session id = " + i + ",activity :" + b);
                    }

                    @Override
                    public void onProgressChanged(int i, float p) {
                        Log.v(LOG_TAG, "SessionCallback#onProgressChanged : session id = " + i + ",progress : " + p);
                    }

                    @Override
                    public void onFinished(int i, boolean b) {
                        Log.v(LOG_TAG, "SessionCallback#onFinished : session id = " + i + ", result :" + (b ? "success" : "failure"));
                        mPackageInstaller.unregisterSessionCallback(this);
                    }
                };
        mPackageInstaller.registerSessionCallback(sessionCallback);

        int sessionId = -1;
        try {
            sessionId = mPackageInstaller.createSession(params);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return sessionId;
    }

    private int writeSession(final int sessionId) {
        long sizeBytes = -1;
        final String splitName = "hoge";
        final String apkPath = "/sdcard/base.apk";

        final File file = new File(apkPath);
        if (file.isFile()) {
            sizeBytes = file.length();
        }
        Log.v(LOG_TAG, "writeSession() apk size :" + sizeBytes);

        PackageInstaller.Session session = null;
        InputStream in = null;
        OutputStream out = null;
        try {
            try {
                session = mPackageInstaller.openSession(sessionId);
                in = new FileInputStream(apkPath);
                out = session.openWrite(splitName, 0, sizeBytes);
                int total = 0;
                byte[] buffer = new byte[65536];
                int c;
                while ((c = in.read(buffer)) != -1) {
                    total += c;
                    out.write(buffer, 0, c);
                }
                session.fsync(out);
            } catch (IOException e) {
            }

            return 0;
        } finally {

            try {
                out.close();
                in.close();
                session.close();
            } catch (IOException e) {
            }
        }
    }

    private void commitSession(final int sessionId) {
        PackageInstaller.Session session = null;
        try {
            Log.v(LOG_TAG, "commitSession(): session id = "+sessionId );
            session = mPackageInstaller.openSession(sessionId);
            Intent intent = new Intent(this, MainActivity.class);
            PendingIntent sender = PendingIntent.getActivity(this, 0, intent, 0);
            session.commit(sender.getIntentSender());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            session.close();
        }
    }
}
