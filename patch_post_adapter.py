import re

with open('/data/data/com.termux/files/home/Prion/app/src/main/java/ml/docilealligator/infinityforreddit/adapters/PostRecyclerViewAdapter.java', 'r') as f:
    content = f.read()

imports = "import ml.docilealligator.infinityforreddit.utils.SeenPostsManager;\n"
if "import ml.docilealligator.infinityforreddit.utils.SeenPostsManager;" not in content:
    content = content.replace("import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;", "import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;\n" + imports)

# We need to find the click listeners for posts, upvote, downvote, save, share, comments.
# Since PostRecyclerViewAdapter is 5626 lines long, it's safer to grep for the methods.
