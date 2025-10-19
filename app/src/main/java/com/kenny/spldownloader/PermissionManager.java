package com.kenny.spldownloader;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class PermissionManager {

    /**
     * 检查并请求必要的权限
     */
    public static void checkAndRequestPermissions(Activity activity,
                                                  String[] permissionsToRequest) {
        if (permissionsToRequest.length > 0) {
            ActivityCompat.requestPermissions(activity, permissionsToRequest, 1001);
        }
    }

    /**
     * 获取需要请求的权限列表
     */
    public static String[] getRequiredPermissions(Context context) {
        List<String> permissionsToRequest = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 需要媒体权限
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO);
            }
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Android 9- 需要存储权限
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
        // Android 10-12 不需要额外权限（使用MediaStore）

        return permissionsToRequest.toArray(new String[0]);
    }

    /**
     * 检查是否具有必要的权限
     */
    public static boolean hasRequiredPermissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        // Android 10-12 总是返回true，因为不需要权限
        return true;
    }
}