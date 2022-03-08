package qupath.lib.gui.panes;

import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Abstract class to represent all different objects that can go into a Project browser's tree.
 * @author Melvin Gelbard
 */
abstract class ProjectTreeRow {
	
	/**
	 * Types of possible {@code ProjectTreeRow}s.
	 */
	enum Type {
		/**
		 * Either the {@code Project} or a String displaying 'No Project'.
		 */
		ROOT,
		
		/**
		 * Tags associated with {@code ProjectImageEntry}s.
		 */
		METADATA,
		
		/**
		 * {@code ProjectImageEntry}s.
		 */
		IMAGE
	}
	
	/**
	 * String that will be used to display the object ({@code setText()}.
	 * @return displayableString
	 */
	abstract String getDisplayableString();
	
	/**
	 * Return the type of {@code ProjectTreeRow} this object represents.
	 * <p>
	 * E.g. {@code ROOT}, {@code TAG}, etc..
	 * @return type
	 */
	abstract Type getType();
	
	/**
	 * Get all the {@link ProjectImageEntry} objects inside the specified collection.
	 * @param imageRows
	 * @return collection of ProjectImageEntry 
	 */
	static Collection<ProjectImageEntry<BufferedImage>> getEntries(Collection<ImageRow> imageRows) {
		return imageRows.stream().map(row -> row.getEntry()).collect(Collectors.toList());
	}

	/**
	 * Get the {@link ProjectImageEntry} corresponding to the specified {@link #ProjectTreeRow}.
	 * <p>
	 * If the specified object is not an {@link ImageRow} (e.g. {@link MetadataRow}), return {@code null}.
	 * @param projectTreeRow
	 * @return Project image entry
	 */
	static ProjectImageEntry<BufferedImage> getEntry(ProjectTreeRow projectTreeRow) {
		if (projectTreeRow.getType() == Type.IMAGE)
			return ((ImageRow)projectTreeRow).getEntry();
		return null;
	}
	
	/**
	 * Object representing the root of the TreeView in {@link ProjectBrowser}. Either a 'No project' String or the project (name).
	 */
	static class RootRow extends ProjectTreeRow {

		private Project<?> project;
		private String originalString;

		RootRow(Project<?> project) {
			this.originalString = project == null ? "No project" : project.getName();
			this.project = project;
		}

		@Override
		String getDisplayableString() {
			return originalString;
		}

		@Override
		Type getType() {
			return Type.ROOT;
		}
		@Override
		public int hashCode() {
			return Objects.hash(project);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			RootRow other = (RootRow) obj;
			return Objects.equals(project, other.project);
		}
	}
	
	/**
	 * Object representing a metadata row in the TreeView in {@link ProjectBrowser}. Represented as Strings.
	 */
	static class MetadataRow extends ProjectTreeRow {
	
		private String originalString;
		
		MetadataRow(String value) {
			this.originalString = value;
		}

		@Override
		String getDisplayableString() {
			return originalString;
		}
		
		@Override
		Type getType() {
			return Type.METADATA;
		}

		@Override
		public int hashCode() {
			return Objects.hash(originalString);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			MetadataRow other = (MetadataRow) obj;
			return Objects.equals(originalString, other.originalString);
		}
	}
	
	/**
	 * Object representing a {@link ProjectImageEntry}/row in the TreeView in {@link ProjectBrowser}.
	 */
	public static class ImageRow extends ProjectTreeRow {
		
		private ProjectImageEntry<BufferedImage> entry;
		
		ImageRow(ProjectImageEntry<BufferedImage> entry) {
			this.entry = entry;
		}

		@Override
		String getDisplayableString() {
			return entry.getImageName();
		}

		@Override
		Type getType() {
			return Type.IMAGE;
		}
		
		ProjectImageEntry<BufferedImage> getEntry() {
			return entry;
		}

		@Override
		public int hashCode() {
			return Objects.hash(entry);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ImageRow other = (ImageRow) obj;
			return Objects.equals(entry, other.entry);
		}
	}
}
