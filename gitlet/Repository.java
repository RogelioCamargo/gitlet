package gitlet;

import java.io.File;
import java.util.*;

import static gitlet.Utils.*;

/** Represents a gitlet repository.
 *  @author Rogelio Camargo
 */
public class Repository {
    
    // directories
    public static final File CWD = new File(System.getProperty("user.dir"));
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    public static final File REFS_DIR = join(GITLET_DIR, "refs");
    public static final File BRANCHES_DIR = join(REFS_DIR, "branches");
    public static final File OBJECTS_DIR = join(GITLET_DIR, "objects");

    // files
    public static final File HEAD = join(GITLET_DIR, "HEAD");

    /**
     * <pre>
     *     .gitlet
     *     ---- refs
     *     -------- heads
     *     ---- objects
     *     -------- blobs
     *     -------- commits
     *     ---- index
     *     ---- HEAD
     * </pre>
     */

    public static void setupPersistence() {
        List<File> directories = List.of(
                GITLET_DIR, REFS_DIR, BRANCHES_DIR, OBJECTS_DIR, Blob.BLOBS_DIR, Commit.COMMITS_DIR
        );
        for (File directory: directories) {
            directory.mkdir();
        }
    }

    public static void checkWorkingDirectory() {
        if (!GITLET_DIR.exists() && !GITLET_DIR.isDirectory()) {
            exit("Not in an initialized Gitlet directory.");
        }
    }

    public static void initialize() {
        if (GITLET_DIR.exists()) {
            exit("A Gitlet version-control system already exists in the current directory.");
        }

        // create .gitlet directory and subdirectories 
        setupPersistence();
        // create initial commit
        Commit initialCommit = new Commit();
        initialCommit.serialize();

        // create master branch file and store pointer to initial commit
        updateBranchFile("master", initialCommit.getId());
        // create head file and store name of the current branch
        updateHeadFile("master");

        // create staging area and serialize
        StagingArea stagingArea = new StagingArea();
        stagingArea.serialize();
    }

    public static void addFileToStagingArea(String filename) {
        File fileToStage = join(CWD, filename);
        if (!fileToStage.exists()) {
            exit("File does not exist.");
        }

        // deserialize staging area
        StagingArea stagingArea = StagingArea.deserialize();
        HashMap<String, String> stagedTrackedFiles = stagingArea.getFilesStagedForAddition();

        // get the tracked files from the latest commit (which should be stored in HEAD file)
        Commit headCommit = getHeadCommit();
        HashMap<String, String> currentTrackedFiles = headCommit.getTrackedFiles();

        // read contents of file to stage
        byte[] contentsOfFileToStage = readContents(fileToStage);

        // create new blob (ONLY BLOB IS CREATED, NOT SERIALIZED UPON CREATION)
        Blob newBlob = new Blob(filename, contentsOfFileToStage);

        // get blob ids
        String newBlobId = newBlob.getId();
        String currentBlobId = currentTrackedFiles.getOrDefault(filename, "");
        String stagedBlobId = stagedTrackedFiles.getOrDefault(filename, "");

        // if latest commit already tracks filename with exact contents -> don't stage file
        if (currentBlobId.equals(newBlobId)) {
            // unstage file (from both addition and/or removal) if latest commit already
            // links filename with exact contents
            if (!stagedBlobId.equals(newBlobId)) {
                stagingArea.unstageFileForAddition(filename);
                stagingArea.unstageFileForRemoval(filename);
                // delete blob
                Blob blobToDelete = Blob.deserialize(stagedBlobId);
                blobToDelete.delete();
            }
        }
        else {
            // if filename has been staged already, remove the blob previously tied to because new blob
            // will take its place
            if (!stagedBlobId.equals("")) {
                // delete blob
                Blob blobToDelete = Blob.deserialize(stagedBlobId);
                blobToDelete.delete();
            }

            // serialize new blob
            newBlob.serialize();
            // stage file for addition im staging area with the new contents
            stagingArea.stageFileForAddition(filename, newBlob.getId());
        }

        // serialize staging area
        stagingArea.serialize();
    }

