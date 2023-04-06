package gitlet;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;

import static gitlet.Utils.*;

/** Represents a gitlet staging area object.
 *  @author Rogelio Camargo
 */
public class StagingArea implements Serializable {
    // files staged for addition
    private HashMap<String, String> added;
    // files staged for removal
    private HashSet<String> removed;

    public StagingArea() {
        this.added = new HashMap<>();
        this.removed = new HashSet<>();
    }

    public static StagingArea deserialize() {
        return readObject(join(".gitlet", "index"), StagingArea.class);
    }

    public boolean hasFileStaged(String filename) {
        return hasFileStagedForRemoval(filename) || hasFileStagedForAddition(filename);
    }

    public boolean hasFileStagedForAddition(String filename) {
        return added.containsKey(filename);
    }
    public boolean hasFileStagedForRemoval(String filename) {
        return removed.contains(filename);
    }

    public boolean isEmpty() {
        return added.isEmpty() && removed.isEmpty();
    }

    public void clear() {
        added.clear();
        removed.clear();
    }

    public void unstageFileForAddition(String filename) {
        added.remove(filename);
    }

    public void unstageFileForRemoval(String filename) { removed.remove(filename); }

    public void stageFileForAddition(String filename, String blobId) {
        added.put(filename, blobId);
    }

    public HashMap<String, String> getFilesStagedForAddition() {
        return added;
    }

    public HashSet<String> getFilesStagedForRemoval() {
        return removed;
    }

    public void stageFileForRemoval(String filename) {
        removed.add(filename);
    }

    public void serialize() {
        writeObject(join(".gitlet", "index"), this);
    }
}
