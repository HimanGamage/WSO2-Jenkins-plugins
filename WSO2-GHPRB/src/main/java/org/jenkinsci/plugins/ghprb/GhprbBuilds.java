package org.jenkinsci.plugins.ghprb;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.Hudson;
import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.model.Cause.UpstreamCause;
import hudson.model.queue.QueueTaskFuture;
import hudson.plugins.git.util.BuildData;
import jenkins.model.Jenkins;

import org.jenkinsci.plugins.ghprb.DownstreamJobsProcessor.DownstreamBuilds;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestMergeResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author janinko updated by Himan Gamage <Himan@wso2.com>
 */
public class GhprbBuilds {
	private static final Logger logger = Logger.getLogger(GhprbBuilds.class
			.getName());
	private GhprbTrigger trigger;
	private ParametersAction parameters=null;




	private GhprbCause causeForTrigger;
	private AbstractBuild mainBuild;
	private GhprbRepository repo;

	private List<DownstreamBuilds> downstreamBuilds;
	private Map<String, AbstractBuild> buildDetails;


	public ParametersAction getParameters() {
		return parameters;
	}

	public void setParameters(ParametersAction parameters) {
		this.parameters = parameters;
	}

	public GhprbTrigger getTrigger() {
		return trigger;
	}

	public GhprbRepository getRepo() {
		return repo;
	}

	public void setBuildDetails(Map<String, AbstractBuild> buildDetails) {
		this.buildDetails = buildDetails;
	}

	public Map<String, AbstractBuild> getBuildDetails() {
		return buildDetails;
	}

	// --

	public AbstractBuild getMainBuild() {
		return mainBuild;
	}

	public void setMainBuild(AbstractBuild mainBuild) {
		this.mainBuild = mainBuild;
	}

	public List<DownstreamBuilds> getDownstreamBuilds() {
		return downstreamBuilds;
	}

	public void setDownstreamBuilds(List<DownstreamBuilds> downstreamBuilds) {
		this.downstreamBuilds = downstreamBuilds;
	}

	public GhprbBuilds(GhprbTrigger trigger, GhprbRepository repo) {
		this.trigger = trigger;
		this.repo = repo;
	}

	public String build(GhprbPullRequest pr) {
		StringBuilder sb = new StringBuilder();
		if (cancelBuild(pr.getId())) {
			sb.append("Previous build stopped.");
		}

		if (pr.isMergeable()) {
			sb.append(" Merged build triggered.");
		} else {
			sb.append(" Build triggered.");
		}

		GhprbCause cause = new GhprbCause(pr.getHead(), pr.getId(),
				pr.isMergeable(), pr.getTarget(), pr.getSource(),
				pr.getAuthorEmail(), pr.getTitle(), pr.getUrl());
		this.causeForTrigger = cause;
		QueueTaskFuture<?> build = trigger.startJob(cause, repo);
		if (build == null) {
			logger.log(Level.SEVERE, "Job did not start");
		}
		return sb.toString();
	}

	private boolean cancelBuild(int id) {
		return false;
	}

	private GhprbCause getCauseForTheBuild(AbstractBuild build) {
		Cause cause = build.getCause(GhprbCause.class);
		if (cause == null || (!(cause instanceof GhprbCause)))
			return null;
		return (GhprbCause) cause;
	}

	public void onStarted(AbstractBuild build,GhprbTrigger mainTrigger) {
	
		
		if (getCauseForTheBuild(build) == null) {
			if(parameters!=null){
				build.addAction(parameters);
				
			}else{
				return;
			}
		}
			
		repo.createCommitStatus(build, GHCommitState.PENDING, (causeForTrigger
				.isMerged() ? "Merged build started." : "Build started."),
				causeForTrigger.getPullID(), causeForTrigger);
		try {
			build.setDescription("<a title=\"" + causeForTrigger.getTitle()
					+ "\" href=\"" + repo.getRepoUrl() + "/pull/"
					+ causeForTrigger.getPullID() + "\">PR #"
					+ causeForTrigger.getPullID() + "</a>: "
					+ causeForTrigger.getAbbreviatedTitle());
		} catch (IOException ex) {
			logger.log(Level.SEVERE, "Can't update build description", ex);
		}
	}

	// public void onCompleted(AbstractBuild build) {
	// GhprbCause c = getCauseForTheBuild(build);
	// if (c == null)
	// return;
	//
	// // remove the BuildData action that we may have added earlier to avoid
	// // having two of them, and because the one we added isn't correct
	// // @see GhprbTrigger
	// BuildData fakeOne = null;
	// for (BuildData data : build.getActions(BuildData.class)) {
	// if (data.getLastBuiltRevision() != null
	// && !data.getLastBuiltRevision().getSha1String()
	// .equals(c.getCommit())) {
	// fakeOne = data;
	// break;
	// }
	// }
	// if (fakeOne != null) {
	// build.getActions().remove(fakeOne);
	// }
	//
	// logger.log(Level.SEVERE, "----------------- finished");
	//
	// // merge();
	// // --
	//
	// }
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

