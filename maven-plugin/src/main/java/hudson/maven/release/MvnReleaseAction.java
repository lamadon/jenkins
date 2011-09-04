/*
 * The MIT License
 * 
 * Copyright (c) 2009, NDS Group Ltd., James Nord, CloudBees, Inc.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.maven.release;

import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSet;
import hudson.maven.Messages;
import hudson.model.Cause.UserIdCause;
import hudson.model.PermalinkProjectAction;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import jenkins.model.Jenkins;

import org.apache.maven.shared.release.versions.DefaultVersionInfo;
import org.apache.maven.shared.release.versions.VersionParseException;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * The action appears as the link in the side bar that users will click on in
 * order to start the release process.
 * 
 * @author domi
 * @author James Nord
 */
public class MvnReleaseAction implements PermalinkProjectAction {

	private MavenModuleSet project;
	private boolean selectCustomScmCommentPrefix;
	private boolean selectAppendHudsonUsername;

	private transient boolean isReleaseBuild = false;

	private transient String releaseVersion;
	private transient String developmentVersion;
	private transient boolean appendJenkinsBuildNumber;
	private transient String repoDescription;
	private transient String scmUsername;
	private transient String scmPassword;
	private transient String scmTag;
	private transient String scmCommentPrefix;
	private transient boolean appendJenkinsUserName;
	private transient String setJenkinsUserName;

	public MvnReleaseAction(MavenModuleSet project, boolean selectCustomScmCommentPrefix, boolean selectAppendHudsonUsername) {
		this.project = project;
		this.selectCustomScmCommentPrefix = selectCustomScmCommentPrefix;
		this.selectAppendHudsonUsername = selectAppendHudsonUsername;
	}

	public boolean isReleaseBuild() {
		return isReleaseBuild;
	}

	public List<Permalink> getPermalinks() {
		return PERMALINKS;
	}

	public String getDisplayName() {
		return Messages.MvnReleaseAction_perform_release_name();
	}

	public String getIconFileName() {
		if (MvnReleaseBuildWrapper.hasReleasePermission(project)) {
			return "installer.gif"; //$NON-NLS-1$
		}
		// by returning null the link will not be shown.
		return null;
	}

	public String getUrlName() {
		return "mvnrelease"; //$NON-NLS-1$
	}

	public boolean isSelectCustomScmCommentPrefix() {
		return selectCustomScmCommentPrefix;
	}

	public void setSelectCustomScmCommentPrefix(boolean selectCustomScmCommentPrefix) {
		this.selectCustomScmCommentPrefix = selectCustomScmCommentPrefix;
	}

	public boolean isSelectAppendHudsonUsername() {
		return selectAppendHudsonUsername;
	}

	public void setSelectAppendHudsonUsername(boolean selectAppendHudsonUsername) {
		this.selectAppendHudsonUsername = selectAppendHudsonUsername;
	}

	public Collection<MavenModule> getModules() {
		return project.getModules();
	}

	public MavenModule getRootModule() {
		return project.getRootModule();
	}

	public String computeReleaseVersion() {
		try {
			DefaultVersionInfo dvi = new DefaultVersionInfo(getRootModule().getVersion());
			return dvi.getReleaseVersionString();
		} catch (VersionParseException vpEx) {
			Logger logger = Logger.getLogger(this.getClass().getName());
			logger.log(Level.WARNING, "Failed to compute next version.", vpEx);
			return getRootModule().getVersion().replace("-SNAPSHOT", "");
		}
	}

	public String computeRepoDescription() {
		StringBuilder sb = new StringBuilder();
		sb.append(project.getRootModule().getName());
		sb.append(':');
		sb.append(computeReleaseVersion());
		return sb.toString();
	}

	public String computeScmTag() {
		// maven default is artifact-version
		StringBuilder sb = new StringBuilder();
		sb.append(getRootModule().getModuleName().artifactId);
		sb.append('-');
		sb.append(computeReleaseVersion());
		return sb.toString();
	}

	public String computeNextVersion() {
		try {
			DefaultVersionInfo dvi = new DefaultVersionInfo(getRootModule().getVersion());
			return dvi.getNextVersion().getSnapshotVersionString();
		} catch (VersionParseException vpEx) {
			Logger logger = Logger.getLogger(this.getClass().getName());
			logger.log(Level.WARNING, "Failed to compute next version.", vpEx);
			return "NaN-SNAPSHOT";
		}
	}

	public boolean isNexusSupportEnabled() {
		// return
		// project.getBuildWrappersList().get(MvnReleaseBuildWrapper.class).getDescriptor().isNexusSupport();
		return false;
	}

