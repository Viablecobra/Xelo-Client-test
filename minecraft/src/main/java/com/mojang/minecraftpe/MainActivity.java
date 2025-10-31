package com.mojang.minecraftpe;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.*;
import android.os.Process;
import android.os.storage.StorageManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.*;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.core.math.MathUtils;
import androidx.core.splashscreen.SplashScreen;
import androidx.preference.PreferenceManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.androidgamesdk.GameActivity;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.mojang.android.StringValue;
import com.mojang.minecraftpe.platforms.Platform;

import org.conscrypt.BuildConfig;
import org.fmod.FMOD;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.*;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("JavaJniMissingFunction")
public class MainActivity extends GameActivity implements View.OnKeyListener, FilePickerManagerHandler {
    static final int MSG_CORRELATION_CHECK = 672;
    static final int MSG_CORRELATION_RESPONSE = 837;
    static final int OPEN_FILE_RESULT_CODE = 5;
    private static final int POST_NOTIFICATIONS_PERMISSION_ID = 2;
    public static int RESULT_PICK_IMAGE = 1;
    static final int SAVE_FILE_RESULT_CODE = 4;
    private static final int STORAGE_PERMISSION_ID = 1;
    private static boolean _isPowerVr;
    private static boolean mHasStoragePermission ;
    private static boolean mHasReadMediaImagesPermission;
    public static MainActivity mInstance;
    Class SystemProperties;
    private ClipboardManager clipboardManager;
    Method getPropMethod;
    HeadsetConnectionReceiver headsetConnectionReceiver;
    private Locale initialUserLocale;
    private BatteryMonitor mBatteryMonitor;
    private AlertDialog mDialog;
    private HardwareInformation mHardwareInformation;
    public int mLastPermissionRequestReason;
    private NetworkMonitor mNetworkMonitor;
    public ParcelFileDescriptor mPickedFileDescriptor;
    private ThermalMonitor mThermalMonitor;

    private TextToSpeech textToSpeechManager;
    private Thread mMainThread = null;
    public int virtualKeyboardHeight = 0;

    int keyboardHeight = 0;
    private boolean _fromOnCreate = false;
    private boolean mCursorLocked = false;
    private boolean mIsSoftKeyboardVisible = false;
    private long mFileDialogCallback = 0;
    private float mVolume = 1.0f;
    private boolean mIsRunningInAppCenter = false;
    private boolean mPauseTextboxUIUpdates = false;
    AtomicInteger mCaretPositionMirror = new AtomicInteger(0);
    AtomicReference<String> mCurrentTextMirror = new AtomicReference<>("");
    public List<ActivityListener> mActivityListeners = new ArrayList();
    private FilePickerManager mFilePickerManager = null;
    private WorldRecovery mWorldRecovery = null;
    private WifiManager mWifiManager = null;
    private WifiManager.MulticastLock mMulticastLock = null;
    private AppExitInfoHelper mAppExitInfoHelper = null;
    private BrazeManager mBrazeManager = null;
    Platform platform;

