package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.ThemeActivity;

import java.util.ArrayList;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.helpers.PasscodeHelper;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;
import xyz.nextalone.nagram.NaConfig;

/** A navigation-only index. All settings and their side effects remain in their original pages. */
public class HuanghunFeatureDirectoryActivity extends BaseNekoSettingsActivity {
    private static final int TYPE_HERO = 30;
    private static final int TYPE_FEATURE = 31;
    private final ArrayList<Row> rows = new ArrayList<>();

    private static final class Row {
        final int type;
        final int title;
        final int description;
        final int icon;
        final Runnable open;

        Row(int type, int title, int description, int icon, Runnable open) {
            this.type = type;
            this.title = title;
            this.description = description;
            this.icon = icon;
            this.open = open;
        }
    }

    private void header(int title) {
        addRow();
        rows.add(new Row(TYPE_HEADER, title, 0, 0, null));
    }

    private void entry(int title, int description, int icon, Runnable open) {
        addRow();
        rows.add(new Row(TYPE_FEATURE, title, description, icon, open));
    }

    private void endSection() {
        addRow();
        rows.add(new Row(TYPE_SHADOW, 0, 0, 0, null));
    }

    @Override
    protected void updateRows() {
        super.updateRows();
        rows.clear();

        addRow();
        rows.add(new Row(TYPE_HERO, R.string.hfd_hero_title, R.string.hfd_hero_desc, R.drawable.msg_list, null));

        header(R.string.hfd_chat_header);
        // General: Dialogs and Folder; Chat: Chats, Double Tap, Menu, Interaction, Channels, Search tag.
        entry(R.string.hfd_dialogs, R.string.hfd_dialogs_desc, R.drawable.msg_discussion,
                () -> openGeneral(NaConfig.INSTANCE.getSortByTime().getKey()));
        entry(R.string.hfd_folders, R.string.hfd_folders_desc, R.drawable.msg_discussion,
                () -> openGeneral(NekoConfig.hideAllTab.getKey()));
        entry(R.string.hfd_chat_options, R.string.hfd_chat_options_desc, R.drawable.msg_discussion,
                () -> openChat(NekoConfig.unreadBadgeOnBackButton.getKey()));
        entry(R.string.hfd_double_tap, R.string.hfd_double_tap_desc, R.drawable.msg_discussion,
                () -> openChat("DoubleTapIncoming"));
        entry(R.string.hfd_menus, R.string.hfd_menus_desc, R.drawable.msg_discussion,
                () -> openChat("ChatMenu"));
        entry(R.string.hfd_interactions, R.string.hfd_interactions_desc, R.drawable.msg_discussion,
                () -> openChat(NaConfig.INSTANCE.getGroupedMessageMenu().getKey()));
        entry(R.string.hfd_channels, R.string.hfd_channels_desc, R.drawable.msg_discussion,
                () -> openChat(NekoConfig.hideSendAsChannel.getKey()));
        entry(R.string.hfd_search_tags, R.string.hfd_search_tags_desc, R.drawable.msg_discussion,
                () -> openChat(NaConfig.INSTANCE.getSearchHashtagDefaultPageChannel().getKey()));
        entry(R.string.hfd_wallpaper, R.string.hfd_wallpaper_desc, R.drawable.msg_background,
                () -> presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_HUANGHUN_WALLPAPER)));
        entry(R.string.hfd_translation, R.string.hfd_translation_desc, R.drawable.ic_translate,
                () -> presentFragment(new NekoTranslatorSettingsActivity()));
        // Experimental: Pangu.
        entry(R.string.hfd_pangu, R.string.hfd_pangu_desc, R.drawable.msg_edit,
                () -> openExperimental(NaConfig.INSTANCE.getEnablePanguOnSending().getKey()));
        endSection();

        header(R.string.hfd_appearance_header);
        // General: General, Appearance, Blur and Main Tabs.
        entry(R.string.hfd_titles, R.string.hfd_titles_desc, R.drawable.msg_theme,
                () -> openGeneral(NaConfig.INSTANCE.getFolderNameAsTitle().getKey()));
        entry(R.string.hfd_appearance, R.string.hfd_appearance_desc, R.drawable.msg_theme,
                () -> openGeneral(NekoConfig.typeface.getKey()));
        entry(R.string.hfd_blur, R.string.hfd_blur_desc, R.drawable.msg_theme,
                () -> openGeneral(NaConfig.INSTANCE.getStrokeOnViews().getKey()));
        entry(R.string.hfd_main_tabs, R.string.hfd_main_tabs_desc, R.drawable.msg_theme,
                () -> openGeneral(NaConfig.INSTANCE.getHideBottomNavigationBar().getKey()));
        // The first visible keyed setting in the sticker-size group is just below its slider.
        entry(R.string.hfd_sticker_size, R.string.hfd_sticker_size_desc, R.drawable.msg_sticker,
                () -> openChat(NaConfig.INSTANCE.getShowTimeHint().getKey()));
        entry(R.string.hfd_stickers, R.string.hfd_stickers_desc, R.drawable.msg_sticker,
                () -> openChat(NekoConfig.dontSendGreetingSticker.getKey()));
        // Emoji packs are asynchronous and their IDs are dynamic: open the full page for browsing.
        entry(R.string.hfd_emoji_system, R.string.hfd_emoji_system_desc, R.drawable.msg_emoji_cat_solar,
                () -> openEmoji("useSystemEmoji"));
        entry(R.string.hfd_emoji_custom, R.string.hfd_emoji_custom_desc, R.drawable.msg_emoji_cat_solar,
                () -> openEmoji("customEmojiAdd"));
        entry(R.string.hfd_emoji_browse, R.string.hfd_emoji_browse_desc, R.drawable.msg_emoji_cat_solar,
                () -> openEmoji(null));
        entry(R.string.hfd_pet, R.string.hfd_pet_desc, R.drawable.msg_emoji_cat_solar,
                () -> presentFragment(new NekoPetActivity()));
        endSection();

        header(R.string.hfd_media_header);
        // Chat: Camera, Media and Transcribe; Experimental: Media.
        entry(R.string.hfd_camera, R.string.hfd_camera_desc, R.drawable.msg_camera,
                () -> openChat(NekoConfig.disableInstantCamera.getKey()));
        entry(R.string.hfd_media, R.string.hfd_media_desc, R.drawable.msg_camera,
                () -> openChat(NaConfig.INSTANCE.getShowSmallGIF().getKey()));
        entry(R.string.hfd_transcribe, R.string.hfd_transcribe_desc, R.drawable.msg_camera,
                () -> openChat("TranscribeProviderShort"));
        entry(R.string.hfd_media_experimental, R.string.hfd_media_experimental_desc, R.drawable.msg_camera,
                () -> openExperimental(NaConfig.INSTANCE.getNoiseSuppressAndVoiceEnhance().getKey()));
        entry(R.string.hfd_video, R.string.hfd_video_desc, R.drawable.msg_camera,
                () -> openExtension(NekoExtensionsActivity.Section.VIDEO));
        // HuanghunCallSettingsActivity only uses generated numeric row keys. Open the whole page.
        entry(R.string.hfd_calls, R.string.hfd_calls_desc, R.drawable.msg_camera,
                () -> presentFragment(new HuanghunCallSettingsActivity()));
        endSection();

        header(R.string.hfd_privacy_header);
        // General: Privacy; Chat: Confirmations. AyuMoments: independently useful privacy rows.
        entry(R.string.hfd_privacy_general, R.string.hfd_privacy_general_desc, R.drawable.msg_permissions,
                () -> openGeneral(NekoConfig.hidePhone.getKey()));
        entry(R.string.hfd_confirmations, R.string.hfd_confirmations_desc, R.drawable.msg_permissions,
                () -> openChat(NekoConfig.askBeforeCall.getKey()));
        entry(R.string.hfd_privacy_folder, R.string.hfd_privacy_folder_desc, R.drawable.msg_permissions,
                () -> openExtension(NekoExtensionsActivity.Section.PRIVACY));
        entry(R.string.hfd_block, R.string.hfd_block_desc, R.drawable.msg_disable,
                () -> openExtension(NekoExtensionsActivity.Section.BLOCK));
        entry(R.string.hfd_ghost, R.string.hfd_ghost_desc, R.drawable.msg_permissions,
                () -> openExperimental("GhostMode"));
        entry(R.string.hfd_filters, R.string.hfd_filters_desc, R.drawable.msg_permissions,
                () -> openExperimental(NaConfig.INSTANCE.getRegexFiltersEnabled().getKey()));
        entry(R.string.hfd_last_seen, R.string.hfd_last_seen_desc, R.drawable.msg_permissions,
                () -> openExperimental(NaConfig.INSTANCE.getSaveLocalLastSeen().getKey()));
        // Mirror the original home page's visibility condition; never expose a hidden passcode page.
        if (!PasscodeHelper.isSettingsHidden()) {
            entry(R.string.hfd_passcode, R.string.hfd_passcode_desc, R.drawable.msg_permissions, () -> {
                if (!PasscodeHelper.isSettingsHidden()) {
                    presentFragment(new NekoPasscodeSettingsActivity());
                }
            });
        }
        endSection();

        header(R.string.hfd_automation_header);
        entry(R.string.hfd_comeback, R.string.hfd_comeback_desc, R.drawable.msg_discussion,
                () -> openExtension(NekoExtensionsActivity.Section.COMEBACK));
        entry(R.string.hfd_attention, R.string.hfd_attention_desc, R.drawable.msg_contacts,
                () -> openExtension(NekoExtensionsActivity.Section.ATTENTION));
        entry(R.string.hfd_sign_in, R.string.hfd_sign_in_desc, R.drawable.msg_calendar2,
                () -> openExtension(NekoExtensionsActivity.Section.SIGN_IN));
        entry(R.string.hfd_active, R.string.hfd_active_desc, R.drawable.msg_reactions2,
                () -> openExtension(NekoExtensionsActivity.Section.ACTIVE));
        endSection();

        header(R.string.hfd_local_header);
        entry(R.string.hfd_local, R.string.hfd_local_desc, R.drawable.msg_edit,
                () -> openExtension(NekoExtensionsActivity.Section.LOCAL));
        // Cross-link to Experimental's General group at its local-only feature.
        entry(R.string.hfd_local_premium, R.string.hfd_local_premium_desc, R.drawable.msg_fave,
                () -> openExperimental(NekoConfig.localPremium.getKey()));
        endSection();

        header(R.string.hfd_network_header);
        // General: Storage, Connections, Map, Notifications and AutoDownload.
        entry(R.string.hfd_storage, R.string.hfd_storage_desc, R.drawable.msg_settings,
                () -> openGeneral(NaConfig.INSTANCE.getSaveToChatSubfolder().getKey()));
        entry(R.string.hfd_connection, R.string.hfd_connection_desc, R.drawable.msg_settings,
                () -> openGeneral(NekoConfig.useIPv6.getKey()));
        entry(R.string.hfd_map, R.string.hfd_map_desc, R.drawable.msg_settings,
                () -> openGeneral(NekoConfig.useOSMDroidMap.getKey()));
        entry(R.string.hfd_notifications, R.string.hfd_notifications_desc, R.drawable.msg_settings,
                () -> openGeneral(NaConfig.INSTANCE.getPushServiceType().getKey()));
        entry(R.string.hfd_downloads, R.string.hfd_downloads_desc, R.drawable.msg_settings,
                () -> openGeneral(NekoConfig.disableAutoDownloadingWin32Executable.getKey()));
        // Experimental: Connections.
        entry(R.string.hfd_transfer_experimental, R.string.hfd_transfer_experimental_desc, R.drawable.msg_settings,
                () -> openExperimental(NekoConfig.uploadBoost.getKey()));
        endSection();

        header(R.string.hfd_data_header);
        // Experimental: General, AyuMoments, N-Config and Story. These anchors remain visible
        // regardless of dependent switches, unlike the conditionally inserted detail rows.
        entry(R.string.hfd_experimental_general, R.string.hfd_experimental_general_desc, R.drawable.msg_fave,
                () -> openExperimental(NaConfig.INSTANCE.getBackAnimationStyle().getKey()));
        entry(R.string.hfd_deleted, R.string.hfd_deleted_desc, R.drawable.msg_fave,
                () -> openExperimental(NaConfig.INSTANCE.getEnableSaveDeletedMessages().getKey()));
        entry(R.string.hfd_edits, R.string.hfd_edits_desc, R.drawable.msg_fave,
                () -> openExperimental(NaConfig.INSTANCE.getEnableSaveEditsHistory().getKey()));
        entry(R.string.hfd_nconfig, R.string.hfd_nconfig_desc, R.drawable.msg_fave,
                () -> openExperimental(NaConfig.INSTANCE.getShowRPCError().getKey()));
        entry(R.string.hfd_stories, R.string.hfd_stories_desc, R.drawable.msg_fave,
                () -> openExperimental(NaConfig.INSTANCE.getDisableStories().getKey()));
        entry(R.string.hfd_backup, R.string.hfd_backup_desc, R.drawable.msg_settings,
                () -> presentFragment(new NekoSettingsActivity()));
        entry(R.string.hfd_cleanup, R.string.hfd_cleanup_desc, R.drawable.msg_clear,
                () -> openExtension(NekoExtensionsActivity.Section.CLEANUP));
        endSection();
    }

    private void openGeneral(String key) {
        openNekoX(new NekoGeneralSettingsActivity(), key);
    }

    private void openChat(String key) {
        openNekoX(new NekoChatSettingsActivity(), key);
    }

    private void openExperimental(String key) {
        openNekoX(new NekoExperimentalSettingsActivity(), key);
    }

    private void openNekoX(BaseNekoXSettingsActivity page, String key) {
        if (presentFragment(page)) {
            // Same asynchronous scroll pattern as SettingsHelper. A missing key safely leaves
            // the original page open, without touching a setting or invoking an action.
            AndroidUtilities.runOnUIThread(() -> page.scrollToRow(key, null));
        }
    }

    private void openEmoji(String key) {
        NekoEmojiSettingsActivity page = new NekoEmojiSettingsActivity();
        if (presentFragment(page) && key != null) {
            // Unlike BaseNekoX, BaseNekoSettingsActivity expects a non-null fallback.
            AndroidUtilities.runOnUIThread(() -> {
                if (page.getFragmentView() != null) {
                    page.scrollToRow(key, () -> { });
                }
            });
        }
    }

    private void openExtension(NekoExtensionsActivity.Section section) {
        presentFragment(new NekoExtensionsActivity(section));
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.hfd_title);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position >= 0 && position < rows.size()) {
            Runnable open = rows.get(position).open;
            if (open != null) {
                open.run();
            }
        }
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new DirectoryAdapter(context);
    }

    private class DirectoryAdapter extends BaseListAdapter {
        DirectoryAdapter(Context context) {
            super(context);
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).type;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position >= 0 && position < rows.size() && rows.get(position).open != null;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_FEATURE) {
                FeatureCell cell = new FeatureCell(mContext);
                RecyclerListView.Holder holder = new RecyclerListView.Holder(cell);
                // FeatureCell handles its own touch events, so RecyclerListView's
                // item-click listener will not receive taps on it. Use the live
                // adapter position to keep recycled cards mapped to the right row.
                cell.setOnClickListener(view -> onItemClick(view, holder.getAdapterPosition(), 0, 0));
                return holder;
            } else if (viewType == TYPE_HERO) {
                return new RecyclerListView.Holder(new HeroCell(mContext));
            }
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            Row row = rows.get(position);
            if (row.type == TYPE_HEADER) {
                ((HeaderCell) holder.itemView).setText(getString(row.title));
            } else if (row.type == TYPE_FEATURE) {
                ((FeatureCell) holder.itemView).bind(row);
            } else if (row.type == TYPE_HERO) {
                ((HeroCell) holder.itemView).bind(row);
            }
        }
    }

    private class FeatureCell extends FrameLayout {
        private final ImageView icon;
        private final TextView title;
        private final TextView description;
        private final ImageView arrow;
        private final GradientDrawable cardBackground;
        private final GradientDrawable iconBackground;

        FeatureCell(Context context) {
            super(context);
            setFocusable(true);
            setClickable(true);

            cardBackground = new GradientDrawable();
            cardBackground.setCornerRadius(AndroidUtilities.dp(18));
            cardBackground.setColor(ColorUtils.setAlphaComponent(
                    getThemedColor(Theme.key_windowBackgroundWhite), Theme.isCurrentThemeDark() ? 44 : 150));
            cardBackground.setStroke(AndroidUtilities.dp(1), ColorUtils.setAlphaComponent(Color.WHITE, Theme.isCurrentThemeDark() ? 30 : 115));
            setBackground(cardBackground);
            setForeground(Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(Color.WHITE, 45), Theme.RIPPLE_MASK_ROUNDRECT_6DP, AndroidUtilities.dp(18)));

            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(82));
            params.setMargins(AndroidUtilities.dp(16), AndroidUtilities.dp(4), AndroidUtilities.dp(16), AndroidUtilities.dp(4));
            setLayoutParams(params);

            FrameLayout iconHolder = new FrameLayout(context);
            iconBackground = new GradientDrawable();
            iconBackground.setCornerRadius(AndroidUtilities.dp(14));
            iconBackground.setColor(ColorUtils.setAlphaComponent(getThemedColor(Theme.key_windowBackgroundWhiteBlueText4), 30));
            iconBackground.setStroke(AndroidUtilities.dp(1), ColorUtils.setAlphaComponent(getThemedColor(Theme.key_windowBackgroundWhiteBlueText4), 45));
            iconHolder.setBackground(iconBackground);
            addView(iconHolder, LayoutHelper.createFrame(46, 46, Gravity.LEFT | Gravity.CENTER_VERTICAL, 14, 0, 0, 0));

            icon = new ImageView(context);
            icon.setScaleType(ImageView.ScaleType.CENTER);
            icon.setColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlueText4));
            iconHolder.addView(icon, LayoutHelper.createFrame(25, 25, Gravity.CENTER));

            LinearLayout textLayout = new LinearLayout(context);
            textLayout.setOrientation(LinearLayout.VERTICAL);
            textLayout.setGravity(Gravity.CENTER_VERTICAL);
            addView(textLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 72, 0, 48, 0));

            title = new TextView(context);
            title.setTextSize(16);
            title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            title.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            title.setSingleLine(true);
            textLayout.addView(title, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            description = new TextView(context);
            description.setTextSize(12);
            description.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            description.setMaxLines(1);
            description.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
            descriptionParams.topMargin = AndroidUtilities.dp(4);
            textLayout.addView(description, descriptionParams);

            arrow = new ImageView(context);
            arrow.setImageResource(R.drawable.msg_arrowright);
            arrow.setColorFilter(ColorUtils.setAlphaComponent(getThemedColor(Theme.key_windowBackgroundWhiteGrayText), 185));
            addView(arrow, LayoutHelper.createFrame(20, 20, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 14, 0));
        }

        void bind(Row row) {
            title.setText(getString(row.title));
            description.setText(getString(row.description));
            icon.setImageResource(row.icon);
            setContentDescription(getString(row.title) + ". " + getString(row.description));
        }
    }

    private class HeroCell extends FrameLayout {
        private final TextView eyebrow;
        private final TextView title;
        private final TextView description;
        private final ImageView icon;

        HeroCell(Context context) {
            super(context);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(132));
            params.setMargins(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
            setLayoutParams(params);

            GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0x8C5C84D8, 0x7250BEB1, 0x6695D3B3});
            background.setCornerRadius(AndroidUtilities.dp(24));
            background.setStroke(AndroidUtilities.dp(1), 0x66FFFFFF);
            setBackground(background);

            LinearLayout textLayout = new LinearLayout(context);
            textLayout.setOrientation(LinearLayout.VERTICAL);
            textLayout.setGravity(Gravity.CENTER_VERTICAL);
            addView(textLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT, 20, 0, 76, 0));

            eyebrow = new TextView(context);
            eyebrow.setTextSize(11);
            eyebrow.setLetterSpacing(.08f);
            eyebrow.setTextColor(0xEFFFFFFF);
            eyebrow.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            textLayout.addView(eyebrow, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            title = new TextView(context);
            title.setTextSize(22);
            title.setTextColor(Color.WHITE);
            title.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
            titleParams.topMargin = AndroidUtilities.dp(5);
            textLayout.addView(title, titleParams);

            description = new TextView(context);
            description.setTextSize(12);
            description.setTextColor(0xE8FFFFFF);
            description.setMaxLines(2);
            LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
            descriptionParams.topMargin = AndroidUtilities.dp(5);
            textLayout.addView(description, descriptionParams);

            icon = new ImageView(context);
            icon.setImageResource(R.drawable.msg_list);
            icon.setColorFilter(0xE8FFFFFF);
            icon.setAlpha(.18f);
            addView(icon, LayoutHelper.createFrame(66, 66, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 18, 0));
        }

        void bind(Row row) {
            eyebrow.setText(getString(R.string.hfd_hero_eyebrow));
            title.setText(getString(row.title));
            description.setText(getString(row.description));
            setContentDescription(getString(row.title) + ". " + getString(row.description));
        }
    }
}
