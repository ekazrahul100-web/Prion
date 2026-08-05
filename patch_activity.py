import re

with open('/data/data/com.termux/files/home/Prion/app/src/main/java/ml/docilealligator/infinityforreddit/activities/ReelsActivity.java', 'r') as f:
    content = f.read()

# 1. Imports
imports = """import androidx.appcompat.widget.SwitchCompat;
import android.widget.ImageView;
import ml.docilealligator.infinityforreddit.readpost.ReadPostsList;
import ml.docilealligator.infinityforreddit.readpost.ReadPostsListInterface;
"""
content = content.replace("import ml.docilealligator.infinityforreddit.readpost.NullReadPostsList;", "import ml.docilealligator.infinityforreddit.readpost.NullReadPostsList;\n" + imports)

# 2. Add UI fields
fields_old = """    private TextView sfwTextView;
    private TextView nsfwTextView;

    private boolean isNsfwMode = false;"""

fields_new = """    private TextView sfwTextView;
    private TextView nsfwTextView;
    private SwitchCompat hideSeenToggle;
    private ImageView refreshButton;

    private boolean isNsfwMode = false;"""

content = content.replace(fields_old, fields_new)

# 3. FindViews
findviews_old = """        sfwTextView = findViewById(R.id.sfw_text_view);
        nsfwTextView = findViewById(R.id.nsfw_text_view);"""

findviews_new = """        sfwTextView = findViewById(R.id.sfw_text_view);
        nsfwTextView = findViewById(R.id.nsfw_text_view);
        hideSeenToggle = findViewById(R.id.hide_seen_toggle);
        refreshButton = findViewById(R.id.refresh_button);
        
        hideSeenToggle.setChecked(mSharedPreferences.getBoolean("hide_read_posts_in_reels", false));
        hideSeenToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mSharedPreferences.edit().putBoolean("hide_read_posts_in_reels", isChecked).apply();
        });
        
        refreshButton.setOnClickListener(v -> {
            if (isNsfwMode) {
                nsfwAdapter.clear();
                nsfwAfter = null;
                nsfwPosition = 0;
            } else {
                sfwAdapter.clear();
                sfwAfter = null;
                sfwPosition = 0;
            }
            fetchVideos();
        });"""

content = content.replace(findviews_old, findviews_new)

# 4. Fix filter types
filter_old = """                    filter.containVideoType = true;
                    filter.containGifType = true;
                    filter.containTextType = true;
                    filter.containImageType = true;
                    filter.containLinkType = true;
                    filter.containGalleryType = true;"""

filter_new = """                    filter.containVideoType = true;
                    filter.containGifType = true;
                    filter.containTextType = false;
                    filter.containImageType = false;
                    filter.containLinkType = false;
                    filter.containGalleryType = false;"""

content = content.replace(filter_old, filter_new)

# 5. Fix parsePostsSync call to pass real readPostsList if toggle is ON
parse_old = "LinkedHashSet<Post> posts = ParsePost.parsePostsSync(response.body(), -1, filter, NullReadPostsList.getInstance());"
parse_new = """
                    ReadPostsListInterface readList = NullReadPostsList.getInstance();
                    if (mSharedPreferences.getBoolean("hide_read_posts_in_reels", false)) {
                        readList = new ReadPostsList(ml.docilealligator.infinityforreddit.database.RedditDataRoomDatabase.getDatabase(ReelsActivity.this).readPostDao(), finalAccountName, ml.docilealligator.infinityforreddit.readpost.ReadPostType.INVALID);
                    }
                    LinkedHashSet<Post> posts = ParsePost.parsePostsSync(response.body(), -1, filter, readList);"""

content = content.replace(parse_old, parse_new)

with open('/data/data/com.termux/files/home/Prion/app/src/main/java/ml/docilealligator/infinityforreddit/activities/ReelsActivity.java', 'w') as f:
    f.write(content)
