package com.kenny.spldownloader;

import android.util.Log;

import com.kenny.spldownloader.model.SongInfo;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaylistParser {

    private static final String TAG = "PlaylistParser";
    private static final String SONG_API = "https://api.vkeys.cn/v2/music/tencent?mid=";
    private static final String PLAYLIST_API = "https://api.vkeys.cn/v2/music/tencent/dissinfo?id=";
    private static final int MAX_RETRY_COUNT = 3; // 最大重试次数
    private static final int RETRY_DELAY_MS = 2000; // 重试延迟（毫秒）

    // 线程池用于并发处理
    private ExecutorService executorService = Executors.newFixedThreadPool(5);

    public interface ParseCallback {
        void onParseComplete(List<SongInfo> songList);
        void onParseError(String errorMessage);
        void onSongInfoAdded(SongInfo songInfo);
        void onParseProgress(int current, int total);
        void onRetryProgress(int retryCount, int retryingCount, int totalRetry); // 新增：重试进度回调
    }

    /**
     * 解析URL并获取歌曲列表
     */
    public void parseUrl(String url, ParseCallback callback) {
        Log.i(TAG, "开始解析URL: " + url);
        new Thread(() -> {
            try {
                if (url.contains("taoge.html") || url.contains("dissinfo")) {
                    List<SongInfo> songList = processPlaylist(url, callback);
                    callback.onParseComplete(songList);
                } else if (url.contains("playsong.html") || url.contains("songmid")) {
                    List<SongInfo> songList = processSingleSong(url);
                    callback.onParseComplete(songList);
                } else {
                    String errorMsg = "不支持的链接格式: " + url;
                    Log.e(TAG, errorMsg);
                    callback.onParseError(errorMsg);
                }
            } catch (Exception e) {
                String errorMsg = "解析失败: " + e.getMessage();
                Log.e(TAG, errorMsg, e);
                callback.onParseError(errorMsg);
            }
        }).start();
    }

    /**
     * 快速处理歌单 - 使用并发请求（带重试机制）
     */
    private List<SongInfo> processPlaylist(String url, ParseCallback callback) throws Exception {
        String playlistId = extractPlaylistId(url);
        if (playlistId == null || playlistId.isEmpty()) {
            throw new Exception("无法提取歌单ID");
        }

        Log.i(TAG, "处理歌单 - ID: " + playlistId);

        // 获取歌单基本信息（带重试）
        String playlistInfoUrl = PLAYLIST_API + playlistId + "&page=1&num=1";
        String response = sendGetRequestWithRetry(playlistInfoUrl, "获取歌单信息");
        JSONObject jsonObject = new JSONObject(response);

        if (jsonObject.getInt("code") != 200) {
            String errorMsg = "获取歌单信息失败: " + jsonObject.getString("message");
            Log.e(TAG, errorMsg + " - 歌单ID: " + playlistId);
            throw new Exception(errorMsg);
        }

        JSONObject data = jsonObject.getJSONObject("data");
        JSONObject info = data.getJSONObject("info");
        int totalSongs = info.getInt("songnum");

        Log.d(TAG, "歌单总歌曲数: " + totalSongs);

        if (totalSongs == 0) {
            Log.w(TAG, "歌单为空 - 歌单ID: " + playlistId);
            return new ArrayList<>();
        }

        // 一次性获取所有歌曲（最大支持50首）
        int pageSize = Math.min(totalSongs, 50);
        String allSongsUrl = PLAYLIST_API + playlistId + "&page=1&num=" + pageSize;
        String allSongsResponse = sendGetRequestWithRetry(allSongsUrl, "获取歌曲列表");
        JSONObject allSongsJson = new JSONObject(allSongsResponse);

        if (allSongsJson.getInt("code") != 200) {
            String errorMsg = "获取歌曲列表失败 - 歌单ID: " + playlistId;
            Log.e(TAG, errorMsg);
            throw new Exception(errorMsg);
        }

        JSONArray songArray = allSongsJson.getJSONObject("data").getJSONArray("list");
        Log.i(TAG, "成功获取歌曲列表 - 数量: " + songArray.length() + ", 歌单ID: " + playlistId);

        // 使用线程池并发处理歌曲信息
        List<SongInfo> tempSongList = new ArrayList<>();
        List<GetSongInfoTask> tasks = new ArrayList<>();

        for (int i = 0; i < songArray.length(); i++) {
            JSONObject song = songArray.getJSONObject(i);
            String songMid = song.getString("mid");

            // 创建获取歌曲信息的任务
            GetSongInfoTask task = new GetSongInfoTask(songMid, tempSongList, callback, i, songArray.length());
            tasks.add(task);
        }

        // 提交所有任务到线程池
        for (GetSongInfoTask task : tasks) {
            executorService.submit(task);
        }

        // 等待所有任务完成
        executorService.shutdown();
        try {
            // 设置超时时间，避免无限等待
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                Log.w(TAG, "线程池超时，强制关闭");
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
            Log.e(TAG, "线程池被中断", e);
        }

        // 检查是否有失败的任务需要重试
        List<GetSongInfoTask> failedTasks = new ArrayList<>();
        for (GetSongInfoTask task : tasks) {
            if (!task.isCompleted()) {
                failedTasks.add(task);
            }
        }

        // 重试失败的任务
        if (!failedTasks.isEmpty()) {
            Log.w(TAG, "有 " + failedTasks.size() + " 个任务需要重试");
            retryFailedTasks(failedTasks, callback);
        }

        // 重新创建线程池供下次使用
        executorService = Executors.newFixedThreadPool(5);

        Log.i(TAG, "歌单解析完成 - 成功解析歌曲数: " + tempSongList.size() + ", 歌单ID: " + playlistId);
        return tempSongList;
    }

    /**
     * 重试失败的任务
     */
    private void retryFailedTasks(List<GetSongInfoTask> failedTasks, ParseCallback callback) {
        for (int retryCount = 1; retryCount <= MAX_RETRY_COUNT; retryCount++) {
            Log.i(TAG, "开始第 " + retryCount + " 次重试，剩余任务: " + failedTasks.size());

            if (callback != null) {
                callback.onRetryProgress(retryCount, failedTasks.size(), MAX_RETRY_COUNT);
            }

            // 等待一段时间再重试
            try {
                Thread.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.w(TAG, "重试等待被中断");
                break;
            }

            List<GetSongInfoTask> stillFailed = new ArrayList<>();
            ExecutorService retryExecutor = Executors.newFixedThreadPool(3);

            for (GetSongInfoTask task : failedTasks) {
                retryExecutor.submit(() -> {
                    try {
                        task.run();
                        if (!task.isCompleted()) {
                            synchronized (stillFailed) {
                                stillFailed.add(task);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "重试任务执行失败", e);
                        synchronized (stillFailed) {
                            stillFailed.add(task);
                        }
                    }
                });
            }

            retryExecutor.shutdown();
            try {
                if (!retryExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    retryExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                retryExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }

            failedTasks = stillFailed;
            if (failedTasks.isEmpty()) {
                Log.i(TAG, "第 " + retryCount + " 次重试成功，所有任务完成");
                break;
            } else {
                Log.w(TAG, "第 " + retryCount + " 次重试后仍有 " + failedTasks.size() + " 个任务失败");
            }
        }

        if (!failedTasks.isEmpty()) {
            Log.e(TAG, "重试结束，仍有 " + failedTasks.size() + " 个任务失败");
        }
    }

    /**
     * 处理单曲链接
     */
    private List<SongInfo> processSingleSong(String url) throws Exception {
        String songMid = extractSongMid(url);
        Log.i(TAG, "处理单曲 - MID: " + songMid);

        if (songMid != null && !songMid.isEmpty()) {
            SongInfo songInfo = getSongInfoWithRetry(songMid);
            List<SongInfo> songList = new ArrayList<>();
            songList.add(songInfo);
            Log.i(TAG, "单曲解析成功 - 歌曲: " + songInfo.getSongName() + ", MID: " + songMid);
            return songList;
        } else {
            String errorMsg = "无法提取歌曲MID - URL: " + url;
            Log.e(TAG, errorMsg);
            throw new Exception(errorMsg);
        }
    }

    /**
     * 获取歌曲信息（带重试）
     */
    private SongInfo getSongInfoWithRetry(String songMid) throws Exception {
        int retryCount = 0;
        Exception lastException = null;

        while (true) {
            try {
                return fetchSongInfo(songMid);
            } catch (Exception e) {
                retryCount++;
                lastException = e;

                // 检查是否是503错误或网络错误
                boolean shouldRetry = false;
                if (e.getMessage() != null) {
                    shouldRetry = e.getMessage().contains("503") ||
                            e.getMessage().contains("系统运行错误") ||
                            e.getMessage().contains("HTTP") ||
                            e.getMessage().contains("连接") ||
                            e.getMessage().contains("timeout") ||
                            e.getMessage().contains("Network");
                }

                if (retryCount <= MAX_RETRY_COUNT && shouldRetry) {
                    Log.w(TAG, "获取歌曲信息遇到错误，第 " + retryCount + " 次重试 - MID: " + songMid + " - " + e.getMessage());
                    Thread.sleep(RETRY_DELAY_MS);
                } else {
                    throw e;
                }
            }
        }

    }

    /**
     * 发送GET请求（带重试）
     */
    private String sendGetRequestWithRetry(String url, String operation) throws Exception {
        int retryCount = 0;
        Exception lastException = null;

        while (true) {
            try {
                return sendGetRequest(url);
            } catch (Exception e) {
                retryCount++;
                lastException = e;

                // 检查是否是503错误或网络错误
                boolean shouldRetry = false;
                if (e.getMessage() != null) {
                    shouldRetry = e.getMessage().contains("503") ||
                            e.getMessage().contains("系统运行错误") ||
                            e.getMessage().contains("HTTP") ||
                            e.getMessage().contains("连接") ||
                            e.getMessage().contains("timeout") ||
                            e.getMessage().contains("Network");
                }

                if (retryCount <= MAX_RETRY_COUNT && shouldRetry) {
                    Log.w(TAG, operation + " 遇到错误，第 " + retryCount + " 次重试 - URL: " + url + " - " + e.getMessage());
                    Thread.sleep(RETRY_DELAY_MS);
                } else {
                    throw e;
                }
            }
        }

    }

    /**
     * 获取歌曲信息的任务
     */
    private class GetSongInfoTask implements Runnable {
        private final String songMid;
        private final List<SongInfo> targetList;
        private final ParseCallback callback;
        private final int currentIndex;
        private final int totalCount;
        private boolean completed = false;
        private SongInfo songInfoResult = null; // 重命名以避免冲突

        public GetSongInfoTask(String songMid, List<SongInfo> targetList, ParseCallback callback, int currentIndex, int totalCount) {
            this.songMid = songMid;
            this.targetList = targetList;
            this.callback = callback;
            this.currentIndex = currentIndex;
            this.totalCount = totalCount;
        }

        @Override
        public void run() {
            int retryCount = 0;
            Exception lastException;

            while (true) {
                try {
                    // 修复：使用 PlaylistParser.this 明确调用外部类方法
                    songInfoResult = PlaylistParser.this.fetchSongInfo(songMid);
                    synchronized (targetList) {
                        targetList.add(songInfoResult);
                    }

                    // 通知进度更新
                    if (callback != null) {
                        callback.onParseProgress(currentIndex + 1, totalCount);
                    }

                    completed = true;
                    Log.d(TAG, "成功获取歌曲信息 - 歌曲: " + songInfoResult.getSongName() + ", MID: " + songMid);
                    return;
                } catch (Exception e) {
                    retryCount++;
                    lastException = e;

                    // 检查是否是503错误或网络错误
                    boolean shouldRetry = false;
                    if (e.getMessage() != null) {
                        shouldRetry = e.getMessage().contains("503") ||
                                e.getMessage().contains("系统运行错误") ||
                                e.getMessage().contains("HTTP") ||
                                e.getMessage().contains("连接") ||
                                e.getMessage().contains("timeout") ||
                                e.getMessage().contains("Network");
                    }

                    if (retryCount <= MAX_RETRY_COUNT && shouldRetry) {
                        Log.w(TAG, "获取歌曲信息遇到错误，第 " + retryCount + " 次重试 - MID: " + songMid + " - " + e.getMessage());
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    } else {
                        Log.e(TAG, "获取歌曲信息失败 - MID: " + songMid, e);
                        break;
                    }
                }
            }

            if (!completed) {
                Log.e(TAG, "获取歌曲信息最终失败 - MID: " + songMid + ", 错误: " +
                        lastException.getMessage());
            }
        }

        public boolean isCompleted() {
            return completed;
        }

        public SongInfo getSongInfoResult() { // 重命名方法
            return songInfoResult;
        }
    }

    /**
     * 提取歌曲MID
     */
    private String extractSongMid(String url) {
        // 提取songmid参数
        Pattern pattern = Pattern.compile("[?&]songmid=([^&]*)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }

        pattern = Pattern.compile("[?&]mid=([^&]*)");
        matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }

        Log.w(TAG, "无法从URL提取歌曲MID - URL: " + url);
        return null;
    }

    /**
     * 提取歌单ID
     */
    private String extractPlaylistId(String url) {
        // 提取歌单id参数
        Pattern pattern = Pattern.compile("[?&]id=([^&]*)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return Objects.requireNonNull(matcher.group(1)).replaceAll("\\*+$", ""); // 移除末尾的星号
        }
        Log.w(TAG, "无法从URL提取歌单ID - URL: " + url);
        return null;
    }

    /**
     * 获取歌曲信息 - 重命名以避免与内部类方法冲突
     */
    private SongInfo fetchSongInfo(String songMid) throws Exception {
        String apiUrl = SONG_API + songMid;
        String response = sendGetRequest(apiUrl);

        JSONObject jsonObject = new JSONObject(response);
        int responseCode = jsonObject.getInt("code");

        if (responseCode == 200) {
            JSONObject data = jsonObject.getJSONObject("data");
            SongInfo songInfo = new SongInfo();
            songInfo.setMid(songMid);
            songInfo.setSongName(data.getString("song"));
            songInfo.setSinger(data.getString("singer"));
            return songInfo;
        } else if (responseCode == 503) {
            String message = jsonObject.optString("message", "系统运行错误");
            throw new Exception("API返回503错误 - 代码: " + responseCode + ", 消息: " + message);
        } else {
            String errorMsg = "获取歌曲信息API错误 - 代码: " + responseCode +
                    ", 消息: " + jsonObject.optString("message", "未知错误") +
                    ", MID: " + songMid;
            Log.e(TAG, errorMsg);
            throw new Exception(errorMsg);
        }
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

    /**
     * 关闭线程池
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            Log.d(TAG, "线程池已关闭");
        }
    }
}