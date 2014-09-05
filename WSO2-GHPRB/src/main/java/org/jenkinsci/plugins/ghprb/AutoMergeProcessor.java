package org.jenkinsci.plugins.ghprb;



import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BallColor;
import hudson.model.RunAction;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.model.Run;
import hudson.plugins.git.util.BuildData;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.github.GHCommitState;
import org.kohsuke.stapler.export.Exported;




public class AutoMergeProcessor  {
	private static final Logger logger = Logger.getLogger(AutoMergeProcessor.class.getName());
	private Map<String,Integer> downstreamBuilds;
    private transient List<DownstreamBuilds> downstreamBuildList;
    private static final transient String NOT_BUILT_NUMBER = "</a>#0000<a>";
    List<DownstreamBuilds> returningChildList = new ArrayList<DownstreamBuilds>();

    public AutoMergeProcessor() {
	}
	 public List<DownstreamBuilds> getDownstreamBuildList(AbstractBuild build) {
		   
		 List<AbstractProject> childs = build.getProject().getDownstreamProjects();
	        downstreamBuildList = findDownstream(childs, 1, new ArrayList<Integer>(),build.getParent().getFullName(),build.getNumber());
	    
	        return returningChildList;
	    }
	 
	 private List<DownstreamBuilds> findDownstream(List<AbstractProject> childs, int depth,List<Integer> parentChildSize,String upProjectName,int upBuildNumber) {
	    	List<DownstreamBuilds> childList = new ArrayList<DownstreamBuilds>();

	    	for (Iterator<AbstractProject> iterator = childs.iterator(); iterator.hasNext();) {
	            AbstractProject project = iterator.next();
	           
	            DownstreamBuilds downstreamBuild = new DownstreamBuilds();
	            downstreamBuild.setProjectName(project.getFullName());
	            downstreamBuild.setProjectUrl(project.getUrl());
	            

	            List<AbstractProject> childProjects = project.getDownstreamProjects();
	            if (!childProjects.isEmpty()) {
	                downstreamBuild.setChilds(findDownstream(childProjects,depth + 1, parentChildSize,project.getFullName(),downstreamBuild.getBuildNumber()));
	           
	            }
	            childList.add(downstreamBuild);
	            returningChildList.add(downstreamBuild);//himan added
	      
	        }
	        return childList;
	    }
	 

	 
	 
	 
	    public class DownstreamBuilds {

	        private String projectName, projectUrl,upProjectName;
	        private List<DownstreamBuilds> childs;
	        private int depth, childNumber,buildNumber,upBuildNumber;
	        private List<Integer> parentChildSize;
	        private transient AbstractProject project;
	        private transient Run<?, ?> run;
	        
	        private void initilize(){
	        	logger.log(Level.SEVERE, "-------- init Downstreambuilds :"+projectName);
	        	project = Hudson.getInstance().getItemByFullName(projectName, AbstractProject.class);
	        	run = project.getBuildByNumber(buildNumber);
	        }
	        
	        public List<Integer> getParentChildSize() {
	            return parentChildSize;
	        }

	        public void setParentChildSize(List<Integer> parentChildSize) {
	            this.parentChildSize = parentChildSize;
	        }

	        public String getProjectName() {
	            return projectName;
	        }

	        public void setProjectName(String projectName) {
	            this.projectName = projectName;
	        }

	        public String getProjectUrl() {
	            return projectUrl;
	        }

	        public void setProjectUrl(String projectUrl) {
	            this.projectUrl = projectUrl;
	        }

	        public int getBuildNumber() {
	        	return buildNumber;
	        }
	        
	        public String currentBuildNumber() {
	        	if(buildNumber == 0){
	        		return NOT_BUILT_NUMBER;
	        	}
	            return Integer.toString(buildNumber);
	        }

	        public int getDepth() {
	            return depth;
	        }

	        public void setBuildNumber(int buildNumber) {
	            this.buildNumber = buildNumber;
	        }

	        public int getChildNumber() {
	            return childNumber;
	        }

	        public void setChildNumber(int childNumber) {
	            this.childNumber = childNumber;
	        }

	        public String getImageUrl() {
	        	if(run == null ){
	        		initilize();
	        	}
	        	if (run == null || run.isBuilding()) {
	                return BallColor.GREY.anime().getImage();
	            } else {
	                return run.getResult().color.getImage();
	            }
	        }

	        public List<DownstreamBuilds> getChilds() {
	            return childs;
	        }

	        public void setChilds(List<DownstreamBuilds> childs) {
	            this.childs = childs;
	        }

	        public void setDepth(int depth) {
	            this.depth = depth;
	        }

	        public String getStatusMessage() {
	        	if(project == null ){
	        		initilize();
	        	}
	            
	            if (run == null) {
	                return Result.NOT_BUILT.toString();
	            } else if (run.isBuilding()) {
	                return run.getDurationString();
	            } else {
	                return run.getTimestamp().getTime().toString() + " - " + run.getResult().toString();
	            }
	        	
	        }
	        
	        public String getUpProjectName() {
	    		return upProjectName;
	    	}
	    	public void setUpProjectName(String upProjectName) {
	    		this.upProjectName = upProjectName;
	    	}
	    	public int getUpBuildNumber() {
	    		return upBuildNumber;
	    	}
	    	public void setUpBuildNumber(int upBuildNumber) {
	    		this.upBuildNumber = upBuildNumber;
	    	}
	    }

	    
	    //overrided methods
		public String getDisplayName() {
			return "Downstream build view";
		}
		//overrided methods
		@Exported(visibility = 2)
		public String getUrlName() {
			return "downstreambuildview";
		}
		//overrided methods
		public String getIconFileName() {
			return downstreamBuilds == null ? null : "clipboard.gif";
		}
		//overrided methods
	     public void onAttached(Run r) {
	//    	 logger.log(Level.SEVERE, "--------onattached");
	       // build = (AbstractBuild<?, ?>) r;
	    }
	   //overrided methods
	     public void onLoad(Run r) {
//	    	 logger.log(Level.SEVERE, "--------onLoad(Run)");
	    	 // build = (AbstractBuild<?, ?>) r;
	    }

//		public AbstractBuild getBuild() {
//			return build;
//		}
	   //overrided methods
	 	public void onBuildComplete() {
			// TODO Auto-generated method stub
			
		}
	 	//overrided methods
		public void onLoad() {
			// TODO Auto-generated method stub
			
		}
	

		public int getDownstreamBuildNumber(String projectName) {
			if(downstreamBuilds == null) {
				return 0;
			}
	        Integer result = downstreamBuilds.get(projectName);
	                
			return result!= null ? result : 0;
		}
	
	
	
	
}

