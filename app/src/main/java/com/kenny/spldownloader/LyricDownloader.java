package com.kenny.spldownloader;

import android.content.Context;
import android.util.Log;

import com.kenny.spldownloader.model.SongInfo;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class LyricDownloader {

    private static final String TAG = "LyricDownloader";
    private static final String LYRIC_API = "https://api.vkeys.cn/v2/music/tencent/lyric?mid=";
    private static final int MAX_RETRY_COUNT = 3; // 最大重试次数
    private static final int RETRY_DELAY_MS = 2000; // 重试延迟（毫秒）

    /**
     * 下载歌词（带重试机制）
     */
    public String downloadLyric(String songMid, LrcFileManager.LyricType lyricType) throws Exception {
        String apiUrl = LYRIC_API + songMid;
        Log.d(TAG, "开始下载歌词 - MID: " + songMid + ", 类型: " + lyricType);

        int retryCount = 0;
        Exception lastException;

        while (true) {
            try {
                String response = sendGetRequest(apiUrl);
                JSONObject jsonObject = new JSONObject(response);
                int responseCode = jsonObject.getInt("code");

                if (responseCode == 200) {
                    JSONObject data = jsonObject.getJSONObject("data");

                    String lyric;
                    if (lyricType == LrcFileManager.LyricType.NORMAL) {
                        // 普通歌词 - 直接从lrc字段获取
                        lyric = data.has("lrc") ? data.getString("lrc") : "";
                        if (lyric.isEmpty()) {
                            Log.w(TAG, "普通歌词内容为空 - MID: " + songMid);
                            throw new Exception("歌词内容为空");
                        }
                        Log.d(TAG, "普通歌词下载成功 - MID: " + songMid + ", 歌词长度: " + lyric.length());
                    } else {
                        // 逐字歌词 - 从yrc字段获取
                        lyric = data.has("yrc") ? data.getString("yrc") : "";
                        if (lyric.isEmpty()) {
                            Log.w(TAG, "逐字歌词内容为空 - MID: " + songMid);
                            throw new Exception("歌词内容为空");
                        }
                        Log.d(TAG, "逐字歌词下载成功 - MID: " + songMid + ", 歌词长度: " + lyric.length());
                    }
                    return lyric;
                } else if (responseCode == 503) {
                    // JSON响应中的503错误，需要重试
                    retryCount++;
                    String message = jsonObject.optString("message", "系统运行错误");
                    lastException = new Exception("API服务不可用 - 代码: " + responseCode + ", 消息: " + message);

                    if (retryCount <= MAX_RETRY_COUNT) {
                        Log.w(TAG, "服务器返回503错误，第 " + retryCount + " 次重试 - MID: " + songMid + ", 消息: " + message);
                        // 等待一段时间后重试
                        Thread.sleep(RETRY_DELAY_MS);
                    } else {
                        Log.e(TAG, "服务器返回503错误，已达到最大重试次数 - MID: " + songMid);
                        throw lastException;
                    }
                } else {
                    // 其他错误，不重试
                    String errorMsg = "API响应错误 - 代码: " + responseCode + ", 消息: " + jsonObject.optString("message", "未知错误");
                    Log.e(TAG, "歌词下载失败 - MID: " + songMid + " - " + errorMsg);
                    throw new Exception(errorMsg);
                }
            } catch (Exception e) {
                // 检查是否是网络连接问题
                if (e.getMessage() != null &&
                        (e.getMessage().contains("HTTP") || e.getMessage().contains("连接") ||
                                e.getMessage().contains("timeout") || e.getMessage().contains("Network"))) {
                    retryCount++;
                    lastException = e;

                    if (retryCount <= MAX_RETRY_COUNT) {
                        Log.w(TAG, "网络错误，第 " + retryCount + " 次重试 - MID: " + songMid + " - " + e.getMessage());
                        Thread.sleep(RETRY_DELAY_MS);
                    } else {
                        Log.e(TAG, "网络错误，已达到最大重试次数 - MID: " + songMid);
                        throw e;
                    }
                } else {
                    // 其他错误，直接抛出
                    throw e;
                }
            }
        }

    }

    /**
     * 下载并转换歌词（带重试机制）
     */
    public void downloadAndConvertSong(Context context, SongInfo songInfo, LrcFileManager.LyricType lyricType) throws Exception {
        Log.i(TAG, "开始处理歌曲 - 名称: " + songInfo.getSongName() +
                ", 歌手: " + songInfo.getSinger() +
                ", MID: " + songInfo.getMid() +
                ", 歌词类型: " + lyricType);

        String lyricContent = downloadLyric(songInfo.getMid(), lyricType);
        if (lyricContent != null && !lyricContent.isEmpty()) {
            String lrcContent;

            if (lyricType == LrcFileManager.LyricType.NORMAL) {
                // 普通歌词 - 直接使用
                lrcContent = lyricContent;
                Log.d(TAG, "普通歌词处理完成 - 歌曲: " + songInfo.getSongName());
            } else {
                // 逐字歌词 - 需要转换
                LrcConverter converter = new LrcConverter();
                lrcContent = converter.convertYrcToStandardLrc(lyricContent);
                Log.d(TAG, "逐字歌词转换完成 - 歌曲: " + songInfo.getSongName());
            }

            saveLrcFile(context, songInfo, lrcContent, lyricType);
            songInfo.setDownloadStatus(SongInfo.DownloadStatus.SUCCESS);
            Log.i(TAG, "歌词保存成功 - 歌曲: " + songInfo.getSongName());
        } else {
            String errorMsg = "获取到的歌词内容为空";
            Log.e(TAG, "歌词内容为空 - 歌曲: " + songInfo.getSongName() + ", MID: " + songInfo.getMid());
            throw new Exception(errorMsg);
        }
    }

    /**
     * 保存歌词文件
     */
    private void saveLrcFile(Context context, SongInfo songInfo, String lrcContent, LrcFileManager.LyricType lyricType) throws Exception {
        boolean success = LrcFileManager.saveLrcFile(context, songInfo.getFileName(), lrcContent, lyricType);
        if (!success) {
            String errorMsg = "保存歌词文件失败";
            Log.e(TAG, errorMsg + " - 歌曲: " + songInfo.getSongName() + ", 文件名: " + songInfo.getFileName());
            throw new Exception(errorMsg);
        }
        Log.d(TAG, "歌词文件保存成功 - 歌曲: " + songInfo.getSongName() + ", 文件名: " + songInfo.getFileName());
    }

    /**
     * 发送GET请求
     */
    private String sendGetRequest(String url) throws Exception {
        Log.d(TAG, "发送HTTP请求: " + url);

        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        con.setRequestProperty("Accept", "application/json");
        con.setConnectTimeout(10000); // 10秒连接超时
        con.setReadTimeout(15000);    // 15秒读取超时

        int responseCode = con.getResponseCode();
        Log.d(TAG, "HTTP响应代码: " + responseCode + ", URL: " + url);

        if (responseCode != 200) {
            String errorMsg = "HTTP请求失败，响应码: " + responseCode;
            Log.e(TAG, errorMsg + " - URL: " + url);
            throw new RuntimeException(errorMsg);
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));
        String inputLine;
        StringBuilder response = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        String responseStr = response.toString();
        Log.d(TAG, "HTTP响应内容长度: " + responseStr.length() + ", URL: " + url);

        // 检查JSON响应中的code字段
        try {
            JSONObject jsonObject = new JSONObject(responseStr);
            int jsonCode = jsonObject.getInt("code");
            if (jsonCode == 503) {
                String message = jsonObject.optString("message", "系统运行错误");
                throw new RuntimeException("API返回503错误: " + message);
            }
        } catch (Exception e) {
            // JSON解析失败，继续使用原始响应
            Log.w(TAG, "JSON解析失败，使用原始响应: " + e.getMessage());
        }

        return responseStr;
    }
}