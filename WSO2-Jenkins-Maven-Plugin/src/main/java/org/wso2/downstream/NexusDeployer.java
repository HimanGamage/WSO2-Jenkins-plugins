package org.wso2.downstream;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import jenkins.mvn.GlobalSettingsProvider;
import jenkins.mvn.SettingsProvider;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.Authentication;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.cli.transfer.BatchModeMavenTransferListener;
import org.apache.maven.repository.Proxy;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.maven.MavenBuild;
import hudson.maven.MavenEmbedder;
import hudson.maven.MavenEmbedderException;
import hudson.maven.MavenEmbedderRequest;
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.maven.MavenUtil;
import hudson.maven.RedeployPublisher;
import hudson.maven.reporters.MavenAbstractArtifactRecord;
import hudson.maven.reporters.MavenArtifactRecord;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Node;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.tasks.Maven.MavenInstallation;
/**
 * 
 * @author Himan Gamage <Himan@wso2.com>
 */
public class NexusDeployer {
	public String id; 
	public  boolean evenIfUnstable;
	public  String releaseEnvVar;
	/**
	 * Repository URL to deploy artifacts to.
	 */
	public String url ;
	public boolean uniqueVersion;
	private static final Logger LOG = Logger.getLogger(NexusDeployer.class.getName());


public NexusDeployer(NexusDetails details){
	this.id = details.id;
	this.url = details.url;
	this.uniqueVersion = details.uniqueVersion;
	this.evenIfUnstable = details.evenIfUnstable;
	this.releaseEnvVar = details.releaseEnvVar;
}

	
	public boolean deploy(AbstractBuild<?, ?> build, TaskListener listener)
			throws IOException, InterruptedException {
	
			if (build.getResult().isWorseThan(getTreshold()))
				return true; // build failed. Don't publish

			/**
			 * Check if we should skip or not
			 */
			if (releaseEnvVar != null) {
				String envVarValue = build.getEnvironment(listener).get(
						releaseEnvVar);
				if ("true".equals(envVarValue)) { // null or false are ignored
					// listener.getLogger()
					// .println(
					// "[INFO] Skipping deploying artifact as release build is in progress.");
					return true; // skip the deploy
				}
			}

			List<MavenAbstractArtifactRecord> mavenAbstractArtifactRecords = getActions(
					build, listener);
			if (mavenAbstractArtifactRecords == null
					|| mavenAbstractArtifactRecords.isEmpty()) {
				// listener.getLogger()
				// .println(
				// "[ERROR] No artifacts are recorded. Is this a Maven project?");
				build.setResult(Result.FAILURE);
				return true;
			}

			if (build instanceof MavenModuleSetBuild
					&& ((MavenModuleSetBuild) build).getParent()
							.isArchivingDisabled()) {
				// listener.getLogger().println("[ERROR] You cannot use the \"Deploy artifacts to Maven repository\" feature if you "
				// +
				// "disabled automatic artifact archiving");
				build.setResult(Result.FAILURE);
				return true;
			}

			long startupTime = System.currentTimeMillis();
			try {
				MavenEmbedder embedder = createEmbedder(listener, build);
				ArtifactRepositoryLayout layout = (ArtifactRepositoryLayout) embedder
						.lookup(ArtifactRepositoryLayout.ROLE, "default");
				ArtifactRepositoryFactory factory = (ArtifactRepositoryFactory) embedder
						.lookup(ArtifactRepositoryFactory.ROLE);
				ArtifactRepository artifactRepository = null;
				if (url != null) {
					// By default we try to get the repository definition from the
					// job configuration
					artifactRepository = getDeploymentRepository(factory, layout,
							id, url);
				}
				for (MavenAbstractArtifactRecord mavenAbstractArtifactRecord : mavenAbstractArtifactRecords) {
					if (artifactRepository == null
							&& mavenAbstractArtifactRecord instanceof MavenArtifactRecord) {
						// If no repository definition is set on the job level we
						// try to take it from the POM
						MavenArtifactRecord mavenArtifactRecord = (MavenArtifactRecord) mavenAbstractArtifactRecord;
						artifactRepository = getDeploymentRepository(factory,
								layout, mavenArtifactRecord.repositoryId,
								mavenArtifactRecord.repositoryUrl);
					}
					if (artifactRepository == null) {
						listener.getLogger()
								.println(
										"[ERROR] No Repository settings defined in the job configuration or distributionManagement of the module.");
						build.setResult(Result.FAILURE);
						return true;
					}
					mavenAbstractArtifactRecord.deploy(embedder,
							artifactRepository, listener);

				}
				// listener.getLogger().println("[INFO] Deployment done in " +
				// Util.getTimeSpanString(System.currentTimeMillis() -
				// startupTime));
				return true;
			} catch (MavenEmbedderException e) {
				e.printStackTrace(listener.error(e.getMessage()));
			} catch (ComponentLookupException e) {
				e.printStackTrace(listener.error(e.getMessage()));

			} catch (ArtifactDeploymentException e) {
				e.printStackTrace(listener.error(e.getMessage()));
			}
			// failed
			build.setResult(Result.FAILURE);
			// listener.getLogger().println("[INFO] Deployment failed after " +
			// Util.getTimeSpanString(System.currentTimeMillis() - startupTime));

		
		
		return true;

	}

