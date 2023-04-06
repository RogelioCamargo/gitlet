package gitlet;

import java.io.File;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;

import static gitlet.Utils.*;

/** Represents a gitlet commit object.
 *  @author Rogelio Camargo
 */
public class Commit implements Serializable {
    private final String message;
    private final Date timestamp;
    // a commit can have at most two parents (gitlet simplification compared to real git)
    private final List<String> parents;
    // hash map where filename are keys and blob hash pointers are values
    private final HashMap<String, String> trackedFiles;
    private final String id;
    public static final File COMMITS_DIR = join(".gitlet", "objects", "commits");

    public Commit() {
      this("initial commit", new ArrayList<>(), new HashMap<>(), new Date(0));
    }

    public Commit(String message, List<String> parents, HashMap<String, String> trackedFiles) {
        this(message, parents, trackedFiles, new Date());
    }

    public Commit(String message, List<String> parents, HashMap<String, String> trackedFiles, Date timestamp) {
        this.message = message;
        this.parents = parents;
        this.trackedFiles = trackedFiles;
        this.timestamp = timestamp;
        // generate commit id
        this.id = generateId();
    }

    public static Commit deserialize(String commitId) {
        File fileToReadFrom = join(COMMITS_DIR, commitId);
        return readObject(fileToReadFrom, Commit.class);
    }

    public static boolean exists(String commitId) {
        File commitFile = join(COMMITS_DIR, commitId);
        return commitFile.exists();
    }

    public String getId() {
        return id;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getParents() { return parents; }

    public boolean hasParents() {
        return !parents.isEmpty();
    }

    public String getParent() {
        return parents.get(0);
    }

    public String getMergedParent() {
        return parents.get(1);
    }
    
    public HashMap<String, String> getTrackedFiles() {
        return trackedFiles;
    }

    public void serialize() {
        File fileToReadTo = join(COMMITS_DIR, id);
        writeObject(fileToReadTo, this);
    }

    public String toString() {
        StringBuilder commitBuilder = new StringBuilder();
        commitBuilder.append("===\n");
        commitBuilder.append("commit " + id + "\n");
        // if merged commit
        if (parents.size() == 2) {
            commitBuilder.append("Merge: " + getParent().substring(0, 7) + " " + getMergedParent().substring(0, 7) + "\n");
        }
        SimpleDateFormat formatter = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy Z", Locale.ENGLISH);
        commitBuilder.append("Date: " + formatter.format(timestamp) + "\n");
        commitBuilder.append(message + "\n");
        return commitBuilder.toString();
    }

    private String generateId() {
        return sha1(message, timestamp.toString(), parents.toString(), trackedFiles.toString());
    }
}
