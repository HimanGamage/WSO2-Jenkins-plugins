package org.jenkinsci.plugins.ghprb;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;

public class DownstreamJobsProcessor {
	private static final Logger logger = Logger
			.getLogger(DownstreamJobsProcessor.class.getName());
	private Map<String, Integer> downstreamBuilds;
	private static final transient String NOT_BUILT_NUMBER = "</a>#0000<a>";
	List<AbstractProject> childList = new ArrayList<AbstractProject>();

	

	public List<AbstractProject> getDownstreamBuildList(AbstractBuild build) {

		List<AbstractProject> childs = build.getProject()
				.getDownstreamProjects();
		findDownstream(childs, new ArrayList<Integer>(), build.getParent()
				.getFullName());
		return childList;

	}

	private List<AbstractProject> findDownstream(List<AbstractProject> childs,
			List<Integer> parentChildSize, String upProjectName) {

		for (Iterator<AbstractProject> iterator = childs.iterator(); iterator
				.hasNext();) {
			AbstractProject project = iterator.next();

			List<AbstractProject> childProjects = project
					.getDownstreamProjects();
			if (!childProjects.isEmpty()) {
				findDownstream(childProjects, parentChildSize,
						project.getFullName());
			}
			childList.add(project);
		}
		return childList;
	}
}
