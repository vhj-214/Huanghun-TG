package tw.nekomimi.nekogram.settings;

import static android.view.View.OVER_SCROLL_NEVER;
import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.BasePermissionsActivity;
import org.telegram.ui.Cells.SettingsSearchCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.DocumentSelectActivity;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.helpers.AppRestartHelper;
import tw.nekomimi.nekogram.helpers.CloudSettingsHelper;
import tw.nekomimi.nekogram.helpers.PasscodeHelper;
import tw.nekomimi.nekogram.helpers.SettingsBackupHelper;
import tw.nekomimi.nekogram.helpers.SettingsHelper;
import tw.nekomimi.nekogram.helpers.SettingsSearchResult;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;
import tw.nekomimi.nekogram.utils.AlertUtil;

public class NekoSettingsActivity extends BaseNekoSettingsActivity {

    private static final int MENU_SEARCH = 1;
    private static final int MENU_SYNC = 2;

    private int everydayHeaderRow, everydayEndRow;
    private int featureDirectoryRow, generalRow, chatRow, translatorRow, petRow;
    private int automationHeaderRow, automationEndRow;
    private int attentionRow, signInRow, activeRow, comebackRow;
    private int mediaHeaderRow, mediaEndRow;
    private int videoRow, localRow, experimentRow, extensionsRow;
    private int safetyHeaderRow, safetyEndRow;
    private int passcodeRow, privacyRow, blockRow;
    private int maintenanceHeaderRow, maintenanceEndRow;
    private int exportSettingsRow, importSettingsRow, cleanupRow, resetSettingsRow, appRestartRow, aboutRow;
    private int dangerHeaderRow, goodbyeRow;

    @Override
    protected void updateRows() {
        super.updateRows();

        everydayHeaderRow = addRow();
        featureDirectoryRow = addRow();
        generalRow = addRow();
        chatRow = addRow();
        translatorRow = addRow();
        petRow = addRow();
        everydayEndRow = addRow();

        automationHeaderRow = addRow();
        attentionRow = addRow();
        signInRow = addRow();
        activeRow = addRow();
        comebackRow = addRow();
        automationEndRow = addRow();

        mediaHeaderRow = addRow();
        videoRow = addRow();
        localRow = addRow();
        experimentRow = addRow();
        extensionsRow = addRow();
        mediaEndRow = addRow();

        safetyHeaderRow = addRow();
        if (!PasscodeHelper.isSettingsHidden()) {
            passcodeRow = addRow();
        } else {
            passcodeRow = -1;
        }
        privacyRow = addRow();
        blockRow = addRow();
        safetyEndRow = addRow();

        maintenanceHeaderRow = addRow();
        exportSettingsRow = addRow();
        importSettingsRow = addRow();
        cleanupRow = addRow();
        resetSettingsRow = addRow();
        appRestartRow = addRow();
        aboutRow = addRow();
        maintenanceEndRow = addRow();

        // “Goodbye” permanently deletes the current account. Keep it visually
        // isolated from pet settings and retain the original confirmation flow.
        dangerHeaderRow = addRow();
        goodbyeRow = addRow();
    }

