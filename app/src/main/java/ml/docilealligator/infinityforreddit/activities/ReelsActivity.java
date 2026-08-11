package ml.docilealligator.infinityforreddit.activities;

import java.util.Map;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.VideoSize;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.viewpager2.widget.ViewPager2;
import androidx.appcompat.widget.SwitchCompat;
import android.widget.ImageView;
import android.widget.EditText;
import android.text.Editable;
import android.text.TextWatcher;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import ml.docilealligator.infinityforreddit.adapters.CategoryAdapter;
import ml.docilealligator.infinityforreddit.utils.NsfwCategoryManager;



import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.Executor;

import javax.inject.Inject;
import javax.inject.Named;

import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.adapters.ReelsAdapter;
import ml.docilealligator.infinityforreddit.apis.RedditAPI;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.post.ParsePost;
import ml.docilealligator.infinityforreddit.post.Post;
import ml.docilealligator.infinityforreddit.postfilter.PostFilter;
import ml.docilealligator.infinityforreddit.readpost.NullReadPostsList;
import ml.docilealligator.infinityforreddit.readpost.ReadPostsListInterface;
import ml.docilealligator.infinityforreddit.thing.SaveThing;
import ml.docilealligator.infinityforreddit.thing.SortType;
import ml.docilealligator.infinityforreddit.thing.VoteThing;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.utils.SeenPostsManager;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;

import androidx.media3.ui.AspectRatioFrameLayout;
import retrofit2.Retrofit;

public class ReelsActivity extends BaseActivity {

    // ── Intent extras ──────────────────────────────────────────────────────
    /** Optional: pass a subreddit name to lock Reels to that subreddit only. */
    public static final String EXTRA_SUBREDDIT_NAME = "reels_subreddit_name";

    // ── SharedPreferences keys (delegated to ReelsSettingsActivity) ─────
    private static final String PREF_HIDE_SEEN_REELS = ReelsSettingsActivity.PREF_HIDE_SEEN_REELS;
    private static final String REELS_NAMESPACE = "reels";

    // ── Dwell time to mark a video as "seen" (ms) ────────────────────────
    private static final int DWELL_TIME_MS = 5000;


    // ── Modes ─────────────────────────────────────────────────────────────
    private static final int MODE_SFW        = 0;
    private static final int MODE_SUBSCRIBED = 1;
    private static final int MODE_NSFW       = 2;

    // ── Injected dependencies ─────────────────────────────────────────────
    @Inject @Named("default")
    SharedPreferences mSharedPreferences;

    @Inject @Named("current_account")
    SharedPreferences mCurrentAccountSharedPreferences;

    @Inject
    CustomThemeWrapper mCustomThemeWrapper;

    @Inject @Named("oauth")
    Retrofit mOauthRetrofit;

    @Inject @Named("no_oauth")
    Retrofit mRetrofit;

    @Inject
    Executor mExecutor;

    @Inject
    RedditDataRoomDatabase mRedditDataRoomDatabase;

    // ── Views ─────────────────────────────────────────────────────────────
    private ViewPager2 viewPager;
    private ProgressBar progressBar;
    private LinearLayout modeSelectorContainer;
    private LinearLayout sortSelectorContainer;
    private TextView currentModeTextView;
    private TextView sortTypeTextView;
    private ImageView refreshButton;
    private ImageView reelsSettingsButton;
    private LinearLayout categorySelectorContainer;
    private TextView categoryTextView;


    public static final String PREF_NSFW_CATEGORY = "pref_reels_nsfw_category";



    // ── Adapters ──────────────────────────────────────────────────────────

    private ReelsAdapter sfwAdapter;
    private ReelsAdapter nsfwAdapter;
    private ReelsAdapter subscribedAdapter;

    // ── State ─────────────────────────────────────────────────────────────
    private int currentMode = MODE_SFW;