	public void merge(GHCommitState state, AbstractBuild currentBuild,GhprbTrigger mainTrigger) {
		
		//check the cause for the build
		GhprbCause c=null;
		if(mainBuild!=null){
			 c = getCauseForTheBuild(mainBuild);
		
		}else{
			
			mainBuild=mainTrigger.getGhprb().getBuilds().getMainBuild();
		
			
		}
		
		
		if (c == null) {
		
				if (parameters == null) {
		
					return;
				} else {
		
					c = causeForTrigger;
					mainBuild=mainTrigger.getGhprb().getBuilds().getMainBuild();
				}
			
			
		}else{
		
			if(!findCause(currentBuild, mainBuild)){
		
				return;
			}
		}

		if (state == GHCommitState.FAILURE) {
			repo.addComment(
					c.getPullID(),
					"Build failed. Pull ID: " + c.getPullID() + "\nGithub : "
							+ mainBuild.getDescription() + "\nJenkins: "
							+ Jenkins.getInstance().getRootUrl()
							+ mainBuild.getUrl() + "\nFaild Jenkins build:"
							+ Jenkins.getInstance().getRootUrl()
							+ currentBuild.getUrl());
		}

		repo.createCommitStatus(mainBuild, state,
				(c.isMerged() ? "Merged build finished." : "Build finished."),
				c.getPullID(), causeForTrigger);

		String publishedURL = GhprbTrigger.getDscp().getPublishedURL();
		if (publishedURL != null && !publishedURL.isEmpty()) {
			StringBuilder msg = new StringBuilder();

			if (state == GHCommitState.SUCCESS) {
				msg.append(GhprbTrigger.getDscp().getMsgSuccess());
			} else {
				msg.append(GhprbTrigger.getDscp().getMsgFailure());
			}
			msg.append("\nRefer to this link for build results: ");
			msg.append(publishedURL).append(mainBuild.getUrl());

			int numLines = GhprbTrigger.getDscp().getlogExcerptLines();
			if (state != GHCommitState.SUCCESS && numLines > 0) {
				// on failure, append an excerpt of the build log
				try {
					// wrap log in "code" markdown
					msg.append("\n\n**Build Log**\n*last ").append(numLines)
							.append(" lines*\n");
					msg.append("\n ```\n");
					List<String> log = mainBuild.getLog(numLines);
					for (String line : log) {
						msg.append(line).append('\n');
					}
					msg.append("```\n");
				} catch (IOException ex) {
					logger.log(Level.WARNING,
							"Can't add log excerpt to commit comments", ex);
				}
			}

			repo.addComment(c.getPullID(), msg.toString());
		}

		// close failed pull request automatically
		if (state == GHCommitState.FAILURE
				&& trigger.isAutoCloseFailedPullRequests()) {

			try {
				GHPullRequest pr = repo.getPullRequest(c.getPullID());

				if (pr.getState().equals(GHIssueState.OPEN)) {
					repo.closePullRequest(c.getPullID());
				}
			} catch (IOException ex) {
				logger.log(Level.SEVERE, "Can't close pull request", ex);
			}
		}

		if (state == GHCommitState.SUCCESS
				&& trigger.getAutoMergePullRequests()) {
			try {
				GHPullRequestMergeResponse mergeRequest;
				int attemptCount = 0;
				do {
					mergeRequest = repo.getMergeRequest(c.getPullID(),
							"sucessfully commited the changes"
									+ "\nTimestamp : " + mainBuild.getTime()
									+ "\n" + Jenkins.getInstance().getRootUrl()
									+ mainBuild.getUrl() + "\n");

					attemptCount++;

					if (mergeRequest.isMerged()) {
						logger.log(
								Level.INFO,
								"Successfully merged pull request "
										+ c.getPullID() + " SHA: "
										+ mergeRequest.getSha()
										+ ". Message is: "
										+ mergeRequest.getMessage());
						break;
					} else {
						Thread.sleep(10000);
					}
				} while (attemptCount != 3);

				if (!mergeRequest.isMerged()) {
					logger.log(
							Level.WARNING,
							"Could not merge pull request " + c.getPullID()
									+ ". Error is: "
									+ mergeRequest.getMessage());
				}
			}
			/**
			 * Change the exception for catch
			 */
			catch (Exception ex) {
				logger.log(Level.SEVERE, "Can't Merge pull request", ex);
			}
		}
	}

}
