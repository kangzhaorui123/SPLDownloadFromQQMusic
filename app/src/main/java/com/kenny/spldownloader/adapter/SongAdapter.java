// SongAdapter.java
package com.kenny.spldownloader.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
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

public class SongAdapter extends ListAdapter<SongInfo, RecyclerView.ViewHolder> {

    private final Context context;
    private OnItemClickListener listener;
    private OnLoadMoreListener loadMoreListener;

    // 加载更多相关状态
    private boolean isLoading = false;
    private boolean hasMoreData = true;
    private boolean loadFailed = false;

    private static final int TYPE_ITEM = 0;
    private static final int TYPE_LOAD_MORE = 1;

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

    @Override
    public int getItemViewType(int position) {
        if (position == getItemCount() - 1 && hasMoreData) {
            return TYPE_LOAD_MORE;
        }
        return TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_LOAD_MORE) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_load_more, parent, false);
            return new LoadMoreViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_song, parent, false);
            return new ViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ViewHolder) {
            SongInfo song = getItem(position);
            ((ViewHolder) holder).bind(song);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(song, position);
                }
            });
        } else if (holder instanceof LoadMoreViewHolder) {
            ((LoadMoreViewHolder) holder).bind(isLoading, loadFailed);

            holder.itemView.setOnClickListener(v -> {
                if (loadFailed && loadMoreListener != null) {
                    loadMoreListener.onLoadMore();
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        int baseCount = super.getItemCount();
        return hasMoreData ? baseCount + 1 : baseCount;
    }

    public void setSongList(List<SongInfo> songList) {
        submitList(songList != null ? new ArrayList<>(songList) : null);
    }

    // 加载更多相关方法
    public void setLoading(boolean loading) {
        if (isLoading != loading) {
            isLoading = loading;
            if (hasMoreData) {
                notifyItemChanged(getItemCount() - 1);
            }
        }
    }

    public void setHasMoreData(boolean hasMore) {
        if (hasMoreData != hasMore) {
            int oldItemCount = getItemCount();
            hasMoreData = hasMore;
            if (hasMore) {
                notifyItemInserted(oldItemCount);
            } else {
                notifyItemRemoved(oldItemCount);
            }
        }
    }

    public void setLoadFailed(boolean failed) {
        if (loadFailed != failed) {
            loadFailed = failed;
            if (hasMoreData) {
                notifyItemChanged(getItemCount() - 1);
            }
        }
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

    public static class LoadMoreViewHolder extends RecyclerView.ViewHolder {
        ProgressBar progressBar;
        TextView tvStatus;

        public LoadMoreViewHolder(@NonNull View itemView) {
            super(itemView);
            progressBar = itemView.findViewById(R.id.progress_bar);
            tvStatus = itemView.findViewById(R.id.tv_status);
        }

        public void bind(boolean isLoading, boolean loadFailed) {
            if (isLoading) {
                progressBar.setVisibility(View.VISIBLE);
                tvStatus.setText(R.string.loading);
            } else if (loadFailed) {
                progressBar.setVisibility(View.GONE);
                tvStatus.setText(R.string.load_failed);
                tvStatus.setTextColor(itemView.getContext().getColor(R.color.error));
            } else {
                progressBar.setVisibility(View.GONE);
                tvStatus.setText(R.string.load_more);
                tvStatus.setTextColor(itemView.getContext().getColor(R.color.primary));
            }
        }
    }

    public interface OnItemClickListener {
        void onItemClick(SongInfo song, int position);
    }

    public interface OnLoadMoreListener {
        void onLoadMore();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setOnLoadMoreListener(OnLoadMoreListener listener) {
        this.loadMoreListener = listener;
    }
}