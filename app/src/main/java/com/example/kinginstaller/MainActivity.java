package com.example.kinginstaller;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.InstallSourceInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final int FILE_SELECT_CODE = 1;
    private static final String[] KNOWN_INSTALLER_PACKAGES = new String[] {
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.miui.packageinstaller",
            "com.miui.securitycenter",
            "com.samsung.android.packageinstaller",
            "com.heytap.market",
            "com.oppo.market",
            "com.xiaomi.market"
    };
    boolean oppoTrickEnabled, rootTrickEnabled, forceRootEnabled;
    private String pendingVerifyPackageName;
    private long pendingVerifyStartMs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (isGooglePackageExist()) {
            TextView tv = findViewById(R.id.textViewError);
            tv.setText(R.string.google_package_installer_is_installed);
        } else {
            TextView tv = findViewById(R.id.textViewError);
            tv.setText(R.string.missing_google_package_installer);
        }
        Button btnSelect = findViewById(R.id.selectButton);
        btnSelect.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try { showFileChooser(); }
                catch (Exception e) {
                    TextView tv = findViewById(R.id.textViewError);
                    tv.setText(e.toString());
                }
            }
        });

        TextView siteAnnexhack = findViewById(R.id.site_annexhack);
        siteAnnexhack.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    String url = "https://inceptive.ru";
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse(url));
                    startActivity(i);
                } catch (Exception e) {
                    TextView tv = findViewById(R.id.textViewError);
                    tv.setText(e.toString());
                }
            }
        });

        //MAKE OPPO TRICK DISABLED AS DEFAULT AND AVOID HAVE AN UNUSEFUL FAKE INSTALLER
        SharedPreferences oppoTrickStatus = getSharedPreferences("oppo_trick_value", Activity.MODE_PRIVATE);
        oppoTrickEnabled = oppoTrickStatus.getBoolean("oppo_trick_value",false);
        CheckBox oppoTrick = (CheckBox) findViewById(R.id.checkBox1);
        oppoTrick.setChecked(oppoTrickEnabled);
        //MAKE ROOT TRICK DISABLED AS DEFAULT
        SharedPreferences rootTrickStatus = getSharedPreferences("root_trick_value", Activity.MODE_PRIVATE);
        rootTrickEnabled = rootTrickStatus.getBoolean("root_trick_value",false);
        CheckBox rootTrick = (CheckBox) findViewById(R.id.checkBox2);
        rootTrick.setChecked(rootTrickEnabled);
        oppoTrick();

        oppoTrick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                oppoTrickEnabled = !oppoTrickEnabled;
                SharedPreferences.Editor oppoEditor = oppoTrickStatus.edit();
                oppoEditor.putBoolean("oppo_trick_value", oppoTrickEnabled);
                oppoEditor.apply();
                oppoTrick.setChecked(oppoTrickEnabled);
                //Switch off root flags
                SharedPreferences.Editor rootEditor = rootTrickStatus.edit();
                rootEditor.putBoolean("root_trick_value", false);
                rootEditor.apply();
                rootTrick.setChecked(false);
                oppoTrick();

                Log.d("oppo button", "oppo value is " + oppoTrickStatus.getBoolean("oppo_trick_value", false));
                Log.d("root button", "root value is " + rootTrickStatus.getBoolean("root_trick_value", false));
            }
        });

        rootTrick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isDeviceRooted();
                TextView tv = findViewById(R.id.textViewError);
                if (!isDeviceRooted()) {
                    Toast.makeText(getBaseContext(), R.string.device_not_rooted, Toast.LENGTH_SHORT).show();
                    //Switch off root flags
                    SharedPreferences.Editor rootEditor = rootTrickStatus.edit();
                    rootEditor.putBoolean("root_trick_value", false);
                    rootEditor.apply();
                    rootTrick.setChecked(false);
                } else if (isGooglePackageExist() && !forceRootEnabled) {
                    tv.setText(R.string.root_method_warning);
                    //Switch off root flags
                    SharedPreferences.Editor rootEditor = rootTrickStatus.edit();
                    rootEditor.putBoolean("root_trick_value", false);
                    rootEditor.apply();
                    rootTrick.setChecked(false);
                    forceRootEnabled = true;
                } else {
                    tv.setText("");
                    forceRootEnabled = !rootTrickEnabled;
                    rootTrickEnabled = !rootTrickEnabled;
                    SharedPreferences.Editor rootEditor = rootTrickStatus.edit();
                    rootEditor.putBoolean("root_trick_value", rootTrickEnabled);
                    rootEditor.apply();
                    rootTrick.setChecked(rootTrickEnabled);
                    //Switch off oppo flags
                    SharedPreferences.Editor oppoEditor = oppoTrickStatus.edit();
                    oppoEditor.putBoolean("oppo_trick_value", false);
                    oppoEditor.apply();
                    oppoTrick.setChecked(false);
                    oppoTrick();
                }
                Log.d("root check", "is phone rooted " + isDeviceRooted());
                Log.d("oppo button", "oppo value is " + oppoTrickStatus.getBoolean("oppo_trick_value", false));
                Log.d("root button", "root value is " + rootTrickStatus.getBoolean("root_trick_value", false));
            }
        });

        Button btnInstall = findViewById(R.id.installButton);
        btnInstall.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                SharedPreferences oppoTrickStatus = getSharedPreferences("oppo_trick_value", Activity.MODE_PRIVATE);
                oppoTrickEnabled = oppoTrickStatus.getBoolean("oppo_trick_value",false);
                SharedPreferences rootTrickStatus = getSharedPreferences("root_trick_value", Activity.MODE_PRIVATE);
                rootTrickEnabled = rootTrickStatus.getBoolean("root_trick_value",false);
                try {
                    if (rootTrickEnabled) { installAsRoot(); }
                    else installAsKing();
                }
                catch (Exception e) {
                    TextView tv = findViewById(R.id.textViewError);
                    tv.setText(e.toString());
                }
            }
        });

        //RESET BUTTON TO OPEN DEFAULT PACKAGE INSTALLER TO CAN CLEAR AS DEFAULT SETTING
        Button resetButton = findViewById(R.id.resetButton);
        resetButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (isGooglePackageExist()) {
                    try {
                        String installerPackage = getInstalledPackageInstaller();
                        if (installerPackage == null) {
                            throw new PackageManager.NameNotFoundException("No package installer found");
                        }
                        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + installerPackage));
                        startActivity(intent);
                    } catch (Exception e) {
                        TextView tv = findViewById(R.id.textViewError);
                        tv.setText(e.toString());
                    }
                } else {
                    TextView tv = findViewById(R.id.textViewError);
                    tv.setText(R.string.missing_google_package_installer);
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        verifyPendingInstallSource();
    }

    //CHECK IF GOOGLE PACKAGE INSTALLER EXIST ON YOUR DEVICE
    public boolean isGooglePackageExist(){
        return getInstalledPackageInstaller() != null;
    }

    private String getInstalledPackageInstaller() {
        PackageManager pm=getPackageManager();
        for (String installer : KNOWN_INSTALLER_PACKAGES) {
            try {
                pm.getPackageInfo(installer,PackageManager.GET_META_DATA);
                return installer;
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        return null;
    }

    public void oppoTrick() {
        //MAKE OPPO TRICK DISABLED AS DEFAULT AND AVOID HAVE AN UNUSEFUL FAKE INSTALLER
        SharedPreferences oppoTrickStatus = getSharedPreferences("oppo_trick_value", Activity.MODE_PRIVATE);
        oppoTrickEnabled = oppoTrickStatus.getBoolean("oppo_trick_value",false);
        //MAKE ROOT TRICK DISABLED AS DEFAULT
        SharedPreferences rootTrickStatus = getSharedPreferences("root_trick_value", Activity.MODE_PRIVATE);
        rootTrickEnabled = rootTrickStatus.getBoolean("root_trick_value",false);
        PackageManager pm = getApplicationContext().getPackageManager();
        if (oppoTrickEnabled) {
            ComponentName oppoTrickFlagged =
                    new ComponentName(getPackageName(), getPackageName() + ".OppoTrick");
            pm.setComponentEnabledSetting(
                    oppoTrickFlagged,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
        } else {
            ComponentName oppoTrickFlagged =
                    new ComponentName(getPackageName(), getPackageName() + ".OppoTrick");
            pm.setComponentEnabledSetting(
                    oppoTrickFlagged,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        }
        SharedPreferences.Editor oppoEditor = oppoTrickStatus.edit();
        oppoEditor.putBoolean("oppo_trick_value", oppoTrickEnabled);
        oppoEditor.apply();
        SharedPreferences.Editor rootEditor = rootTrickStatus.edit();
        rootEditor.putBoolean("root_trick_value", rootTrickEnabled);
        rootEditor.apply();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.user_info_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.action_search) {
            String url = "https://gitlab.com/annexhack/king-installer";
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse(url));
            startActivity(i);
        }
        if(item.getItemId() == R.id.action_search2) {
            String url = "https://github.com/fcaronte/KingInstaller";
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse(url));
            startActivity(i);
        }
        if(item.getItemId() == R.id.action_search3) {
            String url = "https://github.com/Rikj000/KingInstaller";
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse(url));
            startActivity(i);
        }
        return true;
    }

    private void installAsRoot() {
        try {
            EditText et = findViewById(R.id.pathTextEdit);
            String filepath = et.getText().toString();
            if (filepath.length() == 0) {
                Toast.makeText(this, R.string.select_a_file, Toast.LENGTH_SHORT).show();
                return;
            }

            File myFile = new File(filepath);
            if (!myFile.exists()) {
                Toast.makeText(this, R.string.file_error, Toast.LENGTH_SHORT).show();
                return;
            }

            TextView tv = findViewById(R.id.textViewError);
            String apkPackage = getApkPackageName(filepath);
            StreamLogs installLogs = runSuWithCmd("pm install -r -t \"" + filepath + "\"");
            if (installLogs != null && installLogs.getErrorStreamLog() != null && !installLogs.getErrorStreamLog().trim().isEmpty()) {
                tv.setText(installLogs.getErrorStreamLog());
            }
            if (apkPackage != null && !apkPackage.isEmpty()) {
                StreamLogs installerLogs = runSuWithCmd("cmd package set-installer " + apkPackage + " com.android.vending");
                if (installerLogs != null && installerLogs.getErrorStreamLog() != null && !installerLogs.getErrorStreamLog().trim().isEmpty()) {
                    tv.setText(installerLogs.getErrorStreamLog());
                }
            }
            et.setText("");
            if (tv.getText() == null || tv.getText().toString().trim().isEmpty()) {
                tv.setText("Root install done. If app still not shown on Android Auto, Google policy may block non-whitelisted apps.");
            }
        } catch (Exception e) {
            TextView tv = findViewById(R.id.textViewError);
            tv.setText(e.toString());
        }
    }

    private void installAsKing() {
        try {
            EditText et = findViewById(R.id.pathTextEdit);
            String filepath = et.getText().toString();
            if (filepath.length() == 0) {
                Toast.makeText(this, R.string.select_a_file, Toast.LENGTH_SHORT).show();
                return;
            }
            File myFile = new File(filepath);
            if (!myFile.exists()) {
                Toast.makeText(this, R.string.file_error, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!canInstallUnknownApps()) {
                openUnknownAppsPermissionSettings();
                Toast.makeText(this, "Please allow install unknown apps for KingInstaller.", Toast.LENGTH_LONG).show();
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Toast.makeText(this, "Android 14+ may block installer spoofing. Use Root trick for better chance.", Toast.LENGTH_LONG).show();
            }
            Uri fileUri = FileProvider.getUriForFile(getApplicationContext(), getPackageName() + ".provider", myFile);
            Intent intent = buildInstallIntent(fileUri);
            pendingVerifyPackageName = getApkPackageName(filepath);
            pendingVerifyStartMs = SystemClock.elapsedRealtime();
            TextView tv = findViewById(R.id.textViewError);
            tv.setText("");
            if (tryStartInstallIntent(intent, fileUri)) {
                et.setText("");
            } else {
                pendingVerifyPackageName = null;
                tv.setText("Cannot open a package installer app on this device.");
            }
        } catch (Exception e) {
            TextView tv = findViewById(R.id.textViewError);
            tv.setText(e.toString());
        }
    }

    private Intent buildInstallIntent(Uri fileUri) {
        Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        intent.setData(fileUri);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
        intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, "com.android.vending");
        return intent;
    }

    private boolean tryStartInstallIntent(Intent baseIntent, Uri fileUri) {
        PackageManager pm = getPackageManager();
        Set<String> candidates = new LinkedHashSet<>();
        String preferredInstaller = oppoTrickEnabled ? getInstalledPackageInstaller() : null;
        if (preferredInstaller != null) {
            candidates.add(preferredInstaller);
        }
        for (String installer : KNOWN_INSTALLER_PACKAGES) {
            candidates.add(installer);
        }

        List<ResolveInfo> resolvers = pm.queryIntentActivities(baseIntent, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo resolveInfo : resolvers) {
            if (resolveInfo != null && resolveInfo.activityInfo != null && resolveInfo.activityInfo.packageName != null) {
                candidates.add(resolveInfo.activityInfo.packageName);
            }
        }

        for (String installerPackage : candidates) {
            try {
                Intent targetedIntent = new Intent(baseIntent);
                targetedIntent.setPackage(installerPackage);
                startActivity(targetedIntent);
                return true;
            } catch (Exception ignored) {
            }
        }

        try {
            startActivity(baseIntent);
            return true;
        } catch (Exception ignored) {
        }

        // Some ROMs only handle APK install through ACTION_VIEW.
        try {
            Intent viewIntent = new Intent(Intent.ACTION_VIEW);
            viewIntent.setDataAndType(fileUri, "application/vnd.android.package-archive");
            viewIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            viewIntent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
            viewIntent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, "com.android.vending");
            startActivity(viewIntent);
            return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean canInstallUnknownApps() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return true;
        }
        return getPackageManager().canRequestPackageInstalls();
    }

    private void verifyPendingInstallSource() {
        if (pendingVerifyPackageName == null || pendingVerifyPackageName.isEmpty()) {
            return;
        }
        // Give installer UI some time before checking install source.
        if (SystemClock.elapsedRealtime() - pendingVerifyStartMs < 1200L) {
            return;
        }

        String installer = getInstallerPackageNameCompat(pendingVerifyPackageName);
        if (installer == null || installer.isEmpty()) {
            return;
        }

        TextView tv = findViewById(R.id.textViewError);
        if ("com.android.vending".equals(installer)) {
            tv.setText("Install source verified: Google Play Store.");
        } else {
            tv.setText("Install source is " + installer + ". On Android 14+ non-root spoof is often blocked. Use ADB: adb install -r -t -i com.android.vending <apk>");
        }
        pendingVerifyPackageName = null;
    }

    private String getInstallerPackageNameCompat(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                InstallSourceInfo sourceInfo = pm.getInstallSourceInfo(packageName);
                if (sourceInfo != null) {
                    String installingPackage = sourceInfo.getInstallingPackageName();
                    if (installingPackage != null && !installingPackage.isEmpty()) {
                        return installingPackage;
                    }
                }
                return null;
            }
            return pm.getInstallerPackageName(packageName);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void openUnknownAppsPermissionSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
        intent.setData(Uri.parse("package:" + getPackageName()));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private String getApkPackageName(String apkPath) {
        try {
            PackageManager pm = getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(apkPath, 0);
            if (info != null) {
                return info.packageName;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void showFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(
                    Intent.createChooser(intent, "Select APK"), FILE_SELECT_CODE);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "Please install a File Manager.",Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case FILE_SELECT_CODE:
                if (resultCode == RESULT_OK) {
                    if (data == null || data.getData() == null) {
                        Toast.makeText(this, R.string.file_error, Toast.LENGTH_SHORT).show();
                        break;
                    }
                    Uri uri = data.getData();
                    String path = copyFileToInternalStorage(uri, "apk");

                    EditText et = findViewById(R.id.pathTextEdit);
                    et.setText(path);
                }
        }
    }

    public final void clearTempFile() {
        File[] listFiles;
        Context applicationContext = getApplicationContext();
        File file = new File(applicationContext.getFilesDir() + "/apk");
        if (!file.exists() || !file.isDirectory() || (listFiles = file.listFiles()) == null) {
            return;
        }
        for (File file2 : listFiles) {
            file2.delete();
        }
    }
    public void onDestroy() {
        super.onDestroy();
        try {
            clearTempFile();
        } catch (Throwable ignored) {
        }
    }

    private String copyFileToInternalStorage(Uri uri, String newDirName) {
        Uri returnUri = uri;

        Context mContext = getApplicationContext();
        Cursor returnCursor = mContext.getContentResolver().query(returnUri, new String[]{ OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE
        }, null, null, null);
        if (returnCursor == null) {
            return "";
        }


        /*
         * Get the column indexes of the data in the Cursor,
         *     * move to the first row in the Cursor, get the data,
         *     * and display it.
         * */
        int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        returnCursor.moveToFirst();
        String name = returnCursor.getString(nameIndex);
        if (name == null || name.isEmpty()) {
            name = "selected.apk";
        }
        returnCursor.close();

        File output;
        if (!newDirName.equals("")) {
            File dir = new File(mContext.getFilesDir() + "/" + newDirName);
            if (!dir.exists()) {
                dir.mkdir();
            }
            output = new File(mContext.getFilesDir() + "/" + newDirName + "/" + name);
        } else {
            output = new File(mContext.getFilesDir() + "/" + name);
        }
        try {
            InputStream inputStream = mContext.getContentResolver().openInputStream(uri);
            FileOutputStream outputStream = new FileOutputStream(output);
            int read = 0;
            int bufferSize = 1024;
            final byte[] buffers = new byte[bufferSize];
            while ((read = inputStream.read(buffers)) != -1) {
                outputStream.write(buffers, 0, read);
            }

            inputStream.close();
            outputStream.close();

        } catch (Exception e) {

//            L.e("Exception", e.getMessage());
        }

        return output.getPath();
    }

    /**
     * https://github.com/shmykelsa/AA-Tweaker/blob/4d03205f14b2938f96bf04e198dd067cd6fe0967/app/src/main/java/sksa/aa/tweaker/MainActivity.java#L3964
     * @param cmd
     * @return
     */
    public static StreamLogs runSuWithCmd(String cmd) {
        DataOutputStream outputStream = null;
        InputStream inputStream = null;
        InputStream errorStream = null;

        StreamLogs streamLogs = new StreamLogs();
        streamLogs.setOutputStreamLog(cmd);

        try {
            Process su = Runtime.getRuntime().exec("su");
            outputStream = new DataOutputStream(su.getOutputStream());
            inputStream = su.getInputStream();
            errorStream = su.getErrorStream();

            outputStream.writeBytes(cmd + "\n");
            outputStream.flush();
            outputStream.writeBytes("exit\n");
            outputStream.flush();

            try { su.waitFor(); }
            catch (InterruptedException e) { e.printStackTrace(); }
            streamLogs.setInputStreamLog(readStream(inputStream));
            streamLogs.setErrorStreamLog(readStream(errorStream));
        } catch (IOException e) { e.printStackTrace(); }

        return streamLogs;
    }

    public static String readStream(InputStream is) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = is.read(buffer)) != -1) {
            byteArrayOutputStream.write(buffer, 0, length);
        }
        return byteArrayOutputStream.toString("UTF-8");
    }
    public static boolean isDeviceRooted() {
        return checkRootMethod1() || checkRootMethod2() ||
                checkRootMethod3();
    }
    private static boolean checkRootMethod1() {
        String buildTags = android.os.Build.TAGS;
        return buildTags != null && buildTags.contains("test-keys");
    }
    private static boolean checkRootMethod2() {
        String[] paths = { "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su",
                "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
                "/system/bin/failsafe/su", "/data/local/su" };
        for (String path : paths) {
            if (new File(path).exists()) return true;
        }
        return false;
    }
    private static boolean checkRootMethod3() {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[] {
                    "/system/xbin/which", "su" });
            BufferedReader in = new BufferedReader(new
                    InputStreamReader(process.getInputStream()));
            if (in.readLine() != null) return true;
            return false;
        } catch (Throwable t) {
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }
}