    @Override
    public View createView(Context context) {
        View view = super.createView(context);

        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(MENU_SEARCH, R.drawable.outline_header_search, resourcesProvider);
        menu.addItem(MENU_SYNC, R.drawable.cloud_sync, resourcesProvider);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_SEARCH) {
                    showSettingsSearchDialog();
                } else if (id == MENU_SYNC) {
                    CloudSettingsHelper.getInstance().showDialog(NekoSettingsActivity.this);
                }
            }
        });

        return view;
    }

    /**
     * @noinspection SizeReplaceableByIsEmpty
     */
    private void showSettingsSearchDialog() {
        try {
            Activity parent = getParentActivity();
            if (parent == null) return;

            ArrayList<SettingsSearchResult> results = SettingsHelper.onCreateSearchArray(fragment -> AndroidUtilities.runOnUIThread(() -> {
                try {
                    presentFragment(fragment);
                } catch (Exception ignore) {
                }
            }));

            final ArrayList<SettingsSearchResult> filtered = new ArrayList<>(results);
            final String[] currentQuery = new String[]{""};
            final int searchHeight = dp(36);
            final int clearSize = dp(36);
            final int pad = dp(12);

            LinearLayout containerLayout = new LinearLayout(parent);
            containerLayout.setOrientation(LinearLayout.VERTICAL);
            containerLayout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

            FrameLayout searchFrame = new FrameLayout(parent);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, searchHeight + dp(12));
            layoutParams.leftMargin = dp(10);
            layoutParams.rightMargin = dp(10);
            layoutParams.topMargin = dp(6);
            layoutParams.bottomMargin = dp(2);
            searchFrame.setLayoutParams(layoutParams);
            searchFrame.setClipToPadding(true);
            searchFrame.setClipChildren(true);

            ImageView searchIcon = new ImageView(parent);
            searchIcon.setScaleType(ImageView.ScaleType.CENTER);
            searchIcon.setImageResource(R.drawable.ic_ab_search_solar);
            searchIcon.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
            searchFrame.addView(searchIcon, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.CENTER_VERTICAL));

            EditTextBoldCursor searchField = new EditTextBoldCursor(parent);
            searchField.setHint(getString(R.string.Search));
            searchField.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
            searchField.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
            searchField.setSingleLine(true);
            searchField.setBackground(null);
            searchField.setInputType(InputType.TYPE_CLASS_TEXT);
            searchField.setLineColors(getThemedColor(Theme.key_windowBackgroundWhiteInputField), getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated), getThemedColor(Theme.key_text_RedRegular));
            searchField.setPadding(dp(61), pad / 2, dp(48), pad / 2);
            searchField.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER_VERTICAL));
            searchFrame.addView(searchField);

            ImageView clearButton = new ImageView(parent);
            clearButton.setScaleType(ImageView.ScaleType.CENTER);
            clearButton.setImageResource(R.drawable.ic_close_white);
            clearButton.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
            clearButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
            clearButton.setLayoutParams(new FrameLayout.LayoutParams(clearSize, clearSize, Gravity.END | Gravity.CENTER_VERTICAL));
            searchFrame.addView(clearButton);
            containerLayout.addView(searchFrame);

            AlertDialog.Builder builder = new AlertDialog.Builder(parent, resourceProvider);
            builder.setView(containerLayout);
            builder.setNegativeButton(getString(R.string.Close), null);
            final AlertDialog dialog = builder.create();
            dialog.setOnShowListener(d -> {
                try {
                    searchField.requestFocus();
                    AndroidUtilities.showKeyboard(searchField);
                } catch (Exception ignore) {
                }
            });

            RecyclerListView searchListView = new RecyclerListView(parent);
            searchListView.setOverScrollMode(OVER_SCROLL_NEVER);
            searchListView.setLayoutManager(new LinearLayoutManager(parent, LinearLayoutManager.VERTICAL, false));

            var adapter = new RecyclerListView.SelectionAdapter() {
                @Override
                public boolean isEnabled(RecyclerView.ViewHolder holder) {
                    return true;
                }

                @NonNull
                @Override
                public RecyclerListView.Holder onCreateViewHolder(@NonNull ViewGroup parent1, int viewType) {
                    View view = new SettingsSearchCell(parent);
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                    return new RecyclerListView.Holder(view);
                }

                @Override
                public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                    SettingsSearchCell cell = (SettingsSearchCell) holder.itemView;
                    SettingsSearchResult r = filtered.get(position);
                    String[] path = r.path2 != null ? new String[]{r.path1, r.path2} : new String[]{r.path1};
                    CharSequence titleToSet = r.searchTitle == null ? "" : r.searchTitle;
                    String q = currentQuery[0];
                    if (q != null && !q.isEmpty() && titleToSet.length() > 0) {
                        SpannableStringBuilder ss = new SpannableStringBuilder(titleToSet);
                        String lower = titleToSet.toString().toLowerCase(Locale.ROOT);
                        String[] parts = q.split("\\s+");
                        int highlightColor = getThemedColor(Theme.key_windowBackgroundWhiteBlueText4);
                        for (String p : parts) {
                            if (p.isEmpty()) continue;
                            int idx = 0;
                            while (true) {
                                int found = lower.indexOf(p, idx);
                                if (found == -1) break;
                                try {
                                    ss.setSpan(new ForegroundColorSpan(highlightColor), found, found + p.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                } catch (Exception ignore) {
                                }
                                idx = found + p.length();
                            }
                        }
                        titleToSet = ss;
                    }
                    cell.setTextAndValueAndIcon(titleToSet, path, r.iconResId, position < filtered.size() - 1);
                }

                @Override
                public int getItemCount() {
                    return filtered.size();
                }
            };

            searchListView.setAdapter(adapter);
            searchListView.setOnItemClickListener((v, position) -> {
                if (position < 0 || position >= filtered.size()) return;
                SettingsSearchResult r = filtered.get(position);
                try {
                    if (r.openRunnable != null) r.openRunnable.run();
                } catch (Exception ignore) {
                }
                dialog.dismiss();
            });

            containerLayout.addView(searchListView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

            searchField.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @SuppressLint("NotifyDataSetChanged")
                @Override
                public void afterTextChanged(Editable s) {
                    String q = s.toString().toLowerCase(Locale.ROOT).trim();
                    currentQuery[0] = q;
                    filtered.clear();
                    if (q.isEmpty()) {
                        filtered.addAll(results);
                    } else {
                        String[] parts = q.split("\\s+");
                        for (SettingsSearchResult item : results) {
                            String searchable = ((item.searchTitle == null ? "" : item.searchTitle) + " "
                                    + (item.path1 == null ? "" : item.path1) + " "
                                    + (item.path2 == null ? "" : item.path2)).toLowerCase(Locale.ROOT);
                            boolean ok = true;
                            for (String p : parts) {
                                if (!searchable.contains(p)) {
                                    ok = false;
                                    break;
                                }
                            }
                            if (ok) filtered.add(item);
                        }
                    }
                    adapter.notifyDataSetChanged();
                    searchIcon.setVisibility(q.length() > 20 ? View.GONE : View.VISIBLE);
                    clearButton.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                }
            });

            clearButton.setOnClickListener(v -> {
                searchField.setText("");
                searchField.requestFocus();
                AndroidUtilities.showKeyboard(searchField);
            });
            clearButton.setVisibility(View.GONE);

            showDialog(dialog);
        } catch (Exception ignore) {
        }
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.NekoSettings);
    }

    @SuppressLint("ApplySharedPref")
    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == featureDirectoryRow) {
            presentFragment(new HuanghunFeatureDirectoryActivity());
        } else if (position == chatRow) {
            presentFragment(new NekoChatSettingsActivity());
        } else if (position == generalRow) {
            presentFragment(new NekoGeneralSettingsActivity());
        } else if (position == passcodeRow) {
            presentFragment(new NekoPasscodeSettingsActivity());
        } else if (position == experimentRow) {
            presentFragment(new NekoExperimentalSettingsActivity());
        } else if (position == extensionsRow) {
            presentFragment(new HuanghunFeatureDirectoryActivity());
        } else if (position == attentionRow) {
            presentFragment(new NekoExtensionsActivity(NekoExtensionsActivity.Section.ATTENTION));
        } else if (position == signInRow) {
            presentFragment(new NekoExtensionsActivity(NekoExtensionsActivity.Section.SIGN_IN));
        } else if (position == activeRow) {
            presentFragment(new NekoExtensionsActivity(NekoExtensionsActivity.Section.ACTIVE));
        } else if (position == comebackRow) {
            presentFragment(new NekoExtensionsActivity(NekoExtensionsActivity.Section.COMEBACK));
        } else if (position == videoRow) {
            presentFragment(new NekoExtensionsActivity(NekoExtensionsActivity.Section.VIDEO));
        } else if (position == localRow) {
            presentFragment(new NekoExtensionsActivity(NekoExtensionsActivity.Section.LOCAL));
        } else if (position == privacyRow) {
            presentFragment(new NekoExtensionsActivity(NekoExtensionsActivity.Section.PRIVACY));
        } else if (position == blockRow) {
            presentFragment(new NekoExtensionsActivity(NekoExtensionsActivity.Section.BLOCK));
        } else if (position == cleanupRow) {
            presentFragment(new NekoExtensionsActivity(NekoExtensionsActivity.Section.CLEANUP));
        } else if (position == petRow) {
            presentFragment(new NekoPetActivity());
        } else if (position == goodbyeRow) {
            NekoPetActivity.showGoodbyeForAccount(getParentActivity(), currentAccount, resourceProvider);
        } else if (position == translatorRow) {
            presentFragment(new NekoTranslatorSettingsActivity());
        } else if (position == aboutRow) {
            presentFragment(new NekoAboutActivity());
        } else if (position == importSettingsRow) {
            if (Build.VERSION.SDK_INT >= 33) {
                openFilePicker();
            } else {
                DocumentSelectActivity activity = getDocumentSelectActivity(getParentActivity());
                if (activity != null) {
                    presentFragment(activity);
                }
            }
        } else if (position == resetSettingsRow) {
            AlertUtil.showConfirm(getParentActivity(),
                    getString(R.string.ResetSettingsAlert),
                    R.drawable.msg_reset,
                    getString(R.string.Reset),
                    true,
                    () -> {
                        ApplicationLoader.applicationContext.getSharedPreferences("nekocloud", Activity.MODE_PRIVATE).edit().clear().commit();
                        ApplicationLoader.applicationContext.getSharedPreferences("nekox_config", Activity.MODE_PRIVATE).edit().clear().commit();
                        NekoConfig.getPreferences().edit().clear().commit();
                        AppRestartHelper.triggerRebirth(getParentActivity(), new Intent(getParentActivity(), LaunchActivity.class));
                    });
        } else if (position == exportSettingsRow) {
            SettingsBackupHelper.backupSettings(getParentActivity(), resourceProvider);
        } else if (position == appRestartRow) {
            AppRestartHelper.triggerRebirth(getParentActivity(), new Intent(getParentActivity(), LaunchActivity.class));
        }
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            int viewType = holder.getItemViewType();
            switch (viewType) {
                case TYPE_SHADOW: {
                    // Transparent on wallpaper-backed settings pages; section glass is drawn by the base list.
                    break;
                }
                case TYPE_HEADER: {
                    int title = position == everydayHeaderRow ? R.string.HuanghunCategoryEveryday
                            : position == automationHeaderRow ? R.string.HuanghunCategoryAutomation
                            : position == mediaHeaderRow ? R.string.HuanghunCategoryMedia
                            : position == safetyHeaderRow ? R.string.HuanghunCategorySafety
                            : position == maintenanceHeaderRow ? R.string.HuanghunCategoryMaintenance
                            : R.string.HuanghunCategoryDanger;
                    ((HeaderCell) holder.itemView).setText(getString(title));
                    break;
                }
                case TYPE_TEXT: {
                    TextCell textCell = (TextCell) holder.itemView;
                    int title;
                    int description;
                    int icon;
                    if (position == featureDirectoryRow) {
                        title = R.string.HuanghunCategoryAllFeatures; description = R.string.HuanghunCategoryDescAllFeatures; icon = R.drawable.msg_list;
                    } else if (position == chatRow) {
                        title = R.string.Chat; description = R.string.HuanghunCategoryDescChat; icon = R.drawable.msg_discussion;
                    } else if (position == generalRow) {
                        title = R.string.General; description = R.string.HuanghunCategoryDescGeneral; icon = R.drawable.msg_theme;
                    } else if (position == translatorRow) {
                        title = R.string.TranslatorSettings; description = R.string.HuanghunCategoryDescTranslation; icon = R.drawable.ic_translate;
                    } else if (position == passcodeRow) {
                        title = R.string.PasscodeNeko; description = R.string.HuanghunCategoryDescPasscode; icon = R.drawable.msg_permissions;
                    } else if (position == experimentRow) {
                        title = R.string.Experimental; description = R.string.HuanghunCategoryDescExperimental; icon = R.drawable.msg_fave;
                    } else if (position == extensionsRow) {
                        title = R.string.HuanghunExtensions; description = R.string.HuanghunCategoryDescExtensions; icon = R.drawable.msg_settings;
                    } else if (position == petRow) {
                        title = R.string.HuanghunPet; description = R.string.HuanghunCategoryDescPet; icon = R.drawable.msg_emoji_cat_solar;
                    } else if (position == attentionRow) {
                        title = R.string.HuanghunCategoryAttention; description = R.string.HuanghunCategoryDescAttention; icon = R.drawable.msg_contacts;
                    } else if (position == signInRow) {
                        title = R.string.HuanghunCategorySignIn; description = R.string.HuanghunCategoryDescSignIn; icon = R.drawable.msg_calendar2;
                    } else if (position == activeRow) {
                        title = R.string.HuanghunCategoryActive; description = R.string.HuanghunCategoryDescActive; icon = R.drawable.msg_reactions2;
                    } else if (position == comebackRow) {
                        title = R.string.HuanghunCategoryComeback; description = R.string.HuanghunCategoryDescComeback; icon = R.drawable.msg_discussion;
                    } else if (position == videoRow) {
                        title = R.string.HuanghunCategoryVideo; description = R.string.HuanghunCategoryDescVideo; icon = R.drawable.msg_camera;
                    } else if (position == localRow) {
                        title = R.string.HuanghunCategoryLocal; description = R.string.HuanghunCategoryDescLocal; icon = R.drawable.msg_edit;
                    } else if (position == privacyRow) {
                        title = R.string.HuanghunCategoryPrivacy; description = R.string.HuanghunCategoryDescPrivacy; icon = R.drawable.msg_permissions;
                    } else if (position == blockRow) {
                        title = R.string.HuanghunCategoryBlock; description = R.string.HuanghunCategoryDescBlock; icon = R.drawable.msg_disable;
                    } else if (position == cleanupRow) {
                        title = R.string.HuanghunCategoryCleanup; description = R.string.HuanghunCategoryDescCleanup; icon = R.drawable.msg_clear;
                    } else if (position == goodbyeRow) {
                        title = R.string.HuanghunCategoryGoodbye; description = R.string.HuanghunCategoryDescGoodbye; icon = R.drawable.msg_delete;
                    } else if (position == importSettingsRow) {
                        title = R.string.ImportSettings; description = R.string.HuanghunCategoryDescImport; icon = R.drawable.import_solar;
                    } else if (position == exportSettingsRow) {
                        title = R.string.BackupSettings; description = R.string.HuanghunCategoryDescBackup; icon = R.drawable.export_solar;
                    } else if (position == resetSettingsRow) {
                        title = R.string.ResetSettings; description = R.string.HuanghunCategoryDescReset; icon = R.drawable.msg_reset_solar;
                    } else if (position == appRestartRow) {
                        title = R.string.RestartApp; description = R.string.HuanghunCategoryDescRestart; icon = R.drawable.msg_retry_solar;
                    } else {
                        title = R.string.About; description = R.string.HuanghunCategoryDescAbout; icon = R.drawable.msg_info;
                    }
                    boolean divider = position + 1 < rowCount && getItemViewType(position + 1) == TYPE_TEXT;
                    textCell.heightDp = 64;
                    textCell.setTextColor(getThemedColor(position == goodbyeRow
                            ? Theme.key_text_RedRegular : Theme.key_windowBackgroundWhiteBlackText));
                    textCell.setTextAndIcon(getString(title), icon, divider);
                    textCell.setSubtitle(getString(description));
                    textCell.setContentDescription(getString(title) + ". " + getString(description));
                    break;
                }

            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == everydayEndRow || position == automationEndRow || position == mediaEndRow
                    || position == safetyEndRow || position == maintenanceEndRow) {
                return TYPE_SHADOW;
            } else if (position == everydayHeaderRow || position == automationHeaderRow || position == mediaHeaderRow
                    || position == safetyHeaderRow || position == maintenanceHeaderRow || position == dangerHeaderRow) {
                return TYPE_HEADER;
            }
            return TYPE_TEXT;
        }
    }

    private DocumentSelectActivity getDocumentSelectActivity(Activity parent) {
        try {
            if (parent.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                parent.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE);
                return null;
            }
        } catch (Throwable ignore) {
        }
        DocumentSelectActivity fragment = new DocumentSelectActivity(false);
        fragment.setMaxSelectedFiles(1);
        fragment.setAllowPhoto(false);
        fragment.setDelegate(new DocumentSelectActivity.DocumentSelectActivityDelegate() {
            @Override
            public void didSelectFiles(DocumentSelectActivity activity, ArrayList<String> files, String caption, boolean notify, int scheduleDate) {
                activity.finishFragment();
                SettingsBackupHelper.importSettings(parent, new File(files.get(0)));
            }

            @Override
            public void didSelectPhotos(ArrayList<SendMessagesHelper.SendingMediaInfo> photos, boolean notify, int scheduleDate) {
            }

            @Override
            public void startDocumentSelectActivity() {
            }
        });
        return fragment;
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(intent, 21);
        } catch (android.content.ActivityNotFoundException ex) {
            AlertUtil.showSimpleAlert(getParentActivity(), ex);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == 21 && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                File cacheDir = AndroidUtilities.getCacheDir();
                String tempFile = UUID.randomUUID().toString().replace("-", "") + ".nekox-settings.json";
                File file = new File(cacheDir.getPath(), tempFile);
                try {
                    final InputStream inputStream = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
                    if (inputStream != null) {
                        OutputStream outputStream = new FileOutputStream(file);
                        final byte[] buffer = new byte[4 * 1024];
                        int read;
                        while ((read = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, read);
                        }
                        inputStream.close();
                        outputStream.flush();
                        outputStream.close();
                        SettingsBackupHelper.importSettings(getParentActivity(), file);
                    }
                } catch (Exception ignore) {
                }
            }
            super.onActivityResultFragment(requestCode, resultCode, data);
        }
    }
}
