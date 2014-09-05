package org.jenkinsci.plugins.ghprb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import org.apache.tools.ant.property.GetProperty;
import org.jenkinsci.plugins.ghprb.AutoMergeProcessor.DownstreamBuilds;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestMergeResponse;

import com.coravy.hudson.plugins.github.GithubProjectProperty;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.BuildListener;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterValue;
import hudson.model.TaskListener;
import hudson.model.Cause.UpstreamCause;
import hudson.model.Executor;
import hudson.model.Hudson;
import hudson.model.listeners.RunListener;
import hudson.triggers.Trigger;

/**
 * 
 * @author janinko updated by Himan Gamage <Himan@wso2.com>
 */
@Extension
public class GhprbBuildListener extends RunListener<AbstractBuild> {
	/** The Logger. */
	private static final Logger LOG = Logger.getLogger(GhprbBuildListener.class
			.getName());
	private static Map<GhprbTrigger, GhprbBuilds> triggerList = new ConcurrentHashMap<GhprbTrigger, GhprbBuilds>();

	private static Map<GhprbTrigger, List<GhprbTrigger>> subTriggers = new ConcurrentHashMap<GhprbTrigger, List<GhprbTrigger>>();

	private static List<ScheduleBuild2Details> postBuilds = new ArrayList<ScheduleBuild2Details>();

	public static List<ScheduleBuild2Details> getPostBuilds() {
		return postBuilds;
	}

	public static Map<GhprbTrigger, List<GhprbTrigger>> getSubTriggers() {
		return subTriggers;
	}

	public static Map<GhprbTrigger, GhprbBuilds> getTriggerList() {
		return triggerList;
	}

	@Override
	public void onStarted(AbstractBuild build, TaskListener listener) {

		GhprbTrigger trigger = GhprbTrigger.getTrigger(build.getProject());
		if (trigger == null)
			return;

		trigger.getGhprb().getBuilds().onStarted(build, trigger);
	}

	@Override
	public void onCompleted(AbstractBuild build, TaskListener listener) {
		if (build instanceof AbstractBuild) {

			Set<GhprbTrigger> triggerListKeySet = triggerList.keySet();
			 for (GhprbTrigger t : triggerListKeySet) {

				try {
					List<DownstreamBuilds> downstreamList = t.getGhprb()
							.getBuilds().getDownstreamBuilds();
					for (DownstreamBuilds db : downstreamList) {

						if (db.getProjectName() == build.getProject().getName()) {
							AbstractBuild mainBuild = t.getGhprb().getBuilds()
									.getMainBuild();

							LOG.log(Level.SEVERE, "-------------------MainBuild :"+mainBuild.getFullDisplayName());
							
							boolean returnStatus = findCause(build, mainBuild);

							if (returnStatus) {
								if (t.getGhprb().getBuilds().getBuildDetails() == null) {
									t.getGhprb()
											.getBuilds()
											.setBuildDetails(
													new ConcurrentHashMap<String, AbstractBuild>());
								}

								if (build.getResult() == Result.SUCCESS) {
									t.getGhprb()
											.getBuilds()
											.getBuildDetails()
											.put(build.getProject().getName(),
													build);
									if (t.getGhprb().getBuilds()
											.getBuildDetails().size() == t
											.getGhprb().getBuilds()
											.getDownstreamBuilds().size()) {

										t.getGhprb()
												.getBuilds()
												.merge(GHCommitState.SUCCESS,
														build, t);

										mergeOtherPRs(GHCommitState.SUCCESS,
												build, t);

										t.getGhprb().getBuilds()
												.setMainBuild(null);//
										t.getGhprb().getBuilds()
												.setBuildDetails(null);
										startBlockedJobs();
										triggerList.remove(t);

									}

								} else if (build.getResult() == Result.UNSTABLE) {

									t.getGhprb()
											.getBuilds()
											.merge(GHCommitState
													.valueOf(GhprbTrigger
															.getDscp()
															.getUnstableAs()),
													build, t);

									mergeOtherPRs(
											GHCommitState.valueOf(GhprbTrigger
													.getDscp().getUnstableAs()),
											build, t);
									t.getGhprb().getBuilds()
											.setBuildDetails(null);
									t.getGhprb().getBuilds().setMainBuild(null);//
									startBlockedJobs();
									triggerList.remove(t);

								} else {

									t.getGhprb()
											.getBuilds()
											.merge(GHCommitState.FAILURE,
													build, t);

									mergeOtherPRs(GHCommitState.FAILURE, build,
											t);
									t.getGhprb().getBuilds()
											.setBuildDetails(null);
									t.getGhprb().getBuilds().setMainBuild(null);//
									triggerList.remove(t);
									startBlockedJobs();

								}

							}

						}
					}
				} catch (NullPointerException e) {
					LOG.log(Level.SEVERE, "------------------- catch block "+e);
					triggerList.remove(t);
				}

			}

			GhprbTrigger trigger = GhprbTrigger.getTrigger(build.getProject());// original

			if (trigger != null) {
				trigger.getGhprb().getBuilds().setMainBuild(build);
				// set the downstream builds for the PR triggered project
				if (trigger.getGhprb().getBuilds().getDownstreamBuilds() == null) {
					trigger.getGhprb()
							.getBuilds()
							.setDownstreamBuilds(
									new AutoMergeProcessor()
											.getDownstreamBuildList(build));
				}

				if (!triggerList.containsKey(trigger)) {
					triggerList.put(trigger, trigger.getGhprb().getBuilds());
				}

				boolean isCaused = findCause(build, trigger.getGhprb()
						.getBuilds().getMainBuild());
				if (isCaused) {
					if (build.getResult() == Result.SUCCESS) {
						if (trigger.getGhprb().getBuilds()
								.getDownstreamBuilds().size() == 0) {
							trigger.getGhprb()
									.getBuilds()
									.merge(GHCommitState.SUCCESS, build,
											trigger);

							mergeOtherPRs(GHCommitState.SUCCESS, build, trigger);
							trigger.getGhprb().getBuilds()
									.setBuildDetails(null);
							trigger.getGhprb().getBuilds().setMainBuild(null);//
							triggerList.remove(trigger);
							startBlockedJobs();

						}

					} else if (build.getResult() == Result.UNSTABLE) {

						trigger.getGhprb()
								.getBuilds()
								.merge(GHCommitState.valueOf(GhprbTrigger
										.getDscp().getUnstableAs()), build,
										trigger);

						mergeOtherPRs(GHCommitState.valueOf(GhprbTrigger
								.getDscp().getUnstableAs()), build, trigger);
						trigger.getGhprb().getBuilds().setBuildDetails(null);
						trigger.getGhprb().getBuilds().setMainBuild(null);//
						triggerList.remove(trigger);
						startBlockedJobs();

					} else {

						trigger.getGhprb().getBuilds()
								.merge(GHCommitState.FAILURE, build, trigger);

						mergeOtherPRs(GHCommitState.FAILURE, build, trigger);

						trigger.getGhprb().getBuilds().setBuildDetails(null);
						trigger.getGhprb().getBuilds().setMainBuild(null);//
						triggerList.remove(trigger);
						startBlockedJobs();

					}

				}

			}
		}
	}

