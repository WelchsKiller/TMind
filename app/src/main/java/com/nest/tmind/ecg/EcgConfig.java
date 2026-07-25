package com.nest.tmind.ecg;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.location.LocationManager;
import android.os.Build;
import android.os.SystemClock;

import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class EcgConfig {

    public static final String BASE_URL = "http://222.239.250.178:8082/api/v1/auth/";

    public static long endTime;
    public static long appLaunchStartTime = 0L;  // 앱 실행속도 측정용
    public static long uiStartTime = 0L;         // 너가 쓰는 UI 반응속도용

    public static boolean isBleReady = false;

    /* ===== "5분전" / "1시간전" / "yy.MM.dd" 포맷 ===== */
    public static String formatAgo(long timeMs) {
        if (timeMs <= 0) return "";

        long now = System.currentTimeMillis();
        long diff = now - timeMs;
        if (diff < 0) diff = 0;

        long minute = diff / (60 * 1000);
        if (minute < 60) {
            if (minute <= 0) return "방금 전";
            return minute + "분전";
        }

        long hour = diff / (60 * 60 * 1000);
        if (hour < 24) {
            return hour + "시간전";
        }

        long day = diff / (24 * 60 * 60 * 1000);
        if (day <= 3) {
            return day + "일전";
        }

        SimpleDateFormat fmt = new SimpleDateFormat("yy.MM.dd", Locale.KOREA);
        return fmt.format(new Date(timeMs));
    }

    public static String formatMeasuredTime(long millis) {
        // 한국식 요일/날짜 포맷
        SimpleDateFormat sdf =
                new SimpleDateFormat("yyyy.MM.dd(E) HH:mm", Locale.KOREA);
        return sdf.format(new Date(millis));
    }


    // 블루투스 권한이 있는지
    public static boolean hasBluetoothPermission(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) ==
                            PackageManager.PERMISSION_GRANTED;
        } else {
            // S 미만에서는 별도 BT 권한 없음 (위치 권한이 필요함)
            return true;
        }
    }

    // 블루투스가 실제로 켜져 있는지
    public static boolean isBluetoothOn(Context ctx) {
        BluetoothManager btMgr = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = (btMgr != null) ? btMgr.getAdapter() : null;
        return adapter != null && adapter.isEnabled();
    }

    // 위치 권한이 있는지
    public static boolean hasLocationPermission(Context ctx) {
        // 필요에 따라 COARSE 도 같이 체크해도 됨
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    // 위치 서비스(GPS/네트워크)가 켜져 있는지
    public static boolean isLocationServiceOn(Context ctx) {
        LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return false;
        try {
            return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) {
            return false;
        }
    }
    //블루투스, 위치 권한 한번에 확인하기
    public static boolean isBleReady(Context ctx) {
        if (!hasBluetoothPermission(ctx)) return false;

        // S 미만에서만 위치 권한 요구
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            if (!hasLocationPermission(ctx)) return false;
        }

        if (!isBluetoothOn(ctx)) return false;
        if (!isLocationServiceOn(ctx)) return false;
        return true;
    }



}

