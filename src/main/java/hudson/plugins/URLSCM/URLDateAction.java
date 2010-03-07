package hudson.plugins.URLSCM;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import hudson.model.AbstractBuild;
import hudson.model.AbstractModelObject;
import hudson.model.Action;

public class URLDateAction extends AbstractModelObject implements Action {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private HashMap<String, Long> lastModified = new HashMap<String, Long>();

    private final AbstractBuild build;

    protected URLDateAction(AbstractBuild build) {
        this.build = build;
    }

    public AbstractBuild getBuild() {
        return build;
    }

    public long getLastModified(String u) {
        Long l = lastModified.get(u);
        if(l == null) return 0;
        return l;
    }

    public void setLastModified(String u, long l) {
        lastModified.put(u, l);
    }

    public Map<String, String> getUrlDates() {
        Map<String, String> ret = new HashMap<String, String>();
        for(Map.Entry<String, Long> e : lastModified.entrySet()) {
            long sinceEpoch = e.getValue();
            if(sinceEpoch == 0) {
                ret.put(e.getKey(), "Last-modified not supported");
            } else {
                ret.put(e.getKey(), DateFormat.getInstance().format(new Date(sinceEpoch)));
            }
        }
        return ret;
    }

    public String getDisplayName() {
        return "URL Modification Dates";
    }

    public String getIconFileName() {
        return "save.gif";
    }

    public String getSearchUrl() {
        return getUrlName();
    }

    public String getUrlName() {
        return "urlDates";
    }

    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        req.getView(this,chooseAction()).forward(req,rsp);
    }

    protected String chooseAction() {
        return "tagForm.jelly";
    }


}