    @Nullable private String sfwAfter        = null;
    @Nullable private String nsfwAfter       = null;
    @Nullable private String subscribedAfter = null;
    private int sfwPosition        = 0;
    private int nsfwPosition       = 0;
    private int subscribedPosition = 0;
    private boolean isLoading = false;

    /** Current sort type. Default: HOT. */
    @NonNull private SortType.Type currentSortType = SortType.Type.HOT;
    /** Current time filter (only relevant for TOP and CONTROVERSIAL). */
    @Nullable private SortType.Time currentSortTime = null;

    /** If non-null, Reels is locked to this single subreddit (subreddit immersive mode). */
    @Nullable private String lockedSubreddit = null;

    /** Current landscape mode — one of ReelsSettingsActivity.LANDSCAPE_* constants. */
    private int landscapeMode = ReelsSettingsActivity.LANDSCAPE_AUTOROTATE;
    /** Whether HD quality is preferred (affects track selector cap). */
    private boolean preferHd = true;
    /** Whether to auto-advance to the next video when current ends. */
    private boolean autoAdvance = false;

    // ── Dwell tracking ────────────────────────────────────────────────────
    private final Handler dwellHandler = new Handler(Looper.getMainLooper());
    @Nullable private Runnable dwellRunnable;
    private int currentPosition = -1;

    // ── Subreddit pools ───────────────────────────────────────────────────
    private final String[] NSFW_POOL = {
            "nsfw_gif", "gonewild", "RealGirls", "asiansgonewild",
            "latinas", "thick", "petite", "curvy", "collegesluts",
            "nsfw_video", "holdthemoan", "pussy", "boobs", "ass",
            "thong", "milf", "amature", "tits", "nsfw",
            "legalteens", "barelylegal", "BustyPetite", "dirtypenpals",
            "nsfwcosplay", "nsfwoutfits", "PublicFlashing", "FlashingGirls",
            "gwpublic", "BigBoobsGW", "TittyDrop", "TinyTits",
            "PornGifs", "60fpsporn", "highresNSFW", "NSFW_HTML5",
            "gifsgonewild", "JizzToThis", "CumHaters", "FacialFun",
            "AmateurPorn", "Amateur", "AmateurArchives", "HomemadePorn"
    };

    private final String[] SFW_POOL = {
            "videos", "gifs", "TikTokCringe", "funny", "aww", "nextfuckinglevel",
            "PublicFreakout", "IdiotsInCars", "Unexpected", "AbruptChaos",
            "Whatcouldgowrong", "holdmybeer", "CatGifs", "dogpictures",
            "BeAmazed", "interestingasfuck", "nonononoyes", "yesyesyesyesno",
            "MadeMeSmile", "dankvideos", "CrazyFuckingVideos", "woahdude",
            "WatchPeopleDieInside", "AnimalsBeingDerps", "oddlysatisfying"
    };

    // ─────────────────────────────────────────────────────────────────────
    // onCreate
    // ─────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reels);

        // Bind views
        viewPager               = findViewById(R.id.view_pager_reels);
        progressBar             = findViewById(R.id.reels_progress_bar);
        modeSelectorContainer   = findViewById(R.id.mode_selector_container);
        sortSelectorContainer   = findViewById(R.id.sort_selector_container);
        currentModeTextView     = findViewById(R.id.current_mode_text_view);
        sortTypeTextView        = findViewById(R.id.sort_type_text_view);
        refreshButton           = findViewById(R.id.refresh_button);
        reelsSettingsButton     = findViewById(R.id.reels_settings_button);
        categorySelectorContainer = findViewById(R.id.category_selector_container);
        categoryTextView          = findViewById(R.id.category_text_view);

        categorySelectorContainer.setOnClickListener(v -> showCategoryPopup());

        // Read settings from ReelsSettingsActivity prefs
        applySettingsFromPrefs();

        // Subreddit-locked mode from Intent
        lockedSubreddit = getIntent().getStringExtra(EXTRA_SUBREDDIT_NAME);
        if (lockedSubreddit != null) {
            // Hide mode selector and sort center chip – subreddit mode uses its own label
            modeSelectorContainer.setVisibility(View.INVISIBLE);
            sortSelectorContainer.setVisibility(View.VISIBLE);
        }