    public static void makeNewCommit(String message) {
        // get the latest commit (which should be stored in HEAD file)
        Commit headCommit = getHeadCommit();
        createNewCommit(message, List.of(headCommit.getId()));
    }

    public static void removeFileFromTracking(String filename) {
        // deserialize staging area
        StagingArea stagingArea = StagingArea.deserialize();
        HashMap<String, String> stagedTrackedFiles = stagingArea.getFilesStagedForAddition();

        // get the latest tracked files from the latest commit
        Commit headCommit = getHeadCommit();
        HashMap<String, String> currentTrackedFiles = headCommit.getTrackedFiles();

        // get blob ids
        String stagedBlobId = stagedTrackedFiles.getOrDefault(filename, "");
        String currentBlobId = currentTrackedFiles.getOrDefault(filename, "");

        // abort if file is neither staged nor tracked by the head commit
        if (stagedBlobId.equals("") && currentBlobId.equals("")) {
            exit("No reason to remove the file.");
        }

        // unstage file if it's been staged for addition
        if (!stagedBlobId.equals("")) {
            stagingArea.unstageFileForAddition(filename);
        }
        // otherwise, stage file for removal
        else {
            stagingArea.stageFileForRemoval(filename);
        }

        File fileToDelete = join(CWD, filename);
        if (fileToDelete.exists()) {
            byte[] fileToDeleteContents = readContents(join(CWD, filename));
            String blobId = Blob.getIdFromNameAndContents(filename, fileToDeleteContents);
            if (currentBlobId.equals(blobId)) {
                fileToDelete.delete();
            }
        }

        // serialize staging area
        stagingArea.serialize();
    }

    public static void printHeadCommitHistory() {
        // get the latest commit
        Commit headCommit = getHeadCommit();
        Commit currentCommit = headCommit;

        // terminate loop when initial commit is reached (initial commit has no parents)
        // TODO: update this algorithm
        while (currentCommit.hasParents()) {
            // display information about each commit
            System.out.println(currentCommit);
            // get parent of commit
            String parentCommitPointer = currentCommit.getParent();
            // deserialize parent commit
            currentCommit = Commit.deserialize(parentCommitPointer);
        }

        // initial commit is reach, display information
        System.out.println(currentCommit);
    }

    public static void printEntireCommitHistory() {
        // get all commit filenames
        List<String> commitFilenames = plainFilenamesIn(Commit.COMMITS_DIR);
        // display information about all commits ever made
        for (String filename: commitFilenames) {
            Commit commit = Commit.deserialize(filename);
            System.out.println(commit);
        }
    }

    public static void printCommitsWithMessage(String message) {
        boolean hasAtLeastOneCommitWithMessage = false;
        // get all commit filenames
        List<String> commitFilenames = plainFilenamesIn(Commit.COMMITS_DIR);
        // print out all the ids of all commits that have the given commit exit
        for (String filename: commitFilenames) {
            Commit commit = Commit.deserialize(filename);
            if (commit.getMessage().equals(message)) {
                hasAtLeastOneCommitWithMessage = true;
                System.out.println(commit.getId());
            }
        }

        if (!hasAtLeastOneCommitWithMessage) {
            message("Found no commit with that message.");
        }
    }

