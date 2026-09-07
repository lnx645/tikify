package com.tiktoksoundalert.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.android.material.tabs.TabLayout;
import com.tiktoksoundalert.R;
import com.tiktoksoundalert.statistics.TopViewersReflector;
import com.tiktoksoundalert.statistics.UserStats;

import java.util.ArrayList;
import java.util.List;

public class ViewersFragment extends Fragment {

    private static final long REFRESH_MS = 2000;

    private TabLayout tabLayout;
    private RecyclerView recycler;
    private TextView tvEmpty;
    private TopAdapter adapter;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAdded()) refresh();
            refreshHandler.postDelayed(this, REFRESH_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_viewers, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tabLayout = view.findViewById(R.id.tab_layout);
        recycler = view.findViewById(R.id.recycler_top);
        tvEmpty = view.findViewById(R.id.tv_empty);

        adapter = new TopAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                refresh();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
        refreshHandler.postDelayed(refreshRunnable, REFRESH_MS);
    }

    @Override
    public void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    private void refresh() {
        if (!isAdded()) return;
        int tab = tabLayout.getSelectedTabPosition();
        TopViewersReflector r = TopViewersReflector.getInstance();
        List<UserStats> list;
        switch (tab) {
            case 1:
                list = r.topChat(50);
                break;
            case 2:
                list = r.topLikes(50);
                break;
            case 3:
                list = r.topViewers(50);
                break;
            default:
                list = r.topGifters(50);
                break;
        }
        adapter.setTab(tab);
        adapter.setList(list);
        tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    static class TopAdapter extends RecyclerView.Adapter<TopAdapter.VH> {
        private final List<UserStats> items = new ArrayList<>();
        private int tab = 0;

        void setTab(int tab) {
            this.tab = tab;
        }

        void setList(List<UserStats> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_top_user, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            UserStats u = items.get(position);
            holder.tvRank.setText(String.valueOf(position + 1));
            holder.tvName.setText(u.getName());
            holder.tvValue.setText(ViewersFragment.valueText(tab, u));

            String avatar = u.getAvatarUrl();
            if (avatar == null || avatar.isEmpty()) {
                Glide.with(holder.itemView).clear(holder.ivAvatar);
                holder.ivAvatar.setImageDrawable(null);
            } else {
                Glide.with(holder.itemView.getContext())
                        .load(avatar)
                        .transform(new CircleCrop())
                        .placeholder(R.drawable.bg_avatar_circle)
                        .into(holder.ivAvatar);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tvRank;
            final TextView tvName;
            final TextView tvValue;
            final ImageView ivAvatar;

            VH(@NonNull View itemView) {
                super(itemView);
                tvRank = itemView.findViewById(R.id.tv_rank);
                tvName = itemView.findViewById(R.id.tv_name);
                tvValue = itemView.findViewById(R.id.tv_value);
                ivAvatar = itemView.findViewById(R.id.iv_avatar);
            }
        }
    }

    static String valueText(int tab, UserStats u) {
        switch (tab) {
            case 1:
                return u.getCommentCount() + " comments";
            case 2:
                return u.getLikeCount() + " likes";
            case 3:
                return "Active " + u.getActiveMinutes() + " min";
            default:
                return u.getTotalDiamonds() + " diamonds (" + u.getTotalGiftCount() + " gifts)";
        }
    }
}