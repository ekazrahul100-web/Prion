import re

with open('/data/data/com.termux/files/home/Prion/app/src/main/java/ml/docilealligator/infinityforreddit/adapters/ReelsAdapter.java', 'r') as f:
    content = f.read()

# Add imports
content = content.replace("import androidx.recyclerview.widget.RecyclerView;", "import androidx.recyclerview.widget.RecyclerView;\nimport androidx.core.content.ContextCompat;\nimport ml.docilealligator.infinityforreddit.utils.APIUtils;")

# Add logic to onBindViewHolder
bind_view_holder = """        holder.titleText.setText(post.getTitle());
        holder.subredditText.setText("r/" + post.getSubredditName());
        holder.scoreText.setText(String.valueOf(post.getScore()));
        
        holder.upvoteButton.setColorFilter(post.getVoteType() == APIUtils.DIR_UPVOTE ? ContextCompat.getColor(context, R.color.upvoted) : ContextCompat.getColor(context, android.R.color.white), android.graphics.PorterDuff.Mode.SRC_IN);
        holder.downvoteButton.setColorFilter(post.getVoteType() == APIUtils.DIR_DOWNVOTE ? ContextCompat.getColor(context, R.color.downvoted) : ContextCompat.getColor(context, android.R.color.white), android.graphics.PorterDuff.Mode.SRC_IN);
        holder.saveButton.setColorFilter(post.isSaved() ? ContextCompat.getColor(context, R.color.saved) : ContextCompat.getColor(context, android.R.color.white), android.graphics.PorterDuff.Mode.SRC_IN);
"""

content = re.sub(r'        holder\.titleText\.setText\(post\.getTitle\(\)\);\n        holder\.subredditText\.setText\("r/" \+ post\.getSubredditName\(\)\);\n        holder\.scoreText\.setText\(String\.valueOf\(post\.getScore\(\)\)\);', bind_view_holder, content)

# Fix setupGestures
gestures_old = """            upvoteButton.setOnClickListener(v -> {
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
            });"""

gestures_new = """            upvoteButton.setOnClickListener(v -> {
                if (getAdapterPosition() != RecyclerView.NO_POSITION) {
                    Post post = posts.get(getAdapterPosition());
                    if (post.getVoteType() == APIUtils.DIR_UPVOTE) {
                        post.setVoteType(0);
                        post.setScore(post.getScore() - 1);
                    } else {
                        post.setScore(post.getScore() + (post.getVoteType() == APIUtils.DIR_DOWNVOTE ? 2 : 1));
                        post.setVoteType(APIUtils.DIR_UPVOTE);
                    }
                    notifyItemChanged(getAdapterPosition());
                    listener.onUpvote(post, getAdapterPosition());
                }
            });
            downvoteButton.setOnClickListener(v -> {
                if (getAdapterPosition() != RecyclerView.NO_POSITION) {
                    Post post = posts.get(getAdapterPosition());
                    if (post.getVoteType() == APIUtils.DIR_DOWNVOTE) {
                        post.setVoteType(0);
                        post.setScore(post.getScore() + 1);
                    } else {
                        post.setScore(post.getScore() - (post.getVoteType() == APIUtils.DIR_UPVOTE ? 2 : 1));
                        post.setVoteType(APIUtils.DIR_DOWNVOTE);
                    }
                    notifyItemChanged(getAdapterPosition());
                    listener.onDownvote(post, getAdapterPosition());
                }
            });
            commentsButton.setOnClickListener(v -> {
                if (getAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onComments(posts.get(getAdapterPosition()));
                }
            });
            saveButton.setOnClickListener(v -> {
                if (getAdapterPosition() != RecyclerView.NO_POSITION) {
                    Post post = posts.get(getAdapterPosition());
                    post.setSaved(!post.isSaved());
                    notifyItemChanged(getAdapterPosition());
                    listener.onSave(post);
                }
            });"""

content = content.replace(gestures_old, gestures_new)

# Fix double tap
double_tap_old = """                public boolean onDoubleTap(MotionEvent e) {
                    showLikeAnimation();
                    if (getAdapterPosition() != RecyclerView.NO_POSITION) {
                        listener.onUpvote(posts.get(getAdapterPosition()), getAdapterPosition());
                    }
                    return true;
                }"""

double_tap_new = """                public boolean onDoubleTap(MotionEvent e) {
                    showLikeAnimation();
                    if (getAdapterPosition() != RecyclerView.NO_POSITION) {
                        Post post = posts.get(getAdapterPosition());
                        if (post.getVoteType() != APIUtils.DIR_UPVOTE) {
                            post.setScore(post.getScore() + (post.getVoteType() == APIUtils.DIR_DOWNVOTE ? 2 : 1));
                            post.setVoteType(APIUtils.DIR_UPVOTE);
                            notifyItemChanged(getAdapterPosition());
                            listener.onUpvote(post, getAdapterPosition());
                        }
                    }
                    return true;
                }"""

content = content.replace(double_tap_old, double_tap_new)

with open('/data/data/com.termux/files/home/Prion/app/src/main/java/ml/docilealligator/infinityforreddit/adapters/ReelsAdapter.java', 'w') as f:
    f.write(content)

