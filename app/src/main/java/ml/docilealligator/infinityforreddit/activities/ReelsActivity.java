package ml.docilealligator.infinityforreddit.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import android.content.Intent;
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
import ml.docilealligator.infinityforreddit.readpost.ReadPostsList;
import ml.docilealligator.infinityforreddit.readpost.ReadPostsListInterface;
import ml.docilealligator.infinityforreddit.thing.SaveThing;
import ml.docilealligator.infinityforreddit.thing.SortType;
import ml.docilealligator.infinityforreddit.thing.VoteThing;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.utils.SeenPostsManager;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;

import androidx.appcompat.widget.SwitchCompat;
import android.widget.ImageView;

import retrofit2.Retrofit;

public class ReelsActivity extends BaseActivity {

    private static final String REELS_NAMESPACE = "reels";
    private static final String PREF_HIDE_SEEN_REELS = "hide_seen_posts_in_reels";
    private static final int DWELL_TIME_MS = 5000; // 5 seconds

    @Inject
    @Named("default")
    SharedPreferences mSharedPreferences;

    @Inject
    @Named("current_account")
    SharedPreferences mCurrentAccountSharedPreferences;

    @Inject
    CustomThemeWrapper mCustomThemeWrapper;

    @Inject
    @Named("oauth")
    Retrofit mOauthRetrofit;

    @Inject
    @Named("no_oauth")
    Retrofit mRetrofit;

    @Inject
    Executor mExecutor;

    @Inject
    RedditDataRoomDatabase mRedditDataRoomDatabase;

    private ViewPager2 viewPager;
    private ReelsAdapter sfwAdapter;
    private ReelsAdapter nsfwAdapter;
    private ReelsAdapter subscribedAdapter;
    private TextView sfwTextView;
    private TextView nsfwTextView;
    private TextView subscribedTextView;
    private SwitchCompat hideSeenToggle;
    private ImageView refreshButton;

    
    private static final int MODE_SFW = 0;
    private static final int MODE_SUBSCRIBED = 1;
    private static final int MODE_NSFW = 2;
    private int currentMode = MODE_SFW;

    @Nullable
    private String sfwAfter = null;
    @Nullable
    private String nsfwAfter = null;
    @Nullable
    private String subscribedAfter = null;
    private int sfwPosition = 0;
    private int nsfwPosition = 0;
    private int subscribedPosition = 0;
    private boolean isLoading = false;
    
    @Nullable
    private String mAccountName;
    @Nullable
    private String mAccessToken;

