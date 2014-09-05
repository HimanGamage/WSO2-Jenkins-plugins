package org.jenkinsci.plugins.ghprb;

import hudson.model.AbstractProject;
import hudson.model.ParametersAction;
import hudson.plugins.git.RevisionParameterAction;
import hudson.plugins.git.util.BuildData;

public class ScheduleBuild2Details {
	private final AbstractProject project;
	public AbstractProject getProject() {
		return project;
	}

	private final int quietPeriod;
	private final GhprbCause cause;
	private final ParametersAction parametersAction;
	private final BuildData buildData;
	private final RevisionParameterAction revisionParameterAction;
	
	public ScheduleBuild2Details(AbstractProject project,int quietPeriod,GhprbCause cause,ParametersAction parametersAction,BuildData buildData,RevisionParameterAction revisionParameterAction) {
		this.project=project;
		this.quietPeriod=quietPeriod;
		this.cause=cause;
		this.parametersAction=parametersAction;
		this.buildData=buildData;
		this.revisionParameterAction=revisionParameterAction;
	}

	public int getQuietPeriod() {
		return quietPeriod;
	}

	public GhprbCause getCause() {
		return cause;
	}

	public ParametersAction getParametersAction() {
		return parametersAction;
	}

	public BuildData getBuildData() {
		return buildData;
	}

	public RevisionParameterAction getRevisionParameterAction() {
		return revisionParameterAction;
	}
}
