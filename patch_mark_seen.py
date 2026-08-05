import re

with open('/data/data/com.termux/files/home/Prion/app/src/main/java/ml/docilealligator/infinityforreddit/adapters/PostRecyclerViewAdapter.java', 'r') as f:
    content = f.read()

# We need to find all occurrences of insertReadPost and append markSeen after it.
# We will use regex to find ReadPostModification.insertReadPost( ... );
pattern = re.compile(r'(ReadPostModification\.insertReadPost\([^;]+;\))')

def repl(m):
    return m.group(1) + '\n                        ml.docilealligator.infinityforreddit.utils.SeenPostsManager.markSeen(mSharedPreferences, post.getId());'

content = pattern.sub(repl, content)

with open('/data/data/com.termux/files/home/Prion/app/src/main/java/ml/docilealligator/infinityforreddit/adapters/PostRecyclerViewAdapter.java', 'w') as f:
    f.write(content)