    public static void printCurrentStatus() {
        System.out.println("=== Branches ===");
        // displays what branches currently exist
        List<String> branchNames = plainFilenamesIn(BRANCHES_DIR);
        String currentBranch = getCurrentBranch();
        for (String branchName: branchNames) {
            if (branchName.equals(currentBranch)) {
                // marks the current branch with *
                System.out.println("*" + branchName);
            } else {
                System.out.println(branchName);
            }
        }

        // get staging area
        StagingArea stagingArea = StagingArea.deserialize();
        List<String> filenames = plainFilenamesIn(CWD);

        System.out.println("\n=== Staged Files ===");
        // displays files that have been staged for addition
        for (String filename: filenames) {
            if (stagingArea.hasFileStagedForAddition(filename)) {
                System.out.println(filename);
            }
        }

        System.out.println("\n=== Removed Files ===");
        // displays files that have been staged for removal
        for (String filename: stagingArea.getFilesStagedForRemoval()) {
            System.out.println(filename);
        }

        // Extra Credit:
        System.out.println("\n=== Modifications Not Staged For Commit ===\n");
        System.out.println("=== Untracked Files ===\n");
    }

    public static void checkoutFileFromHeadCommit(String filename) {
        // get tracked files from head commit
        Commit headCommit = getHeadCommit();
        HashMap<String, String> headCommitTrackedFiles = headCommit.getTrackedFiles();

        // abort if the file doesn't exist in the head commit
        if (!headCommitTrackedFiles.containsKey(filename)) {
            exit("File does not exist in that commit.");
        }

        // get blob of file that needs to be checked out
        Blob fileBlob = Blob.deserialize(headCommitTrackedFiles.get(filename));
        // create or update contents of file in the working directory
        writeContents(join(CWD, filename), fileBlob.getContents());
    }

    public static void checkoutFileFromGivenCommit(String commitId, String filename) {
        // abort if no commit with the given id exists
        if (!Commit.exists(commitId)) {
            exit("No commit with that id exists.");
        }

        // get tracked files from given commit
        Commit commit = Commit.deserialize(commitId);
        HashMap<String, String> commitTrackedFiles = commit.getTrackedFiles();

        // abort if the file doesn't exist in the head commit
        if (!commitTrackedFiles.containsKey(filename)) {
            exit("File does not exist in that commit.");
        }

        // get blob of file that needs to be checked out
        Blob fileBlob = Blob.deserialize(commitTrackedFiles.get(filename));
        // create or update contents of file in the working directory
        writeContents(join(CWD, filename), fileBlob.getContents());
    }

    public static void checkoutBranch(String branchName) {
        File branchFile = join(BRANCHES_DIR, branchName);
        if (!branchFile.exists()) {
            exit("No such branch exists.");
        }

        if (branchName.equals(getCurrentBranch())) {
            exit("No need to checkout the current branch.");
        }

        // get head commit from current branch
        Commit commitFromCurrentBranch = getCommitFromBranch(branchName);
        // get head commit from given branch
        Commit commitFromGivenBranch = getCommitFromBranch(branchName);
        // ensure no untracked file can be overwritten
        if (hasUntrackedFileThatCanBeOverWritten(commitFromGivenBranch)) {
            exit("There is an untracked file in the way; delete it, or add and commit it first.");
        }

        // get tracked files from branch we're checkout
        HashMap<String, String> givenBranchTrackedFiles = commitFromGivenBranch.getTrackedFiles();

        // clear current working directory
        clearWorkingDirectory();

        // take all files in the commit at the head of the given branch, and place them in the working directory
        for (Map.Entry<String, String> entry: givenBranchTrackedFiles.entrySet()) {
            // get contents of file via blob
            Blob blob = Blob.deserialize(entry.getValue());
            writeContents(join(CWD, entry.getKey()), blob.getContents());
        }

        // clear the staging area
        StagingArea stagingArea = StagingArea.deserialize();
        stagingArea.clear();
        stagingArea.serialize();

        // the given branch is now the current branch
        updateHeadFile(branchName);
    }

    private static void clearWorkingDirectory() {
        List<String> filenamesInCWD = plainFilenamesIn(CWD);
        for (String filename: filenamesInCWD) {
            restrictedDelete(join(CWD, filename));
        }
    }

