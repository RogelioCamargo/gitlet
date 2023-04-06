package gitlet;

import static gitlet.Utils.*;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author Rogelio Camargo
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND1> <OPERAND2> ... 
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            exit("Please enter a command.");
        }
        String firstArg = args[0];

        switch(firstArg) {
            case "init":
                validateNumArgs("init", args, 1);
                Repository.initialize();
                break;
            case "add":
                validateNumArgs("add", args, 2);
                Repository.checkWorkingDirectory();
                String message = args[1];
                if (message.isEmpty()) {
                    exit("Please enter a commit message.");
                }
                Repository.addFileToStagingArea(args[1]);
                break;
            case "commit":
                validateNumArgs("commit", args, 2);
                Repository.checkWorkingDirectory();
                if (args[1].isEmpty()) {
                    exit("Please enter a commit message.");
                }
                Repository.makeNewCommit(args[1]);
                break;
            case "rm":
                validateNumArgs("rm", args, 2);
                Repository.checkWorkingDirectory();
                Repository.removeFileFromTracking(args[1]);
                break;
            case "log":
                validateNumArgs("log", args, 1);
                Repository.checkWorkingDirectory();
                Repository.printHeadCommitHistory();
                break;
            case "global-log":
                validateNumArgs("global-log", args, 1);
                Repository.checkWorkingDirectory();
                Repository.printEntireCommitHistory();
                break;
            case "find":
                validateNumArgs("find", args, 2);
                Repository.checkWorkingDirectory();
                Repository.printCommitsWithMessage(args[1]);
                break;
            case "status":
                validateNumArgs("status", args, 1);
                Repository.checkWorkingDirectory();
                Repository.printCurrentStatus();
                break;
            case "checkout":
                Repository.checkWorkingDirectory();
                if (args.length == 2) {
                    Repository.checkoutBranch(args[1]);
                }
                else if (args.length == 3) {
                    if (!args[1].equals("--")) {
                        exit("Incorrect operands.");
                    }
                    Repository.checkoutFileFromHeadCommit(args[2]);
                }
                else if (args.length == 4) {
                    if (!args[2].equals("--")) {
                        exit("Incorrect operands.");
                    }
                    Repository.checkoutFileFromGivenCommit(args[1], args[3]);
                }
                else {
                    throw new RuntimeException(
                            String.format("Invalid number of arguments for: %s.", "checkout"));
                }
                break;
            case "branch":
                validateNumArgs("branch", args, 2);
                Repository.checkWorkingDirectory();
                Repository.createNewBranch(args[1]);
                break;
            case "rm-branch":
                validateNumArgs("rm-branch", args, 2);
                Repository.checkWorkingDirectory();
                Repository.removeBranch(args[1]);
                break;
            case "reset":
                validateNumArgs("rm-branch", args, 2);
                Repository.checkWorkingDirectory();
                Repository.checkoutCommit(args[1]);
                break;
            case "merge":
                validateNumArgs("merge", args, 2);
                Repository.checkWorkingDirectory();
                Repository.mergeBranchWithCurrentBranch(args[1]);
                break;
            default:
                exit("No command with that name exists.");
        }
    }

    /**
     * Checks the number of arguments versus the expected number,
     * throws a RuntimeException if they do not match.
     *
     * @param cmd Name of command you are validating
     * @param args Argument array from command line
     * @param n Number of expected arguments
     */
    public static void validateNumArgs(String cmd, String[] args, int n) {
        if (args.length != n) {
            exit("Incorrect operands.");
        }
    }
}