    Messenger mService = null;
    MessageConnectionStatus mBound = MessageConnectionStatus.NOTSET;
    final Messenger mMessenger = new Messenger(new IncomingHandler());
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mService = new Messenger(iBinder);
            mBound = MessageConnectionStatus.CONNECTED;
            Message obtain = Message.obtain(null, MainActivity.MSG_CORRELATION_CHECK, 0, 0);
            obtain.replyTo = mMessenger;
            try {
                mService.send(obtain);
            } catch (RemoteException unused) {
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mService = null;
            mBound = MessageConnectionStatus.DISCONNECTED;
        }
    };

    private int _userInputStatus = -1;
    private String[] _userInputText = null;
    private ArrayList<StringValue> _userInputValues = new ArrayList<>();
    ActivityManager.MemoryInfo mCachedMemoryInfo = new ActivityManager.MemoryInfo();
    long mCachedMemoryInfoUpdateTime = 0;
    long mCachedUsedMemory = 0;
    long mCachedUsedMemoryUpdateTime = 0;
    Debug.MemoryInfo mCachedDebugMemoryInfo = new Debug.MemoryInfo();
    long mCachedDebugMemoryUpdateTime = 0;
    private long mCallback = 0;

    enum MessageConnectionStatus {
        NOTSET,
        CONNECTED,
        DISCONNECTED
    }


    private void assertIsMainThread() {
    }

    private static native void nativeWaitCrashManagementSetupComplete();


    public static boolean isPowerVR() {
        return _isPowerVr;
    }

    public static void saveScreenshot(String filename, int w, int h, int[] pixels) {
        final Bitmap bitmap = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);

        try (FileOutputStream fileOutputStream = new FileOutputStream(filename)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fileOutputStream);
            fileOutputStream.flush();
        } catch (FileNotFoundException e) {
            System.err.println("Couldn't create file: " + filename);
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    native void nativeFireNetworkChangedEvent(String networkConnectionType);

    native boolean isAndroidChromebook();



    public void buyGame() {
    }

    public int checkLicense() {
        return 0;
    }

    public void displayDialog(int dialogId) {
    }

    public boolean hasBuyButtonWhenInvalidLicense() {
        return true;
    }

    native boolean isAndroidAmazon();

    native void nativeSetIntegrityToken(String integrityToken);

    native void nativeSetIntegrityTokenErrorMessage(String errorMessage);

    native boolean isAndroidTrial();

    native boolean isBrazeEnabled();

    public boolean isBrazeSDKDisabled() {
        return true;
    }

    protected boolean isDemo() {
        return false;
    }

    public native boolean isEduMode();

    public native boolean isPublishBuild();

    native boolean isTestInfrastructureDisabled();

    native void nativeBackPressed();

    native void nativeRunNativeCallbackOnUiThread(long fn);

    native void nativeBackSpacePressed();

    native String nativeCheckIfTestsAreFinished();

    native void nativeClearAButtonState();

    native void nativeDeviceCorrelation(long myTime, String theirDeviceId, long theirTime, String theirLastSessionId);

    native String nativeGetActiveScreen();

    native String nativeGetDevConsoleLogName();

    native String nativeGetDeviceId();

    native String nativeGetLogText(String fileInfo);

    native long nativeInitializeLibHttpClient(long hcInitArgs);

    native long nativeInitializeXboxLive(long xalInitArgs, long xblInitArgs);

    public native boolean nativeKeyHandler(final int keycode, final int action);

    native void nativeOnDestroy();

    native void nativeOnPickFileCanceled();

    native void nativeOnPickFileSuccess(String tempPickedFilePath);

    native void nativeOnPickImageCanceled(long callback);

    native void nativeOnPickImageSuccess(long callback, String picturePath);

    native void nativeProcessIntentUriQuery(String host, String query);

    native void nativeResize(int width, int height);

    native void nativeReturnKeyPressed();

    native void nativeSetHeadphonesConnected(boolean connected);

    native String nativeSetOptions(String optionsString);

    native void nativeCaretPosition(final int caretPosition);

    native void nativeSetTextboxText(String text);

    native void nativeShutdown();

    native void nativeStopThis();

    native void nativeStoragePermissionRequestResult(boolean result, int reason);

    native void nativeSuspend();

    native void nativeSuspendGameplayUpdates(boolean z);

    @Override
    public void onBackPressed() {
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return false;
    }

    native void onSoftKeyboardClosed();

    public void postScreenshotToFacebook(String filename, int w, int h, int[] pixels) {
    }

    public void statsTrackEvent(String eventName, String eventParameters) {
    }

    public void statsUpdateUserData(String graphicsVendor, String graphicsRenderer) {
    }

    public void tick() {
    }

    public void launchUri(String uri) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
    }

    public void share(String title, String description, String uri) {
        final Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_SUBJECT, title);
        intent.putExtra(Intent.EXTRA_TITLE, description);
        intent.putExtra(Intent.EXTRA_TEXT, uri);
        intent.setType("text/plain");
        startActivity(Intent.createChooser(intent, title));
    }

    public AppExitInfoHelper initializeAppExitInfoHelper() {
        if (mAppExitInfoHelper == null) {
            mAppExitInfoHelper = new AppExitInfoHelper(getApplicationContext());
        }
        return mAppExitInfoHelper;
    }

    public void openAndroidAppSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        } catch (ActivityNotFoundException e) {
        }
    }

    public void setClipboard(String value) {
        clipboardManager.setPrimaryClip(ClipData.newPlainText("Clipdata", value));
    }

    public float getKeyboardHeight() {
        return keyboardHeight;
    }

    public void updateTextboxText(final String text) {
        runOnUiThread(() -> {
            if (!showingKeyboard)
                return;
            showingKeyboard = false;
            keyboardInput.setText(text);
            keyboardInput.setSelection(keyboardInput.getText().length());
            showingKeyboard = true;
        });
    }

    public void trackPurchaseEvent(String contentId, String contentType, String revenue, String clientId, String userId, String playerSessionId, String currencyCode, String eventName) {
    }

    public void setBrazeID(String ID) {
    }

    public void enableBrazeSDK() {
    }

    public void disableBrazeSDK() {
    }

    public void setCachedDeviceId(String deviceId) {
        final SharedPreferences.Editor edit = PreferenceManager.getDefaultSharedPreferences(this).edit();
        edit.putString("deviceId", deviceId);
        edit.apply();
    }

    public void throwRuntimeExceptionFromNative(final String message) {
        new Handler(getMainLooper()).post(() -> {
            throw new RuntimeException(message);
        });
    }

    public void deviceIdCorrelationStart() {
        final SharedPreferences defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final int i = defaultSharedPreferences.getInt("correlationAttempts", 10);
        if (i == 0) {
            return;
        }
        final Intent intent = new Intent();
        intent.setComponent(new ComponentName(getPackageName(), "com.mojang.minecraftpe.ImportService"));
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        SharedPreferences.Editor edit = defaultSharedPreferences.edit();
        edit.putInt("correlationAttempts", i - 1);
        edit.apply();
    }

    public BatteryMonitor getBatteryMonitor() {
        if (mBatteryMonitor == null) {
            mBatteryMonitor = new BatteryMonitor();
        }
        return mBatteryMonitor;
    }

    public HardwareInformation getHardwareInfo() {
        if (mHardwareInformation == null) {
            mHardwareInformation = new HardwareInformation(this);
        }
        return mHardwareInformation;
    }

    public ThermalMonitor getThermalMonitor() {
        if (mThermalMonitor == null) {
            mThermalMonitor = new ThermalMonitor();
        }
        return mThermalMonitor;
    }

    public CrashManager initializeCrashManager(String crashDumpFolder, String sessionId) {
        return new CrashManager();
    }

    public void initializeCrashManager() {
    }

    @SuppressLint("ResourceType")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        mMainThread = Thread.currentThread();
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        if (getResources() == null) {
            Process.killProcess(Process.myPid());
        }
        nativeWaitCrashManagementSetupComplete();
        initializeAppExitInfoHelper();
        mBrazeManager = new BrazeManager();
        if (isEduMode()) {
            try {
                getClassLoader().loadClass("com.microsoft.applications.events.HttpClient").getConstructor(Context.class).newInstance(getApplicationContext());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            configureBrazeAtRuntime();
            if (Build.VERSION.SDK_INT >= 33) {
                mBrazeManager.requestPushPermission();
            }
        }
        platform = Platform.createPlatform(true);
        setVolumeControlStream(3);
        FMOD.init(this);
        headsetConnectionReceiver = new HeadsetConnectionReceiver();
        mNetworkMonitor = new NetworkMonitor(getApplicationContext());
        platform.onAppStart(getWindow().getDecorView());
        mHasStoragePermission = checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, Process.myPid(), Process.myUid()) == 0;
        mHasReadMediaImagesPermission = Build.VERSION.SDK_INT < 33 || checkPermission("android.permission.READ_MEDIA_IMAGES", Process.myPid(), Process.myUid()) == 0;
        nativeSetHeadphonesConnected(((AudioManager) getSystemService(Context.AUDIO_SERVICE)).isWiredHeadsetOn());
        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        initialUserLocale = Locale.getDefault();
        final FilePickerManager filePickerManager = new FilePickerManager(this);
        mFilePickerManager = filePickerManager;
        addListener(filePickerManager);
        mInstance = this;
        _fromOnCreate = true;
        findViewById(android.R.id.content).getRootView().addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                nativeResize(right - left, bottom - top);
            }
        });

        final View view = findViewById(android.R.id.content).getRootView();
        view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                Rect rect = new Rect();
                view.getWindowVisibleDisplayFrame(rect);
                keyboardHeight = (view.getHeight() - rect.height());

                try {
                    nativeResize(view.getWidth(), view.getHeight());
                } catch (UnsatisfiedLinkError ignored) {
                }
            }
        });
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                if (Build.VERSION.SDK_INT >= 19) {
                    fullscreenHandler.postDelayed(fullscreenRunnable, 500);
                }
            }
        });

        mWorldRecovery = new WorldRecovery(getApplicationContext(), getApplicationContext().getContentResolver());
    }


    private Handler fullscreenHandler = new Handler();
    private Runnable fullscreenRunnable = new Runnable() {
        @TargetApi(19)
        @Override
        public void run() {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    };

    public long getLowMemoryThreshold() {
        return getMemoryInfo().threshold;
    }

    public long getUsableSpace(String str) {
        try {
            return new File(str).getUsableSpace();
        } catch (SecurityException e) {
            return 0L;
        }
    }

    public void initializeMulticast() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        mWifiManager = wifiManager;
        if (wifiManager != null) {
            mMulticastLock = wifiManager.createMulticastLock("MCLock");
            setMulticastReferenceCounting(false);
        }
    }

    public void releaseMulticast() {
        if (mMulticastLock == null || !isMulticastHeld()) {
            return;
        }
        mMulticastLock.release();
    }

    public void acquireMulticast() {
        if (mMulticastLock == null || isMulticastHeld()) {
            return;
        }
        mMulticastLock.acquire();
    }

    public void setMulticastReferenceCounting(boolean useReferenceCounting) {
        WifiManager.MulticastLock multicastLock = mMulticastLock;
        if (multicastLock != null) {
            multicastLock.setReferenceCounted(useReferenceCounting);
        }
    }

    public void lockCursor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            View rootView = findViewById(android.R.id.content).getRootView();
            mCursorLocked = true;
            rootView.setPointerIcon(PointerIcon.getSystemIcon(getApplicationContext(), 0));
            rootView.requestPointerCapture();
        }
    }

    public void unlockCursor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            View rootView = findViewById(android.R.id.content).getRootView();
            mCursorLocked = false;
            rootView.setPointerIcon(PointerIcon.getSystemIcon(getApplicationContext(), 1000));
            rootView.releasePointerCapture();
  