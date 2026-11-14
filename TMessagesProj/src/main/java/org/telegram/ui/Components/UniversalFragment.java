package org.telegram.ui.Components;

import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Business.QuickRepliesController;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;

public abstract class UniversalFragment extends BaseFragment {

    public UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getTitle());
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        SizeNotifierFrameLayout contentView = new SizeNotifierFrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY)
                );
            }
        };
        android.graphics.drawable.Drawable wallpaper = Theme.getCachedWallpaper();
        contentView.setBackgroundImage(wallpaper, Theme.isWallpaperMotion());
        contentView.setBackgroundColor(wallpaper != null ? Color.TRANSPARENT : Theme.getColor(Theme.key_windowBackgroundGray));
        actionBar.setBackgroundColor(Color.TRANSPARENT);
        actionBar.setCastShadows(false);

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, this::onLongClick) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
//                applyScrolledPosition();
                super.onMeasure(widthSpec, heightSpec);
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                savedScrollPosition = -1;
            }
        };
        // Universal pages previously received opaque white cards from the
        // adapter. Disable only that fill and use a guarded glass section.
        listView.adapter.setApplyBackground(false);
        listView.setBackgroundColor(Color.TRANSPARENT);
        final Paint sectionGlassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        listView.setSections(
            view -> {
                if (view.getParent() != listView) return false;
                androidx.recyclerview.widget.RecyclerView.ViewHolder holder = listView.getChildViewHolder(view);
                return holder != null && !UniversalAdapter.isShadow(holder.getItemViewType());
            },
            UniversalAdapter::isShadow,
            AndroidUtilities.dp(12),
            16,
            (canvas, rect, topRadius, bottomRadius, alpha) -> {
                sectionGlassPaint.setColor(Theme.isCurrentThemeDark() ? Color.BLACK : Color.WHITE);
                sectionGlassPaint.setAlpha(Math.round((Theme.isCurrentThemeDark() ? 0x26 : 0x20) * alpha));
                float radius = Math.max(topRadius, bottomRadius);
                canvas.drawRoundRect(rect, radius, radius, sectionGlassPaint);
            },
            false
        );
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        return fragmentView = contentView;
    }

    protected abstract CharSequence getTitle();
    protected abstract void fillItems(ArrayList<UItem> items, UniversalAdapter adapter);
    protected abstract void onClick(UItem item, View view, int position, float x, float y);
    protected abstract boolean onLongClick(UItem item, View view, int position, float x, float y);

    private int savedScrollPosition = -1;
    private int savedScrollOffset;

    public void saveScrollPosition() {
        if (listView != null && listView.getChildCount() > 0) {
            View view = null;
            int position = -1;
            int top = Integer.MAX_VALUE;
            for (int i = 0; i < listView.getChildCount(); i++) {
                int childPosition = listView.getChildAdapterPosition(listView.getChildAt(i));
                View child = listView.getChildAt(i);
                if (childPosition != RecyclerListView.NO_POSITION && child.getTop() < top) {
                    view = child;
                    position = childPosition;
                    top = child.getTop();
                }
            }
            if (view != null) {
                savedScrollPosition = position;
                savedScrollOffset = view.getTop();
                if (savedScrollPosition == 0 && savedScrollOffset > AndroidUtilities.dp(88)) {
                    savedScrollOffset = AndroidUtilities.dp(88);
                }
                listView.layoutManager.scrollToPositionWithOffset(position, view.getTop() - listView.getPaddingTop());
            }
        }
    }

    public void applyScrolledPosition() {
        if (savedScrollPosition >= 0) {
            listView.layoutManager.scrollToPositionWithOffset(savedScrollPosition, savedScrollOffset - listView.getPaddingTop());
        }
    }
}