	protected Result getTreshold() {
		if (evenIfUnstable) {
			return Result.UNSTABLE;
		} else {
			return Result.SUCCESS;
		}
	}

	protected List<MavenAbstractArtifactRecord> getActions(
			AbstractBuild<?, ?> build, TaskListener listener) {
		List<MavenAbstractArtifactRecord> actions = new ArrayList<MavenAbstractArtifactRecord>();

		MavenModuleSetBuild mavenBuild = (MavenModuleSetBuild) build;
		if (mavenBuild == null) {
			return actions;
		}
		for (Entry<MavenModule, MavenBuild> e : mavenBuild
				.getModuleLastBuilds().entrySet()) {
			MavenAbstractArtifactRecord a = e.getValue().getAction(
					MavenAbstractArtifactRecord.class);
			if (a == null) {
				listener.getLogger().println(
						"No artifacts are recorded for module"
								+ e.getKey().getName()
								+ ". Is this a Maven project?");
			} else {
				actions.add(a);
			}

		}
		return actions;
	}

	private ArtifactRepository getDeploymentRepository(
			ArtifactRepositoryFactory factory, ArtifactRepositoryLayout layout,
			String repositoryId, String repositoryUrl)
			throws ComponentLookupException {
		if (repositoryUrl == null)
			return null;
		final ArtifactRepository repository = factory
				.createDeploymentArtifactRepository(repositoryId,
						repositoryUrl, layout, uniqueVersion);
		return new RedeployPublisher.WrappedArtifactRepository(repository, uniqueVersion);
	}

	private MavenEmbedder createEmbedder(TaskListener listener,
			AbstractBuild<?, ?> build) throws MavenEmbedderException,
			IOException, InterruptedException {
		MavenInstallation m = null;
		File settingsLoc = null, remoteGlobalSettingsFromConfig = null;
		String profiles = null;
		Properties systemProperties = null;
		String privateRepository = null;
		FilePath remoteSettingsFromConfig = null;

		File tmpSettings = File.createTempFile("jenkins", "temp-settings.xml");
		try {
			AbstractProject project = build.getProject();

			if (project instanceof MavenModuleSet) {
				MavenModuleSet mavenModuleSet = ((MavenModuleSet) project);
				profiles = mavenModuleSet.getProfiles();
				systemProperties = mavenModuleSet.getMavenProperties();

				String altSettingsPath = SettingsProvider
						.getSettingsRemotePath(
								((MavenModuleSet) project).getSettings(),
								build, listener);
				String remoteGlobalSettingsPath = GlobalSettingsProvider
						.getSettingsRemotePath(
								((MavenModuleSet) project).getGlobalSettings(),
								build, listener);
				if (remoteGlobalSettingsPath != null) {
					remoteGlobalSettingsFromConfig = new File(
							remoteGlobalSettingsPath);
				}

				Node buildNode = build.getBuiltOn();

				if (buildNode == null) {
					// assume that build was made on master
					buildNode = Jenkins.getInstance();
				}

				if (StringUtils.isBlank(altSettingsPath)) {
					// get userHome from the node where job has been executed
					String remoteUserHome = build.getWorkspace().act(
							new GetUserHome());
					altSettingsPath = remoteUserHome + "/.m2/settings.xml";
				}

				// we copy this file in the master in a temporary file
				FilePath filePath = new FilePath(tmpSettings);
				FilePath remoteSettings = build.getWorkspace().child(
						altSettingsPath);
				if (!remoteSettings.exists()) {
					// JENKINS-9084 we finally use $M2_HOME/conf/settings.xml as
					// maven does

					String mavenHome = ((MavenModuleSet) project).getMaven()
							.forNode(buildNode, listener).getHome();
					String settingsPath = mavenHome + "/conf/settings.xml";
					remoteSettings = build.getWorkspace().child(settingsPath);
				}
				listener.getLogger().println(
						"Maven RedeployPublisher use remote "
								+ (buildNode != null ? buildNode.getNodeName()
										: "local") + " maven settings from : "
								+ remoteSettings.getRemote());
				remoteSettings.copyTo(filePath);
				settingsLoc = tmpSettings;

			}

			MavenEmbedderRequest mavenEmbedderRequest = new MavenEmbedderRequest(
					listener, m != null ? m.getHomeDir() : null, profiles,
					systemProperties, privateRepository, settingsLoc);

			if (remoteGlobalSettingsFromConfig != null) {
				mavenEmbedderRequest
						.setGlobalSettings(remoteGlobalSettingsFromConfig);
			}

			mavenEmbedderRequest
					.setTransferListener(new BatchModeMavenTransferListener(
							listener.getLogger()));

			return MavenUtil.createEmbedder(mavenEmbedderRequest);
		} finally {
			if (tmpSettings != null) {
				tmpSettings.delete();
			}
		}
	}

	private static final class GetUserHome implements
			Callable<String, IOException> {
		private static final long serialVersionUID = -8755705771716056636L;

		public String call() throws IOException {
			return System.getProperty("user.home");
		}
	}
	

}
