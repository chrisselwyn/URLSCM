package hudson.plugins.URLSCM;

import java.util.HashMap;

import hudson.model.AbstractModelObject;
import hudson.model.Action;

public class URLDateAction extends AbstractModelObject implements Action {
	private HashMap<String, Long> lastModified = new HashMap<String, Long>();
	
	public long getLastModified(String u) {
		Long l = lastModified.get(u);
		if(l == null) return 0;
		return l;
	}
	
	public void setLastModified(String u, long l) {
		lastModified.put(u, l);
	}
	
	public String getDisplayName() {
		return "URL Modification Dates";
	}

	public String getIconFileName() {
		return null;
	}

	public String getUrlName() {
		return "urlModification";
	}

	public String getSearchUrl() {
		return getUrlName();
	}

}
