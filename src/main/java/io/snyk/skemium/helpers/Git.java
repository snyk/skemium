package io.snyk.skemium.helpers;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/// Helper to interact with local Git repositories.
public class Git {
    private static final Logger LOG = LoggerFactory.getLogger(Git.class);

    /// Information about a local Git repository current commit, branch and tag (if any).
    ///
    /// @param commit Current commit of the local Git repository; `null` if not found
    /// @param branch Current branch of the local Git repository; `null` if not found
    /// @param tag    Current tag of the local Git repository; `null` if none set
    public record GitInfo(@Nullable String commit, @Nullable String branch, @Nullable String tag) {
    }

    /// Returns current information about a local Git Repository.
    ///
    /// @param gitRepoPath Local path to the repository
    /// @return A [GitInfo] object
    /// @throws IOException
    /// @throws GitAPIException
    public static GitInfo getInfo(final Path gitRepoPath) throws IOException, GitAPIException {
        final FileRepositoryBuilder repoBuilder = new FileRepositoryBuilder()
                .setGitDir(gitRepoPath.resolve(".git").toFile());

        try (final Repository repository = repoBuilder.readEnvironment().findGitDir().build()) {
            final String commit = getCurrentCommit(repository);
            final String branch = getCurrentBranch(repository);

            try (final org.eclipse.jgit.api.Git git = new org.eclipse.jgit.api.Git(repository)) {
                return new GitInfo(
                        commit,
                        branch,
                        getTagForCommit(git, commit));
            }
        }
    }

    /// Similar to [#getInfo(Path)], but logs and returns an "empty" [GitInfo] if it fails.
    /// For example, it can fail if the given gitRepoPath is not a Git repository.
    ///
    /// @param gitRepoPath Local path to the repository
    /// @return A [GitInfo] object
    public static GitInfo tryGetInfo(final Path gitRepoPath) {
        try {
            return Git.getInfo(gitRepoPath);
        } catch (final Exception e) {
            LOG.warn("Unable to gather Git info", e);
            return new GitInfo(null, null, null);
        }
    }

    private static String getCurrentCommit(final Repository repository) throws IOException {
        final ObjectId head = repository.resolve(Constants.HEAD);
        if (head == null) {
            return null;
        }
        return head.getName();
    }

    private static String getCurrentBranch(final Repository repository) throws IOException {
        return repository.getBranch();
    }

    private static String getTagForCommit(final org.eclipse.jgit.api.Git git, final String commitId) throws GitAPIException, IOException {
        if (commitId == null) {
            return null;
        }

        final List<Ref> tags = git.tagList().call();
        try (RevWalk walk = new RevWalk(git.getRepository())) {
            final RevCommit commit = walk.parseCommit(ObjectId.fromString(commitId));
            for (Ref tag : tags) {
                final ObjectId tagObjectId = tag.getPeeledObjectId() != null ? tag.getPeeledObjectId() : tag.getObjectId();
                final RevCommit tagCommit = walk.parseCommit(tagObjectId);
                if (commit.equals(tagCommit)) {
                    return tag.getName();
                }
            }
        }
        return null;
    }

}