        // Refresh button
        refreshButton.setOnClickListener(v -> {
            clearCurrentAdapter();
            fetchVideos();
        });

        // Settings button → ReelsSettingsActivity
        reelsSettingsButton.setOnClickListener(v ->
                startActivity(new Intent(this, ReelsSettingsActivity.class)));

        // Build the shared InteractionListener
        ReelsAdapter.InteractionListener listener = buildInteractionListener();

        sfwAdapter        = new ReelsAdapter(this, listener);
        nsfwAdapter       = new ReelsAdapter(this, listener);
        subscribedAdapter = new ReelsAdapter(this, listener);

        viewPager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        viewPager.setAdapter(sfwAdapter);

        // Mode selector dropdown
        modeSelectorContainer.setOnClickListener(v -> showModePopup());

        // Sort selector bottom sheet
        sortSelectorContainer.setOnClickListener(v -> showSortPopup());

        // Page-change callback
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                ReelsAdapter currentAdapter = getCurrentAdapter();

                if (currentMode == MODE_NSFW)             nsfwPosition = position;
                else if (currentMode == MODE_SUBSCRIBED)  subscribedPosition = position;
                else                                       sfwPosition = position;

                currentAdapter.playVideoAt(position);

                // Apply landscape mode for current video
                detectAndApplyLandscapeMode(currentAdapter, position);

                // Auto-advance: attach listener to jump when video ends
                if (autoAdvance) attachAutoAdvanceListener(currentAdapter, position);

                if (position >= currentAdapter.getItemCount() - 5 && !isLoading) {
                    fetchVideos();
                }