	public void doSubmit(StaplerRequest req, StaplerResponse resp) throws IOException, ServletException {
		MvnReleaseBuildWrapper.checkReleasePermission(project);
		MvnReleaseBuildWrapper wrapper = project.getBuildWrappersList().get(MvnReleaseBuildWrapper.class);

		// JSON collapses everything in the dynamic specifyVersions section so
		// we need to fall back to
		// good old http...
		Map<?, ?> httpParams = req.getParameterMap();

		final boolean appendHudsonBuildNumber = httpParams.containsKey("appendHudsonBuildNumber"); //$NON-NLS-1$
		final boolean closeNexusStage = httpParams.containsKey("closeNexusStage"); //$NON-NLS-1$
		final String repoDescription = closeNexusStage ? getString("repoDescription", httpParams) : ""; //$NON-NLS-1$
		final boolean specifyScmCredentials = httpParams.containsKey("specifyScmCredentials"); //$NON-NLS-1$
		final String scmUsername = specifyScmCredentials ? getString("scmUsername", httpParams) : null; //$NON-NLS-1$
		final String scmPassword = specifyScmCredentials ? getString("scmPassword", httpParams) : null; //$NON-NLS-1$
		final boolean specifyScmCommentPrefix = httpParams.containsKey("specifyScmCommentPrefix"); //$NON-NLS-1$
		final String scmCommentPrefix = specifyScmCommentPrefix ? getString("scmCommentPrefix", httpParams) : null; //$NON-NLS-1$
		final boolean specifyScmTag = httpParams.containsKey("specifyScmTag"); //$NON-NLS-1$
		final String scmTag = specifyScmTag ? getString("scmTag", httpParams) : null; //$NON-NLS-1$

		final boolean appendJenkinsUserName = specifyScmCommentPrefix && httpParams.containsKey("appendHudsonUserName"); //$NON-NLS-1$

		final String releaseVersion = getString("releaseVersion", httpParams); //$NON-NLS-1$
		final String developmentVersion = getString("developmentVersion", httpParams); //$NON-NLS-1$

		// TODO make this nicer by showing a html error page.
		// this will throw an exception so control will terminate if the dev
		// version is not a "SNAPSHOT".
		enforceDeveloperVersion(developmentVersion);

		this.releaseVersion = releaseVersion;

		// schedule release build
		synchronized (project) {
			if (project.scheduleBuild(0, new UserIdCause())) {
				wrapper.enableRelease();
				this.isReleaseBuild = true;
				this.releaseVersion = releaseVersion;
				this.developmentVersion = developmentVersion;
				this.appendJenkinsBuildNumber = appendHudsonBuildNumber;
				this.repoDescription = repoDescription;
				this.scmUsername = scmUsername;
				this.scmPassword = scmPassword;
				this.scmTag = scmTag;
				this.scmCommentPrefix = scmCommentPrefix;
				this.appendJenkinsUserName = appendJenkinsUserName;
				this.setJenkinsUserName = Jenkins.getAuthentication().getName();
				// redirect to project page
				resp.sendRedirect(req.getContextPath() + '/' + project.getUrl());
			} else {
				// redirect to error page.
				// TODO try and get this to go back to the form page with an
				// error at the top.
				resp.sendRedirect(req.getContextPath() + '/' + project.getUrl() + '/' + getUrlName() + "/failed");
			}
		}
	}

	public String getReleaseVersion() {
		return releaseVersion;
	}

	/**
	 * returns the value of the key as a String. if multiple values have been
	 * submitted, the first one will be returned.
	 * 
	 * @param key
	 * @param httpParams
	 * @return
	 */
	private String getString(String key, Map<?, ?> httpParams) {
		return (String) (((Object[]) httpParams.get(key))[0]);
	}

	/**
	 * Enforces that the developer version is actually a developer version and
	 * ends with "-SNAPSHOT".
	 * 
	 * @throws IllegalArgumentException
	 *             if the version does not end with "-SNAPSHOT"
	 */
	private void enforceDeveloperVersion(String version) throws IllegalArgumentException {
		if (!version.endsWith("-SNAPSHOT")) {
			throw new IllegalArgumentException(String.format(Locale.ENGLISH, "Developer Version (%s) is not a valid version (it must end with \"-SNAPSHOT\")",
					version));
		}
	}

	private static final List<Permalink> PERMALINKS = Collections.singletonList(LastReleasePermalink.INSTANCE);

	/**
	 * @return the developmentVersion
	 */
	public String getDevelopmentVersion() {
		return developmentVersion;
	}

	/**
	 * @return the appendJenkinsBuildNumber
	 */
	public boolean isAppendJenkinsBuildNumber() {
		return appendJenkinsBuildNumber;
	}

	/**
	 * @return the repoDescription
	 */
	public String getRepoDescription() {
		return repoDescription;
	}

	/**
	 * @return the scmUsername
	 */
	public String getScmUsername() {
		return scmUsername;
	}

	/**
	 * @return the scmPassword
	 */
	public String getScmPassword() {
		return scmPassword;
	}

	/**
	 * @return the scmTag
	 */
	public String getScmTag() {
		return scmTag;
	}

	/**
	 * @return the scmCommentPrefix
	 */
	public String getScmCommentPrefix() {
		return scmCommentPrefix;
	}

	/**
	 * @return the appendJenkinsUserName
	 */
	public boolean isAppendJenkinsUserName() {
		return appendJenkinsUserName;
	}

	/**
	 * @return the setJenkinsUserName
	 */
	public String getSetJenkinsUserName() {
		return setJenkinsUserName;
	}
}