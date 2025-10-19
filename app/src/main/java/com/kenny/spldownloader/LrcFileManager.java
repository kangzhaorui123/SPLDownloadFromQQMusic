package com.kenny.spldownloader;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class LrcFileManager {

    private static final String TAG = "LrcFileManager";
    private static final String LRC_FOLDER = "LRC"; // 普通歌词文件夹
    private static final String SPL_FOLDER = "SPL"; // 逐字歌词文件夹

    /**
     * 歌词类型枚举
     */
    public enum LyricType {
        NORMAL,     // 普通歌词
        WORD_BY_WORD // 逐字歌词
    }

    /**
     * 保存歌词文件（自动选择最佳方式）
     */
    public static boolean saveLrcFile(Context context, String fileName, String content, LyricType lyricType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 使用MediaStore
            return saveWithMediaStore(context, fileName, content, lyricType);
        } else {
            // Android 9- 使用传统方式（需要权限）
            return saveWithLegacyMethod(context, fileName, content, lyricType);
        }
    }

    /**
     * MediaStore方式（Android 10+）
     */
    @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.Q)
    private static boolean saveWithMediaStore(Context context, String fileName, String content, LyricType lyricType) {
        try {
            ContentResolver resolver = context.getContentResolver();

            // 确保文件名以.lrc结尾
            String safeFileName = ensureLrcExtension(makeSafeFileName(fileName));

            // 根据歌词类型选择文件夹
            String folder = getFolderByLyricType(lyricType);

            // 1. 检查文件是否已存在
            Uri existingUri = findExistingFile(resolver, safeFileName, folder);
            if (existingUri != null) {
                // 文件已存在，直接覆盖
                return updateExistingFile(resolver, existingUri, content);
            }

            // 2. 创建新文件
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, safeFileName);

            // 使用更具体的MIME类型，避免系统自动添加.txt后缀
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream");

            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + folder);

            Uri uri = resolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), contentValues);

            if (uri != null) {
                try (OutputStream outputStream = resolver.openOutputStream(uri)) {
                    if (outputStream != null) {
                        outputStream.write(content.getBytes(StandardCharsets.UTF_8));
                        Log.d(TAG, "MediaStore文件保存成功: " + safeFileName + " 到文件夹: " + folder);
                        return true;
                    }
                }
            }

            Log.e(TAG, "MediaStore创建文件失败");
            return false;

        } catch (Exception e) {
            Log.e(TAG, "MediaStore保存文件失败: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 查找已存在的文件
     */
    @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.Q)
    private static Uri findExistingFile(ContentResolver resolver, String fileName, String folder) {
        String[] projection = {MediaStore.MediaColumns._ID};
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + " = ? AND " +
                MediaStore.MediaColumns.RELATIVE_PATH + " = ?";
        String[] selectionArgs = {fileName, Environment.DIRECTORY_DOWNLOADS + "/" + folder};

        try (Cursor cursor = resolver.query(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                projection,
                selection,
                selectionArgs,
                null)) {

            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID));
                return ContentUris.withAppendedId(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), id);
            }
        } catch (Exception e) {
            Log.e(TAG, "查询文件失败: " + e.getMessage());
        }

        return null;
    }

    /**
     * 更新已存在的文件 - 直接覆盖
     */
    @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.Q)
    private static boolean updateExistingFile(ContentResolver resolver, Uri uri, String content) {
        try {
            try (OutputStream outputStream = resolver.openOutputStream(uri, "wt")) {
                if (outputStream != null) {
                    outputStream.write(content.getBytes(StandardCharsets.UTF_8));
                    Log.d(TAG, "文件覆盖成功");
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "文件覆盖失败: " + e.getMessage(), e);
        }
        return false;
    }

    /**
     * 传统文件方式（Android 9-）
     */
    private static boolean saveWithLegacyMethod(Context context, String fileName, String content, LyricType lyricType) {
        try {
            // 根据歌词类型选择文件夹
            String folder = getFolderByLyricType(lyricType);

            // 保存到 Download/指定文件夹 目录
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File targetDir = new File(downloadDir, folder);

            if (!targetDir.exists() && !targetDir.mkdirs()) {
                Log.e(TAG, "创建目录失败: " + targetDir.getAbsolutePath());
                return false;
            }

            // 确保文件名以.lrc结尾
            String safeFileName = ensureLrcExtension(makeSafeFileName(fileName));
            File file = new File(targetDir, safeFileName);

            // 直接覆盖文件，不检查是否已存在
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(content);
                Log.d(TAG, "传统方式文件保存成功: " + file.getAbsolutePath());
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "传统方式保存失败: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 根据歌词类型获取文件夹名称
     */
    private static String getFolderByLyricType(LyricType lyricType) {
        return lyricType == LyricType.NORMAL ? LRC_FOLDER : SPL_FOLDER;
    }

    /**
     * 确保文件名以.lrc结尾
     */
    private static String ensureLrcExtension(String fileName) {
        if (fileName == null) return "unknown.lrc";

        // 如果文件名已经以.lrc结尾，直接返回
        if (fileName.toLowerCase().endsWith(".lrc")) {
            return fileName;
        }

        // 如果文件名有其他扩展名，先移除再添加.lrc
        String nameWithoutExt = fileName;
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            nameWithoutExt = fileName.substring(0, lastDotIndex);
        }

        return nameWithoutExt + ".lrc";
    }

    /**
     * 生成安全的文件名
     */
    private static String makeSafeFileName(String fileName) {
        if (fileName == null) return "unknown.lrc";

        return fileName.replaceAll("[<>:\"/\\\\|?*]", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * 获取LRC文件保存的目录路径（用于显示给用户）
     */
    public static String getLrcDirectoryPath(LyricType lyricType) {
        String folder = getFolderByLyricType(lyricType);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return Environment.DIRECTORY_DOWNLOADS + "/" + folder;
        } else {
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File targetDir = new File(downloadDir, folder);
            return targetDir.getAbsolutePath();
        }
    }
}