    public static void createNewBranch(String branchName) {
        // get branch file
        File newBranchFile = join(BRANCHES_DIR, branchName);
        // don't create a new branch if one with given name already exists
        if (newBranchFile.exists()) {
            exit("A branch with that name already exists.");
        }

        // get the head commit
        Commit headCommit = getHeadCommit();
        // store the head commit pointer in the new branch file
        writeContents(newBranchFile, headCommit.getId());
    }

    public static void removeBranch(String branchName) {
        File branchFile = join(BRANCHES_DIR, branchName);
        // aborts if a branch with the given name does not exist
        if (!branchFile.exists()) {
            exit("A branch with that name does not exist.");
        }

        // can't remove the branch you're currently on
        if (branchName.equals(getCurrentBranch())) {
            exit("Cannot remove the current branch.");
        }

        // delete branch file
        branchFile.delete();
    }

    public static void checkoutCommit(String commitId) {
        if (!Commit.exists(commitId)) {
            exit("No commit with that id exists.");
        }

        // get head commit from current branch
        Commit currentCommit = getHeadCommit();
        // get head commit from given branch
        Commit destinationCommit = Commit.deserialize(commitId);
        // ensure no untracked file can be overwritten
        if (hasUntrackedFileThatCanBeOverWritten(destinationCommit)) {
            exit("There is an untracked file in the way; delete it, or add and commit it first.");
        }

        // clear current working directory
        clearWorkingDirectory();

        // get tracked files from branch we're checkout
        HashMap<String, String> destinationTrackedFiles = destinationCommit.getTrackedFiles();
        // take all files in the commit at the head of the given branch, and place them in the working directory
        for (Map.Entry<String, String> entry: destinationTrackedFiles.entrySet()) {
            // get contents of file via blob
            Blob blob = Blob.deserialize(entry.getValue());
            writeContents(join(CWD, entry.getKey()), blob.getContents());
        }

        // clear the staging area
        StagingArea stagingArea = StagingArea.deserialize();
        stagingArea.clear();
        stagingArea.serialize();

        // update the pointer of the current branch to the currently checked out commit
        updateBranchFile(getCurrentBranch(), commitId);
    }

    public static void mergeBranchWithCurrentBranch(String branchName) {
        File branchFile = join(BRANCHES_DIR, branchName);
        if (!branchFile.exists()) {
            exit("A branch with that name does not exist.");
        }
        if (getCurrentBranch().equals(branchFile)) {
            exit("Cannot merge a branch with itself.");
        }

        StagingArea stagingArea = StagingArea.deserialize();
        if (!stagingArea.isEmpty()) {
            exit("You have uncommitted changes.");
        }

        Commit currentBranchCommit = getCommitFromBranch(getCurrentBranch());
        Commit givenBranchCommit = getCommitFromBranch(branchName);

        if (hasUntrackedFileThatCanBeOverWritten(givenBranchCommit)) {
            exit("There is an untracked file in the way; delete it, or add and commit it first.");
        }
        // find split point
        Commit splitPointCommit = findSplitPoint(currentBranchCommit, givenBranchCommit);
        // if the split point is the same commit as the given branch, then we do nothing; the merge is complete
        if (splitPointCommit.getId().equals(givenBranchCommit.getId())) {
            exit("Given branch is an ancestor of the current branch.");
        }
        if (splitPointCommit.getId().equals(currentBranchCommit.getId())) {
            exit("Current branch fast-forwarded.");
        }

        mergeContents(currentBranchCommit, givenBranchCommit, splitPointCommit, getCurrentBranch(), branchName);
    }

