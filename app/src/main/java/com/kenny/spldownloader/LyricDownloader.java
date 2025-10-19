package com.kenny.spldownloader;

import android.content.Context;

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

    /**
     * 下载歌词
     */
    public String downloadLyric(String songMid, LrcFileManager.LyricType lyricType) throws Exception {
        String apiUrl = LYRIC_API + songMid;
        String response = sendGetRequest(apiUrl);

        JSONObject jsonObject = new JSONObject(response);
        if (jsonObject.getInt("code") == 200) {
            JSONObject data = jsonObject.getJSONObject("data");

            if (lyricType == LrcFileManager.LyricType.NORMAL) {
                // 普通歌词 - 直接从lrc字段获取
                return data.has("lrc") ? data.getString("lrc") : "";
            } else {
                // 逐字歌词 - 从yrc字段获取
                return data.has("yrc") ? data.getString("yrc") : "";
            }
        }
        return null;
    }

    /**
     * 下载并转换歌词
     */
    public void downloadAndConvertSong(Context context, SongInfo songInfo, LrcFileManager.LyricType lyricType) throws Exception {
        String lyricContent = downloadLyric(songInfo.getMid(), lyricType);
        if (lyricContent != null && !lyricContent.isEmpty()) {
            String lrcContent;

            if (lyricType == LrcFileManager.LyricType.NORMAL) {
                // 普通歌词 - 直接使用
                lrcContent = lyricContent;
            } else {
                // 逐字歌词 - 需要转换
                LrcConverter converter = new LrcConverter();
                lrcContent = converter.convertYrcToStandardLrc(lyricContent);
            }

            saveLrcFile(context, songInfo, lrcContent, lyricType);
            songInfo.setDownloadStatus(SongInfo.DownloadStatus.SUCCESS);
        } else {
            throw new Exception("无法获取歌词内容");
        }
    }

    /**
     * 保存歌词文件
     */
    private void saveLrcFile(Context context, SongInfo songInfo, String lrcContent, LrcFileManager.LyricType lyricType) throws Exception {
        boolean success = LrcFileManager.saveLrcFile(context, songInfo.getFileName(), lrcContent, lyricType);
        if (!success) {
            throw new Exception("保存歌词文件失败");
        }
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
}