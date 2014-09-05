package org.wso2.downstream;
/**
 * 
 * @author Himan Gamage <Himan@wso2.com>
 */
public class NexusDetails {

	public final String id;
	public final String url;
	public final boolean uniqueVersion;
	public final boolean evenIfUnstable;
	public final String releaseEnvVar;

	public NexusDetails(String id, String url, boolean uniqueVersion,
			boolean evenIfUnstable, String releaseEnvVar) {
		this.id = id;
		this.url = url;
		this.uniqueVersion = uniqueVersion;
		this.evenIfUnstable = evenIfUnstable;
		this.releaseEnvVar = releaseEnvVar;
	}

}
