package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.Cells.TextSettingsCell;

import tw.nekomimi.nekogram.DatacenterActivity;

/** Branded About screen for 黄昏. */
public class NekoAboutActivity extends BaseNekoSettingsActivity {

    private int notificationChannelRow;
    private int feedbackChannelRow;
    private int translationRow;
    private int datacenterStatusRow;

    @Override
    protected void updateRows() {
        super.updateRows();

        notificationChannelRow = addRow();
        feedbackChannelRow = addRow();
        translationRow = addRow();
        datacenterStatusRow = addRow();
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.About);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == notificationChannelRow) {
            MessagesController.getInstance(currentAccount).openByUserName("hqsh_dbtz", NekoAboutActivity.this, 1);
        } else if (position == feedbackChannelRow) {
            MessagesController.getInstance(currentAccount).openByUserName("hqsh_db", NekoAboutActivity.this, 1);
        } else if (position == translationRow) {
            Browser.openUrl(getParentActivity(), "https://crowdin.com/project/NagramX");
        } else if (position == datacenterStatusRow) {
            presentFragment(new DatacenterActivity(0));
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
            if (holder.getItemViewType() == TYPE_SETTINGS) {
                TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                if (position == notificationChannelRow) {
                    textCell.setTextAndValue("黄昏通知频道", "@hqsh_dbtz", true);
                } else if (position == feedbackChannelRow) {
                    textCell.setTextAndValue("黄昏反馈频道", "@hqsh_db", true);
                } else if (position == translationRow) {
                    textCell.setTextAndValue(getString(R.string.TransSite), "Crowdin", true);
                } else if (position == datacenterStatusRow) {
                    textCell.setText(getString(R.string.DatacenterStatus), false);
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            return TYPE_SETTINGS;
        }
    }
}
