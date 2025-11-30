// UrlParser.java
package com.kenny.spldownloader.service;

import android.os.Build;
import android.util.Log;
import com.kenny.spldownloader.config.AppConfig;
import com.kenny.spldownloader.model.SongInfo;
import com.kenny.spldownloader.network.ApiClient;
import com.kenny.spldownloader.network.ApiException;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlParser {
    private static final String TAG = "UrlParser";
    private final ApiClient apiClient;

    public UrlParser() {
        this.apiClient = ApiClient.getInstance();
    }

    public List<SongInfo> parseInput(String input) throws Exception {
        Log.d(TAG, "开始解析输入: " + input);

        if (isUrl(input)) {
            return parseUrl(input);
        } else {
            return searchByKeyword(input, 1, 20);
        }
    }

    private boolean isUrl(String input) {
        return input.startsWith("http://") || input.startsWith("https://") ||
                input.contains("qq.com") || input.contains("y.qq.com");
    }

    public List<SongInfo> parseUrl(String url) throws Exception {
        Log.d(TAG, "开始解析URL: " + url);

        if (url.contains("taoge.html") || url.contains("dissinfo")) {
            return parsePlaylist(url);
        } else if (url.contains("playsong.html") || url.contains("songmid")) {
            return parseSingleSong(url);
        } else {
            throw new Exception("不支持的链接格式: " + url);
        }
    }

    public List<SongInfo> searchByKeyword(String keyword, int page, int pageSize) throws Exception {
        Log.d(TAG, "开始搜索关键词: " + keyword + ", 页码: " + page);

        if (keyword == null || keyword.trim().isEmpty()) {
            throw new Exception("搜索关键词不能为空");
        }

        String encodedKeyword = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            encodedKeyword = URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8);
        }
        String apiUrl = AppConfig.BASE_API_URL + "/search/song?word=" + encodedKeyword +
                "&page=" + page + "&num=" + pageSize;
        Log.d(TAG, "搜索API URL: " + apiUrl);

        try {
            JSONObject jsonObject = apiClient.executeGetRequestJson(apiUrl);

            if (jsonObject.getInt("code") != 200) {
                String message = jsonObject.optString("message", "未知错误");
                throw new Exception("搜索失败: " + message + " (代码: " + jsonObject.getInt("code") + ")");
            }

            JSONArray data = jsonObject.getJSONArray("data");
            List<SongInfo> songList = new ArrayList<>();

            for (int i = 0; i < data.length(); i++) {
                JSONObject songJson = data.getJSONObject(i);
                SongInfo song = new SongInfo();
                song.setMid(songJson.getString("mid"));
                song.setSongName(songJson.getString("song"));
                song.setSinger(songJson.getString("singer"));
                songList.add(song);
            }

            Log.i(TAG, "搜索完成 - 找到歌曲数量: " + songList.size());
            return songList;

        } catch (ApiException e) {
            throw new Exception("网络请求失败: " + e.getMessage());
        } catch (Exception e) {
            throw new Exception("解析搜索结果失败: " + e.getMessage());
        }
    }

    private List<SongInfo> parsePlaylist(String url) throws Exception {
        String playlistId = extractPlaylistId(url);
        if (playlistId == null || playlistId.isEmpty()) {
            throw new Exception("无法提取歌单ID");
        }

        Log.i(TAG, "解析歌单 - ID: " + playlistId);
        String apiUrl = AppConfig.BASE_API_URL + AppConfig.ENDPOINT_PLAYLIST + "?id=" + playlistId + "&page=1&num=50";

        JSONObject jsonObject = apiClient.executeGetRequestJson(apiUrl);
        if (jsonObject.getInt("code") != 200) {
            String message = jsonObject.optString("message", "未知错误");
            throw new Exception("获取歌单信息失败: " + message);
        }

        JSONObject data = jsonObject.getJSONObject("data");
        JSONArray list = data.getJSONArray("list");
        List<SongInfo> songList = new ArrayList<>();

        for (int i = 0; i < list.length(); i++) {
            JSONObject songJson = list.getJSONObject(i);
            SongInfo song = new SongInfo();
            song.setMid(songJson.getString("mid"));
            song.setSongName(songJson.getString("song"));
            song.setSinger(songJson.getString("singer"));
            songList.add(song);
        }

        return songList;
    }

    private List<SongInfo> parseSingleSong(String url) throws Exception {
        String songMid = extractSongMid(url);
        if (songMid == null || songMid.isEmpty()) {
            throw new Exception("无法提取歌曲MID");
        }

        Log.i(TAG, "解析单曲 - MID: " + songMid);
        String apiUrl = AppConfig.BASE_API_URL + "?mid=" + songMid;

        JSONObject jsonObject = apiClient.executeGetRequestJson(apiUrl);
        if (jsonObject.getInt("code") != 200) {
            String message = jsonObject.optString("message", "未知错误");
            throw new Exception("获取歌曲信息失败: " + message);
        }

        JSONObject data = jsonObject.getJSONObject("data");
        SongInfo song = new SongInfo();
        song.setMid(songMid);
        song.setSongName(data.getString("song"));
        song.setSinger(data.getString("singer"));

        List<SongInfo> songList = new ArrayList<>();
        songList.add(song);
        return songList;
    }

    private String extractPlaylistId(String url) {
        Pattern pattern = Pattern.compile("[?&]id=([^&]*)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return Objects.requireNonNull(matcher.group(1)).replaceAll("\\*+$", "");
        }
        return null;
    }

    private String extractSongMid(String url) {
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
}