	private void mergeOtherPRs(GHCommitState commitState, AbstractBuild build,
			GhprbTrigger trigger) {
		List<GhprbTrigger> triggers = subTriggers.get(trigger);
		if (triggers != null) {
			for (GhprbTrigger tri : triggers) {
				tri.getGhprb().getBuilds().merge(commitState, build, trigger);
				tri.getGhprb().getBuilds().setParameters(null);
				tri.getGhprb().getBuilds().setMainBuild(null);//
			}
			subTriggers.remove(trigger);
		}
	}

	private void startBlockedJobs() {
		List<ScheduleBuild2Details> removeList = new ArrayList<ScheduleBuild2Details>();

		for (ScheduleBuild2Details scheduledBuildDetails : postBuilds) {

			if (!scheduledBuildDetails.getProject().isBuildBlocked() && !scheduledBuildDetails.getProject().isBuilding()) {

				
				scheduledBuildDetails.getProject().scheduleBuild2(
						scheduledBuildDetails.getQuietPeriod(),
						scheduledBuildDetails.getCause(),
						scheduledBuildDetails.getParametersAction(),
						scheduledBuildDetails.getBuildData(),
						scheduledBuildDetails.getRevisionParameterAction());
				removeList.add(scheduledBuildDetails);
			}else{
				LOG.log(Level.WARNING,scheduledBuildDetails.getProject()+" is waiting");
			}
		}

		for (ScheduleBuild2Details sdb : removeList) {
			postBuilds.remove(sdb);

		}

	}

	private boolean findCause(AbstractBuild<?, ?> currentBuild,
			AbstractBuild<?, ?> mainBuild) {
		boolean status = false;
		for (Cause c : currentBuild.getCauses()) {
			if (status == true) {
				return status;
			}
			if (!(c instanceof UpstreamCause)) {
				if (currentBuild == mainBuild) {
					status = true;
					return status;
				}
			} else {
				UpstreamCause upcause = (UpstreamCause) c;
				String upProjectName = upcause.getUpstreamProject();
				int buildNumber = upcause.getUpstreamBuild();
				AbstractProject upproject = Hudson
						.getInstance()
						.getItemByFullName(upProjectName, AbstractProject.class);
				AbstractBuild upBuild = (AbstractBuild) upproject
						.getBuildByNumber(buildNumber);
				status = findCause(upBuild, mainBuild);
			}
		}
		return status;
	}

}