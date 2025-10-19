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

    // 线程池用于并发处理
    private ExecutorService executorService = Executors.newFixedThreadPool(5);

    public interface ParseCallback {
        void onParseComplete(List<SongInfo> songList);
        void onParseError(String errorMessage);
        void onSongInfoAdded(SongInfo songInfo);
    }

    /**
     * 解析URL并获取歌曲列表
     */
    public void parseUrl(String url, ParseCallback callback) {
        new Thread(() -> {
            try {
                if (url.contains("taoge.html") || url.contains("dissinfo")) {
                    List<SongInfo> songList = processPlaylist(url);
                    callback.onParseComplete(songList);
                } else if (url.contains("playsong.html") || url.contains("songmid")) {
                    List<SongInfo> songList = processSingleSong(url);
                    callback.onParseComplete(songList);
                } else {
                    callback.onParseError("不支持的链接格式");
                }
            } catch (Exception e) {
                callback.onParseError("解析失败: " + e.getMessage());
            }
        }).start();
    }

    /**
     * 快速处理歌单 - 使用并发请求
     */
    private List<SongInfo> processPlaylist(String url) throws Exception {
        String playlistId = extractPlaylistId(url);
        if (playlistId == null || playlistId.isEmpty()) {
            throw new Exception("无法提取歌单ID");
        }

        // 获取歌单基本信息
        String playlistInfoUrl = PLAYLIST_API + playlistId + "&page=1&num=1";
        String response = sendGetRequest(playlistInfoUrl);
        JSONObject jsonObject = new JSONObject(response);

        if (jsonObject.getInt("code") != 200) {
            throw new Exception("获取歌单信息失败: " + jsonObject.getString("message"));
        }

        JSONObject data = jsonObject.getJSONObject("data");
        JSONObject info = data.getJSONObject("info");
        int totalSongs = info.getInt("songnum");

        Log.d(TAG, "歌单总歌曲数: " + totalSongs);

        if (totalSongs == 0) {
            return new ArrayList<>();
        }

        // 一次性获取所有歌曲（最大支持50首）
        int pageSize = Math.min(totalSongs, 50);
        String allSongsUrl = PLAYLIST_API + playlistId + "&page=1&num=" + pageSize;
        String allSongsResponse = sendGetRequest(allSongsUrl);
        JSONObject allSongsJson = new JSONObject(allSongsResponse);

        if (allSongsJson.getInt("code") != 200) {
            throw new Exception("获取歌曲列表失败");
        }

        JSONArray songArray = allSongsJson.getJSONObject("data").getJSONArray("list");

        // 使用线程池并发处理歌曲信息
        List<SongInfo> tempSongList = new ArrayList<>();
        List<GetSongInfoTask> tasks = new ArrayList<>();

        for (int i = 0; i < songArray.length(); i++) {
            JSONObject song = songArray.getJSONObject(i);
            String songMid = song.getString("mid");

            // 创建获取歌曲信息的任务
            GetSongInfoTask task = new GetSongInfoTask(songMid, tempSongList);
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
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // 重新创建线程池供下次使用
        executorService = Executors.newFixedThreadPool(5);

        return tempSongList;
    }

    /**
     * 处理单曲链接
     */
    private List<SongInfo> processSingleSong(String url) throws Exception {
        String songMid = extractSongMid(url);
        if (songMid != null && !songMid.isEmpty()) {
            SongInfo songInfo = getSongInfo(songMid);
            if (songInfo != null) {
                List<SongInfo> songList = new ArrayList<>();
                songList.add(songInfo);
                return songList;
            } else {
                throw new Exception("无法获取歌曲信息: " + songMid);
            }
        } else {
            throw new Exception("无法提取歌曲MID");
        }
    }

    /**
     * 获取歌曲信息的任务
     */
    private class GetSongInfoTask implements Runnable {
        private final String songMid;
        private final List<SongInfo> targetList;

        public GetSongInfoTask(String songMid, List<SongInfo> targetList) {
            this.songMid = songMid;
            this.targetList = targetList;
        }

        @Override
        public void run() {
            try {
                SongInfo songInfo = getSongInfo(songMid);
                if (songInfo != null) {
                    synchronized (targetList) {
                        targetList.add(songInfo);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "获取歌曲信息失败: " + songMid, e);
            }
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
        return null;
    }

    /**
     * 获取歌曲信息
     */
    private SongInfo getSongInfo(String songMid) throws Exception {
        String apiUrl = SONG_API + songMid;
        String response = sendGetRequest(apiUrl);

        JSONObject jsonObject = new JSONObject(response);
        if (jsonObject.getInt("code") == 200) {
            JSONObject data = jsonObject.getJSONObject("data");
            SongInfo songInfo = new SongInfo();
            songInfo.setMid(songMid);
            songInfo.setSongName(data.getString("song"));
            songInfo.setSinger(data.getString("singer"));
            return songInfo;
        }
        return null;
    }

    /**
     * 发送GET请求
     */
    private String sendGetRequest(String url) throws Exception {
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        con.setRequestProperty("Accept", "application/json");
        con.setConnectTimeout(10000); // 10秒连接超时
        con.setReadTimeout(15000);    // 15秒读取超时

        int responseCode = con.getResponseCode();
        if (responseCode != 200) {
            throw new RuntimeException("HTTP请求失败，响应码: " + responseCode);
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));
        String inputLine;
        StringBuilder response = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        return response.toString();
    }

    /**
     * 关闭线程池
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}