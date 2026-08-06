import re

with open("app/src/main/java/ml/docilealligator/infinityforreddit/activities/ReelsActivity.java", "r") as f:
    content = f.read()

# Add imports
content = content.replace("import android.widget.TextView;", """import android.widget.TextView;
import android.widget.ProgressBar;
import android.widget.PopupMenu;
import android.view.MenuItem;
import android.widget.LinearLayout;""")

# Add variables
content = content.replace("private ViewPager2 viewPager;", """private ViewPager2 viewPager;
    private ProgressBar progressBar;
    private LinearLayout modeSelectorContainer;
    private TextView currentModeTextView;""")

# Remove old TextViews
content = re.sub(r'private TextView sfwTextView;[\s\S]*?private TextView subscribedTextView;', '', content)

# Initialize new views
content = content.replace("viewPager = findViewById(R.id.view_pager_reels);", """viewPager = findViewById(R.id.view_pager_reels);
        progressBar = findViewById(R.id.reels_progress_bar);
        modeSelectorContainer = findViewById(R.id.mode_selector_container);
        currentModeTextView = findViewById(R.id.current_mode_text_view);""")

content = re.sub(r'sfwTextView = findViewById\(R\.id\.sfw_text_view\);[\s\S]*?subscribedTextView = findViewById\(R\.id\.subscribed_text_view\);', '', content)

# Set up dropdown menu
dropdown_setup = """        modeSelectorContainer.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(ReelsActivity.this, modeSelectorContainer);
            popup.getMenu().add(0, MODE_SFW, 0, "SFW");
            popup.getMenu().add(0, MODE_SUBSCRIBED, 1, "Subscribed");
            popup.getMenu().add(0, MODE_NSFW, 2, "NSFW");
            popup.setOnMenuItemClickListener(item -> {
                int newMode = item.getItemId();
                if (currentMode != newMode) {
                    ReelsAdapter oldAdapter = currentMode == MODE_NSFW ? nsfwAdapter : (currentMode == MODE_SUBSCRIBED ? subscribedAdapter : sfwAdapter);
                    oldAdapter.releasePlayers();
                    currentMode = newMode;
                    updateModeUI();
                    
                    ReelsAdapter newAdapter = currentMode == MODE_NSFW ? nsfwAdapter : (currentMode == MODE_SUBSCRIBED ? subscribedAdapter : sfwAdapter);
                    int newPosition = currentMode == MODE_NSFW ? nsfwPosition : (currentMode == MODE_SUBSCRIBED ? subscribedPosition : sfwPosition);
                    
                    viewPager.setAdapter(newAdapter);
                    viewPager.setCurrentItem(newPosition, false);
                    if (newAdapter.getItemCount() == 0) {
                        fetchVideos();
                    } else {
                        newAdapter.playVideoAt(newPosition);
                    }
                }
                return true;
            });
            popup.show();
        });"""

content = re.sub(r'sfwTextView\.setOnClickListener\([\s\S]*?subscribedTextView\.setOnClickListener\([\s\S]*?\}\);', dropdown_setup, content)

# Update updateModeUI
update_ui = """    private void updateModeUI() {
        if (currentMode == MODE_NSFW) {
            currentModeTextView.setText("NSFW");
        } else if (currentMode == MODE_SUBSCRIBED) {
            currentModeTextView.setText("Subscribed");
        } else {
            currentModeTextView.setText("SFW");
        }
    }"""
content = re.sub(r'private void updateModeUI\(\) \{[\s\S]*?\}', update_ui, content)

# Update isLoading logic
content = content.replace("isLoading = true;", """isLoading = true;
        new Handler(Looper.getMainLooper()).post(() -> progressBar.setVisibility(View.VISIBLE));""")

content = content.replace("isLoading = false;", """isLoading = false;
                        progressBar.setVisibility(View.GONE);""")

content = content.replace("new Handler(Looper.getMainLooper()).post(() -> isLoading = false);", """new Handler(Looper.getMainLooper()).post(() -> {
                        isLoading = false;
                        progressBar.setVisibility(View.GONE);
                    });""")

with open("app/src/main/java/ml/docilealligator/infinityforreddit/activities/ReelsActivity.java", "w") as f:
    f.write(content)
