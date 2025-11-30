// AppConfig.java
package com.kenny.spldownloader.config;

public class AppConfig {
    private AppConfig() {}

    // API配置
    public static final String BASE_API_URL = "https://api.vkeys.cn/v2/music/tencent";
    public static final int CONNECT_TIMEOUT = 10000;
    public static final int READ_TIMEOUT = 15000;
    public static final int MAX_RETRY_COUNT = 3;
    public static final int RETRY_DELAY_MS = 2000;

    // API端点
    public static final String ENDPOINT_LYRIC = "/lyric";
    public static final String ENDPOINT_SONG_INFO = "";
    public static final String ENDPOINT_PLAYLIST = "/dissinfo";
    public static final String ENDPOINT_SEARCH = "/search/song"; // 新增搜索端点

    // 文件配置保持不变...
    public static final String FOLDER_NORMAL_LRC = "LRC";
    public static final String FOLDER_WORD_BY_WORD = "SPL";

    // 通知配置保持不变...
    public static final String NOTIFICATION_CHANNEL_ID = "lyric_download_channel";
    public static final int NOTIFICATION_ID_SINGLE = 1001;
    public static final int NOTIFICATION_ID_BATCH = 1002;

    // 权限请求码保持不变...
    public static final int PERMISSION_REQUEST_CODE = 1001;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int INITIAL_PAGE = 1;
}