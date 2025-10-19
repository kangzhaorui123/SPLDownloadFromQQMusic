package com.kenny.spldownloader.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.kenny.spldownloader.R;
import com.kenny.spldownloader.model.SongInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SongAdapter extends ListAdapter<SongInfo, SongAdapter.ViewHolder> {

    private final Context context;
    private OnItemClickListener listener;

    // 使用 DiffUtil 来优化列表更新
    private static final DiffUtil.ItemCallback<SongInfo> DIFF_CALLBACK = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull SongInfo oldItem, @NonNull SongInfo newItem) {
            // 如果 MID 相同，则认为是同一个项目
            return Objects.equals(oldItem.getMid(), newItem.getMid());
        }

        @Override
        public boolean areContentsTheSame(@NonNull SongInfo oldItem, @NonNull SongInfo newItem) {
            // 检查内容是否相同（歌曲名、歌手、下载状态）
            return Objects.equals(oldItem.getSongName(), newItem.getSongName()) &&
                    Objects.equals(oldItem.getSinger(), newItem.getSinger()) &&
                    Objects.equals(oldItem.getDownloadStatus(), newItem.getDownloadStatus());
        }
    };

    public SongAdapter(Context context) {
        super(DIFF_CALLBACK);
        this.context = context;
    }

    public SongAdapter(Context context, List<SongInfo> songList) {
        super(DIFF_CALLBACK);
        this.context = context;
        if (songList != null) {
            submitList(new ArrayList<>(songList));
        }
    }

    /**
     * 设置新的歌曲列表 - 使用 DiffUtil 进行优化更新
     */
    public void setSongList(List<SongInfo> songList) {
        submitList(songList != null ? new ArrayList<>(songList) : null);
    }

    /**
     * 更新单个歌曲的状态
     */
    public void updateSong(int position, SongInfo song) {
        List<SongInfo> currentList = getCurrentList();
        if (position >= 0 && position < currentList.size()) {
            List<SongInfo> newList = new ArrayList<>(currentList);
            newList.set(position, song);
            submitList(newList);
        }
    }

    /**
     * 添加歌曲到列表
     */
    public void addSong(SongInfo song) {
        List<SongInfo> currentList = getCurrentList();
        List<SongInfo> newList = new ArrayList<>(currentList);
        newList.add(song);
        submitList(newList);
    }

    /**
     * 清空歌曲列表
     */
    public void clearSongs() {
        submitList(new ArrayList<>());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_song, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SongInfo song = getItem(position);

        holder.tvSongName.setText(song.getSongName() != null ? song.getSongName() : "");
        holder.tvSinger.setText(song.getSinger() != null ? song.getSinger() : "");

        // 根据下载状态设置芯片
        if (song.getDownloadStatus() != null) {
            switch (song.getDownloadStatus()) {
                case SUCCESS:
                    holder.chipStatus.setText("完成");
                    holder.chipStatus.setChipBackgroundColorResource(R.color.success);
                    holder.chipStatus.setChipIconResource(R.drawable.ic_check);
                    break;
                case FAILED:
                    holder.chipStatus.setText("失败");
                    holder.chipStatus.setChipBackgroundColorResource(R.color.error);
                    holder.chipStatus.setChipIconResource(R.drawable.ic_error);
                    break;
                case NONE:
                default:
                    holder.chipStatus.setText("待下载");
                    holder.chipStatus.setChipBackgroundColorResource(R.color.secondary);
                    holder.chipStatus.setChipIconResource(R.drawable.ic_pending);
                    break;
            }
        } else {
            // 处理 downloadStatus 为 null 的情况
            holder.chipStatus.setText("未知");
            holder.chipStatus.setChipBackgroundColorResource(R.color.secondary);
            holder.chipStatus.setChipIconResource(R.drawable.ic_pending);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null && position >= 0 && position < getItemCount()) {
                listener.onItemClick(song, position);
            }
        });
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvSongName, tvSinger;
        Chip chipStatus;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSongName = itemView.findViewById(R.id.tv_song_name);
            tvSinger = itemView.findViewById(R.id.tv_singer);
            chipStatus = itemView.findViewById(R.id.chip_status);
        }
    }

    // 点击监听器接口
    public interface OnItemClickListener {
        void onItemClick(SongInfo song, int position);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }
}