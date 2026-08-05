package ml.docilealligator.infinityforreddit.adapters.navigationdrawer;

import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.databinding.ItemNavDrawerMenuGroupTitleBinding;
import ml.docilealligator.infinityforreddit.databinding.ItemNavDrawerMenuItemBinding;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;

public class RedditSectionRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_MENU_GROUP_TITLE = 1;
    private static final int VIEW_TYPE_MENU_ITEM = 2;
    private static final int REDDIT_SECTION_ITEMS = 1;

    private final BaseActivity baseActivity;
    private final int primaryTextColor;
    private final int secondaryTextColor;
    private final int primaryIconColor;
    private boolean collapseRedditSection;
    private final NavigationDrawerRecyclerViewMergedAdapter.ItemClickListener itemClickListener;

    public RedditSectionRecyclerViewAdapter(BaseActivity baseActivity, CustomThemeWrapper customThemeWrapper,
                                            SharedPreferences navigationDrawerSharedPreferences,
                                            NavigationDrawerRecyclerViewMergedAdapter.ItemClickListener itemClickListener) {
        this.baseActivity = baseActivity;
        primaryTextColor = customThemeWrapper.getPrimaryTextColor();
        secondaryTextColor = customThemeWrapper.getSecondaryTextColor();
        primaryIconColor = customThemeWrapper.getPrimaryIconColor();
        collapseRedditSection = navigationDrawerSharedPreferences.getBoolean(SharedPreferencesUtils.COLLAPSE_REDDIT_SECTION, false);
        this.itemClickListener = itemClickListener;
    }

    public void setCollapseRedditSection(boolean collapseRedditSection) {
        this.collapseRedditSection = collapseRedditSection;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? VIEW_TYPE_MENU_GROUP_TITLE : VIEW_TYPE_MENU_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_MENU_GROUP_TITLE) {
            return new MenuGroupTitleViewHolder(ItemNavDrawerMenuGroupTitleBinding
                    .inflate(LayoutInflater.from(parent.getContext()), parent, false));
        } else {
            return new MenuItemViewHolder(ItemNavDrawerMenuItemBinding
                    .inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof MenuGroupTitleViewHolder) {
            ((MenuGroupTitleViewHolder) holder).binding.titleTextViewItemNavDrawerMenuGroupTitle.setText(R.string.label_reddit);
            if (collapseRedditSection) {
                ((MenuGroupTitleViewHolder) holder).binding.collapseIndicatorImageViewItemNavDrawerMenuGroupTitle.setImageResource(R.drawable.ic_baseline_arrow_drop_up_24dp);
            } else {
                ((MenuGroupTitleViewHolder) holder).binding.collapseIndicatorImageViewItemNavDrawerMenuGroupTitle.setImageResource(R.drawable.ic_baseline_arrow_drop_down_24dp);
            }

            holder.itemView.setOnClickListener(view -> {
                if (collapseRedditSection) {
                    collapseRedditSection = !collapseRedditSection;
                    notifyItemRangeInserted(holder.getBindingAdapterPosition() + 1, REDDIT_SECTION_ITEMS);
                } else {
                    collapseRedditSection = !collapseRedditSection;
                    notifyItemRangeRemoved(holder.getBindingAdapterPosition() + 1, REDDIT_SECTION_ITEMS);
                }
                notifyItemChanged(holder.getBindingAdapterPosition());
            });
        } else if (holder instanceof MenuItemViewHolder) {
            ((MenuItemViewHolder) holder).binding.textViewItemNavDrawerMenuItem.setText("TikTok Feed");
            ((MenuItemViewHolder) holder).binding.imageViewItemNavDrawerMenuItem.setImageDrawable(
                androidx.core.content.ContextCompat.getDrawable(baseActivity, R.drawable.ic_video_day_night_24dp));
            holder.itemView.setOnClickListener(view -> itemClickListener.onMenuClick(-100)); // Special ID for Reels
        }
    }

    @Override
    public int getItemCount() {
        return collapseRedditSection ? 1 : 1 + REDDIT_SECTION_ITEMS;
    }

    class MenuGroupTitleViewHolder extends RecyclerView.ViewHolder {
        ItemNavDrawerMenuGroupTitleBinding binding;

        MenuGroupTitleViewHolder(@NonNull ItemNavDrawerMenuGroupTitleBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            if (baseActivity.typeface != null) {
                binding.titleTextViewItemNavDrawerMenuGroupTitle.setTypeface(baseActivity.typeface);
            }
            binding.titleTextViewItemNavDrawerMenuGroupTitle.setTextColor(secondaryTextColor);
            binding.collapseIndicatorImageViewItemNavDrawerMenuGroupTitle.setColorFilter(secondaryTextColor, android.graphics.PorterDuff.Mode.SRC_IN);
        }
    }

    class MenuItemViewHolder extends RecyclerView.ViewHolder {
        ItemNavDrawerMenuItemBinding binding;

        MenuItemViewHolder(@NonNull ItemNavDrawerMenuItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            if (baseActivity.typeface != null) {
                binding.textViewItemNavDrawerMenuItem.setTypeface(baseActivity.typeface);
            }
            binding.textViewItemNavDrawerMenuItem.setTextColor(primaryTextColor);
            binding.imageViewItemNavDrawerMenuItem.setColorFilter(primaryIconColor, android.graphics.PorterDuff.Mode.SRC_IN);
        }
    }
}
