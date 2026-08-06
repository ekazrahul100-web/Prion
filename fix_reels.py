import re

with open("app/src/main/java/ml/docilealligator/infinityforreddit/activities/ReelsActivity.java", "r") as f:
    content = f.read()

# Add constants
content = content.replace("private boolean isNsfwMode = false;", """
    private static final int MODE_SFW = 0;
    private static final int MODE_SUBSCRIBED = 1;
    private static final int MODE_NSFW = 2;
    private int currentMode = MODE_SFW;
""")

# Add Subscribed variables
content = content.replace("private ReelsAdapter nsfwAdapter;", """private ReelsAdapter nsfwAdapter;
    private ReelsAdapter subscribedAdapter;""")
content = content.replace("private TextView nsfwTextView;", """private TextView nsfwTextView;
    private TextView subscribedTextView;""")
content = content.replace("private String nsfwAfter = null;", """private String nsfwAfter = null;
    @Nullable
    private String subscribedAfter = null;""")
content = content.replace("private int nsfwPosition = 0;", """private int nsfwPosition = 0;
    private int subscribedPosition = 0;""")

# Initialize Subscribed variables
content = content.replace("nsfwTextView = findViewById(R.id.nsfw_text_view);", """nsfwTextView = findViewById(R.id.nsfw_text_view);
        subscribedTextView = findViewById(R.id.subscribed_text_view);""")

content = content.replace("nsfwAdapter = new ReelsAdapter(this, listener);", """nsfwAdapter = new ReelsAdapter(this, listener);
        subscribedAdapter = new ReelsAdapter(this, listener);""")

# Update refresh button
content = content.replace("""        refreshButton.setOnClickListener(v -> {
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
        });""", """        refreshButton.setOnClickListener(v -> {
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
        });""")

# Update sfw and nsfw click listeners
content = content.replace("""        sfwTextView.setOnClickListener(v -> {
            if (isNsfwMode) {
                nsfwAdapter.releasePlayers();
                isNsfwMode = false;
                updateModeUI();
                viewPager.setAdapter(sfwAdapter);
                viewPager.setCurrentItem(sfwPosition, false);
                if (sfwAdapter.getItemCount() == 0) {
                    fetchVideos();
                } else {
                    sfwAdapter.playVideoAt(sfwPosition);
                }
            }
        });""", """        sfwTextView.setOnClickListener(v -> {
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
        });""")

content = content.replace("""        nsfwTextView.setOnClickListener(v -> {
            if (!isNsfwMode) {
                sfwAdapter.releasePlayers();
                isNsfwMode = true;
                updateModeUI();
                viewPager.setAdapter(nsfwAdapter);
                viewPager.setCurrentItem(nsfwPosition, false);
                if (nsfwAdapter.getItemCount() == 0) {
                    fetchVideos();
                } else {
                    nsfwAdapter.playVideoAt(nsfwPosition);
                }
            }
        });""", """        nsfwTextView.setOnClickListener(v -> {
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
        });""")

# Update ViewPager page change listener
content = content.replace("""            public void onPageSelected(int position) {
                super.onPageSelected(position);
                ReelsAdapter currentAdapter = isNsfwMode ? nsfwAdapter : sfwAdapter;
                if (isNsfwMode) nsfwPosition = position;
                else sfwPosition = position;""", """            public void onPageSelected(int position) {
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
                }""")

# Update initial viewPager adapter
content = content.replace("""        if (isNsfwMode) {
            viewPager.setAdapter(nsfwAdapter);
        } else {
            viewPager.setAdapter(sfwAdapter);
        }""", """        if (currentMode == MODE_NSFW) {
            viewPager.setAdapter(nsfwAdapter);
        } else if (currentMode == MODE_SUBSCRIBED) {
            viewPager.setAdapter(subscribedAdapter);
        } else {
            viewPager.setAdapter(sfwAdapter);
        }""")

# Update updateModeUI
content = content.replace("""    private void updateModeUI() {
        if (isNsfwMode) {
            sfwTextView.setTextColor(0x88ffffff);
            sfwTextView.setTypeface(null, android.graphics.Typeface.NORMAL);
            nsfwTextView.setTextColor(0xffffffff);
            nsfwTextView.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            sfwTextView.setTextColor(0xffffffff);
            sfwTextView.setTypeface(null, android.graphics.Typeface.BOLD);
            nsfwTextView.setTextColor(0x88ffffff);
            nsfwTextView.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
    }""", """    private void updateModeUI() {
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
    }""")

# Update fetchVideos
fetch_videos_new = """    private void fetchVideos() {
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
    }"""

# Use regex to replace fetchVideos
import re
content = re.sub(r'private void fetchVideos\(\) \{.*?(?=^\s*@Override)', fetch_videos_new + "\n\n", content, flags=re.DOTALL | re.MULTILINE)

content = content.replace("if (sfwAdapter != null) sfwAdapter.releasePlayers();", """if (sfwAdapter != null) sfwAdapter.releasePlayers();
        if (subscribedAdapter != null) subscribedAdapter.releasePlayers();""")

with open("app/src/main/java/ml/docilealligator/infinityforreddit/activities/ReelsActivity.java", "w") as f:
    f.write(content)