    private static void mergeContents(Commit currentCommit, Commit otherCommit, Commit splitCommit,
                                         String currentBranchName, String otherBranchName) {
        boolean hasConflict = false;
        // get tracked files from all three commits
        HashMap<String, String> currentTrackedFiles = currentCommit.getTrackedFiles();
        HashMap<String, String> otherTrackedFiles = otherCommit.getTrackedFiles();
        HashMap<String, String> splitTrackedFiles = splitCommit.getTrackedFiles();

        // group all tracked files
        HashSet<String> allTrackedFiles = new HashSet<>();
        for (String filename: currentTrackedFiles.keySet()) {
            allTrackedFiles.add(filename);
        }
        for (String filename: otherTrackedFiles.keySet()) {
            allTrackedFiles.add(filename);
        }
        for (String filename: splitTrackedFiles.keySet()) {
            allTrackedFiles.add(filename);
        }

        // iterate through all tracked files and merge accordingly
        for (String filename: allTrackedFiles) {
            String splitBlobId = splitTrackedFiles.getOrDefault(filename, "");
            String currentBlobId = currentTrackedFiles.getOrDefault(filename, "");
            String otherBlobId = otherTrackedFiles.getOrDefault(filename, "");

            // case 2: modified in HEAD but not OTHER
            // case 3: modified in OTHER and HEAD but in the same way
            // case 5: not in SPLIT nor OTHER but in HEAD
            // case 8: unmodified in OTHER but not present in HEAD
            if (currentBlobId.equals(otherBlobId) || splitBlobId.equals(otherBlobId)) {
                continue;
            }
            if (splitBlobId.equals(currentBlobId)) {
                // case 7: unmodified in HEAD but not present in OTHER
                if (otherBlobId.equals("")) {
                    removeFileFromTracking(filename);
                }
                // case 1: modified in OTHER but not HEAD
                // case 6: not in SPLIT nor HEAD but in OTHER
                else {
                    Blob otherBlob = Blob.deserialize(otherBlobId);
                    // overwrite file with other's contents
                    writeContents(join(CWD, otherBlob.getFilename()), otherBlob.getContents());
                    addFileToStagingArea(otherBlob.getFilename());
                }

            }
            // case 4: modified in OTHER and HEAD but in different ways
            else {
                StringBuilder newMergedContents = new StringBuilder();
                newMergedContents.append("<<<<<<< HEAD\n");
                if (!currentBlobId.equals("")) {
                    Blob currentBlob = Blob.deserialize(currentBlobId);
                    String currentBlobContents = currentBlob.getContentsAsString();
                    newMergedContents.append(currentBlobContents);
                }
                newMergedContents.append("=======\n");
                if (!otherBlobId.equals("")) {
                    Blob otherBlob = Blob.deserialize(otherBlobId);
                    String otherBlobContents = otherBlob.getContentsAsString();
                    newMergedContents.append(otherBlobContents);
                }
                newMergedContents.append(">>>>>>>");
                // overwrite file with merged contents
                writeContents(join(CWD, filename), newMergedContents.toString());
                addFileToStagingArea(filename);
                hasConflict = true;
            }
        }

        // create new merged commit
        createNewCommit("Merged " + otherBranchName + " into " + currentBranchName + ".",
                List.of(currentCommit.getId(), otherCommit.getId()));
        // print if conflict was encountered
        if (hasConflict) {
            System.out.println("Encountered a merge conflict.");
        }
    }

    private static Commit findSplitPoint(Commit a, Commit b) {
        Set<String> parentCommitIdsOfA = new HashSet<>();
        // perform bfs on commit a
        Deque<Commit> fringe = new ArrayDeque<>();
        // set up starting commit
        fringe.addLast(a);
        while (!fringe.isEmpty()) {
            Commit commit = fringe.removeFirst();

            // add to commit id to set
            parentCommitIdsOfA.add(commit.getId());
            // get parents of commit
            List<String> commitParentIds = commit.getParents();
            // visit both parents
            for (String commitId: commitParentIds) {
                fringe.addLast(Commit.deserialize(commitId));
            }
        }

        // perform bfs on commit b, exit if commit id is in set
        fringe = new ArrayDeque<>();
        // set up starting commit
        fringe.addLast(b);
        while (!fringe.isEmpty()) {
            Commit commit = fringe.removeFirst();
            if (parentCommitIdsOfA.contains(commit.getId())) {
                return commit;
            }
            // get parents of commit
            List<String> commitParentIds = commit.getParents();
            // visit both parents
            for (String commitId: commitParentIds) {
                fringe.addLast(Commit.deserialize(commitId));
            }
        }

        return null;
    }

