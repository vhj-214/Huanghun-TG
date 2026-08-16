package com.Huanghun.outfit;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 黄昏本地个性装扮中心。
 * 选择器和预览完全离线工作；所有样式均只保存在当前设备。
 */
public class HuanghunOutfitCenterActivity extends BaseFragment {

    private static final List<String> CATEGORIES = Arrays.asList(
            HuanghunOutfitConfig.CATEGORY_BUBBLE,
            HuanghunOutfitConfig.CATEGORY_CALL,
            HuanghunOutfitConfig.CATEGORY_JOIN
    );

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("黄昏个性装扮");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(16), dp(14), dp(24));
        scrollView.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView headline = titleView(context, "黄昏 · 本地潮流装扮", 22, true);
        content.addView(headline, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));
        TextView subhead = titleView(context, "三大分类 · 150 套原创动态主题 · 仅在本机显示", 14, false);
        subhead.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        content.addView(subhead, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));

        LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (int index = 0; index < CATEGORIES.size(); index += 2) {
            LinearLayout line = new LinearLayout(context);
            line.setGravity(Gravity.CENTER);
            line.setOrientation(LinearLayout.HORIZONTAL);
            int count = Math.min(2, CATEGORIES.size() - index);
            for (int column = 0; column < count; column++) {
                String category = CATEGORIES.get(index + column);
                CategoryCard card = new CategoryCard(context, category);
                card.setOnClickListener(v -> presentFragment(new HuanghunOutfitCategoryActivity(category)));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(count == 1 ? ViewGroup.LayoutParams.MATCH_PARENT : 0, dp(count == 1 ? 164 : 182), count == 1 ? 0f : 1f);
                params.setMargins(column == 0 ? 0 : dp(6), dp(7), column == 0 && count == 2 ? dp(6) : 0, dp(7));
                line.addView(card, params);
            }
            grid.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(count == 1 ? 178 : 196)));
        }
        content.addView(grid);

        TextView note = titleView(context, "每个分类均提供 50 套不同造型的原创主题。预览确认后，效果会真实作用于聊天气泡、来电或群聊入场提示。", 14, false);
        note.setLineSpacing(dp(3), 1f);
        note.setPadding(dp(6), dp(12), dp(6), 0);
        note.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        content.addView(note, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(82)));

        root.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        return fragmentView = root;
    }

    private static TextView titleView(Context context, String value, int size, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        if (bold) {
            view.setTypeface(AndroidUtilities.bold());
        }
        return view;
    }

    private static final class CategoryCard extends FrameLayout {
        CategoryCard(Context context, String category) {
            super(context);
            int cardBackground = Theme.getColor(Theme.key_windowBackgroundWhite);
            setBackground(Theme.createRoundRectDrawable(dp(18), cardBackground));
            setClickable(true);
            setFocusable(true);

            HuanghunOutfitConfig.OutfitItem item = HuanghunOutfitConfig.getItems(category).get(0);
            OutfitVisualView preview = new OutfitVisualView(context, item);
            addView(preview, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, dp(112), Gravity.TOP));

            TextView title = titleView(context, HuanghunOutfitConfig.categoryTitle(category), 16, true);
            title.setPadding(dp(14), 0, dp(14), 0);
            addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, dp(28), Gravity.BOTTOM, 0, 0, 0, dp(25)));
            TextView subtitle = titleView(context, "50 套 · " + HuanghunOutfitConfig.categoryDescription(category), 11, false);
            subtitle.setSingleLine(true);
            subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
            subtitle.setPadding(dp(14), 0, dp(14), 0);
            subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            addView(subtitle, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, dp(24), Gravity.BOTTOM, 0, 0, 0, dp(4)));
        }
    }

    public static final class HuanghunOutfitCategoryActivity extends BaseFragment {
        private final String category;
        private RecyclerListView listView;
        private OutfitAdapter adapter;

        HuanghunOutfitCategoryActivity(String category) {
            this.category = category;
        }

        @Override
        public View createView(Context context) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setTitle(HuanghunOutfitConfig.categoryTitle(category));
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    }
                }
            });
            FrameLayout root = new FrameLayout(context);
            root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
            listView = new RecyclerListView(context);
            listView.setLayoutManager(new GridLayoutManager(context, 2));
            listView.setClipToPadding(false);
            listView.setPadding(dp(12), dp(12), dp(12), dp(20));
            adapter = new OutfitAdapter(context, category, this);
            listView.setAdapter(adapter);
            root.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            return fragmentView = root;
        }

        @Override
        public void onResume() {
            super.onResume();
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }
    }

    public static final class HuanghunOutfitPreviewActivity extends BaseFragment {
        private final HuanghunOutfitConfig.OutfitItem item;
        private OutfitVisualView preview;
        private ToneGenerator toneGenerator;
        private TextView status;

        HuanghunOutfitPreviewActivity(HuanghunOutfitConfig.OutfitItem item) {
            this.item = item;
        }

        @Override
        public View createView(Context context) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setTitle("预览 · " + item.name);
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    }
                }
            });
            FrameLayout root = new FrameLayout(context);
            root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(16), dp(18), dp(16), dp(16));

            preview = new OutfitVisualView(context, item);
            preview.setBackground(Theme.createRoundRectDrawable(dp(22), Theme.getColor(Theme.key_windowBackgroundWhite)));
            content.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(330)));
            TextView description = titleView(context, HuanghunOutfitConfig.categoryDescription(item.category), 15, false);
            description.setPadding(dp(4), dp(16), dp(4), dp(4));
            description.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            content.addView(description, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

            if (HuanghunOutfitConfig.CATEGORY_CALL.equals(item.category)) {
                TextView audition = button(context, "试听本地铃声", item.secondary);
                audition.setOnClickListener(v -> playTone());
                LinearLayout.LayoutParams auditionParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
                auditionParams.setMargins(0, dp(8), 0, dp(8));
                content.addView(audition, auditionParams);
            }
            TextView apply = button(context, "使用这套装扮", item.primary);
            apply.setOnClickListener(v -> {
                HuanghunOutfitConfig.saveSelected(context, item.category, item.id);
                refreshStatus();
            });
            content.addView(apply, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
            TextView reset = button(context, "恢复本地默认效果", Color.rgb(118, 126, 142));
            reset.setOnClickListener(v -> {
                HuanghunOutfitConfig.clearSelected(context, item.category);
                refreshStatus();
            });
            LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
            resetParams.setMargins(0, dp(10), 0, 0);
            content.addView(reset, resetParams);
            status = titleView(context, "", 13, false);
            status.setGravity(Gravity.CENTER);
            status.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            content.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
            refreshStatus();

            root.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            return fragmentView = root;
        }

        private void refreshStatus() {
            if (status == null) return;
            status.setText(HuanghunOutfitConfig.isSelected(getContext(), item) ? "已保存到本机：当前正在使用" : "当前为预览状态，确认后仅在本机生效");
        }

        private void playTone() {
            try {
                if (toneGenerator == null) {
                    toneGenerator = new ToneGenerator(AudioManager.STREAM_RING, 82);
                }
                int[] tones = new int[]{ToneGenerator.TONE_CDMA_ABBR_ALERT, ToneGenerator.TONE_PROP_BEEP2, ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, ToneGenerator.TONE_SUP_RINGTONE, ToneGenerator.TONE_CDMA_CALL_SIGNAL_ISDN_INTERGROUP};
                toneGenerator.startTone(tones[item.variant % tones.length], 1800);
            } catch (Throwable ignored) {
                if (status != null) status.setText("系统当前无法试听铃声，请检查媒体音量。");
            }
        }

        @Override
        public void onFragmentDestroy() {
            if (toneGenerator != null) {
                toneGenerator.release();
                toneGenerator = null;
            }
            super.onFragmentDestroy();
        }
    }

    private static final class OutfitAdapter extends RecyclerListView.SelectionAdapter {
        private final Context context;
        private final List<HuanghunOutfitConfig.OutfitItem> items;
        private final HuanghunOutfitCategoryActivity activity;

        OutfitAdapter(Context context, String category, HuanghunOutfitCategoryActivity activity) {
            this.context = context;
            this.items = new ArrayList<>(HuanghunOutfitConfig.getItems(category));
            this.activity = activity;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new OutfitGridCard(context));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            OutfitGridCard card = (OutfitGridCard) holder.itemView;
            HuanghunOutfitConfig.OutfitItem item = items.get(position);
            card.bind(item, HuanghunOutfitConfig.isSelected(context, item));
            card.setOnClickListener(v -> activity.presentFragment(new HuanghunOutfitPreviewActivity(item)));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }
    }

    private static final class OutfitGridCard extends FrameLayout {
        private final OutfitVisualView visual;
        private final TextView name;
        private final TextView state;

        OutfitGridCard(Context context) {
            super(context);
            RecyclerView.LayoutParams rootParams = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(224));
            rootParams.setMargins(dp(5), dp(5), dp(5), dp(5));
            setLayoutParams(rootParams);
            setPadding(dp(4), dp(4), dp(4), dp(4));
            visual = new OutfitVisualView(context, null);
            addView(visual, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, dp(146), Gravity.TOP));
            name = titleView(context, "", 15, true);
            name.setPadding(dp(9), 0, dp(9), 0);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            addView(name, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, dp(32), Gravity.TOP, 0, dp(149), 0, 0));
            state = titleView(context, "", 12, false);
            state.setPadding(dp(9), 0, dp(9), 0);
            addView(state, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, dp(28), Gravity.TOP, 0, dp(181), 0, 0));
        }

        void bind(HuanghunOutfitConfig.OutfitItem item, boolean selected) {
            setBackground(Theme.createRoundRectDrawable(dp(16), Theme.getColor(Theme.key_windowBackgroundWhite)));
            visual.setItem(item);
            name.setText(item.name);
            state.setText(selected ? "正在使用 · 本地" : "点击预览 · 本地");
            state.setTextColor(selected ? item.primary : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        }
    }

    static final class OutfitVisualView extends View {
        private HuanghunOutfitConfig.OutfitItem item;
        private final ValueAnimator animator;
        private float progress;

        OutfitVisualView(Context context, HuanghunOutfitConfig.OutfitItem item) {
            super(context);
            this.item = item;
            setWillNotDraw(false);
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(2600L);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(animation -> {
                progress = (float) animation.getAnimatedValue();
                invalidate();
            });
        }

        void setItem(HuanghunOutfitConfig.OutfitItem item) {
            this.item = item;
            invalidate();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!animator.isStarted()) animator.start();
        }

        @Override
        protected void onDetachedFromWindow() {
            animator.cancel();
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (item != null) {
                HuanghunOutfitVisuals.drawPreview(canvas, item, progress, getWidth(), getHeight());
            }
        }
    }

    private static TextView button(Context context, String text, int color) {
        TextView view = titleView(context, text, 16, true);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(Color.WHITE);
        view.setBackground(Theme.createRoundRectDrawable(dp(15), color));
        return view;
    }
}
