package com.nest.tmind.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** BLE 측정에 필요한 권한 */
public final class BlePermissionHelper {

    private BlePermissionHelper() {
    }

    public static String[] requiredPermissions() {
        List<String> perms = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN);
            perms.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
            perms.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        // 일부 기기에서 BLE 스캔에 위치 권한이 추가로 필요
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!perms.contains(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                perms.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            }
        }
        return perms.toArray(new String[0]);
    }

    public static boolean hasAll(Context ctx) {
        for (String p : requiredPermissions()) {
            if (ContextCompat.checkSelfPermission(ctx, p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static boolean allGranted(Map<String, Boolean> result) {
        if (result == null || result.isEmpty()) return false;
        for (Boolean ok : result.values()) {
            if (ok == null || !ok) return false;
        }
        return true;
    }
}
