import re

with open('/data/data/com.termux/files/home/Prion/app/src/main/java/ml/docilealligator/infinityforreddit/activities/ReelsActivity.java', 'r') as f:
    content = f.read()

# Add the import and injected field
content = content.replace("import ml.docilealligator.infinityforreddit.utils.SeenPostsManager;", "import ml.docilealligator.infinityforreddit.utils.SeenPostsManager;\nimport ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;")

injection = """    @Inject
    Executor mExecutor;

    @Inject
    RedditDataRoomDatabase mRedditDataRoomDatabase;"""
content = content.replace("    @Inject\n    Executor mExecutor;", injection)

# Fix the getDatabase call
content = content.replace("ml.docilealligator.infinityforreddit.RedditDataRoomDatabase.getDatabase(ReelsActivity.this)", "mRedditDataRoomDatabase")

with open('/data/data/com.termux/files/home/Prion/app/src/main/java/ml/docilealligator/infinityforreddit/activities/ReelsActivity.java', 'w') as f:
    f.write(content)

