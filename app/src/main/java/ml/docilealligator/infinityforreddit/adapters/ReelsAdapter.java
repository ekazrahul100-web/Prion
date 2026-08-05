package ml.docilealligator.infinityforreddit.adapters;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.post.Post;


public class ReelsAdapter extends RecyclerView.Adapter<ReelsAdapter.ReelViewHolder> {

    public interface InteractionListener {
        void onUpvote(Post post, int position);
        void onDownvote(Post post, int position);
        void onComments(Post post);
        void onSave(Post post);
        void onShare(Post post);
    }

    private final Context context;
    private final InteractionListener listener;
    private final List<Post> posts = new ArrayList<>();
    
    public ReelsAdapter(Context context, InteractionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    private int currentPlayingPosition = -1;
    private final Map<Integer, ExoPlayer> players = new HashMap<>();

    public void addPosts(List<Post> newPosts) {
        int start = posts.size();
        posts.addAll(newPosts);
        notifyItemRangeInserted(start, newPosts.size());
    }

    @Nullable
    public Post getPostAt(int position) {
        if (position >= 0 && position < posts.size()) {
            return posts.get(position);
        }
        return null;
    }

    public void releasePlayers() {
        for (ExoPlayer player : players.values()) {
            if (player != null) {
                player.stop();
                player.release();
            }
        }
        players.clear();
        notifyDataSetChanged();
    }

    public void clear() {
        releasePlayers();
        posts.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ReelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_reel, parent, false);
        return new ReelViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReelViewHolder holder, int position) {
        Post post = posts.get(position);
        
        holder.titleText.setText(post.getTitle());
        holder.subredditText.setText("r/" + post.getSubredditName());
        holder.scoreText.setText(String.valueOf(post.getScore()));

        // We will manage playback separately when the page is selected
        holder.playerView.setPlayer(null);
        
        holder.setupGestures();
    }

    @Override
    public void onViewRecycled(@NonNull ReelViewHolder holder) {
        super.onViewRecycled(holder);
        holder.playerView.setPlayer(null);
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    public void playVideoAt(int position) {
        currentPlayingPosition = position;

        List<Integer> toRemove = new ArrayList<>();
        for (Integer pos : players.keySet()) {
            if (pos < position - 1 || pos > position + 1) {
                toRemove.add(pos);
            }
        }
        for (Integer pos : toRemove) {
            ExoPlayer p = players.remove(pos);
            if (p != null) {
                p.stop();
                p.release();
            }
        }

        for (int i = position - 1; i <= position + 1; i++) {
            if (i >= 0 && i < posts.size()) {
                if (!players.containsKey(i)) {
                    Post post = posts.get(i);
                    String url = post.getVideoUrl();
                    if (url == null || url.isEmpty()) {
                        url = post.getUrl();
                    }

                    ExoPlayer player = new ExoPlayer.Builder(context).build();
                    player.setRepeatMode(Player.REPEAT_MODE_ALL);

                    DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(context);
                    ProgressiveMediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(MediaItem.fromUri(Uri.parse(url)));

                    player.setMediaSource(mediaSource);
                    player.prepare();
                    player.setPlayWhenReady(i == position);
                    players.put(i, player);
                    notifyItemChanged(i);
                } else {
                    ExoPlayer p = players.get(i);
                    if (p != null) {
                        p.setPlayWhenReady(i == position);
                    }
                }
            }
        }
    }

    class ReelViewHolder extends RecyclerView.ViewHolder {
        PlayerView playerView;
        TextView titleText;
        TextView subredditText;
        TextView scoreText;
        ImageView likeAnimation;
        View upvoteButton;
        View downvoteButton;
        View commentsButton;
        View saveButton;
        View shareButton;

        public ReelViewHolder(@NonNull View itemView) {
            super(itemView);
            playerView = itemView.findViewById(R.id.player_view);
            titleText = itemView.findViewById(R.id.title_text);
            subredditText = itemView.findViewById(R.id.subreddit_text);
            scoreText = itemView.findViewById(R.id.score_text);
            likeAnimation = itemView.findViewById(R.id.like_animation);
            
            upvoteButton = itemView.findViewById(R.id.upvote_button);
            downvoteButton = itemView.findViewById(R.id.downvote_button);
            commentsButton = itemView.findViewById(R.id.comments_button);
            saveButton = itemView.findViewById(R.id.save_button);
            shareButton = itemView.findViewById(R.id.share_button);
        }

        public void setupGestures() {
            ExoPlayer player = players.get(getAdapterPosition());
            playerView.setPlayer(player);

            upvoteButton.setOnClickListener(v -> {
                if (getAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onUpvote(posts.get(getAdapterPosition()), getAdapterPosition());
                }
            });
            downvoteButton.setOnClickListener(v -> {
                if (getAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onDownvote(posts.get(getAdapterPosition()), getAdapterPosition());
                }
            });
            commentsButton.setOnClickListener(v -> {
                if (getAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onComments(posts.get(getAdapterPosition()));
                }
            });
            saveButton.setOnClickListener(v -> {
                if (getAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onSave(posts.get(getAdapterPosition()));
                }
            });
            shareButton.setOnClickListener(v -> {
                if (getAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onShare(posts.get(getAdapterPosition()));
                }
            });

            GestureDetector gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    showLikeAnimation();
                    if (getAdapterPosition() != RecyclerView.NO_POSITION) {
                        listener.onUpvote(posts.get(getAdapterPosition()), getAdapterPosition());
                    }
                    return true;
                }

                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    if (player != null && getAdapterPosition() == currentPlayingPosition) {
                        if (player.isPlaying()) {
                            player.pause();
                        } else {
                            player.play();
                        }
                    }
                    return true;
                }
            });

            playerView.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
        }

        private void showLikeAnimation() {
            likeAnimation.setVisibility(View.VISIBLE);
            likeAnimation.setScaleX(0f);
            likeAnimation.setScaleY(0f);
            likeAnimation.animate()
                    .scaleX(2f).scaleY(2f)
                    .alpha(0f)
                    .setDuration(500)
                    .withEndAction(() -> {
                        likeAnimation.setVisibility(View.GONE);
                        likeAnimation.setAlpha(1f);
                        likeAnimation.setScaleX(1f);
                        likeAnimation.setScaleY(1f);
                    })
                    .start();
        }
    }
}
