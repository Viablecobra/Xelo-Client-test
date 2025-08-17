package com.origin.launcher;

import android.content.DialogInterface;
import org.jetbrains.annotations.NotNull;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Button;
import android.widget.EditText;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import androidx.core.content.FileProvider;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Build;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private TextView listener;
    private Button mbl2_button;
    private com.google.android.material.button.MaterialButton shareLogsButton;
    
    // Store all log messages
    private StringBuilder logBuffer = new StringBuilder();
    private File persistentLogFile;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        
        listener = view.findViewById(R.id.listener);
        mbl2_button = view.findViewById(R.id.mbl2_load);
        shareLogsButton = view.findViewById(R.id.share_logs_button);
        Handler handler = new Handler(Looper.getMainLooper());
        
        // Initialize persistent log file
        persistentLogFile = new File(requireContext().getCacheDir(), "latestlog.txt");
        
        // Load existing logs if they exist
        loadExistingLogs();
        
        mbl2_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mbl2_button.setEnabled(false);
                logMessage("Starting Minecraft launcher...");
                
                // Get package name from settings
                String packageName = getPackageNameFromSettings();
                startLauncher(handler, listener, "launcher_mbl2.dex", packageName);
            }
        });
        
        // Set initial log text
        String initialMessage = "Ready to launch Minecraft - " + getCurrentTimestamp();
        logMessage(initialMessage);
        
        // Set up share button
        shareLogsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareLogs();
            }
        });
        
        return view;
    }
    
    private void loadExistingLogs() {
        try {
            if (persistentLogFile.exists()) {
                StringBuilder existingLogs = new StringBuilder();
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(persistentLogFile));
                String line;
                while ((line = reader.readLine()) != null) {
                    existingLogs.append(line).append("\n");
                }
                reader.close();
                
                logBuffer = existingLogs;
                listener.setText(logBuffer.toString());
            } else {
                // Create the log file
                persistentLogFile.createNewFile();
            }
        } catch (Exception e) {
            logMessage("Error loading existing logs: " + e.getMessage());
        }
    }
    
    private void logMessage(String message) {
        String timestampedMessage = getCurrentTimestamp() + " - " + message;
        
        // Add to buffer
        logBuffer.append(timestampedMessage).append("\n");
        
        // Update UI
        if (listener != null) {
            listener.setText(logBuffer.toString());
        }
        
        // Write to persistent file
        saveToPersistentLog(timestampedMessage);
    }
    
    private void appendLogMessage(String message) {
        String timestampedMessage = getCurrentTimestamp() + " - " + message;
        
        // Add to buffer
        logBuffer.append(timestampedMessage).append("\n");
        
        // Update UI
        if (listener != null) {
            listener.append("\n" + timestampedMessage);
        }
        
        // Write to persistent file
        saveToPersistentLog(timestampedMessage);
    }
    
    private void saveToPersistentLog(String message) {
        try {
            FileWriter writer = new FileWriter(persistentLogFile, true); // append mode
            writer.write(message + "\n");
            writer.close();
        } catch (Exception e) {
            // Silent fail to avoid infinite logging loop
        }
    }
    
    private String getCurrentTimestamp() {
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
    }

    private String getPackageNameFromSettings() {
        SharedPreferences prefs = requireContext().getSharedPreferences("settings", 0);
        return prefs.getString("mc_package_name", "com.mojang.minecraftpe");
    }

    private void shareLogs() {
        try {
            // Create the sharing intent
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            
            // Get the file URI using FileProvider
            android.net.Uri fileUri = FileProvider.getUriForFile(
                requireContext(),
                "com.origin.launcher.fileprovider",
                persistentLogFile
            );
            
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Xelo Client Logs");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Xelo Client Latest Logs");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            // Start the sharing activity
            startActivity(Intent.createChooser(shareIntent, "Share Logs"));
            
        } catch (Exception e) {
            // Show error message
            android.widget.Toast.makeText(requireContext(), "Failed to share logs: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void startLauncher(Handler handler, TextView listener, String launcherDexName, String mcPackageName) {    
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // Check if fragment is still attached
                if (!isAdded()) {
                    return;
                }
                
                File cacheDexDir = new File(requireActivity().getCodeCacheDir(), "dex");
                handleCacheCleaning(cacheDexDir, handler);
                
                ApplicationInfo mcInfo = null;
                try {
                    mcInfo = requireActivity().getPackageManager().getApplicationInfo(mcPackageName, PackageManager.GET_META_DATA);
                    final ApplicationInfo finalMcInfo = mcInfo;
                    handler.post(() -> appendLogMessage("Found Minecraft at: " + finalMcInfo.sourceDir));
                } catch(Exception e) {
                    handler.post(() -> {
                        appendLogMessage("ERROR: Minecraft can't be found - " + e.getMessage());
                        alertAndExit("Minecraft cant be found", "Perhaps you dont have it installed?");
                    });
                    return;
                };
                
                Object pathList = getPathList(requireActivity().getClassLoader());
                processDexFiles(mcInfo, cacheDexDir, pathList, handler, launcherDexName);
                if (!processNativeLibraries(mcInfo, pathList, handler)) {
                    return;
                };
                
                handler.post(() -> appendLogMessage("Launching Minecraft..."));
                
                // Final check before launching
                if (isAdded()) {
                    launchMinecraft(mcInfo);
                } else {
                    handler.post(() -> {
                        appendLogMessage("ERROR: Fragment no longer attached, cannot launch Minecraft");
                        mbl2_button.setEnabled(true);
                    });
                }
            } catch (Exception e) {
                String logMessage = e.getCause() != null ? e.getCause().toString() : e.toString();                
                handler.post(() -> {
                    appendLogMessage("LAUNCH FAILED: " + logMessage);
                    mbl2_button.setEnabled(true);
                });                
            }
        });    
    }

    @SuppressLint("SetTextI18n")
    private void handleCacheCleaning(@NotNull File cacheDexDir, Handler handler) {
        if (cacheDexDir.exists() && cacheDexDir.isDirectory()) {
            handler.post(() -> appendLogMessage(cacheDexDir.getAbsolutePath() + " not empty, doing cleaning"));
            for (File file : Objects.requireNonNull(cacheDexDir.listFiles())) {
                if (file.delete()) {
                    handler.post(() -> appendLogMessage(file.getName() + " deleted"));
                }
            }
        } else {
            handler.post(() -> appendLogMessage(cacheDexDir.getAbsolutePath() + " is empty, skip cleaning"));
        }
    }

    private Object getPathList(@NotNull ClassLoader classLoader) throws Exception {
        Field pathListField = Objects.requireNonNull(classLoader.getClass().getSuperclass()).getDeclaredField("pathList");
        pathListField.setAccessible(true);
        return pathListField.get(classLoader);
    }

    private void processDexFiles(ApplicationInfo mcInfo, File cacheDexDir, @NotNull Object pathList, @NotNull Handler handler, String launcherDexName) throws Exception {
        Method addDexPath = pathList.getClass().getDeclaredMethod("addDexPath", String.class, File.class);
        File launcherDex = new File(cacheDexDir, launcherDexName);

        copyFile(requireActivity().getAssets().open(launcherDexName), launcherDex);
        handler.post(() -> appendLogMessage(launcherDexName + " copied to " + launcherDex.getAbsolutePath()));

        if (launcherDex.setReadOnly()) {
            addDexPath.invoke(pathList, launcherDex.getAbsolutePath(), null);
            handler.post(() -> appendLogMessage(launcherDexName + " added to dex path list"));
        } else {
            throw new Exception("Failed to set launcher dex as read-only");
        }
        
        ArrayList<String> copiedDexes = new ArrayList<String>();
        try (ZipFile zipFile = new ZipFile(mcInfo.sourceDir)) {
            for (int i = 10; i >= 0; i--) {
                String dexName = "classes" + (i == 0 ? "" : i) + ".dex";
                ZipEntry dexFile = zipFile.getEntry(dexName);
                if (dexFile != null) {
                    File mcDex = new File(cacheDexDir, dexName);
                    copyFile(zipFile.getInputStream(dexFile), mcDex);
                    if (mcDex.setReadOnly()) {
                        addDexPath.invoke(pathList, mcDex.getAbsolutePath(), null);
                        copiedDexes.add(dexName);
                    } else {
                        handler.post(() -> appendLogMessage("Warning: Failed to set " + dexName + " as read-only"));
                    }
                }
            }
        } catch (Throwable th) {
            handler.post(() -> appendLogMessage("Warning: Error processing dex files: " + th.getMessage()));
        }    
        handler.post(() -> appendLogMessage("Dex files " + copiedDexes.toString() + " copied and added to dex path list"));        
    }

    private boolean processNativeLibraries(ApplicationInfo mcInfo, @NotNull Object pathList, @NotNull Handler handler) throws Exception {
        FileInputStream inStream = new FileInputStream(getApkWithLibs(mcInfo));
        BufferedInputStream bufInStream = new BufferedInputStream(inStream);
        ZipInputStream inZipStream = new ZipInputStream(bufInStream);
        if (!checkLibCompatibility(inZipStream)) {
            handler.post(() -> {
                appendLogMessage("ERROR: Wrong minecraft architecture - device uses " + Build.SUPPORTED_ABIS[0]);
                alertAndExit("Wrong minecraft architecture", "The minecraft you have installed does not support the same main architecture (" + Build.SUPPORTED_ABIS[0] + ") your device uses, mbloader cant work with it");
            });
            return false;
        } 		    
        Method addNativePath = pathList.getClass().getDeclaredMethod("addNativePath", Collection.class);
        ArrayList<String> libDirList = new ArrayList<>();
        File libdir = new File(mcInfo.nativeLibraryDir);
        if (libdir.list() == null || libdir.list().length == 0 
         || (mcInfo.flags & ApplicationInfo.FLAG_EXTRACT_NATIVE_LIBS) != ApplicationInfo.FLAG_EXTRACT_NATIVE_LIBS) {
            loadUnextractedLibs(mcInfo);
            libDirList.add(requireActivity().getCodeCacheDir().getAbsolutePath() + "/");
        } else {
            libDirList.add(mcInfo.nativeLibraryDir);
        }
        addNativePath.invoke(pathList, libDirList);
        handler.post(() -> appendLogMessage(mcInfo.nativeLibraryDir + " added to native library directory path"));
        return true;
    }

    private static Boolean checkLibCompatibility(ZipInputStream zip) throws Exception{
         ZipEntry ze = null;
         String requiredLibDir = "lib/" + Build.SUPPORTED_ABIS[0] + "/";
         while ((ze = zip.getNextEntry()) != null) {
             if (ze.getName().startsWith(requiredLibDir)) {
                 return true;
             }
         }
         zip.close();
         return false;
     }

     private void alertAndExit(String issue, String description) {
        AlertDialog alertDialog = new AlertDialog.Builder(requireActivity()).create();
        alertDialog.setTitle(issue);
        alertDialog.setMessage(description);
        alertDialog.setCancelable(false);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Exit",
        new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                requireActivity().finish();
            }
        });
        alertDialog.show();         
     }

    private void loadUnextractedLibs(ApplicationInfo appInfo) throws Exception {
        FileInputStream inStream = new FileInputStream(getApkWithLibs(appInfo));
        BufferedInputStream bufInStream = new BufferedInputStream(inStream);
        ZipInputStream inZipStream = new ZipInputStream(bufInStream);
        String zipPath = "lib/" + Build.SUPPORTED_ABIS[0] + "/";
        String outPath = requireActivity().getCodeCacheDir().getAbsolutePath() + "/";
        File dir = new File(outPath);
        dir.mkdir();
        extractDir(appInfo, inZipStream, zipPath, outPath);
    }

    public String getApkWithLibs(ApplicationInfo pkg) throws PackageManager.NameNotFoundException {
        String[] sn=pkg.splitSourceDirs;
        if (sn != null && sn.length > 0) {
            String cur_abi = Build.SUPPORTED_ABIS[0].replace('-','_');
            for(String n:sn){
                if(n.contains(cur_abi)){
                    return n;
                }
            }
        }
        return pkg.sourceDir;
    }

    private static void extractDir(ApplicationInfo mcInfo, ZipInputStream zip, String zip_folder, String out_folder ) throws Exception{
        ZipEntry ze = null;
        while ((ze = zip.getNextEntry()) != null) {
            if (ze.getName().startsWith(zip_folder) && !ze.getName().contains("c++_shared")) {
                String strippedName = ze.getName().substring(zip_folder.length());
                String path = out_folder + "/" + strippedName;
                OutputStream out = new FileOutputStream(path);
                BufferedOutputStream outBuf = new BufferedOutputStream(out);
                byte[] buffer = new byte[9000];
                int len;
                while ((len = zip.read(buffer)) != -1) {
                    outBuf.write(buffer, 0, len);
                }
                outBuf.close();
            }
        }
        zip.close();
    }

    private void launchMinecraft(ApplicationInfo mcInfo) throws ClassNotFoundException {
        Class<?> launcherClass = requireActivity().getClassLoader().loadClass("com.mojang.minecraftpe.Launcher");
        
        // Log the successful launch attempt
        appendLogMessage("Successfully loaded launcher class: " + launcherClass.getName());
        
        // Create a new intent for Minecraft to ensure it launches in a new instance
        Intent mcActivity = new Intent(requireActivity(), launcherClass);
        mcActivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mcActivity.putExtra("MC_SRC", mcInfo.sourceDir);

        if (mcInfo.splitSourceDirs != null) {
            ArrayList<String> listSrcSplit = new ArrayList<>();
            Collections.addAll(listSrcSplit, mcInfo.splitSourceDirs);
            mcActivity.putExtra("MC_SPLIT_SRC", listSrcSplit);
            appendLogMessage("Added split source dirs: " + listSrcSplit.size() + " entries");
        }
        
        // Add additional flags to ensure proper launch
        mcActivity.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        
        appendLogMessage("Starting Minecraft activity...");
        startActivity(mcActivity);
        appendLogMessage("Minecraft launched successfully - finishing launcher activity");
        requireActivity().finish();
    }

    private static void copyFile(InputStream from, @NotNull File to) throws IOException {
        File parentDir = to.getParentFile();
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            throw new IOException("Failed to create directories");
        }
        if (!to.exists() && !to.createNewFile()) {
            throw new IOException("Failed to create new file");
        }
        try (BufferedInputStream input = new BufferedInputStream(from);
             BufferedOutputStream output = new BufferedOutputStream(Files.newOutputStream(to.toPath()))) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
        }
    }
    
    // Add method to clear logs if needed
    public void clearLogs() {
        logBuffer = new StringBuilder();
        if (listener != null) {
            listener.setText("");
        }
        try {
            persistentLogFile.delete();
            persistentLogFile.createNewFile();
        } catch (Exception e) {
            // Silent fail
        }
    }
}