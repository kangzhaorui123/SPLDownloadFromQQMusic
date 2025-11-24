// LyricService.java
package com.kenny.spldownloader.service;

import android.util.Log;
import com.kenny.spldownloader.config.AppConfig;
import com.kenny.spldownloader.converter.LrcConverter;
import com.kenny.spldownloader.model.LyricResponse;
import com.kenny.spldownloader.model.SongInfo;
import com.kenny.spldownloader.network.ApiClient;
import com.kenny.spldownloader.network.ApiException;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.concurrent.Callable;

public class LyricService {
    private static final String TAG = "LyricService";

    private final ApiClient apiClient;
    private final LrcConverter converter;

    public LyricService() {
        this.apiClient = ApiClient.getInstance();
        this.converter = new LrcConverter();
    }

    public LyricResponse downloadLyric(String songMid) throws ApiException, JSONException {
        // 修正：使用正确的歌词API端点
        String apiUrl = AppConfig.BASE_API_URL + AppConfig.ENDPOINT_LYRIC + "?mid=" + songMid;
        Log.d(TAG, "下载歌词 - MID: " + songMid + ", URL: " + apiUrl);

        String response = apiClient.executeGetRequest(apiUrl);
        JSONObject jsonObject = new JSONObject(response);
        JSONObject data = jsonObject.getJSONObject("data");

        return LyricResponse.fromJson(data);
    }

    public Callable<String> createDownloadTask(SongInfo songInfo, LyricType lyricType) {
        return () -> {
            try {
                LyricResponse response = downloadLyric(songInfo.getMid());
                String lyricContent;

                switch (lyricType) {
                    case NORMAL:
                        lyricContent = response.getLrc();
                        if (!response.hasNormalLyric()) {
                            throw new ApiException("普通歌词内容为空");
                        }
                        break;
                    case WORD_BY_WORD:
                        lyricContent = response.getYrc();
                        if (!response.hasWordByWordLyric()) {
                            throw new ApiException("逐字歌词内容为空");
                        }
                        // 转换YRC格式
                        lyricContent = converter.convertYrcToStandardLrc(lyricContent);
                        break;
                    default:
                        throw new ApiException("不支持的歌词类型");
                }

                if (lyricContent == null || lyricContent.trim().isEmpty()) {
                    throw new ApiException("歌词内容为空");
                }

                return lyricContent;

            } catch (Exception e) {
                Log.e(TAG, "下载歌词失败 - 歌曲: " + songInfo.getSongName() +
                        ", MID: " + songInfo.getMid() + ", 错误: " + e.getMessage());
                throw e;
            }
        };
    }

    public enum LyricType {
        NORMAL, WORD_BY_WORD
    }
}