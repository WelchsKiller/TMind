package com.nest.tmind.ecg;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.nest.tmind.R;
import com.nest.tmind.ecg.model.BleManager;
import com.nest.tmind.ui.DashboardActivity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class EcgBleService extends Service {

    private String lastDeviceAddress; // 연결했던 기기 주소 저장

    // ===== 계측기 명령 (프로토콜 문서 보고 실제 값으로 맞추기) =====
    private static final String CMD_START_MEASURE = "@E000\r\n"; // 예: 시작 명령
    private static final String CMD_STOP_MEASURE  = "@E001\r\n"; // 예: 정지 명령

    // ===== 바인더/리스너 =====
    public class EcgBinder extends Binder {
        public EcgBleService getService() {
            return EcgBleService.this;
        }
    }

    public interface EcgListener {
        void onSamples(float[] samples);
        void onHrRr(Integer hr, Integer hrvMs);
    }

    public interface ConnectionListener {
        void onBleConnectionChanged(boolean ready);
        void onBleConnectionFailed();
    }

    private final IBinder binder = new EcgBinder();
    private final List<EcgListener> listeners = new ArrayList<>();
    private final List<ConnectionListener> connectionListeners = new ArrayList<>();

    public void addListener(EcgListener l) {
        synchronized (listeners) {
            if (!listeners.contains(l)) listeners.add(l);
        }
    }

    public void removeListener(EcgListener l) {
        synchronized (listeners) {
            listeners.remove(l);
        }
    }

    public void addConnectionListener(ConnectionListener l) {
        synchronized (connectionListeners) {
            if (!connectionListeners.contains(l)) connectionListeners.add(l);
        }
    }

    public void removeConnectionListener(ConnectionListener l) {
        synchronized (connectionListeners) {
            connectionListeners.remove(l);
        }
    }

    private void dispatchConnectionChanged(boolean ready) {
        synchronized (connectionListeners) {
            for (ConnectionListener l : connectionListeners) {
                l.onBleConnectionChanged(ready);
            }
        }
    }

    private void dispatchConnectionFailed() {
        synchronized (connectionListeners) {
            for (ConnectionListener l : connectionListeners) {
                l.onBleConnectionFailed();
            }
        }
    }

    /**
     * 측정 화면 진입 시: 저장된 기기 삭제 → 기존 연결 해제 → 새 스캔 (최대 3회)
     */
    public void startFreshScanForMeasure() {
        freshMeasureMode = true;
        autoConnectEnabled = true;
        if (measuring) {
            writeAscii(CMD_STOP_MEASURE);
            measuring = false;
        }
        stopScan();
        closeGatt();
        clearSavedDevice();
        scanAttempt = 0;
        best = null;
        EcgConfig.isBleReady = false;
        BLE_Stat = false;
        dispatchConnectionChanged(false);

        refreshBtObjects();
        if (!hasAllPerms()) {
            toast("BLE 권한이 필요합니다. 앱에서 권한을 허용해주세요.");
            dispatchConnectionFailed();
            return;
        }
        if (btAdp == null || !btAdp.isEnabled()) {
            toast("블루투스를 켜주세요.");
            dispatchConnectionFailed();
            return;
        }
        if (!isLocationEnabled() && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            toast("위치 서비스를 켜주세요.");
            promptEnableLocation();
            dispatchConnectionFailed();
            return;
        }
        startScan();
    }

    private void clearSavedDevice() {
        lastDeviceAddress = null;
        getSharedPreferences("ecg_ble", MODE_PRIVATE).edit().remove("last_device").apply();
    }

    /** BLE 서비스 중지 (HRV 측정 외 화면에서 호출) */
    public static final String ACTION_STOP = "com.nest.tmind.ecg.ACTION_STOP";

    public static void stopBleService(Context context) {
        context.getApplicationContext().stopService(
                new Intent(context.getApplicationContext(), EcgBleService.class));
    }

    /** @deprecated 측정 화면에서는 startFreshScanForMeasure() 사용 */
    public void ensureConnection() {
        autoConnectEnabled = true;
        startAutoConnect();
    }

    /** 측정 화면 이탈: 측정 중지 + BLE 연결 해제 (자동 재연결 없음) */
    public void stopMeasurementAndDisconnect() {
        autoConnectEnabled = false;
        freshMeasureMode = false;
        measuring = false;
        writeAscii(CMD_STOP_MEASURE);
        latestHr = null;
        latestRr = null;
        rrHistoryMs.clear();
        stopScan();
        closeGatt();
    }

    // ===== Foreground Service Notification =====
    private static final String CHANNEL_ID = "ecg_measure_channel";
    private static final int NOTI_ID = 1001;

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "심박 측정",
                    NotificationManager.IMPORTANCE_LOW
            );
            ch.setDescription("심박 자동 측정을 위한 서비스");
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(ch);
            }
        }
    }

    private Notification buildNotification() {
        Intent i = new Intent(this, com.nest.tmind.ui.DashboardActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                this,
                0,
                i,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_heart)
                .setContentTitle("심박 측정 서비스")
                .setContentText("BLE 기기와 연결 유지 중입니다.")
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }

    // ===== 상태 공개용 =====
    // measuring: "데이터 수신(측정)" 여부만 의미. 연결 여부는 gatt != null 또는 BLE_Stat로 판단.
    private volatile boolean measuring = false; // 실제 스트림 수신 중 여부

    public boolean isMeasuring() {
        return measuring;
    }

    // 필요하면 연결 상태도 외부에서 보고 싶을 때 사용 가능
    public boolean isConnected() {
        return BLE_Stat && gatt != null;
    }

    // ===== UI 없음: 대신 메인 스레드 핸들러 =====
    private Handler h;

    // ===== 스캔/연결 설정 =====
    private static final String[] NAME_KEYWORDS = new String[]{"EKG", "NS_EKG", "NEST", "EKG_"};
    private static final int SCAN_TIMEOUT_MS = 8000;

    // ===== UUID =====
    private static final UUID UUID_SERVICE = BleManager.UUID_SERVICE;
    private static final UUID UUID_CHAR_WRITE = BleManager.UUID_CHAR_WRITE;
    private static final UUID UUID_CHAR_NOTIFY = BleManager.UUID_CHAR_NOTIFY;
    private static final UUID UUID_CCCD = BleManager.UUID_CCCD;

    // ===== BLE =====
    private BluetoothAdapter btAdp;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private boolean scanning = false;
    private boolean isConnecting = false;
    private ScanResult best;
    private Runnable scanTimeout;
    private BluetoothGattCharacteristic chWrite, chNotify;

    // ===== 프레임 파싱 =====
    private final StringBuilder frameBuf = new StringBuilder();
    private static final int MAX_FRAME_SCAN = 256;

    // ===== 파형/스파이크 =====
    private static final int FS_PLOT = 250;           // 그래프 샘플링
    private static final long MIN_GAP_MS = 100;
    private static final long COOLDOWN_MS = 250;

    private float[] spikeKernel;
    private int beatIdx = -1;
    private long lastSpikeStartMs = 0;
    private long blockUntilMs = 0;

    private static final double FRAME_MS = 1000.0 * 25.0 / FS_PLOT; // 100ms
    private double beatPeriodMs = 1000.0;
    private double beatTimerMs = 0.0;
    private boolean haveHr = false;   // 필요시 확장용 (지금은 RR/HR에서 자동 계산)

    private static final float mvPerCount = 0.0030f;

    private boolean lastSignalGood = false;
    private boolean everHadHr = false;
    private int framesWithoutHr = 0;
    private static final int CONTACT_LOST_FRAMES = 20;
    private float[] prevMv25 = null;
    private float spikeScale = 1f;
    private float nextSpikeScale = 1f;
    private final Random rnd = new Random();

    private String prevData50 = null;
    private final Deque<Long> spikeTimes = new ArrayDeque<>();

    // ===== HR/RR =====
    private Integer latestHr = null;
    private Integer latestRr = null;

    private volatile boolean streamReady = false;
    private int framesSinceStart = 0;
    private long firstDataMs = 0;

    private boolean BLE_Stat = false;

    private int scanAttempt = 0;
    private static final int MAX_SCAN_ATTEMPTS = 3;

    private volatile boolean freshMeasureMode = false;
    private volatile boolean autoConnectEnabled = false;

    // ===== RR 히스토리 (HRV) =====
    private final Deque<Integer> rrHistoryMs = new ArrayDeque<>();
    private static final int MAX_RR_HISTORY = 256;

    // ===== 1초마다 HR/HRV 계산해서 리스너에게 보내는 타이머 =====
    private final Runnable uiTicker = new Runnable() {
        @Override
        public void run() {
            Pair vals = computeDisplayHrRr();
            Integer hr = vals.hr;
            Integer rrMs = vals.rrMs;

            Integer hrvMs = null;
            if (rrMs != null) {
                updateRrHistory(rrMs);
                hrvMs = computeSdnnFromHistory();
            }

            dispatchHrRr(hr, hrvMs);
            if (measuring) {
                if (hr != null) MeasureSessionStats.addHr(hr);
                if (rrMs != null) MeasureSessionStats.addRrMs(rrMs);
                if (hrvMs != null) MeasureSessionStats.addHrvMs(hrvMs);
            }
            h.postDelayed(this, 1000);
        }
    };

    // ===== BLE 브로드캐스트 리시버 =====
    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(i.getAction())) {
                int st = i.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (st == BluetoothAdapter.STATE_ON && autoConnectEnabled) {
                    startAutoConnect();
                }
            }
        }
    };

    // ===== 서비스 기본 =====
    @Override
    public void onCreate() {
        super.onCreate();
        h = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        startForeground(NOTI_ID, buildNotification());

        // 🔹 서비스 시작 시점에는 아직 준비 전
        EcgConfig.isBleReady = false;

        buildIconSpikeKernel(FS_PLOT);
        refreshBtObjects();

        // BLE 상태 변화 리시버 등록
        IntentFilter f = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(btReceiver, f);

        // 1초 주기 HR/HRV 타이머 시작
        h.post(uiTicker);
        // BLE 연결은 HRV 측정 화면(startFreshScanForMeasure)에서만 시작
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopMeasurementAndDisconnect();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        stopMeasurementAndDisconnect();
        try {
            unregisterReceiver(btReceiver);
        } catch (Exception ignore) {
        }
        if (h != null) {
            h.removeCallbacks(uiTicker);
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // ===== 리스너 호출 =====
    private void dispatchSamples(float[] samples) {
        if (!measuring) return;   // 측정 OFF면 화면으로 안 보냄
        synchronized (listeners) {
            for (EcgListener l : listeners) {
                l.onSamples(samples);
            }
        }
    }

    private void dispatchHrRr(Integer hr, Integer hrvMs) {
        if (!measuring) return;
        synchronized (listeners) {
            for (EcgListener l : listeners) {
                l.onHrRr(hr, hrvMs);
            }
        }
    }

    // ===== BLE 유틸 =====
    private void refreshBtObjects() {
        BluetoothManager btMgr = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        btAdp = (btMgr != null) ? btMgr.getAdapter() : null;
        scanner = (btAdp != null) ? btAdp.getBluetoothLeScanner() : null;
    }

    private void startAutoConnect() {
        if (!autoConnectEnabled) return;

        refreshBtObjects();

        if (isReadyForMeasure() && !freshMeasureMode) {
            dispatchConnectionChanged(true);
            return;
        }

        if (gatt != null && (!EcgConfig.isBleReady || freshMeasureMode)) {
            closeGatt();
        }

        if (scanning || isConnecting || gatt != null) {
            return;
        }

        EcgConfig.isBleReady = false;
        dispatchConnectionChanged(false);

        if (!hasAllPerms()) {
            toast("BLE 권한이 필요합니다. 앱에서 권한을 허용해주세요.");
            return;
        }
        if (btAdp == null || !btAdp.isEnabled()) {
            toast("블루투스를 켜주세요.");
            return;
        }
        if (!isLocationEnabled() && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            toast("위치 서비스를 켜주세요.");
            promptEnableLocation();
            return;
        }

        if (freshMeasureMode && scanAttempt >= MAX_SCAN_ATTEMPTS) {
            toast("기기 연결에 실패했습니다. (" + MAX_SCAN_ATTEMPTS + "회 시도)");
            freshMeasureMode = false;
            dispatchConnectionFailed();
            return;
        }

        best = null;
        if (!freshMeasureMode) {
            scanAttempt = 0;
        }
        startScan();
    }

    private final ScanCallback scanCb = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult r) {
            BluetoothDevice d = r.getDevice();
            if (d == null) return;

            String n;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                n = (ContextCompat.checkSelfPermission(EcgBleService.this, Manifest.permission.BLUETOOTH_CONNECT) == 0)
                        ? d.getName() : null;
            } else {
                n = d.getName();
            }
            if (n == null) n = "";

            String advName = (r.getScanRecord() != null) ? r.getScanRecord().getDeviceName() : "";
            if (advName == null) advName = "";

            String nameToCheck = (n + " " + advName).toUpperCase();

            for (String k : NAME_KEYWORDS) {
                if (nameToCheck.contains(k)) {
                    if (best == null || r.getRssi() > best.getRssi()) best = r;
                    stopScan();
                    connect(best.getDevice());
                    return;
                }
            }
        }
    };

    private void startScan() {
        if (scanner == null || scanning) return;
        scanning = true;
        toast("기기 스캔을 시작합니다 (" + (scanAttempt + 1) + "/" + MAX_SCAN_ATTEMPTS + ")");

        try {
            ScanSettings.Builder sb = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                sb.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                        .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT);
            }
            scanner.startScan(Collections.emptyList(), sb.build(), scanCb);
        } catch (SecurityException e) {
            scanning = false;
            toast("스캔 권한 오류");
            return;
        }

        if (scanTimeout != null) h.removeCallbacks(scanTimeout);
        // A50 등: 스캔 타임아웃을 조금 더 길게
        final int timeoutMs = (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
                ? SCAN_TIMEOUT_MS + 4000
                : SCAN_TIMEOUT_MS;
        scanTimeout = () -> {
            stopScan();
            if (best != null) {
                connect(best.getDevice());
            } else {
                if (++scanAttempt < MAX_SCAN_ATTEMPTS) {
                    h.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            startScan();
                        }
                    }, 500);
                } else {
                    toast("기기를 찾지 못했습니다 (" + MAX_SCAN_ATTEMPTS + "회 시도)");
                    if (freshMeasureMode) {
                        freshMeasureMode = false;
                        dispatchConnectionFailed();
                    }
                }
            }
        };
        h.postDelayed(scanTimeout, timeoutMs);
    }

    @SuppressLint("MissingPermission")
    private void stopScan() {
        if (!scanning) return;
        scanning = false;
        try {
            if (scanner != null) scanner.stopScan(scanCb);
        } catch (Exception ignore) {
        }
        if (scanTimeout != null) h.removeCallbacks(scanTimeout);
    }

    @SuppressLint("MissingPermission")
    private void connect(BluetoothDevice d) {
        if (isConnecting || d == null) return;

        // 새로 연결 시도 → 아직 준비 안됨
        EcgConfig.isBleReady = false;

        isConnecting = true;
        toast("연결 중… " + (d.getName() != null ? d.getName() : d.getAddress()));
        try {
            // 🔹 마지막으로 연결한 기기 주소 저장
            lastDeviceAddress = d.getAddress();
            getSharedPreferences("ecg_ble", MODE_PRIVATE)
                    .edit()
                    .putString("last_device", lastDeviceAddress)
                    .apply();

            gatt = d.connectGatt(
                    getApplicationContext(),
                    false,
                    gattCb,                       // 기존 콜백 그대로 사용
                    BluetoothDevice.TRANSPORT_LE
            );
        } catch (SecurityException e) {
            isConnecting = false;
            toast("연결 권한 필요");
        }
    }


    private final BluetoothGattCallback gattCb = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                MetricsManager.getInstance().onBleConnected();

                isConnecting = false;
                try {
                    g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                } catch (Exception ignore) {
                }
                // Galaxy A50 등 구버전: 서비스 검색 전 대기 시간 확보
                long discoverDelayMs = (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ? 1200 : 600;
                h.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                refreshCache(g);
                            }
                            g.discoverServices();
                        } catch (Exception ignore) {
                        }
                    }
                }, discoverDelayMs);
                return;
            }
            // Samsung 등에서 흔한 GATT_ERROR(133) → 재연결
            if (status == 133 || status == 8 || status == 22) {
                isConnecting = false;
                EcgConfig.isBleReady = false;
                closeGatt();
                h.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!autoConnectEnabled && !freshMeasureMode) return;
                        if (scanAttempt < MAX_SCAN_ATTEMPTS) {
                            startAutoConnect();
                        } else {
                            freshMeasureMode = false;
                            dispatchConnectionFailed();
                        }
                    }
                }, 1000);
                return;
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                MetricsManager.getInstance().onBleDisconnected();

                isConnecting = false;
                EcgConfig.isBleReady = false;   // 🔴 연결 끊김 → 준비 아님

                h.post(new Runnable() {
                    @Override
                    public void run() {
                        toast("연결 해제(status=" + status + ")");
                    }
                });
                closeGatt();
                h.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!autoConnectEnabled) return;
                        if (freshMeasureMode && scanAttempt >= MAX_SCAN_ATTEMPTS) {
                            freshMeasureMode = false;
                            dispatchConnectionFailed();
                            return;
                        }
                        startAutoConnect();
                    }
                }, 800);
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                EcgConfig.isBleReady = false;
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        toast("서비스 검색 실패");
                    }
                });
                return;
            }
            BluetoothGattService svc = g.getService(UUID_SERVICE);
            if (svc == null) {
                EcgConfig.isBleReady = false;
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        toast("서비스 없음");
                    }
                });
                return;
            }
            chNotify = svc.getCharacteristic(UUID_CHAR_NOTIFY);
            chWrite = svc.getCharacteristic(UUID_CHAR_WRITE);
            if (chNotify == null || chWrite == null) {
                EcgConfig.isBleReady = false;
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        toast("캐릭터리스틱 누락");
                    }
                });
                return;
            }

            try {
                // A50 등 구형 기기: MTU 요청 실패해도 측정 계속
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    g.requestMtu(185);
                }
            } catch (Exception ignore) {
            }
            g.setCharacteristicNotification(chNotify, true);
            BluetoothGattDescriptor cccd = chNotify.getDescriptor(UUID_CCCD);
            if (cccd != null) {
                int props = chNotify.getProperties();
                cccd.setValue((props & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                        ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        : BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                try {
                    g.writeDescriptor(cccd);
                } catch (Exception e) {
                    refreshCache(g);
                    h.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                g.discoverServices();
                            } catch (Exception ignore) {
                            }
                        }
                    }, 350);
                }
            } else {
                afterNotifyReady();
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int status) {
            if (!UUID_CCCD.equals(d.getUuid())) return;
            if (status == BluetoothGatt.GATT_SUCCESS) {
                EcgConfig.isBleReady = true;
                afterNotifyReady();
            } else {
                EcgConfig.isBleReady = false;
                if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION ||
                        status == BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION) {
                    try {
                        g.getDevice().createBond();
                    } catch (Exception ignore) {
                    }
                }
                refreshCache(g);
                h.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            g.discoverServices();
                        } catch (Exception ignore) {
                        }
                    }
                }, 350);
            }
        }

        private void refreshCache(BluetoothGatt gatt) {
            try {
                gatt.getClass().getMethod("refresh").invoke(gatt);
            } catch (Exception ignored) {
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic ch) {

            Log.d("BLEBLE", "onCharacteristicChanged() measuring=" + measuring);

            // ✅ 측정 중일 때만 데이터 처리
            if (!measuring) return;

            byte[] b = ch.getValue();
            if (b == null || b.length == 0) return;
            onAsciiBytes(b);
        }
    };

    private void afterNotifyReady() {
        BLE_Stat = true;
        EcgConfig.isBleReady = true;

        h.post(new Runnable() {
            @Override
            public void run() {
                writeAscii("@Ark\r\n");
                h.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        writeAscii("@F0\r\n");
                    }
                }, 120);
            }
        });

        measuring = false;
        dispatchConnectionChanged(true);
        freshMeasureMode = false;
        scanAttempt = 0;
        toast("기기 연결이 완료되었습니다.");
    }


    @SuppressLint("MissingPermission")
    private void writeAscii(String s) {
        if (gatt == null || chWrite == null) return;
        try {
            chWrite.setValue(s.getBytes(StandardCharsets.US_ASCII));
            chWrite.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            gatt.writeCharacteristic(chWrite);
        } catch (Exception ignore) {
        }
    }

    // ===== 외부에서 측정을 켜고 끄는 API =====

    /** @return 측정 시작 성공 여부 */
    public boolean startMeasurement() {
        Log.d("BLEBLE", "startMeasurement() called, ready=" + (gatt != null) + "," + (chWrite != null) + "," + EcgConfig.isBleReady);

        if (gatt == null || chWrite == null || !EcgConfig.isBleReady) {
            toast("BLE 기기에 아직 연결되지 않았습니다.");
            return false;
        }

        resetStreamState();
        haveHr = false;
        beatTimerMs = 0.0;
        MeasureSessionStats.reset();
        writeAscii(CMD_START_MEASURE);
        measuring = true;
        Log.d("BLEBLE", "startMeasurement measuring=true, @E000 sent");
        return true;
    }

    private void resetStreamState() {
        streamReady = false;
        framesSinceStart = 0;
        frameBuf.setLength(0);
        prevData50 = null;
        prevMv25 = null;
        beatIdx = -1;
        EcgCapture.i().setFs(FS_PLOT);
        EcgCapture.i().consumeAll();
        lastSignalGood = false;
        everHadHr = false;
        framesWithoutHr = 0;
    }

    /** 측정 종료: 연결은 유지, 데이터 수신만 끔 */
    public void stopMeasurement() {
        measuring = false;
        writeAscii(CMD_STOP_MEASURE);

        latestHr = null;
        latestRr = null;
        rrHistoryMs.clear();
    }

    // ===== 파서 & 템플릿 기반 파형 =====
    private void onAsciiBytes(byte[] chunk) {
        for (byte bb : chunk) {
            char c = (char) (bb & 0xFF);
            if (c == '\r' || c == '\n') {
                if (frameBuf.length() > 0) {
                    parseFrameSafe(frameBuf.toString());
                    frameBuf.setLength(0);
                }
            } else {
                frameBuf.append(c);
                tryParseFromBuffer();
                if (frameBuf.length() > MAX_FRAME_SCAN) {
                    int at = frameBuf.indexOf("@");
                    if (at >= 0) frameBuf.delete(0, at);
                    else frameBuf.setLength(0);
                }
            }
        }
        tryParseFromBuffer();
    }

    private void tryParseFromBuffer() {
        String s = frameBuf.toString();
        int at = s.indexOf('@');
        if (at < 0) return;
        if (at > 0) {
            frameBuf.delete(0, at);
            s = frameBuf.toString();
        }
        if (s.length() < 2) return;
        if (s.charAt(1) != 'W' && s.charAt(1) != 'w') return;

        int baseLen = 2 + 50 + 1;
        if (s.length() < baseLen) return;

        char statusChar = s.charAt(2 + 50);
        int statusVal = ((statusChar - 0x20) & 0x3F);
        int optLen = (statusVal == 1) ? (2 + 3) : 0;
        int totalLen = baseLen + optLen;

        if (s.length() >= totalLen) {
            String one = s.substring(0, totalLen);
            parseFrameSafe(one);
            frameBuf.delete(0, totalLen);
        }
    }

    private void parseFrameSafe(String line) {
        if (line.length() < 2 || line.charAt(0) != '@') return;
        char kind = line.charAt(1);
        if (kind != 'W' && kind != 'w') return;

        int pos = 2;
        int N = 25;
        float[] mv25 = new float[N];
        for (int i = 0; i < N; i++) {
            int v12 = dec12(line.charAt(pos), line.charAt(pos + 1));
            pos += 2;
            mv25[i] = (v12 - 2048) * mvPerCount;
        }

        if (!streamReady) {
            streamReady = true;
            framesSinceStart = 0;
            firstDataMs = System.currentTimeMillis();
        }
        framesSinceStart++;

        String data50 = line.substring(2, 2 + 50);
        boolean changed = (prevData50 == null) || !data50.equals(prevData50);
        prevData50 = data50;

        char statusChar = line.charAt(pos++);
        int statusVal = ((statusChar - 0x20) & 0x3F);

        boolean hrInFrame = false;
        if (statusVal == 1 && line.length() >= pos + 5) {
            int hrDev = dec12(line.charAt(pos), line.charAt(pos + 1));
            pos += 2;
            int rrDev = dec18(line.charAt(pos), line.charAt(pos + 1), line.charAt(pos + 2));
            pos += 3;

            if (hrDev >= 0 && hrDev <= 200) {
                hrInFrame = true;
                everHadHr = true;
                framesWithoutHr = 0;
                latestHr = hrDev;
                haveHr = true;
                if (rrDev >= 300 && rrDev <= 2000) {
                    latestRr = rrDev;
                    beatPeriodMs = rrDev;
                } else {
                    beatPeriodMs = 60000.0 / Math.max(1, hrDev);
                }
                if (measuring && hrDev >= 40 && hrDev <= 180) {
                    MeasureSessionStats.addHr(hrDev);
                    if (rrDev >= 333 && rrDev <= 1500) {
                        MeasureSessionStats.addRrMs(rrDev);
                    }
                }
                Log.d("HRRR", "parsed HR=" + latestHr + ", RR=" + latestRr);
            }
        }

        if (!hrInFrame) {
            framesWithoutHr++;
        }

        boolean contact = everHadHr && framesWithoutHr <= CONTACT_LOST_FRAMES;

        if (!contact) {
            haveHr = false;
            prevMv25 = mv25;
            if (lastSignalGood) {
                beatIdx = -1;
                beatTimerMs = 0.0;
                latestHr = null;
                latestRr = null;
                everHadHr = false;
                dispatchHrRr(null, null);
            }
            lastSignalGood = false;
            dispatchSamples(new float[N]);
            return;
        }

        if (!lastSignalGood) {
            beatIdx = 0;
            spikeScale = 1f;
            beatTimerMs = 0.0;
        }
        lastSignalGood = true;
        haveHr = latestHr != null;

        // 그래프/저장용 (접촉 중)
        EcgCapture.i().setFs(FS_PLOT);
        EcgCapture.i().add(mv25);

        // RMS 기반 스케일
        if (prevMv25 != null) {
            double sum2 = 0;
            for (int i = 0; i < N; i++) {
                double d = mv25[i] - prevMv25[i];
                sum2 += d * d;
            }
            double rms = Math.sqrt(sum2 / N);
            nextSpikeScale = mapRmsToScale((float) rms);
        } else {
            nextSpikeScale = 1f;
        }
        prevMv25 = mv25;

        if (haveHr) {
            beatTimerMs += FRAME_MS;
            if (beatTimerMs >= beatPeriodMs) {
                beatTimerMs -= beatPeriodMs;
                beatIdx = 0;
                spikeScale = nextSpikeScale;
                lastSpikeStartMs = System.currentTimeMillis();
            }
        }

        float[] out = new float[N];
        for (int i = 0; i < N; i++) {
            if (beatIdx >= 0 && beatIdx < spikeKernel.length) {
                out[i] = spikeKernel[beatIdx++] * spikeScale;
            } else {
                out[i] = 0f;
            }
        }
        if (beatIdx >= spikeKernel.length) {
            beatIdx = -1;
        }

        dispatchSamples(Arrays.copyOf(out, N));

        if (changed) tryFilter();
    }

    // ===== 스케일 맵핑 =====
    private float mapRmsToScale(float rmsMv) {
        float s = 0.8f + 0.8f * rmsMv;
        s = Math.max(0.8f, Math.min(1.2f, s));
        float jitter = 1f + (rnd.nextFloat() - 0.5f) * 0.04f;
        return s * jitter;
    }

    // ===== 디코더 =====
    private int dec6(char c) {
        return (c - 0x20) & 0x3F;
    }

    private int dec12(char a, char b) {
        return dec6(a) | (dec6(b) << 6);
    }

    private int dec18(char a, char b, char c) {
        return dec6(a) | (dec6(b) << 6) | (dec6(c) << 12);
    }

    // ===== ECG 템플릿 =====
    private void buildIconSpikeKernel(int fs) {
        int n = fs;
        spikeKernel = new float[n];

        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1);
            float y = 0f;

            y += 0.08f * gauss(t, 0.18f, 0.03f);   // P
            y += -0.25f * gauss(t, 0.46f, 0.015f); // Q
            y += 1.00f * gauss(t, 0.50f, 0.010f);  // R
            y += -0.45f * gauss(t, 0.54f, 0.015f); // S
            y += 0.30f * gauss(t, 0.70f, 0.05f);   // T

            spikeKernel[i] = y;
        }

        float maxAbs = 0f;
        for (float v : spikeKernel) maxAbs = Math.max(maxAbs, Math.abs(v));
        if (maxAbs < 1e-3f) maxAbs = 1f;
        for (int i = 0; i < n; i++) spikeKernel[i] /= maxAbs;
    }

    private float gauss(float t, float mu, float sigma) {
        float x = (t - mu) / sigma;
        return (float) Math.exp(-0.5f * x * x);
    }

    // ===== 권한/유틸 =====
    private boolean hasAllPerms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            int scan = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN);
            int conn = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT);
            return scan == PackageManager.PERMISSION_GRANTED &&
                    conn == PackageManager.PERMISSION_GRANTED;
        } else {
            int fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
            int coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
            Log.d("PERM", "fine=" + fine + ", coarse=" + coarse);
            return fine == PackageManager.PERMISSION_GRANTED ||
                    coarse == PackageManager.PERMISSION_GRANTED;
        }
    }

    private boolean isLocationEnabled() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return true;
        try {
            return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) {
            return true;
        }
    }

    private void promptEnableLocation() {
        try {
            Intent i = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignore) {
        }
    }

    private void toast(String s) {
        h.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void closeGatt() {
        if (gatt != null) {
            try {
                gatt.disconnect();
            } catch (Exception ignore) {
            }
            try {
                gatt.close();
            } catch (Exception ignore) {
            }
            gatt = null;
            chWrite = null;
            chNotify = null;
        }
        measuring = false;
        BLE_Stat = false;
        EcgConfig.isBleReady = false;
        dispatchConnectionChanged(false);
    }

    // ===== tryFilter (fallback HR 계산용) =====
    private void tryFilter() {
        long now = System.currentTimeMillis();
        if (!streamReady) return;
        if (framesSinceStart < 2) return;
        if (now - firstDataMs < 200) return;

        if (now < blockUntilMs) return;
        if (now - lastSpikeStartMs < MIN_GAP_MS) return;

        lastSpikeStartMs = now;
        beatIdx = 0;

        spikeScale = nextSpikeScale;

        spikeTimes.addLast(now);
        while (spikeTimes.size() > 6) spikeTimes.removeFirst();

        long spikeDurMs = (long) (1000f * (spikeKernel.length / (float) FS_PLOT));
        blockUntilMs = now + spikeDurMs + COOLDOWN_MS;
    }

    // ===== HR/RR 계산 =====
    private static class Pair {
        Integer hr;
        Integer rrMs;

        Pair(Integer h, Integer r) {
            hr = h;
            rrMs = r;
        }
    }

    private Pair computeDisplayHrRr() {
        Integer hr = latestHr;
        Integer rr = latestRr;

        Integer outHr = null;
        Integer outRrMs = null;

        if (rr != null && rr >= 300 && rr <= 2000) {
            outRrMs = rr;
            int calcHr = Math.round(60000f / rr);
            if (calcHr >= 0 && calcHr <= 200) {
                outHr = calcHr;
            }
        }

        if (outHr == null && hr != null && hr >= 0 && hr <= 200) {
            outHr = hr;
            int calcRr = Math.round(60000f / Math.max(1, hr));
            if (calcRr >= 300 && calcRr <= 2000) {
                outRrMs = calcRr;
            }
        }

        return new Pair(outHr, outRrMs);
    }

    private void updateRrHistory(int rrMs) {
        if (rrMs < 300 || rrMs > 2000) return;
        rrHistoryMs.addLast(rrMs);
        while (rrHistoryMs.size() > MAX_RR_HISTORY) {
            rrHistoryMs.removeFirst();
        }
    }

    private Integer computeSdnnFromHistory() {
        int n = rrHistoryMs.size();
        if (n < 8) return null;

        double sum = 0.0;
        for (int v : rrHistoryMs) sum += v;
        double mean = sum / n;

        double var = 0.0;
        for (int v : rrHistoryMs) {
            double diff = v - mean;
            var += diff * diff;
        }
        var /= n;

        int sdnn = (int) Math.round(Math.sqrt(var));
        if (sdnn < 0) sdnn = 0;
        if (sdnn > 100) sdnn = 100;

        return sdnn;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);

        // 사용자가 최근앱 화면에서 앱을 스와이프로 날린 경우 여기 들어옴
        // ➜ 포어그라운드 알림 내리고 서비스 종료
        stopForeground(true);
        stopSelf();   // ➜ onDestroy() 호출됨
    }

    // 현재 측정을 시작할 수 있는 준비 상태인지 여부
    public boolean isReadyForMeasure() {
        // gatt, chWrite 가 살아 있고, notify 설정까지 끝난 상태
        return gatt != null && chWrite != null && EcgConfig.isBleReady;
    }

}

