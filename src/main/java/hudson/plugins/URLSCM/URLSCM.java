package hudson.plugins.URLSCM;

import hudson.Extension;
import static hudson.Util.fixEmpty;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogParser;
import hudson.scm.NullChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.util.FormValidation;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Date;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;
import org.apache.commons.codec.binary.Base64;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

public class URLSCM extends hudson.scm.SCM {

    private final ArrayList<URLTuple> urls = new ArrayList<URLTuple>();
    private final boolean clearWorkspace;

    public URLSCM(String[] u, boolean clear) {
        for (int i = 0; i < u.length; i++) {
            urls.add(new URLTuple(u[i]));
        }
        this.clearWorkspace = clear;
    }

    public URLTuple[] getUrls() {
        return urls.toArray(new URLTuple[urls.size()]);
    }

    public boolean isClearWorkspace() {
        return clearWorkspace;
    }

    @Override
    public boolean checkout(AbstractBuild build, Launcher launcher,
            FilePath workspace, BuildListener listener, File changelogFile)
            throws IOException, InterruptedException {
        if (clearWorkspace) {
            workspace.deleteContents();
        }

        URLDateAction dates = new URLDateAction(build);

        for (URLTuple tuple : urls) {
            String urlString = tuple.getUrl();
            InputStream is = null;
            OutputStream os = null;
            try {
                URL url = new URL(urlString);
                URLConnection conn = makeURLConnection(url);
                dates.setLastModified(urlString, conn.getLastModified());
                is = conn.getInputStream();
                String path = new File(url.getPath()).getName();
                listener.getLogger().append("Copying " + urlString + " to " + path + "\n");
                os = workspace.child(path).write();
                byte[] buf = new byte[8192];
                int i = 0;
                while ((i = is.read(buf)) != -1) {
                    os.write(buf, 0, i);
                }
            } catch (Exception e) {
                listener.error("Unable to copy " + urlString + "\n" + e.getMessage());
                return false;
            } finally {
                if (is != null) {
                    is.close();
                }
                if (os != null) {
                    os.close();
                }
            }
            this.createEmptyChangeLog(changelogFile, listener, "log");
        }
        build.addAction(dates);

        return true;
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        return new NullChangeLogParser();
    }

    @Override
    public boolean requiresWorkspaceForPolling() {
        // this plugin does the polling work via the data in the Run
        // the data in the workspace is not used
        return false;
    }

    private boolean poll(AbstractProject project, Launcher launcher,
            FilePath workspace, TaskListener listener) throws IOException,
            InterruptedException {
        boolean change = false;
        Run lastBuild = project.getLastBuild();
        if (lastBuild == null) {
            return true;
        }
        URLDateAction dates = lastBuild.getAction(URLDateAction.class);
        if (dates == null) {
            return true;
        }

        for (URLTuple tuple : urls) {
            String urlString = tuple.getUrl();
            InputStream is = null;
            try {
                URLConnection conn = makeURLConnection(new URL(urlString));

                is = conn.getInputStream();

                long lastMod = conn.getLastModified();
                long lastBuildMod = dates.getLastModified(urlString);
                if (lastBuildMod != lastMod) {
                    listener.getLogger().println(
                            "Found change: " + urlString + " modified " + new Date(lastMod)
                            + " previous modification was " + new Date(lastBuildMod));
                    change = true;
                }
            } catch (IOException e) {
                listener.error("Unable to check " + urlString + "\n" + e.getMessage());
                throw e;
            } finally {
                if (is != null) {
                    is.close();
                }
            }
        }
        return change;
    }

    private URLConnection makeURLConnection(URL url) throws IOException {
        URLConnection conn = url.openConnection();
        conn.setUseCaches(false);
        if (url.getUserInfo() != null && conn instanceof HttpURLConnection) {
            String encodedAuthorization = Base64.encodeBase64String(url.getUserInfo().getBytes());
            encodedAuthorization = stripCRLF(encodedAuthorization);
            conn.setRequestProperty("Authorization", "Basic " + encodedAuthorization);
        }
        return conn;
    }

    private String stripCRLF(String encodedAuthorization) {
        if (encodedAuthorization.endsWith("\r\n")) {
            encodedAuthorization = encodedAuthorization.substring(0, encodedAuthorization.length() - 2);
        }
        return encodedAuthorization;
    }

    @Override
    public SCMRevisionState calcRevisionsFromBuild(AbstractBuild<?, ?> build,
            Launcher launcher, TaskListener listener) throws IOException,
            InterruptedException {
        // we cannot really calculate a sensible revision state for a filesystem folder
        // therefore we return NONE and simply ignore the baseline in compareRemoteRevisionWith
        return SCMRevisionState.NONE;
    }

    @Override
    protected PollingResult compareRemoteRevisionWith(AbstractProject<?, ?> project, Launcher launcher, FilePath workspace, TaskListener listener, SCMRevisionState baseline) throws IOException, InterruptedException {
        if(poll(project, launcher, workspace, listener)) {
            return PollingResult.SIGNIFICANT;
        } else {
            return PollingResult.NO_CHANGES;
        }
    }

    public static final class URLTuple {
        private String urlString;

        public URLTuple(String s) {
            urlString = s;
        }

        public String getUrl() {
            return urlString;
        }
    }

    @Extension
    public static final class DescriptorImpl extends SCMDescriptor<URLSCM> {

        public DescriptorImpl() {
            super(URLSCM.class, null);
            load();
        }

        public String getDisplayName() {
            return "URL Copy";
        }

        @Override
        public SCM newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new URLSCM(req.getParameterValues("URL.url"), req.getParameter("URL.clear") != null);
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            return true;
        }

        public FormValidation doUrlCheck(@QueryParameter final String value)
                throws IOException, ServletException {
            if (!Hudson.getInstance().hasPermission(Hudson.ADMINISTER)) {
                return FormValidation.ok();
            }
            return new FormValidation.URLCheck() {

                @Override
                protected FormValidation check() throws IOException, ServletException {
                    String url = fixEmpty(value);
                    URL u = null;
                    try {
                        u = new URL(url);
                        open(u);
                    } catch (Exception e) {
                        return FormValidation.error("Cannot open " + url);
                    }
                    String path = new File(u.getPath()).getName();
                    if (path.length() == 0) {
                        return FormValidation.error("URL does not contain filename: " + url);
                    }
                    return FormValidation.ok();
                }
            }.check();
        }
    }
}
