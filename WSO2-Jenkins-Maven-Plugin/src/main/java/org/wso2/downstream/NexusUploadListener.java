package org.wso2.downstream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.wso2.downstream.DownstreamProcessor.DownstreamBuilds;

import hudson.Extension;
import hudson.Launcher;
import hudson.maven.MavenModuleSetBuild;
import hudson.maven.RedeployPublisher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.Cause.UpstreamCause;
import hudson.model.Executor;
import hudson.model.Hudson;
import hudson.model.listeners.RunListener;
import hudson.triggers.Trigger;

/**
 * 
 * @author Himan Gamage <Himan@wso2.com>
 */
@Extension
public class NexusUploadListener extends RunListener<AbstractBuild> {
	/** The Logger. */
	private static final Logger LOG = Logger
			.getLogger(NexusUploadListener.class.getName());
	
	private Map<AbstractProject, List<DownstreamBuilds>> downStreamBuilds = new ConcurrentHashMap<AbstractProject, List<DownstreamBuilds>>();
	private Map<AbstractProject, List<AbstractProject>> downStreamFinishedBuildsMap = new ConcurrentHashMap<AbstractProject, List<AbstractProject>>();
	private static Map<AbstractProject, NexusDetails> nexusDetails = new ConcurrentHashMap<AbstractProject, NexusDetails>();

	

	public static Map<AbstractProject, NexusDetails> getNexusDetails() {
		return nexusDetails;
	}

	@Override
	public void onStarted(AbstractBuild build, TaskListener listener) {

	
	}

	@Override
	public void onCompleted(AbstractBuild build, TaskListener listener) {
		try {
			if (build instanceof MavenModuleSetBuild) {
				AbstractProject causedProject=findCause(build).getProject();
				if (causedProject == build.getProject()) {
					downStreamFinishedBuildsMap.remove(build.getProject());
					List<DownstreamBuilds> downstreamBuildList = new DownstreamProcessor()
							.getDownstreamBuildList(build);
					if (downstreamBuildList.size() == 0) {
						if (build.getResult() == Result.SUCCESS) {

							if(nexusDetails.get(build
									.getProject())!=null){
								new NexusDeployer(nexusDetails.get(build
										.getProject())).deploy(build, listener);
								
							}
							
						}

					} else {

						if (build.getResult() == Result.SUCCESS) {
								downStreamBuilds.put(build.getProject(),
										downstreamBuildList);
								
							if (!downStreamFinishedBuildsMap
									.containsKey(causedProject)) {
								
								downStreamFinishedBuildsMap.put(
										causedProject,
										new ArrayList<AbstractProject>());
							}
						} 

					}

				} else {

					if (build.getResult() == Result.SUCCESS) {
						List<AbstractProject> downStreamMapList = downStreamFinishedBuildsMap
								.get(causedProject);
						if (downStreamMapList != null) {
							downStreamFinishedBuildsMap.get(
									causedProject).add(
									build.getProject());

							List<DownstreamBuilds> buildList = downStreamBuilds
									.get(causedProject);

							if (downStreamFinishedBuildsMap.get(
									causedProject).size() == buildList
									.size()) {
								

								if (nexusDetails.get(causedProject) != null) {
									new NexusDeployer(
											nexusDetails.get(causedProject)).deploy(
											findCause(build), listener);

									List<AbstractProject> builtProjects=downStreamFinishedBuildsMap.get(findCause(build).getProject());
									for(AbstractProject p:builtProjects){
										if(nexusDetails.get(p)==null){
											new NexusDeployer(nexusDetails.get(p)).deploy(p.getLastBuild(), listener);	
										}
										
									}
									
									
									
								}

							}

						} 

					}

				}

			} 
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private AbstractBuild findCause(AbstractBuild<?, ?> currentBuild) {

		AbstractBuild testBuild = currentBuild;
		Outer: for (Cause c : currentBuild.getCauses()) {

			if (!(c instanceof UpstreamCause)) {
				testBuild = currentBuild;
				break Outer;

			} else {
				UpstreamCause upcause = (UpstreamCause) c;
				String upProjectName = upcause.getUpstreamProject();
				int buildNumber = upcause.getUpstreamBuild();
				AbstractProject upproject = Hudson
						.getInstance()
						.getItemByFullName(upProjectName, AbstractProject.class);
				AbstractBuild upBuild = (AbstractBuild) upproject
						.getBuildByNumber(buildNumber);
				testBuild = findCause(upBuild);
			}
		}
		return testBuild;
	}

}