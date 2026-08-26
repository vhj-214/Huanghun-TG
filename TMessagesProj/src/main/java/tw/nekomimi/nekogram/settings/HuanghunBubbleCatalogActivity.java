package tw.nekomimi.nekogram.settings;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.LinkedHashSet;
import java.util.Locale;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.helpers.HuanghunBubbleStyleHelper;

/**
 * Local Huanghun bubble catalog. Every positive style ID maps directly to one user-provided
 * original skin resource. The catalog itself never changes message history; it only stores the
 * style to apply to the next locally created outgoing message or to the favorites collection.
 */
public class HuanghunBubbleCatalogActivity extends BaseFragment {

    private static final int MENU_BATCH = 1;

    private RecyclerListView listView;
    private CatalogAdapter adapter;
    private boolean batchMode;
    private final LinkedHashSet<Integer> favorites = new LinkedHashSet<>();

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.HuanghunCustomBubbles));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (batchMode) {
                        saveFavorites();
                    }
                    finishFragment();
                } else if (id == MENU_BATCH) {
                    toggleBatchMode();
                }
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(MENU_BATCH, LocaleController.getString(R.string.HuanghunBubbleBatch));

        favorites.addAll(HuanghunBubbleStyleHelper.readFavorites());
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new GridLayoutManager(context, 3));
        listView.setClipToPadding(false);
        listView.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(8), AndroidUtilities.dp(6), AndroidUtilities.dp(16));
        listView.setAdapter(adapter = new CatalogAdapter(context));
        root.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = root;
        return root;
    }

    private void toggleBatchMode() {
        batchMode = !batchMode;
        actionBar.setTitle(LocaleController.getString(batchMode ? R.string.HuanghunBubbleBatchSelect : R.string.HuanghunCustomBubbles));
        ActionBarMenu menu = actionBar.createMenu();
        menu.clearItems();
        menu.addItem(MENU_BATCH, LocaleController.getString(batchMode ? R.string.Done : R.string.HuanghunBubbleBatch));
        adapter.notifyDataSetChanged();
    }

    private void saveFavorites() {
        NekoConfig.huanghunBubbleFavorites.setConfigString(HuanghunBubbleStyleHelper.writeFavorites(favorites));
        batchMode = false;
    }

    private void onStyleTapped(int style) {
        style = HuanghunBubbleStyleHelper.normalizeStyle(style);
        if (batchMode) {
            if (style == HuanghunBubbleStyleHelper.DEFAULT_STYLE) {
                return;
            }
            if (!favorites.add(style)) {
                favorites.remove(style);
            }
        } else {
            NekoConfig.huanghunBubbleStyle.setConfigInt(style);
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onFragmentDestroy() {
        if (batchMode) {
            saveFavorites();
        }
        super.onFragmentDestroy();
    }

    private final class CatalogAdapter extends RecyclerListView.SelectionAdapter {
        private final Context context;

        CatalogAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return HuanghunBubbleStyleHelper.STYLE_COUNT + 1;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new RecyclerView.ViewHolder(new BubbleStyleCell(context)) {};
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            BubbleStyleCell cell = (BubbleStyleCell) holder.itemView;
            int style = position;
            boolean checked = batchMode ? favorites.contains(style) : HuanghunBubbleStyleHelper.getNextMessageStyle() == style;
            cell.bind(style, checked);
        }
    }

    private final class BubbleStyleCell extends FrameLayout {
        private final ImageView preview;
        private final TextView title;
        private final TextView check;
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF borderRect = new RectF();
        private int style;
        private boolean checked;

        BubbleStyleCell(Context context) {
            super(context);
            setWillNotDraw(false);
            setPadding(AndroidUtilities.dp(5), AndroidUtilities.dp(5), AndroidUtilities.dp(5), AndroidUtilities.dp(5));

            GradientDrawable card = new GradientDrawable();
            card.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            card.setCornerRadius(AndroidUtilities.dp(14));
            setBackground(card);
            setOnClickListener(v -> onStyleTapped(style));

            preview = new ImageView(context);
            preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
            addView(preview, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 112, Gravity.TOP));

            title = new TextView(context);
            title.setTextSize(12);
            title.setSingleLine(true);
            title.setGravity(Gravity.CENTER);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 28, Gravity.BOTTOM));

            check = new TextView(context);
            check.setText("✓");
            check.setTextSize(15);
            check.setGravity(Gravity.CENTER);
            check.setTextColor(Color.WHITE);
            GradientDrawable badge = new GradientDrawable();
            badge.setShape(GradientDrawable.OVAL);
            badge.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
            check.setBackground(badge);
            addView(check, LayoutHelper.createFrame(26, 26, Gravity.RIGHT | Gravity.TOP, 4, 4, 4, 4));

            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(AndroidUtilities.dp(3));
            borderPaint.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        }

        void bind(int style, boolean checked) {
            this.style = style;
            this.checked = checked;
            if (style == HuanghunBubbleStyleHelper.DEFAULT_STYLE) {
                preview.setImageDrawable(new DefaultBubblePreviewDrawable());
                title.setText(LocaleController.getString(R.string.HuanghunBubbleLiquidGlass));
            } else {
                String name = HuanghunBubbleStyleHelper.getSkinResourceName(style);
                int resourceId = getContext().getResources().getIdentifier(name, "drawable", getContext().getPackageName());
                preview.setImageResource(resourceId);
                title.setText(HuanghunBubbleStyleHelper.getStyleDisplayName(style));
            }
            check.setVisibility(checked ? VISIBLE : INVISIBLE);
            invalidate();
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            if (checked) {
                borderRect.set(AndroidUtilities.dp(2), AndroidUtilities.dp(2), getWidth() - AndroidUtilities.dp(2), getHeight() - AndroidUtilities.dp(2));
                canvas.drawRoundRect(borderRect, AndroidUtilities.dp(14), AndroidUtilities.dp(14), borderPaint);
            }
        }
    }

    private static final class DefaultBubblePreviewDrawable extends android.graphics.drawable.Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        DefaultBubblePreviewDrawable() {
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(AndroidUtilities.dp(16));
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        public void draw(android.graphics.Canvas canvas) {
            rect.set(getBounds().left + AndroidUtilities.dp(12), getBounds().top + AndroidUtilities.dp(27), getBounds().right - AndroidUtilities.dp(12), getBounds().bottom - AndroidUtilities.dp(17));
            paint.setColor(0x55FFFFFF);
            canvas.drawRoundRect(rect, AndroidUtilities.dp(22), AndroidUtilities.dp(22), paint);
            canvas.drawText(LocaleController.getString(R.string.HuanghunBubblePreview), rect.centerX(), rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); textPaint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
}
