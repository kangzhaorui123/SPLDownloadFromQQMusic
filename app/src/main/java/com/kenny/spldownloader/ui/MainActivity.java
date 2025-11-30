package com.kenny.spldownloader.ui;

import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kenny.spldownloader.R;
import com.kenny.spldownloader.adapter.SongAdapter;
import com.kenny.spldownloader.config.AppConfig;
import com.kenny.spldownloader.manager.FileManager;
import com.kenny.spldownloader.manager.PermissionManager;
import com.kenny.spldownloader.manager.TaskExecutor;
import com.kenny.spldownloader.model.SongInfo;
import com.kenny.spldownloader.service.LyricService;
import com.kenny.spldownloader.service.UrlParser;
import com.kenny.spldownloader.viewmodel.SongViewModel;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // UI组件
    private EditText etUrl;
    private RadioGroup rgLyricType;
    private View btnParse, btnDownloadAll;
    private View progressBar;
    private TextView tvStatus;
    private RecyclerView recyclerView;
    private SongAdapter adapter;

    // 业务组件
    private SongViewModel songViewModel;
    private LyricService lyricService;
    private UrlParser urlParser;
    private TaskExecutor taskExecutor;

    // 分页相关常量
    private static final int PAGE_SIZE = 20;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initComponents();
        initViews();
        setupPagination();
        setupObservers();
        checkPermissions();
        initNotificationChannel();
    }

    private void initComponents() {
        songViewModel = new ViewModelProvider(this).get(SongViewModel.class);
        lyricService = new LyricService();
        urlParser = new UrlParser();
        taskExecutor = TaskExecutor.getInstance();
    }

    private void initViews() {
        // 初始化UI组件
        etUrl = findViewById(R.id.et_url);
        rgLyricType = findViewById(R.id.rg_lyric_type);
        btnParse = findViewById(R.id.btn_parse);
        btnDownloadAll = findViewById(R.id.btn_download_all);
        progressBar = findViewById(R.id.progress_bar);
        tvStatus = findViewById(R.id.tv_status);
        recyclerView = findViewById(R.id.recycler_view);

        // 设置RecyclerView
        setupRecyclerView();

        // 设置按钮点击监听
        setupButtonListeners();
    }

    private void setupRecyclerView() {
        adapter = new SongAdapter(this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        // 设置点击监听器
        adapter.setOnItemClickListener(this::showDownloadDialog);
    }

    private void setupPagination() {
        // 设置 RecyclerView 滚动监听
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null) {
                    int totalItemCount = layoutManager.getItemCount();
                    int lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition();

                    // 检查是否滚动到底部并且是搜索模式
                    Boolean loadMoreLoading = songViewModel.getLoadMoreLoading().getValue();
                    Boolean hasMoreData = songViewModel.getHasMoreData().getValue();

                    if (loadMoreLoading != null && hasMoreData != null &&
                            !loadMoreLoading &&
                            hasMoreData &&
                            songViewModel.isSearchMode() &&
                            lastVisibleItemPosition >= totalItemCount - 3) {

                        loadMoreSongs();
                    }
                }
            }
        });

        // 设置适配器的加载更多监听器
        adapter.setOnLoadMoreListener(() -> {
            Boolean loadMoreLoading = songViewModel.getLoadMoreLoading().getValue();
            if (songViewModel.isSearchMode() && loadMoreLoading != null && !loadMoreLoading) {
                loadMoreSongs();
            }
        });
    }

    private void setupButtonListeners() {
        // 解析按钮
        btnParse.setOnClickListener(v -> parseInput());

        // 下载全部按钮
        btnDownloadAll.setOnClickListener(v -> {
            List<SongInfo> songs = songViewModel.getSongList().getValue();
            if (songs == null || songs.isEmpty()) {
                showToast("没有可下载的歌曲");
                return;
            }

            startBatchDownload();
        });
    }

    private void setupObservers() {
        // 观察歌曲列表
        songViewModel.getSongList().observe(this, songs -> {
            adapter.setSongList(songs);
            btnDownloadAll.setEnabled(songs != null && !songs.isEmpty());

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
        });

        // 观察加载更多状态
        songViewModel.getLoadMoreLoading().observe(this, loading -> adapter.setLoading(loading));

        // 观察是否有更多数据
        songViewModel.getHasMoreData().observe(this, hasMore -> adapter.setHasMoreData(hasMore));

        // 观察错误信息
        songViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                showErrorDialog("错误", error);
                songViewModel.clearError();
            }
        });

        // 观察进度信息
        songViewModel.getProgressInfo().observe(this, progress -> {
            if (progress != null) {
                showStatus(progress.message());
            }
        });
    }

    private void parseInput() {
        String input = etUrl.getText().toString().trim();
        if (input.isEmpty()) {
            showToast("请输入QQ音乐链接或歌曲名称");
            return;
        }

        if (!PermissionManager.hasRequiredPermissions(this)) {
            String[] permissions = PermissionManager.getRequiredPermissions(this);
            PermissionManager.requestPermissions(this, permissions);
            showStatus("请先授予存储权限");
            return;
        }

        songViewModel.setLoading(true);

        // 自动判断输入类型
        if (isUrl(input)) {
            showStatus("正在解析链接...");
            songViewModel.setSearchMode(false);
            parseUrl(input);
        } else {
            showStatus("正在搜索歌曲...");
            songViewModel.setSearchMode(true);
            searchSongs(input, 1);
        }
    }

    private boolean isUrl(String input) {
        return input.startsWith("http://") || input.startsWith("https://") ||
                input.contains("qq.com") || input.contains("y.qq.com");
    }

    private void parseUrl(String url) {
        // 使用线程执行网络请求，避免在主线程执行
        new Thread(() -> {
            try {
                Log.d(TAG, "开始解析URL: " + url);
                List<SongInfo> songs = urlParser.parseUrl(url);

                runOnUiThread(() -> {
                    songViewModel.setSongList(songs);
                    songViewModel.setLoading(false);
                    showToast("解析成功，找到 " + songs.size() + " 首歌曲");
                    Log.d(TAG, "URL解析成功，歌曲数量: " + songs.size());
                });

            } catch (Exception e) {
                Log.e(TAG, "解析URL失败: " + e.getMessage(), e);

                runOnUiThread(() -> {
                    songViewModel.setLoading(false);
                    String errorMessage = "解析失败: " + e.getMessage();
                    showErrorDialog("解析失败", errorMessage);
                    Log.e(TAG, errorMessage);
                });
            }
        }).start();
    }

    private void searchSongs(String keyword, int page) {
        songViewModel.setCurrentSearchKeyword(keyword);

        if (page == 1) {
            songViewModel.setLoading(true);
            songViewModel.resetPagination();
        } else {
            songViewModel.setLoadMoreLoading(true);
            adapter.setLoading(true);
            adapter.setLoadFailed(false);
        }

        new Thread(() -> {
            try {
                Log.d(TAG, "开始搜索: " + keyword + ", 页码: " + page);
                List<SongInfo> songs = urlParser.searchByKeyword(keyword, page, PAGE_SIZE);

                runOnUiThread(() -> {
                    if (page == 1) {
                        // 第一页，替换数据
                        songViewModel.setSongList(songs);
                        songViewModel.setLoading(false);
                        showToast("搜索成功，找到 " + songs.size() + " 首歌曲");
                    } else {
                        // 加载更多，追加数据
                        songViewModel.appendSongList(songs);
                        songViewModel.setLoadMoreLoading(false);
                        adapter.setLoading(false);

                        if (songs.size() < PAGE_SIZE) {
                            // 没有更多数据了
                            songViewModel.setHasMoreData(false);
                            adapter.setHasMoreData(false);
                            showToast("已加载所有歌曲");
                        } else {
                            showToast("已加载第 " + page + " 页歌曲");
                        }
                    }

                    // 更新分页状态
                    if (!songs.isEmpty()) {
                        songViewModel.incrementPage();
                        // 只有在搜索模式下并且返回了完整一页数据时才显示加载更多
                        if (songs.size() == PAGE_SIZE) {
                            songViewModel.setHasMoreData(true);
                        }
                    }

                    Log.d(TAG, "搜索成功，当前页码: " + songViewModel.getCurrentPage());
                });

            } catch (Exception e) {
                Log.e(TAG, "搜索失败: " + e.getMessage(), e);

                runOnUiThread(() -> {
                    if (page == 1) {
                        songViewModel.setLoading(false);
                        String errorMessage = "搜索失败: " + e.getMessage();
                        showErrorDialog("搜索失败", errorMessage);
                    } else {
                        songViewModel.setLoadMoreLoading(false);
                        adapter.setLoading(false);
                        adapter.setLoadFailed(true);
                        showToast("加载更多失败: " + e.getMessage());
                    }
                    Log.e(TAG, "搜索失败: " + e.getMessage());
                });
            }
        }).start();
    }

    private void loadMoreSongs() {
        String keyword = songViewModel.getCurrentSearchKeyword();
        int nextPage = songViewModel.getCurrentPage();

        if (keyword != null && !keyword.isEmpty()) {
            searchSongs(keyword, nextPage);
        }
    }

    private void showDownloadDialog(SongInfo song, int position) {
        FileManager.LyricType lyricType = getSelectedLyricType();
        String lyricTypeName = lyricType == FileManager.LyricType.NORMAL ? "普通歌词" : "逐字歌词";

        new AlertDialog.Builder(this)
                .setTitle("下载歌词")
                .setMessage("是否下载《" + song.getSongName() + "》的" + lyricTypeName + "？")
                .setPositiveButton("下载", (dialog, which) -> startSingleDownload(song, lyricType, position))
                .setNegativeButton("取消", null)
                .show();
    }

    private void startSingleDownload(SongInfo song, FileManager.LyricType lyricType, int position) {
        songViewModel.setLoading(true);
        showStatus("正在下载《" + song.getSongName() + "》的歌词...");

        CompletableFuture<String> future = taskExecutor.submitWithRetry(
                lyricService.createDownloadTask(song, convertLyricType(lyricType)),
                AppConfig.MAX_RETRY_COUNT
        );

        future.whenComplete((lyricContent, throwable) -> runOnUiThread(() -> {
            songViewModel.setLoading(false);

            if (throwable != null) {
                // 下载失败
                songViewModel.updateSongStatus(position, SongInfo.DownloadStatus.FAILED);
                showErrorDialog("下载失败", throwable.getMessage());
                sendNotification("下载失败", song.getSongName() + " - " + throwable.getMessage(), false);
            } else {
                // 下载成功，保存文件
                boolean success = FileManager.saveLyricFile(
                        MainActivity.this,
                        song.getFileName(),
                        lyricContent,
                        lyricType
                );

                if (success) {
                    songViewModel.updateSongStatus(position, SongInfo.DownloadStatus.SUCCESS);
                    showToast("《" + song.getSongName() + "》下载成功");
                    String folder = lyricType == FileManager.LyricType.NORMAL ? "LRC" : "SPL";
                    sendNotification("下载成功", song.getSongName() + " - 保存到: Download/" + folder, true);
                } else {
                    songViewModel.updateSongStatus(position, SongInfo.DownloadStatus.FAILED);
                    showErrorDialog("保存失败", "无法保存歌词文件");
                }
            }
        }));
    }

    private void startBatchDownload() {
        List<SongInfo> songs = songViewModel.getSongList().getValue();
        if (songs == null || songs.isEmpty()) return;

        songViewModel.setLoading(true);
        FileManager.LyricType lyricType = getSelectedLyricType();

        // 重置所有歌曲状态
        for (int i = 0; i < songs.size(); i++) {
            songViewModel.updateSongStatus(i, SongInfo.DownloadStatus.NONE);
        }

        showStatus("开始批量下载 " + songs.size() + " 首歌曲...");

        // 使用 AtomicInteger 作为计数器，线程安全且 effectively final
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // 使用线程执行批量下载
        new Thread(() -> {
            for (int i = 0; i < songs.size(); i++) {
                SongInfo song = songs.get(i);
                final int position = i;

                try {
                    // 下载歌词
                    String lyricContent = taskExecutor.submitWithRetry(
                            lyricService.createDownloadTask(song, convertLyricType(lyricType)),
                            AppConfig.MAX_RETRY_COUNT
                    ).get(); // 等待完成

                    // 保存文件
                    boolean success = FileManager.saveLyricFile(
                            MainActivity.this,
                            song.getFileName(),
                            lyricContent,
                            lyricType
                    );

                    if (success) {
                        successCount.incrementAndGet();
                        runOnUiThread(() -> {
                            songViewModel.updateSongStatus(position, SongInfo.DownloadStatus.SUCCESS);
                            showStatus("批量下载进度: " + (position + 1) + "/" + songs.size() +
                                    " (成功: " + successCount.get() + ", 失败: " + failCount.get() + ")");
                        });
                    } else {
                        failCount.incrementAndGet();
                        runOnUiThread(() -> {
                            songViewModel.updateSongStatus(position, SongInfo.DownloadStatus.FAILED);
                            showStatus("批量下载进度: " + (position + 1) + "/" + songs.size() +
                                    " (成功: " + successCount.get() + ", 失败: " + failCount.get() + ")");
                        });
                    }

                } catch (Exception e) {
                    failCount.incrementAndGet();
                    runOnUiThread(() -> {
                        songViewModel.updateSongStatus(position, SongInfo.DownloadStatus.FAILED);
                        showStatus("批量下载进度: " + (position + 1) + "/" + songs.size() +
                                " (成功: " + successCount.get() + ", 失败: " + failCount.get() + ")");
                    });
                    Log.e(TAG, "批量下载失败 - 歌曲: " + song.getSongName(), e);
                }

                // 短暂延迟，避免请求过于频繁
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            final int finalSuccessCount = successCount.get();
            final int finalFailCount = failCount.get();

            runOnUiThread(() -> {
                songViewModel.setLoading(false);
                showToast("批量下载完成: 成功 " + finalSuccessCount + " 首, 失败 " + finalFailCount + " 首");
            });
        }).start();
    }

    private FileManager.LyricType getSelectedLyricType() {
        int selectedId = rgLyricType.getCheckedRadioButtonId();
        if (selectedId == R.id.rb_word_by_word) {
            return FileManager.LyricType.WORD_BY_WORD;
        }
        return FileManager.LyricType.NORMAL;
    }

    private LyricService.LyricType convertLyricType(FileManager.LyricType fileLyricType) {
        return fileLyricType == FileManager.LyricType.NORMAL ?
                LyricService.LyricType.NORMAL : LyricService.LyricType.WORD_BY_WORD;
    }

    private void checkPermissions() {
        String[] permissions = PermissionManager.getRequiredPermissions(this);
        if (permissions.length > 0) {
            PermissionManager.requestPermissions(this, permissions);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == AppConfig.PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                showToast("权限已授予");
            } else {
                showToast("部分权限被拒绝，可能影响功能");
            }
        }
    }

    private void initNotificationChannel() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        String channelName = "歌词下载通知";
        String channelDescription = "显示歌词下载进度和结果";

        NotificationChannel channel = new NotificationChannel(
                AppConfig.NOTIFICATION_CHANNEL_ID,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription(channelDescription);
        notificationManager.createNotificationChannel(channel);
    }

    private void sendNotification(String title, String content, boolean isSuccess) {
        // 简化实现 - 使用Toast
        showToast(title + ": " + content);
    }

    // 工具方法
    private void showStatus(String message) {
        if (tvStatus != null) {
            tvStatus.setText(message);
            tvStatus.setVisibility(View.VISIBLE);
        }
    }

    private void hideStatus() {
        if (tvStatus != null) {
            tvStatus.setVisibility(View.GONE);
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showErrorDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (taskExecutor != null) {
            taskExecutor.shutdown();
        }
    }
}