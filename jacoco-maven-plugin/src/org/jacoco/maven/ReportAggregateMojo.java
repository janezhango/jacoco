/*******************************************************************************
 * Copyright (c) 2009, 2016 Mountainminds GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    John Oliver, Marc R. Hoffmann, Jan Wloka - initial API and implementation
 *
 *******************************************************************************/
package org.jacoco.maven;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.jacoco.report.IReportGroupVisitor;

/**
 * <p>
 * Creates a structured code coverage report (HTML, XML, and CSV) from multiple
 * projects within reactor. The report is created from all modules this project
 * depends on. From those projects class and source files as well as JaCoCo
 * execution data files will be collected. This also allows to create coverage
 * reports when tests are in separate projects than the code under test, for
 * example in case of integration tests.
 * </p>
 * 
 * <p>
 * Using the dependency scope allows to distinguish projects which contribute
 * execution data but should not be part of the report itself:
 * </p>
 * 
 * <ul>
 * <li><code>compile</code>: Project source and execution data is included in
 * the report.</li>
 * <li><code>test</code>: Only execution data is considered for the report.</li>
 * </ul>
 * 
 * @goal report-aggregate
 * @requiresProject true
 * @threadSafe
 * @since 0.7.7
 */
public class ReportAggregateMojo extends AbstractReportMojo {

	/**
	 * A list of execution data files to include in the report from each
	 * project. May use wildcard characters (* and ?). When not specified all
	 * *.exec files from the target folder will be included.
	 * 
	 * @parameter default-value="target/*.exec"
	 */
	List<String> dataFileIncludes;

	/**
	 * A list of execution data files to exclude from the report. May use
	 * wildcard characters (* and ?). When not specified nothing will be
	 * excluded.
	 * 
	 * @parameter
	 */
	List<String> dataFileExcludes;

	/**
	 * Output directory for the reports. Note that this parameter is only
	 * relevant if the goal is run from the command line or from the default
	 * build lifecycle. If the goal is run indirectly as part of a site
	 * generation, the output directory configured in the Maven Site Plugin is
	 * used instead.
	 * 
	 * @parameter 
	 *            default-value="${project.reporting.outputDirectory}/jacoco-aggregate"
	 */
	private File outputDirectory;

	/**
	 * The projects in the reactor.
	 * 
	 * @parameter property="reactorProjects"
	 * @readonly
	 */
	private List<MavenProject> reactorProjects;

	@Override
	boolean canGenerateReportRegardingDataFiles() {
		return true;
	}

	@Override
	boolean canGenerateReportRegardingClassesDirectory() {
		return true;
	}

	@Override
	void loadExecutionData(final ReportSupport support) throws IOException {
		final FileFilter filter = new FileFilter(dataFileIncludes,
				dataFileExcludes);
		for (final MavenProject dependency : findDependencies(
				Artifact.SCOPE_COMPILE, Artifact.SCOPE_TEST)) {
			for (final File execFile : filter.getFiles(dependency.getBasedir())) {
				support.loadExecutionData(execFile);
			}
		}
	}

	@Override
	void addFormatters(final ReportSupport support, final Locale locale)
			throws IOException {
		support.addAllFormatters(outputDirectory, outputEncoding, footer,
				locale);
	}

	@Override
	void createReport(final IReportGroupVisitor visitor,
			final ReportSupport support) throws IOException {
		final IReportGroupVisitor group = visitor.visitGroup(title);
		for (final MavenProject dependency : findDependencies(Artifact.SCOPE_COMPILE)) {
			support.processProject(group, dependency.getArtifactId(),
					dependency, getIncludes(), getExcludes(), sourceEncoding);
		}
	}

	@Override
	protected String getOutputDirectory() {
		return outputDirectory.getAbsolutePath();
	}

	@Override
	public void setReportOutputDirectory(final File reportOutputDirectory) {
		if (reportOutputDirectory != null
				&& !reportOutputDirectory.getAbsolutePath().endsWith(
						"jacoco-aggregate")) {
			outputDirectory = new File(reportOutputDirectory,
					"jacoco-aggregate");
		} else {
			outputDirectory = reportOutputDirectory;
		}
	}

	public String getOutputName() {
		return "jacoco-aggregate/index";
	}

	public String getName(final Locale locale) {
		return "JaCoCo Aggregate";
	}

	private List<MavenProject> findDependencies(final String... scopes) {
		final List<MavenProject> result = new ArrayList<MavenProject>();
		final List<String> scopeList = Arrays.asList(scopes);
		for (final Object dependencyObject : getProject().getDependencies()) {
			final Dependency dependency = (Dependency) dependencyObject;
			if (scopeList.contains(dependency.getScope())) {
				final MavenProject project = findProjectFromReactor(dependency);
				if (project != null) {
					result.add(project);
				}
			}
		}
		return result;
	}

	private MavenProject findProjectFromReactor(final Dependency d) {
		for (final MavenProject p : reactorProjects) {
			if (p.getGroupId().equals(d.getGroupId())
					&& p.getArtifactId().equals(d.getArtifactId())
					&& p.getVersion().equals(d.getVersion())) {
				return p;
			}
		}
		return null;
	}

}
