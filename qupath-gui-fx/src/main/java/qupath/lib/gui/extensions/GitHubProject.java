package qupath.lib.gui.extensions;

import java.util.Objects;

/**
 * Helper class to represent a project hosted on GitHub.
 * May be used in combination with {@link UpdateChecker} to query new releases.
 */
public interface GitHubProject {
	
	/**
	 * Get the GitHub repository associated with the project.
	 * @return
	 */
	public GitHubRepo getRepository();
	
	
	/**
	 * Helper class to represent a GitHub repository.
	 */
	public static class GitHubRepo {
	
		private final String name;
		private final String owner;
		private final String repo;
		
		private GitHubRepo(final String name, final String owner, final String repo) {
			this.name = name;
			this.owner = owner;
			this.repo = repo;
		}
		
		/**
		 * Get the owner.
		 * @return
		 */
		public String getOwner() {
			return owner;
		}
		
		/**
		 * Get the repo.
		 * @return
		 */
		public String getRepo() {
			return repo;
		}
		
		/**
		 * Get the name. This can be used to identify the overall project, but is not part of the source code URL.
		 * @return
		 */
		public String getName() {
			return name;
		}
		
		/**
		 * Get the URL for the main repo.
		 * @return
		 */
		public String getUrlString() {
			return "https://github.com/" + owner + "/" + repo;
		}
		
		@Override
		public String toString() {
			return name + " (owner=" + owner + ", repo=" + repo + ")";
		}
		
		/**
		 * Create a new {@link GitHubProject}.
		 * @param name a user-friendly name
		 * @param owner the owner, used as the first part in the GitHub URL
		 * @param repo the repo, used as the second part in the GitHub URL
		 * @return
		 */
		public static GitHubRepo create(String name, String owner, String repo) {
			return new GitHubRepo(name, owner, repo);
		}

		@Override
		public int hashCode() {
			return Objects.hash(name, owner, repo);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			GitHubRepo other = (GitHubRepo) obj;
			return Objects.equals(name, other.name) && Objects.equals(owner, other.owner)
					&& Objects.equals(repo, other.repo);
		}
		
		
		
	}
		
}