                // Dwell timer → mark as seen
                if (dwellRunnable != null) dwellHandler.removeCallbacks(dwellRunnable);
                currentPosition = position;
                dwellRunnable = () -> {
                    if (currentPosition == position) {
                        Post p = currentAdapter.getPostAt(position);
                        if (p != null) {
                            SeenPostsManager.markSeen(mSharedPreferences, p.getId(), REELS_NAMESPACE);
                        }
                    }
                };
                dwellHandler.postDelayed(dwellRunnable, DWELL_TIME_MS);
            }
        });

        fetchVideos();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Settings helpers
    // ─────────────────────────────────────────────────────────────────────

    private void applySettingsFromPrefs() {
        landscapeMode = mSharedPreferences.getInt(
                ReelsSettingsActivity.PREF_LANDSCAPE_MODE,
                ReelsSettingsActivity.LANDSCAPE_AUTOROTATE);
        preferHd    = mSharedPreferences.getBoolean(ReelsSettingsActivity.PREF_QUALITY_HD, true);
        autoAdvance = mSharedPreferences.getBoolean(ReelsSettingsActivity.PREF_AUTO_ADVANCE, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-read settings in case the user changed them in ReelsSettingsActivity
        applySettingsFromPrefs();
        // Re-apply landscape mode to the currently visible video
        ReelsAdapter currentAdapter = getCurrentAdapter();
        int pos = getCurrentPosition();
        if (currentAdapter.getItemCount() > 0) {
            detectAndApplyLandscapeMode(currentAdapter, pos);
        }
    }



    /**
     * Called whenever a new video page is shown. Applies the user's chosen
     * landscape mode: Default (nothing), Autorotate (seamless rotation),
     * or Fill (zoom-crop in portrait).
     */
    private void detectAndApplyLandscapeMode(ReelsAdapter adapter, int position) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Post post = adapter.getPostAt(position);
            if (post == null) return;

            int width = 0, height = 0;
            java.util.ArrayList<Post.Preview> previews = post.getPreviews();
            if (previews != null && !previews.isEmpty()) {
                width  = previews.get(0).getPreviewWidth();
                height = previews.get(0).getPreviewHeight();
            }
            boolean isLandscapeVideo = (width > 0 && height > 0 && width > height);

            switch (landscapeMode) {
                case ReelsSettingsActivity.LANDSCAPE_AUTOROTATE:
                    // Reset fill mode on adapter (use fit, not zoom)
                    adapter.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT, position);
                    if (isLandscapeVideo) {
                        rotateToOrientation(true);
                    } else {
                        rotateToOrientation(false);
                    }
                    break;

                case ReelsSettingsActivity.LANDSCAPE_FILLIN:
                    // Stay portrait; zoom-crop to fill the screen
                    rotateToOrientation(false);
                    adapter.setResizeMode(
                            isLandscapeVideo
                                    ? AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    : AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
                            position);
                    break;

                case ReelsSettingsActivity.LANDSCAPE_DEFAULT:
                default:
                    // Portrait, no changes — video shows with black bars
                    rotateToOrientation(false);
                    adapter.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH, position);
                    break;
            }
        }, 350);
    }

    /**
     * Rotates the screen instantly (no animation) to the given orientation.
     */
    private void rotateToOrientation(boolean landscape) {
        int target = landscape
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;

        int currentOrientation = getResources().getConfiguration().orientation;
        boolean alreadyLandscape = (currentOrientation == Configuration.ORIENTATION_LANDSCAPE);
        if (landscape == alreadyLandscape) return;

        // Suppress rotation animation
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        overridePendingTransition(0, 0);
        setRequestedOrientation(target);
        new Handler(Looper.getMainLooper()).postDelayed(() ->
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE), 400);
    }

    /**
     * Attach an auto-advance listener: when the video at [position] ends,
     * smoothly scroll to the next page.
     */
    private void attachAutoAdvanceListener(ReelsAdapter adapter, int position) {
        adapter.setAutoAdvanceListener(position, () -> {
            if (position + 1 < adapter.getItemCount()) {
                viewPager.setCurrentItem(position + 1, true);
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Mode selector popup
    // ─────────────────────────────────────────────────────────────────────

    private void showModePopup() {
        PopupMenu popup = new PopupMenu(this, modeSelectorContainer);
        popup.getMenu().add(0, MODE_SFW,        0, "SFW");
        popup.getMenu().add(0, MODE_SUBSCRIBED, 1, "Subscribed");
        popup.getMenu().add(0, MODE_NSFW,       2, "NSFW");
        popup.setOnMenuItemClickListener(item -> {
            int newMode = item.getItemId();
            if (currentMode == newMode) return true;
            getCurrentAdapter().releasePlayers();
            currentMode = newMode;
            updateModeUI();
            // Reset sort to HOT when switching modes (keeps it simple)
            currentSortType = SortType.Type.HOT;
            currentSortTime = null;
            updateSortUI();
            ReelsAdapter newAdapter = getCurrentAdapter();
            int newPos = getCurrentPosition();
            viewPager.setAdapter(newAdapter);
            viewPager.setCurrentItem(newPos, false);
            if (newAdapter.getItemCount() == 0) {
                fetchVideos();
            } else {
                newAdapter.playVideoAt(newPos);
            }
            return true;
        });
        popup.show();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Sort selector popup (two-step: type → time if needed)
    // ─────────────────────────────────────────────────────────────────────

    private void showSortPopup() {
        PopupMenu popup = new PopupMenu(this, sortSelectorContainer);
        popup.getMenu().add(0, 0, 0, "Hot");
        popup.getMenu().add(0, 1, 1, "New");
        popup.getMenu().add(0, 2, 2, "Rising");
        popup.getMenu().add(0, 3, 3, "Top…");
        popup.getMenu().add(0, 4, 4, "Controversial…");
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0: applySortType(SortType.Type.HOT,          null);         return true;
                case 1: applySortType(SortType.Type.NEW,          null);         return true;
                case 2: applySortType(SortType.Type.RISING,       null);         return true;
                case 3: showTimePopup(SortType.Type.TOP);                        return true;
                case 4: showTimePopup(SortType.Type.CONTROVERSIAL);              return true;
                default: return false;
            }
        });
        popup.show();
    }

    private void showTimePopup(SortType.Type sortType) {
        PopupMenu popup = new PopupMenu(this, sortSelectorContainer);
        popup.getMenu().add(0, 0, 0, "Past hour");
        popup.getMenu().add(0, 1, 1, "Past 24 hours");
        popup.getMenu().add(0, 2, 2, "Past week");
        popup.getMenu().add(0, 3, 3, "Past month");
        popup.getMenu().add(0, 4, 4, "Past year");
        popup.getMenu().add(0, 5, 5, "All time");
        popup.setOnMenuItemClickListener(item -> {
            SortType.Time time;
            switch (item.getItemId()) {
                case 0:  time = SortType.Time.HOUR;  break;
                case 1:  time = SortType.Time.DAY;   break;
                case 2:  time = SortType.Time.WEEK;  break;
                case 3:  time = SortType.Time.MONTH; break;
                case 4:  time = SortType.Time.YEAR;  break;
                default: time = SortType.Time.ALL;   break;
            }
            applySortType(sortType, time);
            return true;
        });
        popup.show();
    }

    private void applySortType(SortType.Type type, @Nullable SortType.Time time) {
        currentSortType = type;
        currentSortTime = time;
        updateSortUI();
        // Clear current adapter and re-fetch with new sort
        clearCurrentAdapter();
        fetchVideos();
    }

    // ─────────────────────────────────────────────────────────────────────
    // UI helpers
    // ─────────────────────────────────────────────────────────────────────

    private void showCategoryPopup() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_category_picker, null);
        dialog.setContentView(dialogView);

        EditText searchEditText = dialogView.findViewById(R.id.search_category_edit_text);
        RecyclerView recyclerView = dialogView.findViewById(R.id.categories_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        Map<String, List<String>> map = NsfwCategoryManager.loadCategories(this, mSharedPreferences);
        List<CategoryAdapter.CategoryItem> items = new ArrayList<>();
        items.add(new CategoryAdapter.CategoryItem("All NSFW", NsfwCategoryManager.getAllSubreddits(map).size()));

        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            items.add(new CategoryAdapter.CategoryItem(entry.getKey(), entry.getValue().size()));
        }

        CategoryAdapter adapter = new CategoryAdapter(items, categoryName -> {
            dialog.dismiss();
            mSharedPreferences.edit().putString(NsfwCategoryManager.PREF_SELECTED_CATEGORY_NAME, categoryName).apply();
            updateModeUI();
            clearCurrentAdapter();
            fetchVideos();
        });

        recyclerView.setAdapter(adapter);

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s != null ? s.toString() : "");
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        dialog.show();
    }

    private void updateModeUI() {
        if (currentMode == MODE_NSFW) {
            currentModeTextView.setText("NSFW");
            if (lockedSubreddit == null) {
                categorySelectorContainer.setVisibility(View.VISIBLE);
                String selectedCategory = mSharedPreferences.getString(NsfwCategoryManager.PREF_SELECTED_CATEGORY_NAME, "All NSFW");
                categoryTextView.setText(selectedCategory);
            }
        } else {
            if (currentMode == MODE_SUBSCRIBED) currentModeTextView.setText("Subscribed");
            else currentModeTextView.setText("SFW");
            categorySelectorContainer.setVisibility(View.GONE);
        }
    }



    private void updateSortUI() {
        String label = currentSortType.fullName;
        if (currentSortTime != null) label += " · " + currentSortTime.fullName;
        sortTypeTextView.setText(label);
    }

    private ReelsAdapter getCurrentAdapter() {
        if (currentMode == MODE_NSFW)            return nsfwAdapter;
        if (currentMode == MODE_SUBSCRIBED)      return subscribedAdapter;
        return sfwAdapter;
    }

    private int getCurrentPosition() {
        if (currentMode == MODE_NSFW)            return nsfwPosition;
        if (currentMode == MODE_SUBSCRIBED)      return subscribedPosition;
        return sfwPosition;
    }

    private void clearCurrentAdapter() {
        getCurrentAdapter().clear();
        if (currentMode == MODE_NSFW)            nsfwAfter = null;
        else if (currentMode == MODE_SUBSCRIBED) subscribedAfter = null;
        else                                     sfwAfter = null;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Interaction listener
    // ─────────────────────────────────────────────────────────────────────

    private ReelsAdapter.InteractionListener buildInteractionListener() {
        return new ReelsAdapter.InteractionListener() {
            @Override
            public void onUpvote(Post post, int position) {
                String accessToken = mCurrentAccountSharedPreferences.getString(
                        SharedPreferencesUtils.ACCESS_TOKEN, null);
                if (accessToken == null) return;
                SeenPostsManager.markSeen(mSharedPreferences, post.getId(), REELS_NAMESPACE);
                VoteThing.voteThing(ReelsActivity.this, mOauthRetrofit, accessToken,
                        new VoteThing.VoteThingListener() {
                            @Override public void onVoteThingSuccess(int p) {}
                            @Override public void onVoteThingFail(int p) {}
                        }, post.getFullName(), APIUtils.DIR_UPVOTE, position);
            }

            @Override
            public void onDownvote(Post post, int position) {
                String accessToken = mCurrentAccountSharedPreferences.getString(
                        SharedPreferencesUtils.ACCESS_TOKEN, null);
                if (accessToken == null) return;
                SeenPostsManager.markSeen(mSharedPreferences, post.getId(), REELS_NAMESPACE);
                VoteThing.voteThing(ReelsActivity.this, mOauthRetrofit, accessToken,
                        new VoteThing.VoteThingListener() {
                            @Override public void onVoteThingSuccess(int p) {}
                            @Override public void onVoteThingFail(int p) {}
                        }, post.getFullName(), APIUtils.DIR_DOWNVOTE, position);
            }

            @Override
            public void onComments(Post post) {
                openPostDetail(post);
            }

            @Override
            public void onSave(Post post) {
                String accessToken = mCurrentAccountSharedPreferences.getString(
                        SharedPreferencesUtils.ACCESS_TOKEN, null);
                if (accessToken == null) return;
                SeenPostsManager.markSeen(mSharedPreferences, post.getId(), REELS_NAMESPACE);
                SaveThing.saveThing(mOauthRetrofit, accessToken, post.getFullName(),
                        new SaveThing.SaveThingListener() {
                            @Override public void success() {}
                            @Override public void failed() {}
                        });
            }

            @Override
            public void onShare(Post post) {
                SeenPostsManager.markSeen(mSharedPreferences, post.getId(), REELS_NAMESPACE);
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, post.getTitle());
                shareIntent.putExtra(Intent.EXTRA_TEXT, post.getPermalink());
                startActivity(Intent.createChooser(shareIntent, "Share"));
            }

            @Override
            public void onOpenPost(Post post) {
                SeenPostsManager.markSeen(mSharedPreferences, post.getId(), REELS_NAMESPACE);
                openPostDetail(post);
            }
        };
    }

    private void openPostDetail(Post post) {
        Intent intent = new Intent(this, ViewPostDetailActivity.class);
        intent.putExtra(ViewPostDetailActivity.EXTRA_POST_DATA, post);
        startActivity(intent);
        // Slide in from the right — natural "swipe left → open post" feel
        overridePendingTransition(R.anim.enter_from_right, R.anim.exit_to_left);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Fetch videos
    // ─────────────────────────────────────────────────────────────────────

    private void fetchVideos() {
        isLoading = true;
        new Handler(Looper.getMainLooper()).post(() -> progressBar.setVisibility(View.VISIBLE));

        final boolean fetchSubscribed = (currentMode == MODE_SUBSCRIBED);
        final String subreddit;

        if (lockedSubreddit != null) {
            // Subreddit immersive mode — always use this subreddit
            subreddit = lockedSubreddit;
        } else if (!fetchSubscribed) {
            List<String> pool = new ArrayList<>();
            if (currentMode == MODE_NSFW) {
                Map<String, List<String>> categoriesMap = NsfwCategoryManager.loadCategories(this, mSharedPreferences);
                String selectedCategory = mSharedPreferences.getString(NsfwCategoryManager.PREF_SELECTED_CATEGORY_NAME, "All NSFW");
                List<String> categorySubs;
                if (selectedCategory == null || selectedCategory.equals("All NSFW") || !categoriesMap.containsKey(selectedCategory)) {
                    categorySubs = NsfwCategoryManager.getAllSubreddits(categoriesMap);
                } else {

                    categorySubs = categoriesMap.get(selectedCategory);
                }
                if (categorySubs != null && !categorySubs.isEmpty()) {
                    pool.addAll(categorySubs);
                } else {
                    Collections.addAll(pool, NSFW_POOL);
                }
            } else {
                Collections.addAll(pool, SFW_POOL);
            }


            Collections.shuffle(pool);
            int take = Math.min(20, pool.size());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < take; i++) {
                sb.append(pool.get(i));
                if (i < take - 1) sb.append("+");
            }
            subreddit = sb.toString();
        } else {
            subreddit = "popular"; // Fallback for anonymous subscribed feed
        }

        String currentAfter = currentMode == MODE_NSFW ? nsfwAfter
                : (currentMode == MODE_SUBSCRIBED ? subscribedAfter : sfwAfter);

        String accountName = mCurrentAccountSharedPreferences.getString(
                SharedPreferencesUtils.ACCOUNT_NAME, Account.ANONYMOUS_ACCOUNT);
        if (accountName == null) accountName = Account.ANONYMOUS_ACCOUNT;

        RedditAPI api = Account.ANONYMOUS_ACCOUNT.equals(accountName)
                ? mRetrofit.create(RedditAPI.class)
                : mOauthRetrofit.create(RedditAPI.class);

        final String finalAccountName  = accountName;
        final String finalSubreddit    = subreddit;
        final boolean hideSeenEnabled  = mSharedPreferences.getBoolean(PREF_HIDE_SEEN_REELS, false);
        final int capturedMode         = currentMode;
        final SortType.Type sortType   = currentSortType;
        final SortType.Time sortTime   = currentSortTime;
        // Capture before the background thread so we know if this is the first batch
        final int countBeforeAdd       = getCurrentAdapter().getItemCount();

        mExecutor.execute(() -> {
            try {
                retrofit2.Response<String> response;

                String accessToken = mCurrentAccountSharedPreferences.getString(
                        SharedPreferencesUtils.ACCESS_TOKEN, null);
                Map<String, String> oauthHeaders = APIUtils.getOAuthHeader(accessToken);

                if (fetchSubscribed && !Account.ANONYMOUS_ACCOUNT.equals(finalAccountName)) {
                    response = api.getBestPostsListenableFuture(
                            sortType, sortTime, currentAfter, oauthHeaders).get();
                } else if (!Account.ANONYMOUS_ACCOUNT.equals(finalAccountName)) {
                    response = api.getSubredditBestPostsOauthListenableFuture(
                            finalSubreddit, sortType, sortTime, currentAfter, 100,
                            APIUtils.getOAuthHeader(accessToken)).get();
                } else {
                    response = api.getAnonymousFrontPageOrMultiredditPostsListenableFuture(
                            finalSubreddit, sortType, sortTime, currentAfter, 100,
                            APIUtils.getUserAgent(ReelsActivity.this)).get();
                }

                if (response != null && response.isSuccessful() && response.body() != null) {
                    PostFilter filter = new PostFilter();
                    filter.allowNSFW        = true;
                    filter.containVideoType = true;
                    filter.containGifType   = true;
                    filter.containTextType  = false;
                    filter.containImageType = false;
                    filter.containLinkType  = false;
                    filter.containGalleryType = false;

                    ReadPostsListInterface readList = NullReadPostsList.getInstance();
                    LinkedHashSet<Post> posts = ParsePost.parsePostsSync(response.body(), -1, filter, readList);
                    String newAfter = ParsePost.getLastItem(response.body());

                    // Store pagination cursor
                    if (capturedMode == MODE_NSFW)            nsfwAfter = newAfter;
                    else if (capturedMode == MODE_SUBSCRIBED) subscribedAfter = newAfter;
                    else                                       sfwAfter = newAfter;

                    List<Post> videos = new ArrayList<>();
                    if (posts != null) {
                        for (Post p : posts) {
                            if (p.getPostType() != Post.VIDEO_TYPE && p.getPostType() != Post.GIF_TYPE) continue;

                            // In subreddit-locked (immersive) mode, skip the SFW/NSFW filter —
                            // the user explicitly chose this subreddit so show all its video content.
                            if (lockedSubreddit == null) {
                                // NSFW mode: only NSFW posts
                                if (capturedMode == MODE_NSFW && !p.isNSFW()) continue;
                                // SFW mode: no NSFW posts
                                if (capturedMode == MODE_SFW && p.isNSFW()) continue;
                                // Subscribed mode: respect per-account NSFW setting
                                if (capturedMode == MODE_SUBSCRIBED && p.isNSFW()) {
                                    SharedPreferences nsfwPrefs = getSharedPreferences(
                                            SharedPreferencesUtils.NSFW_AND_SPOILER_SHARED_PREFERENCES_FILE,
                                            MODE_PRIVATE);
                                    boolean nsfwAllowed = nsfwPrefs.getBoolean(
                                            finalAccountName + SharedPreferencesUtils.NSFW_BASE, false);
                                    if (!nsfwAllowed) continue;
                                }
                            }

                            // Hide-seen filter
                            if (hideSeenEnabled &&
                                    SeenPostsManager.hasSeen(mSharedPreferences, p.getId(), REELS_NAMESPACE)) continue;

                            videos.add(p);
                        }
                    }

                    new Handler(Looper.getMainLooper()).post(() -> {
                        ReelsAdapter currentAdapter = getCurrentAdapter();
                        currentAdapter.addPosts(videos);
                        isLoading = false;
                        progressBar.setVisibility(View.GONE);

                        if (videos.isEmpty() && newAfter != null) {
                            // No matching videos on this page — fetch the next page
                            fetchVideos();
                        } else if (countBeforeAdd == 0 && !videos.isEmpty()) {
                            // First batch — kick off playback at position 0
                            currentAdapter.playVideoAt(0);
                        }
                    });
                } else {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        isLoading = false;
                        progressBar.setVisibility(View.GONE);
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() -> {
                    isLoading = false;
                    progressBar.setVisibility(View.GONE);
                });
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Activity handles config changes itself — no restart needed.
        // This is intentionally a no-op; the activity continues normally.
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dwellRunnable != null) dwellHandler.removeCallbacks(dwellRunnable);
        if (sfwAdapter        != null) sfwAdapter.releasePlayers();
        if (subscribedAdapter != null) subscribedAdapter.releasePlayers();
        if (nsfwAdapter       != null) nsfwAdapter.releasePlayers();
    }

    // ─────────────────────────────────────────────────────────────────────
    // BaseActivity abstract implementations
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public SharedPreferences getDefaultSharedPreferences() {
        return mSharedPreferences;
    }

    @Override
    public SharedPreferences getCurrentAccountSharedPreferences() {
        return mCurrentAccountSharedPreferences;
    }

    @Override
    public CustomThemeWrapper getCustomThemeWrapper() {
        return mCustomThemeWrapper;
    }

    @Override
    protected void applyCustomTheme() {
        // Fullscreen immersive — no theme chrome needed
    }
}
