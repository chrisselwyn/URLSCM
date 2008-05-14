package hudson.plugins.URLSCM;

import hudson.Plugin;
import hudson.scm.SCMS;

/**
 * @author Michael Donohue
 * @plugin
 */
public class PluginImpl extends Plugin {
    public void start() throws Exception {
        SCMS.SCMS.add(URLSCM.DescriptorImpl.DESCRIPTOR);
    }
}
