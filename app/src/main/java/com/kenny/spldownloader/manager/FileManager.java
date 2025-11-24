// FileManager.java
package com.kenny.spldownloader.manager;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import com.kenny.spldownloader.config.AppConfig;
import java.io.File;
import java.io.FileWriter;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class FileManager {
    private static final String TAG = "FileManager";

    public static boolean saveLyricFile(Context context, String fileName,
                                        String content, LyricType lyricType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return saveWithMediaStore(context, fileName, content, lyricType);
        } else {
            return saveWithLegacyMethod(context, fileName, content, lyricType);
        }
    }

    @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.Q)
    private static boolean saveWithMediaStore(Context context, String fileName,
                                              String content, LyricType lyricType) {
        try {
            ContentResolver resolver = context.getContentResolver();
            String safeFileName = ensureLrcExtension(makeSafeFileName(fileName));
            String folder = getFolderByLyricType(lyricType);

            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, safeFileName);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream");
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/" + folder);

            Uri uri = resolver.insert(
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    contentValues);

            if (uri != null) {
                try (OutputStream outputStream = resolver.openOutputStream(uri)) {
                    if (outputStream != null) {
                        outputStream.write(content.getBytes(StandardCharsets.UTF_8));
                        Log.d(TAG, "MediaStore文件保存成功: " + safeFileName);
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

    private static boolean saveWithLegacyMethod(Context context, String fileName,
                                                String content, LyricType lyricType) {
        try {
            String folder = getFolderByLyricType(lyricType);
            File downloadDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
            File targetDir = new File(downloadDir, folder);

            if (!targetDir.exists() && !targetDir.mkdirs()) {
                Log.e(TAG, "创建目录失败: " + targetDir.getAbsolutePath());
                return false;
            }

            String safeFileName = ensureLrcExtension(makeSafeFileName(fileName));
            File file = new File(targetDir, safeFileName);

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

    private static String getFolderByLyricType(LyricType lyricType) {
        return lyricType == LyricType.NORMAL ?
                AppConfig.FOLDER_NORMAL_LRC : AppConfig.FOLDER_WORD_BY_WORD;
    }

    private static String ensureLrcExtension(String fileName) {
        if (fileName == null) return "unknown.lrc";

        if (fileName.toLowerCase().endsWith(".lrc")) {
            return fileName;
        }

        String nameWithoutExt = fileName;
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            nameWithoutExt = fileName.substring(0, lastDotIndex);
        }

        return nameWithoutExt + ".lrc";
    }

    private static String makeSafeFileName(String fileName) {
        if (fileName == null) return "unknown.lrc";

        return fileName.replaceAll("[<>:\"/\\\\|?*]", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static String getLyricDirectoryPath(LyricType lyricType) {
        String folder = getFolderByLyricType(lyricType);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return Environment.DIRECTORY_DOWNLOADS + "/" + folder;
        } else {
            File downloadDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
            File targetDir = new File(downloadDir, folder);
            return targetDir.getAbsolutePath();
        }
    }

    public enum LyricType {
        NORMAL, WORD_BY_WORD
    }
}