package com.kenny.spldownloader;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kenny.spldownloader.adapter.SongAdapter;
import com.kenny.spldownloader.model.SongInfo;

import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // 通知相关常量
    private static final String CHANNEL_ID = "lyric_download_channel";
    private static final int NOTIFICATION_ID_SINGLE = 1001;
    private static final int NOTIFICATION_ID_BATCH = 1002;

    private EditText etUrl;
    private RadioGroup rgLyricType;
    private Button btnParse, btnDownloadAll;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private SongAdapter adapter;

    // ViewModel
    private SongViewModel songViewModel;

    // 解析器和下载器
    private PlaylistParser playlistParser;
    private LyricDownloader lyricDownloader;

    // 通知管理器
    private NotificationManager notificationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化 ViewModel
        songViewModel = new ViewModelProvider(this).get(SongViewModel.class);

        // 设置Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // 初始化组件
        initNotificationChannel();
        initParsers();
        initViews();
        setupObservers();
        checkPermissions();

        // 恢复 URL 输入框的状态（如果有）
        if (savedInstanceState != null) {
            String savedUrl = savedInstanceState.getString("url_input", "");
            etUrl.setText(savedUrl);

            int selectedRadioId = savedInstanceState.getInt("selected_radio", R.id.rb_normal_lyric);
            rgLyricType.check(selectedRadioId);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // 保存输入框和单选按钮的状态
        if (etUrl != null) {
            outState.putString("url_input", etUrl.getText().toString());
        }
        if (rgLyricType != null) {
            outState.putInt("selected_radio", rgLyricType.getCheckedRadioButtonId());
        }
    }

    /**
     * 初始化解析器和下载器
     */
    private void initParsers() {
        playlistParser = new PlaylistParser();
        lyricDownloader = new LyricDownloader();
    }

    /**
     * 初始化通知渠道 (Android 8.0+ 需要)
     */
    private void initNotificationChannel() {
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "歌词下载通知",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("显示歌词下载进度和结果");
        channel.enableLights(true);
        channel.setLightColor(Color.BLUE);
        channel.enableVibration(true);
        notificationManager.createNotificationChannel(channel);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 关闭解析器
        if (playlistParser != null) {
            playlistParser.shutdown();
        }
    }

    private void initViews() {
        etUrl = findViewById(R.id.et_url);
        rgLyricType = findViewById(R.id.rg_lyric_type);
        btnParse = findViewById(R.id.btn_parse);
        btnDownloadAll = findViewById(R.id.btn_download_all);
        progressBar = findViewById(R.id.progress_bar);
        tvStatus = findViewById(R.id.tv_status);

        // 初始化RecyclerView
        initRecyclerView();

        // 设置按钮点击监听器
        setupButtonListeners();
    }

    /**
     * 设置按钮点击监听器
     */
    private void setupButtonListeners() {
        // 解析链接按钮
        btnParse.setOnClickListener(v -> {
            String inputUrl = etUrl.getText().toString().trim();
            if (!inputUrl.isEmpty()) {
                // 检查权限
                if (PermissionManager.hasRequiredPermissions(MainActivity.this)) {
                    startParseUrl(inputUrl);
                } else {
                    String[] permissions = PermissionManager.getRequiredPermissions(MainActivity.this);
                    PermissionManager.checkAndRequestPermissions(MainActivity.this, permissions);
                    showStatus("请先授予存储权限");
                }
            } else {
                showStatus("请输入链接");
            }
        });

        // 下载全部按钮
        btnDownloadAll.setOnClickListener(v -> {
            List<SongInfo> currentSongs = songViewModel.getSongList().getValue();
            if (currentSongs != null && !currentSongs.isEmpty()) {
                // 获取选择的歌词类型
                LrcFileManager.LyricType lyricType = getSelectedLyricType();
                startBatchDownload(lyricType);
            } else {
                showStatus("没有可下载的歌曲");
            }
        });
    }

    /**
     * 初始化RecyclerView
     */
    private void initRecyclerView() {
        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new SongAdapter(this);
        recyclerView.setAdapter(adapter);

        // 添加分割线
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        // 设置点击监听器
        adapter.setOnItemClickListener(this::showDownloadDialog);
    }

    /**
     * 设置观察者
     */
    private void setupObservers() {
        // 观察歌曲列表变化
        songViewModel.getSongList().observe(this, songs -> {
            adapter.setSongList(songs);
            // 更新下载全部按钮状态
            btnDownloadAll.setEnabled(songs != null && !songs.isEmpty());

            // 更新状态显示
            if (songs != null && !songs.isEmpty()) {
                showStatus(String.format(Locale.getDefault(), "已加载 %d 首歌曲", songs.size()));
            } else {
                hideStatus();
            }
        });

        // 观察加载状态
        songViewModel.getIsLoading().observe(this, isLoading -> {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            btnParse.setEnabled(!isLoading);

            // 更新状态显示
            if (isLoading) {
                showStatus("处理中...");
            } else {
                // 检查是否有歌曲列表，如果有则显示歌曲数量
                List<SongInfo> songs = songViewModel.getSongList().getValue();
                if (songs != null && !songs.isEmpty()) {
                    showStatus(String.format(Locale.getDefault(), "已加载 %d 首歌曲", songs.size()));
                } else {
                    hideStatus();
                }
            }
        });

        // 观察解析错误
        songViewModel.getParseError().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                showStatus("解析错误: " + error);
                songViewModel.setParseError(null); // 清除错误状态
            }
        });
    }

    /**
     * 开始解析URL
     */
    private void startParseUrl(String url) {
        songViewModel.setLoading(true);
        songViewModel.clearSongs();

        showStatus("正在解析链接，请稍候...");

        playlistParser.parseUrl(url, new PlaylistParser.ParseCallback() {
            @Override
            public void onParseComplete(List<SongInfo> parsedSongList) {
                runOnUiThread(() -> {
                    songViewModel.setLoading(false);
                    songViewModel.setSongList(parsedSongList);

                    String result = String.format(Locale.getDefault(), "解析完成，共找到 %d 首歌曲", parsedSongList.size());
                    showStatus(result);
                    Toast.makeText(MainActivity.this, result, Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onParseError(String errorMessage) {
                runOnUiThread(() -> {
                    songViewModel.setLoading(false);
                    songViewModel.setParseError(errorMessage);
                });
            }

            @Override
            public void onSongInfoAdded(SongInfo songInfo) {
                // 如果需要实时添加歌曲，可以在这里实现
            }
        });
    }

    /**
     * 显示下载对话框 - 使用原生 Material Design 2 效果
     */
    private void showDownloadDialog(final SongInfo songInfo, final int position) {
        // 获取当前选择的歌词类型
        final LrcFileManager.LyricType lyricType = getSelectedLyricType();
        String lyricTypeName = lyricType == LrcFileManager.LyricType.NORMAL ? "普通歌词" : "逐字歌词";

        // 使用 MaterialAlertDialogBuilder 并应用默认主题（使用原生 MD2 效果）
        MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(this);

        builder.setTitle("下载歌词")
                .setMessage("是否下载《" + songInfo.getSongName() + "》的" + lyricTypeName + "？")
                .setPositiveButton("下载", (dialog, which) -> startSingleDownload(songInfo, lyricType, position))
                .setNegativeButton("取消", null)
                .show();

        // 移除了自定义按钮样式的代码，使用原生效果
    }

    /**
     * 检查当前是否为深色主题
     */
    private boolean isDarkTheme() {
        int nightModeFlags = getResources().getConfiguration().uiMode &
                android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * 开始单曲下载
     */
    private void startSingleDownload(SongInfo songInfo, LrcFileManager.LyricType lyricType, int position) {
        songViewModel.setLoading(true);
        // 重置下载状态
        songInfo.setDownloadStatus(SongInfo.DownloadStatus.NONE);
        songViewModel.updateSongStatus(position, SongInfo.DownloadStatus.NONE);

        // 发送开始下载通知
        String lyricTypeName = lyricType == LrcFileManager.LyricType.NORMAL ? "普通歌词" : "逐字歌词";
        sendSingleDownloadNotification("开始下载", "正在下载 " + songInfo.getSongName() + " 的" + lyricTypeName, true);

        // 使用线程池执行下载任务
        new Thread(() -> {
            boolean success;
            String errorMessage = null;

            try {
                lyricDownloader.downloadAndConvertSong(MainActivity.this, songInfo, lyricType);
                success = true;
            } catch (Exception e) {
                errorMessage = e.getMessage();
                success = false;
            }

            final boolean finalSuccess = success;
            final String finalErrorMessage = errorMessage;

            runOnUiThread(() -> {
                songViewModel.setLoading(false);

                if (finalSuccess) {
                    songInfo.setDownloadStatus(SongInfo.DownloadStatus.SUCCESS);
                    songViewModel.updateSongStatus(position, SongInfo.DownloadStatus.SUCCESS);

                    // 发送成功通知
                    String folder = lyricType == LrcFileManager.LyricType.NORMAL ? "LRC" : "SPL";
                    sendSingleDownloadNotification("下载成功",
                            " " + songInfo.getSongName() + " \n保存到: Download/" + folder, true);

                    // 使用Toast显示成功信息
                    Toast.makeText(MainActivity.this, "下载成功: " + songInfo.getSongName(), Toast.LENGTH_LONG).show();
                } else {
                    songInfo.setDownloadStatus(SongInfo.DownloadStatus.FAILED);
                    songViewModel.updateSongStatus(position, SongInfo.DownloadStatus.FAILED);

                    // 发送失败通知
                    sendSingleDownloadNotification("下载失败",
                            " " + songInfo.getSongName() + " \n错误: " + finalErrorMessage, false);

                    // 使用Toast显示失败信息
                    Toast.makeText(MainActivity.this, "下载失败: " + songInfo.getSongName() + " - " + finalErrorMessage, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    /**
     * 开始批量下载
     */
    private void startBatchDownload(LrcFileManager.LyricType lyricType) {
        songViewModel.setLoading(true);
        btnDownloadAll.setEnabled(false);
        showStatus("开始批量下载...");

        // 重置所有歌曲状态为NONE
        List<SongInfo> songs = songViewModel.getSongList().getValue();
        if (songs != null) {
            for (int i = 0; i < songs.size(); i++) {
                songs.get(i).setDownloadStatus(SongInfo.DownloadStatus.NONE);
                songViewModel.updateSongStatus(i, SongInfo.DownloadStatus.NONE);
            }
        }

        // 发送初始通知
        sendBatchDownloadNotification(0, songs != null ? songs.size() : 0, 0, 0);

        // 使用线程池执行批量下载
        new Thread(() -> {
            int successCount = 0;
            int failCount = 0;
            List<SongInfo> currentSongs = songViewModel.getSongList().getValue();

            if (currentSongs != null) {
                for (int i = 0; i < currentSongs.size(); i++) {
                    final int position = i;
                    SongInfo song = currentSongs.get(i);
                    try {
                        lyricDownloader.downloadAndConvertSong(MainActivity.this, song, lyricType);
                        successCount++;
                        song.setDownloadStatus(SongInfo.DownloadStatus.SUCCESS);
                        songViewModel.updateSongStatus(position, SongInfo.DownloadStatus.SUCCESS);
                    } catch (Exception e) {
                        failCount++;
                        // 设置下载失败状态
                        song.setDownloadStatus(SongInfo.DownloadStatus.FAILED);
                        songViewModel.updateSongStatus(position, SongInfo.DownloadStatus.FAILED);
                    }

                    final int current = i + 1;
                    final int finalSuccessCount = successCount;
                    final int finalFailCount = failCount;

                    // 更新UI
                    runOnUiThread(() -> {
                        // 更新通知栏进度
                        sendBatchDownloadNotification(current, currentSongs.size(), finalSuccessCount, finalFailCount);

                        // 更新状态显示
                        showStatus(String.format(Locale.getDefault(),
                                "正在下载第 %d/%d 首 (成功: %d, 失败: %d)",
                                current, currentSongs.size(), finalSuccessCount, finalFailCount));
                    });
                }
            }

            final int finalSuccessCount = successCount;
            final int finalFailCount = failCount;
            final String result = String.format(Locale.getDefault(), "批量下载完成，成功 %d 首，失败 %d 首", successCount, failCount);

            runOnUiThread(() -> {
                songViewModel.setLoading(false);
                btnDownloadAll.setEnabled(true);

                // 发送完成通知
                sendBatchCompleteNotification(finalSuccessCount, finalFailCount, lyricType);

                // 更新状态显示
                showStatus(result);

                // 使用Toast显示完成信息
                String message = result + "\n文件保存在: " + LrcFileManager.getLrcDirectoryPath(lyricType);
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    /**
     * 获取用户选择的歌词类型
     */
    private LrcFileManager.LyricType getSelectedLyricType() {
        int selectedId = rgLyricType.getCheckedRadioButtonId();

        if (selectedId == R.id.rb_word_by_word) {
            return LrcFileManager.LyricType.WORD_BY_WORD;
        } else {
            // 默认为普通歌词
            return LrcFileManager.LyricType.NORMAL;
        }
    }

    /**
     * 显示状态信息
     */
    private void showStatus(String message) {
        if (tvStatus != null) {
            tvStatus.setText(message);
            tvStatus.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 隐藏状态信息
     */
    private void hideStatus() {
        if (tvStatus != null) {
            tvStatus.setVisibility(View.GONE);
        }
    }

    private void checkPermissions() {
        String[] permissions = PermissionManager.getRequiredPermissions(this);
        if (permissions.length > 0) {
            PermissionManager.checkAndRequestPermissions(this, permissions);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "部分权限被拒绝，可能影响功能", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * 发送单曲下载通知
     */
    private void sendSingleDownloadNotification(String title, String content, boolean isSuccess) {
        // 创建点击通知后打开应用的Intent
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(isSuccess ? android.R.drawable.ic_dialog_info : android.R.drawable.ic_dialog_alert)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        notificationManager.notify(NOTIFICATION_ID_SINGLE, builder.build());
    }

    /**
     * 发送批量下载进度通知
     */
    private void sendBatchDownloadNotification(int current, int total, int success, int failed) {
        String progressText = String.format(Locale.getDefault(), "正在下载: %d/%d (成功: %d, 失败: %d)", current, total, success, failed);

        // 创建进度通知
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("批量下载歌词")
                .setContentText(progressText)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setProgress(total, current, false)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        notificationManager.notify(NOTIFICATION_ID_BATCH, builder.build());
    }

    /**
     * 发送批量下载完成通知
     */
    private void sendBatchCompleteNotification(int success, int failed, LrcFileManager.LyricType lyricType) {
        String folder = lyricType == LrcFileManager.LyricType.NORMAL ? "LRC" : "SPL";
        String content = String.format(Locale.getDefault(), "下载完成! 成功: %d, 失败: %d\n保存到: Download/%s", success, failed, folder);

        // 创建点击通知后打开应用的Intent
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("批量下载完成")
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setProgress(0, 0, false) // 移除进度条
                .setOngoing(false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        notificationManager.notify(NOTIFICATION_ID_BATCH, builder.build());
    }
}