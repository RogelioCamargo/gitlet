package gitlet;

import java.io.File;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;

import static gitlet.Utils.*;

/** Represents a gitlet blob object.
 *  @author Rogelio Camargo
 */
public class Blob implements Serializable {
    private String filename;
    private byte[] contents;
    private String id;

    public Blob(String filename, byte[] contents) {
        this.filename = filename;
        this.contents = contents;
        this.id = getIdFromNameAndContents(filename, contents);
    }

    public static final File BLOBS_DIR = join(".gitlet", "objects", "blobs");

    public static String getIdFromNameAndContents(String filename, byte[] contents) {
        return sha1(filename, contents);
    }

    public static Blob deserialize(String blobId) {
        File fileToReadFrom = join(BLOBS_DIR, blobId);
        return readObject(fileToReadFrom, Blob.class);
    }

    public String getId() {
        return id;
    }

    public byte[] getContents() { return contents; }

    public String getFilename() { return filename; }

    public String getContentsAsString() { return new String(contents, StandardCharsets.UTF_8); }

    public void serialize() {
        File fileToReadTo = join(BLOBS_DIR, id);
        writeObject(fileToReadTo, this);
    }

    public void delete() {
        File blobFile = join(BLOBS_DIR, id);
        blobFile.delete();
    }
}
