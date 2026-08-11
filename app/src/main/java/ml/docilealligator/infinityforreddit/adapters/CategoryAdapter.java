package ml.docilealligator.infinityforreddit.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ml.docilealligator.infinityforreddit.R;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.ViewHolder> {

    public interface OnCategoryClickListener {
        void onCategoryClick(String categoryName);
    }

    public static class CategoryItem {
        public final String name;
        public final int count;

        public CategoryItem(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }

    private final List<CategoryItem> originalList;
    private final List<CategoryItem> filteredList;
    private final OnCategoryClickListener listener;

    public CategoryAdapter(List<CategoryItem> list, OnCategoryClickListener listener) {
        this.originalList = list;
        this.filteredList = new ArrayList<>(list);
        this.listener = listener;
    }

    public void filter(String query) {
        filteredList.clear();
        if (query == null || query.trim().isEmpty()) {
            filteredList.addAll(originalList);
        } else {
            String lowerQuery = query.trim().toLowerCase();
            for (CategoryItem item : originalList) {
                if (item.name.toLowerCase().contains(lowerQuery)) {
                    filteredList.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_picker, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CategoryItem item = filteredList.get(position);
        holder.nameTextView.setText(item.name);
        holder.countTextView.setText(item.count > 0 ? item.count + " subreddits" : "");
        holder.itemView.setOnClickListener(v -> listener.onCategoryClick(item.name));
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView nameTextView;
        TextView countTextView;

        ViewHolder(View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.category_item_name);
            countTextView = itemView.findViewById(R.id.category_item_count);
        }
    }
}