    private static void updateHeadFile(String branchName) {
        writeContents(HEAD, branchName);
    }

    private static void updateBranchFile(String branchName, String commitId) {
        File branchFile = join(BRANCHES_DIR, branchName);
        writeContents(branchFile, commitId);
    }

    private static Commit getCommitFromBranch(String branchName) {
        File branchFile = join(BRANCHES_DIR, branchName);
        String commitId = readContentsAsString(branchFile);
        return Commit.deserialize(commitId);
    }

    private static String getCurrentBranch() {
        return readContentsAsString(HEAD);
    }

    private static Commit getHeadCommit() {
        File currentBranchFile = join(BRANCHES_DIR, getCurrentBranch());
        String latestCommitPointer = readContentsAsString(currentBranchFile);
        return Commit.deserialize(latestCommitPointer);
    }

    private static void createNewCommit(String message, List<String> parents) {
        // deserialize staging area
        StagingArea stagingArea = StagingArea.deserialize();
        if (stagingArea.isEmpty()) {
            exit("No changes added to the commit.");
        }

        // get parent's tracked files
        Commit parentCommit = Commit.deserialize(parents.get(0));
        HashMap<String, String> currentTrackedFiles = parentCommit.getTrackedFiles();
        // get files staged for addition
        HashMap<String, String> filesStagedForAddition = stagingArea.getFilesStagedForAddition();
        // add or update files that were tracked by the previous commit
        for (Map.Entry<String, String> entry: filesStagedForAddition.entrySet()) {
            String filename = entry.getKey();
            String blobId = entry.getValue();
            // update or start tracking current file with contents
            currentTrackedFiles.put(filename, blobId);
        }

        // get files staged for removal
        HashSet<String> filesStagedForRemoval = stagingArea.getFilesStagedForRemoval();
        // remove files that were tracked by the previous commit
        for (String filename: filesStagedForRemoval) {
            currentTrackedFiles.remove(filename);
        }

        // create a new commit
        Commit newCommit = new Commit(message, parents, currentTrackedFiles);
        newCommit.serialize();

        // clear staging area and serialize
        stagingArea.clear();
        stagingArea.serialize();

        // update pointer of current branch
        updateBranchFile(getCurrentBranch(), newCommit.getId());
    }

    private static boolean hasUntrackedFileThatCanBeOverWritten(Commit endCommit) {
        // get tracked files from start commit
        HashMap<String, String> startTrackedFiles = getHeadCommit().getTrackedFiles();

        // get files from the working directory
        List<String> filenamesInCWD = plainFilenamesIn(CWD);
        // deserialize the staging area
        StagingArea stagingArea = StagingArea.deserialize();

        // find untracked files - files not found in the staging area or in the start commit
        List<String> untrackedFilenames = new ArrayList<>();
        for (String filename: filenamesInCWD) {
            if (!stagingArea.hasFileStaged(filename) && !startTrackedFiles.containsKey(filename)) {
                untrackedFilenames.add(filename);
            }
        }

        // get tracked files from end commit
        HashMap<String, String> endTrackedFiles = endCommit.getTrackedFiles();

        // abort if a working file is untracked and would be overwritten
        for (String filename: untrackedFilenames) {
            String currentBranchBlobId = startTrackedFiles.getOrDefault(filename, "");
            String givenBranchBlobId = endTrackedFiles.getOrDefault(filename, "");
            if (!currentBranchBlobId.equals(givenBranchBlobId)) {
                return true;
            }
        }

        return false;
    }
}
