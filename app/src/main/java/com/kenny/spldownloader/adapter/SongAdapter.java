// SongAdapter.java (修正版本)
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

    private static final DiffUtil.ItemCallback<SongInfo> DIFF_CALLBACK = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull SongInfo oldItem, @NonNull SongInfo newItem) {
            return Objects.equals(oldItem.getMid(), newItem.getMid());
        }

        @Override
        public boolean areContentsTheSame(@NonNull SongInfo oldItem, @NonNull SongInfo newItem) {
            return Objects.equals(oldItem.getSongName(), newItem.getSongName()) &&
                    Objects.equals(oldItem.getSinger(), newItem.getSinger()) &&
                    oldItem.getDownloadStatus().equals(newItem.getDownloadStatus());
        }
    };

    public SongAdapter(Context context) {
        super(DIFF_CALLBACK);
        this.context = context;
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
        holder.bind(song);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(song, position);
            }
        });
    }

    public void setSongList(List<SongInfo> songList) {
        submitList(songList != null ? new ArrayList<>(songList) : null);
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

        public void bind(SongInfo song) {
            tvSongName.setText(song.getSongName() != null ? song.getSongName() : "未知歌曲");
            tvSinger.setText(song.getSinger() != null ? song.getSinger() : "未知歌手");
            updateStatus(song.getDownloadStatus());
        }

        public void updateStatus(SongInfo.DownloadStatus status) {
            if (status != null) {
                switch (status) {
                    case SUCCESS:
                        chipStatus.setText("完成");
                        chipStatus.setChipBackgroundColorResource(R.color.success);
                        chipStatus.setChipIconResource(R.drawable.ic_check);
                        break;
                    case FAILED:
                        chipStatus.setText("失败");
                        chipStatus.setChipBackgroundColorResource(R.color.error);
                        chipStatus.setChipIconResource(R.drawable.ic_error);
                        break;
                    case NONE:
                    default:
                        chipStatus.setText("待下载");
                        chipStatus.setChipBackgroundColorResource(R.color.secondary);
                        chipStatus.setChipIconResource(R.drawable.ic_pending);
                        break;
                }
            }
        }
    }

    public interface OnItemClickListener {
        void onItemClick(SongInfo song, int position);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }
}