    private final Handler dwellHandler = new Handler(Looper.getMainLooper());
    @Nullable
    private Runnable dwellRunnable;
    private int currentPosition = -1;

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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reels);

        viewPager = findViewById(R.id.view_pager_reels);
        sfwTextView = findViewById(R.id.sfw_text_view);
        nsfwTextView = findViewById(R.id.nsfw_text_view);
        subscribedTextView = findViewById(R.id.subscribed_text_view);
        hideSeenToggle = findViewById(R.id.hide_seen_toggle);
        refreshButton = findViewById(R.id.refresh_button);
        
        // Toggle uses the same key that the filter checks
        hideSeenToggle.setChecked(mSharedPreferences.getBoolean(PREF_HIDE_SEEN_REELS, false));
        hideSeenToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mSharedPreferences.edit().putBoolean(PREF_HIDE_SEEN_REELS, isChecked).apply();
        });
        
        refreshButton.setOnClickListener(v -> {
            if (currentMode == MODE_NSFW) {
                nsfwAdapter.clear();
                nsfwAfter = null;
                nsfwPosition = 0;
            } else if (currentMode == MODE_SUBSCRIBED) {
                subscribedAdapter.clear();
                subscribedAfter = null;
                subscribedPosition = 0;
            } else {
                sfwAdapter.clear();
                sfwAfter = null;
                sfwPosition = 0;
            }
            fetchVideos();
        });

        mAccountName = mCurrentAccountSharedPreferences.getString(SharedPreferencesUtils.ACCOUNT_NAME, Account.ANONYMOUS_ACCOUNT);
        mAccessToken = mCurrentAccountSharedPreferences.getString(SharedPreferencesUtils.ACCESS_TOKEN, null);

        ReelsAdapter.InteractionListener listener = new ReelsAdapter.InteractionListener() {
            @Override
            public void onUpvote(Post post, int position) {
                if (mAccessToken == null) return;
                SeenPostsManager.markSeen(mSharedPreferences, post.getId(), REELS_NAMESPACE);
                VoteThing.voteThing(ReelsActivity.this, mOauthRetrofit, mAccessToken, new VoteThing.VoteThingListener() {
                    @Override
                    public void onVoteThingSuccess(int position1) {}
                    @Override
                    public void onVoteThingFail(int position1) {}
                }, post.getFullName(), APIUtils.DIR_UPVOTE, position);
            }

            @Override
            public void onDownvote(Post post, int position) {
                if (mAccessToken == null) return;
                SeenPostsManager.markSeen(mSharedPreferences, post.getId(), REELS_NAMESPACE);
                VoteThing.voteThing(ReelsActivity.this, mOauthRetrofit, mAccessToken, new VoteThing.VoteThingListener() {
                    @Override
                    public void onVoteThingSuccess(int position1) {}
                    @Override
                    public void onVoteThingFail(int position1) {}
                }, post.getFullName(), APIUtils.DIR_DOWNVOTE, position);
            }

            @Override
            public void onComments(Post post) {
                SeenPostsManager.markSeen(mSharedPreferences, post.getId(), REELS_NAMESPACE);
                Intent intent = new Intent(ReelsActivity.this, ViewPostDetailActivity.class);
                intent.putExtra(ViewPostDetailActivity.EXTRA_POST_DATA, post);
                startActivity(intent);
            }

            @Override
            public void onSave(Post post) {
                if (mAccessToken == null) return;
                SeenPostsManager.markSeen(mSharedPreferences, post.getId(), REELS_NAMESPACE);
                SaveThing.saveThing(mOauthRetrofit, mAccessToken, post.getFullName(), new SaveThing.SaveThingListener() {
                    @Override
                    public void success() {}
                    @Override
                    public void failed() {}
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
        };

        sfwAdapter = new ReelsAdapter(this, listener);
        nsfwAdapter = new ReelsAdapter(this, listener);
        subscribedAdapter = new ReelsAdapter(this, listener);
        
        viewPager.setAdapter(sfwAdapter);
        viewPager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);

        sfwTextView.setOnClickListener(v -> {
            if (currentMode != MODE_SFW) {
                ReelsAdapter oldAdapter = currentMode == MODE_NSFW ? nsfwAdapter : subscribedAdapter;
                oldAdapter.releasePlayers();
                currentMode = MODE_SFW;
                updateModeUI();
                viewPager.setAdapter(sfwAdapter);
                viewPager.setCurrentItem(sfwPosition, false);
                if (sfwAdapter.getItemCount() == 0) {
                    fetchVideos();
                } else {
                    sfwAdapter.playVideoAt(sfwPosition);
                }
            }
        });

        nsfwTextView.setOnClickListener(v -> {
            if (currentMode != MODE_NSFW) {
                ReelsAdapter oldAdapter = currentMode == MODE_SFW ? sfwAdapter : subscribedAdapter;
                oldAdapter.releasePlayers();
                currentMode = MODE_NSFW;
                updateModeUI();
                viewPager.setAdapter(nsfwAdapter);
                viewPager.setCurrentItem(nsfwPosition, false);
                if (nsfwAdapter.getItemCount() == 0) {
                    fetchVideos();
                } else {
                    nsfwAdapter.playVideoAt(nsfwPosition);
                }
            }
        });
        
        subscribedTextView.setOnClickListener(v -> {
            if (currentMode != MODE_SUBSCRIBED) {
                ReelsAdapter oldAdapter = currentMode == MODE_NSFW ? nsfwAdapter : sfwAdapter;
                oldAdapter.releasePlayers();
                currentMode = MODE_SUBSCRIBED;
                updateModeUI();
                viewPager.setAdapter(subscribedAdapter);
                viewPager.setCurrentItem(subscribedPosition, false);
                if (subscribedAdapter.getItemCount() == 0) {
                    fetchVideos();
                } else {
                    subscribedAdapter.playVideoAt(subscribedPosition);
                }
            }
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                ReelsAdapter currentAdapter;
                if (currentMode == MODE_NSFW) {
                    nsfwAdapter = nsfwAdapter;
                    currentAdapter = nsfwAdapter;
                    nsfwPosition = position;
                } else if (currentMode == MODE_SUBSCRIBED) {
                    currentAdapter = subscribedAdapter;
                    subscribedPosition = position;
                } else {
                    currentAdapter = sfwAdapter;
                    sfwPosition = position;
                }

                currentAdapter.playVideoAt(position);
                if (position >= currentAdapter.getItemCount() - 5 && !isLoading) {
                    fetchVideos();
                }

                // Cancel any previous dwell timer
                if (dwellRunnable != null) {
                    dwellHandler.removeCallbacks(dwellRunnable);
                }
                currentPosition = position;
                // Always mark after dwell — no toggle gating here.
                // The toggle gates FILTERING, not MARKING.
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

        if (currentMode == MODE_NSFW) {
            viewPager.setAdapter(nsfwAdapter);
        } else if (currentMode == MODE_SUBSCRIBED) {
            viewPager.setAdapter(subscribedAdapter);
        } else {
            viewPager.setAdapter(sfwAdapter);
        }
        fetchVideos();
    }

    private void updateModeUI() {
        sfwTextView.setTextColor(0x88ffffff);
        sfwTextView.setTypeface(null, android.graphics.Typeface.NORMAL);
        nsfwTextView.setTextColor(0x88ffffff);
        nsfwTextView.setTypeface(null, android.graphics.Typeface.NORMAL);
        subscribedTextView.setTextColor(0x88ffffff);
        subscribedTextView.setTypeface(null, android.graphics.Typeface.NORMAL);

        if (currentMode == MODE_NSFW) {
            nsfwTextView.setTextColor(0xffffffff);
            nsfwTextView.setTypeface(null, android.graphics.Typeface.BOLD);
        } else if (currentMode == MODE_SUBSCRIBED) {
            subscribedTextView.setTextColor(0xffffffff);
            subscribedTextView.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            sfwTextView.setTextColor(0xffffffff);
            sfwTextView.setTypeface(null, android.graphics.Typeface.BOLD);
        }
    }

        private void fetchVideos() {
        isLoading = true;
        
        final boolean fetchSubscribed = (currentMode == MODE_SUBSCRIBED);
        String subreddit;
        List<String> pool = new ArrayList<>();
        if (!fetchSubscribed) {
            if (currentMode == MODE_NSFW) {
                Collections.addAll(pool, NSFW_POOL);
            } else {
                Collections.addAll(pool, SFW_POOL);
            }
            Collections.shuffle(pool);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 20; i++) {
                sb.append(pool.get(i));
                if (i < 19) sb.append("+");
            }
            subreddit = sb.toString();
        } else {
            subreddit = "popular"; // Fallback for anonymous
        }
        
        String currentAfter = currentMode == MODE_NSFW ? nsfwAfter : (currentMode == MODE_SUBSCRIBED ? subscribedAfter : sfwAfter);

        String accountName = mCurrentAccountSharedPreferences.getString(SharedPreferencesUtils.ACCOUNT_NAME, Account.ANONYMOUS_ACCOUNT);
        if (accountName == null) accountName = Account.ANONYMOUS_ACCOUNT;
        RedditAPI api = Account.ANONYMOUS_ACCOUNT.equals(accountName) ? mRetrofit.create(RedditAPI.class) : mOauthRetrofit.create(RedditAPI.class);
        
        final String finalAccountName = accountName;
        final boolean hideSeenEnabled = mSharedPreferences.getBoolean(PREF_HIDE_SEEN_REELS, false);
        final String finalSubreddit = subreddit;
        
        mExecutor.execute(() -> {
            try {
                retrofit2.Response<String> response;
                if (fetchSubscribed) {
                    if (Account.ANONYMOUS_ACCOUNT.equals(finalAccountName)) {
                        response = api.getAnonymousFrontPageOrMultiredditPostsListenableFuture(finalSubreddit, SortType.Type.HOT, null, currentAfter, 100, APIUtils.getUserAgent(this)).get();
                    } else {
                        String accessToken = mCurrentAccountSharedPreferences.getString(SharedPreferencesUtils.ACCESS_TOKEN, null);
                        response = api.getBestPostsListenableFuture(SortType.Type.HOT, null, currentAfter, APIUtils.getOAuthHeader(accessToken)).get();
                    }
                } else {
                    if (Account.ANONYMOUS_ACCOUNT.equals(finalAccountName)) {
                        response = api.getAnonymousFrontPageOrMultiredditPostsListenableFuture(finalSubreddit, SortType.Type.HOT, null, currentAfter, 100, APIUtils.getUserAgent(this)).get();
                    } else {
                        String accessToken = mCurrentAccountSharedPreferences.getString(SharedPreferencesUtils.ACCESS_TOKEN, null);
                        response = api.getSubredditBestPostsOauthListenableFuture(finalSubreddit, SortType.Type.HOT, null, currentAfter, 100, APIUtils.getOAuthHeader(accessToken)).get();
                    }
                }
                
                if (response != null && response.isSuccessful() && response.body() != null) {
                    PostFilter filter = new PostFilter();
                    filter.allowNSFW = true;
                    filter.containVideoType = true;
                    filter.containGifType = true;
                    filter.containTextType = false;
                    filter.containImageType = false;
                    filter.containLinkType = false;
                    filter.containGalleryType = false;
                    
                    ReadPostsListInterface readList = NullReadPostsList.getInstance();
                    LinkedHashSet<Post> posts = ParsePost.parsePostsSync(response.body(), -1, filter, readList);
                    String newAfter = ParsePost.getLastItem(response.body());
                    if (currentMode == MODE_NSFW) nsfwAfter = newAfter;
                    else if (currentMode == MODE_SUBSCRIBED) subscribedAfter = newAfter;
                    else sfwAfter = newAfter;
                    
                    List<Post> videos = new ArrayList<>();
                    if (posts != null) {
                        for (Post p : posts) {
                            if (p.getPostType() == Post.VIDEO_TYPE || p.getPostType() == Post.GIF_TYPE) {
                                // Enforce NSFW-only when in NSFW mode
                                if (currentMode == MODE_NSFW && !p.isNSFW()) continue;
                                if (currentMode == MODE_SFW && p.isNSFW()) continue;
                                if (currentMode == MODE_SUBSCRIBED && p.isNSFW() && !mSharedPreferences.getBoolean(SharedPreferencesUtils.NSFW_BASE, false)) continue;
                                
                                // Filter out seen posts if toggle is on (uses reels namespace)
                                if (hideSeenEnabled) {
                                    if (SeenPostsManager.hasSeen(mSharedPreferences, p.getId(), REELS_NAMESPACE)) continue;
                                }
                                
                                videos.add(p);
                            }
                        }
                    }
                    
                    new Handler(Looper.getMainLooper()).post(() -> {
                        ReelsAdapter currentAdapter = currentMode == MODE_NSFW ? nsfwAdapter : (currentMode == MODE_SUBSCRIBED ? subscribedAdapter : sfwAdapter);
                        currentAdapter.addPosts(videos);
                        isLoading = false;
                        if (videos.isEmpty() && newAfter != null) {
                            fetchVideos(); // Fetch more if none were videos
                        } else if (currentAdapter.getItemCount() == videos.size() && videos.size() > 0) {
                            currentAdapter.playVideoAt(0);
                        }
                    });
                } else {
                    new Handler(Looper.getMainLooper()).post(() -> isLoading = false);
                }
            } catch (Exception e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() -> isLoading = false);
            }
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dwellRunnable != null) {
            dwellHandler.removeCallbacks(dwellRunnable);
        }
        if (sfwAdapter != null) sfwAdapter.releasePlayers();
        if (subscribedAdapter != null) subscribedAdapter.releasePlayers();
        if (nsfwAdapter != null) nsfwAdapter.releasePlayers();
    }

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
        // Fullscreen immersive mode for Reels
    }
}
