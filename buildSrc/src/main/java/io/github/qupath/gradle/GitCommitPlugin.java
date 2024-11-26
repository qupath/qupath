/**
 * Plugin to request the latest Git commit tag, adding it to the properties for the project and any subprojects.
 */

package io.github.qupath.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

public class GitCommitPlugin implements Plugin<Project> {

    private static Logger logger = LoggerFactory.getLogger(GitCommitPlugin.class);

    @Override
    public void apply(Project project) {
        var prop = project.getGradle().getExtensions().getExtraProperties().get("qupath.package.git-commit");
        if ("true".equals(prop)) {
            try {
                String commit = getLatestGitCommit();
                addCommitToProject(project, commit);
                for (var p : project.getSubprojects()) {
                    addCommitToProject(p, commit);
                }
            } catch (Exception e) {
                logger.warn("Exception requesting git commit: {}", e.getMessage(), e);
            }
        } else {
            logger.info("I won't try to get the last commit - " +
                    "consider running with \"-Pgit-commit=true\" if you want this next time (assuming Git is installed)");
        }
    }

    private static void addCommitToProject(Project project, String commit) {
        var extensions = project.getExtensions();
        extensions.add("git.commit", commit);
    }

    private static String getLatestGitCommit() throws Exception {
        var process = new ProcessBuilder().command(
                "git", "log", "--pretty=format:\"%h\"", "-n 1"
        ).start();
        try (var reader = process.inputReader()) {
            var commit = reader.lines().collect(Collectors.joining(System.lineSeparator()));
            if (commit.isBlank())
                throw new RuntimeException("Git commit is missing!");
            return commit;
        }
    }
    
}
