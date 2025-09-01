package io.jenkins.plugins.devopsportal.models;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Run;
import hudson.util.CopyOnWriteList;
import io.jenkins.plugins.devopsportal.Messages;
import io.jenkins.plugins.devopsportal.utils.JenkinsUtils;
import io.jenkins.plugins.devopsportal.utils.MiscUtils;
import jenkins.model.Jenkins;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.Serializable;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A persisted record of a DEPLOYMENT operation performed on a run environment.
 *
 * @author Rémi BELLO {@literal <remi@evolya.fr>}
 */
public class DeploymentOperation implements Describable<DeploymentOperation>, Serializable, GenericRunModel {

    private String serviceId;

    private long timestamp; // seconds

    private String applicationName;
    private String applicationVersion;

    private String buildJob;
    private String buildNumber;
    private String buildURL;
    private String buildBranch;
    private String buildCommit;

    private final List<String> tags;

    @DataBoundConstructor
    public DeploymentOperation() {
        tags = new ArrayList<>();
    }

    public String getServiceId() {
        return serviceId;
    }

    @DataBoundSetter
    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @DataBoundSetter
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getApplicationName() {
        return applicationName;
    }

    @DataBoundSetter
    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getApplicationVersion() {
        return applicationVersion;
    }

    @DataBoundSetter
    public void setApplicationVersion(String applicationVersion) {
        this.applicationVersion = applicationVersion;
    }

    public String getBuildJob() {
        return buildJob;
    }

    @DataBoundSetter
    public void setBuildJob(String buildJob) {
        this.buildJob = buildJob;
    }

    public String getBuildNumber() {
        return buildNumber;
    }

    @DataBoundSetter
    public void setBuildNumber(String buildNumber) {
        this.buildNumber = buildNumber;
    }

    public String getBuildURL() {
        return buildURL;
    }

    @DataBoundSetter
    public void setBuildURL(String buildURL) {
        this.buildURL = buildURL;
    }

    public String getBuildBranch() {
        return buildBranch;
    }

    @DataBoundSetter
    public void setBuildBranch(String buildBranch) {
        this.buildBranch = buildBranch;
    }

    public String getBuildCommit() {
        return buildCommit;
    }

    @DataBoundSetter
    public void setBuildCommit(String buildCommit) {
        this.buildCommit = buildCommit;
    }

    @Override
    public void setBuildTimestamp(long timestamp) {
        setTimestamp(timestamp);
    }

    public List<String> getTags() {
        return tags;
    }

    @DataBoundSetter
    public void setTags(String tags) {
        this.tags.clear();
        if (tags != null && !tags.trim().isEmpty()) {
            this.tags.addAll(MiscUtils.split(tags, ","));
        }
    }

    @SuppressWarnings("unused")
    public boolean isBranchProvided() {
        return buildBranch != null && !buildBranch.isEmpty();
    }

    /**
     * Get the display-friendly job name by extracting just the job name from the full folder path.
     * For example: "folder1/folder2/jobName" becomes "jobName"
     */
    @SuppressWarnings("unused")
    public String getDisplayJobName() {
        String jobPath = getFullJobPath();
        if (jobPath == null || jobPath.isEmpty()) {
            return buildJob; // fallback to original
        }

        // Extract the last part of the path (the actual job name)
        String[] pathParts = jobPath.split("/");
        return pathParts[pathParts.length - 1];
    }

    /**
     * Get the full job path, trying to extract it from buildURL if buildJob is incomplete
     */
    @SuppressWarnings("unused")
    public String getFullJobPath() {
        // First try the buildJob field
        if (buildJob != null && !buildJob.isEmpty()) {
            // If buildJob contains a slash, it's likely the full path
            if (buildJob.contains("/")) {
                return buildJob;
            }
            // If buildJob doesn't contain slash but buildURL does, try to extract from URL
            if (buildURL != null && buildURL.contains("/job/")) {
                String extractedPath = extractJobPathFromURL(buildURL);
                if (extractedPath != null && !extractedPath.equals(buildJob)) {
                    return extractedPath;
                }
            }
            return buildJob;
        }

        // If buildJob is empty, try to extract from buildURL
        if (buildURL != null && buildURL.contains("/job/")) {
            return extractJobPathFromURL(buildURL);
        }

        return buildJob; // fallback
    }

    /**
     * Extract job path from Jenkins URL
     * Example: "http://host/job/folder/job/jobname/123/" -> "folder/jobname"
     */
    private String extractJobPathFromURL(String url) {
        if (url == null || !url.contains("/job/")) {
            return null;
        }

        try {
            // Find all /job/ segments in the URL
            String[] parts = url.split("/job/");
            if (parts.length < 2) {
                return null;
            }

            StringBuilder jobPath = new StringBuilder();
            // Skip the first part (everything before first /job/)
            for (int i = 1; i < parts.length; i++) {
                String part = parts[i];
                // Extract the job/folder name (everything before the next slash or end)
                int slashIndex = part.indexOf('/');
                String jobName = slashIndex > 0 ? part.substring(0, slashIndex) : part;

                if (jobPath.length() > 0) {
                    jobPath.append("/");
                }
                jobPath.append(jobName);

                // If this part doesn't end with another /job/, we're done
                if (slashIndex < 0 || !part.substring(slashIndex).startsWith("/job/")) {
                    break;
                }
            }

            return jobPath.toString();
        } catch (Exception e) {
            return null; // If URL parsing fails, return null
        }
    }

    @Override
    public Descriptor<DeploymentOperation> getDescriptor() {
        return Jenkins.get().getDescriptorByType(DeploymentOperation.DescriptorImpl.class);
    }

    @Override
    public boolean equals(final Object that) {
        if (this == that) return true;
        if (that == null || getClass() != that.getClass()) return false;
        final DeploymentOperation other = (DeploymentOperation) that;
        return new EqualsBuilder()
                .append(applicationName, other.applicationName)
                .append(applicationVersion, other.applicationVersion)
                .append(buildBranch, other.buildBranch)
                .append(buildCommit, other.buildCommit)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(applicationName)
                .append(applicationVersion)
                .append(buildJob)
                .append(buildNumber)
                .append(buildURL)
                .append(buildBranch)
                .append(buildCommit)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append(applicationName)
                .append(applicationVersion)
                .append(buildJob)
                .append(buildNumber)
                .append(buildURL)
                .append(buildBranch)
                .append(buildCommit)
                .toString();
    }

    public String getBuildStatusClass() {
        String jobPath = getFullJobPath();
        Run<?, ?> run = JenkinsUtils.getBuild(jobPath, buildBranch, buildNumber).orElse(null);
        if (run != null) {
            return run.getBuildStatusIconClassName();
        }
        return "icon-disabled";
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<DeploymentOperation> implements Serializable {

        private final CopyOnWriteList<DeploymentOperation> runOperations = new CopyOnWriteList<>();

        public DescriptorImpl() {
            super(DeploymentOperation.class);
            load();
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.DeploymentOperation_DisplayName();
        }

        public synchronized List<DeploymentOperation> getRunOperations() {
            List<DeploymentOperation> retVal = new ArrayList<>(runOperations.getView());
            retVal.sort(Comparator.comparing(DeploymentOperation::getApplicationName));
            return retVal;
        }

        public synchronized void append(Consumer<DeploymentOperation> updater) {
            DeploymentOperation record = new DeploymentOperation();
            runOperations.add(record);
            updater.accept(record);
            save();
        }

        public Optional<DeploymentOperation> getLastDeploymentByService(String serviceId) {
            return getRunOperations()
                    .stream()
                    .filter(item -> serviceId.equals(item.getServiceId()))
                    .max(Comparator.comparingLong(DeploymentOperation::getTimestamp));
        }

        public Optional<DeploymentOperation> getLastDeploymentByApplication(String applicationName, String applicationVersion) {
            return getRunOperations()
                    .stream()
                    .filter(item -> item.getApplicationName().equals(applicationName))
                    .filter(item -> item.getApplicationVersion().equals(applicationVersion))
                    .max(Comparator.comparingLong(DeploymentOperation::getTimestamp));
        }

        public List<DeploymentOperation> getDeploymentsByService(String serviceId) {
            return getRunOperations()
                    .stream()
                    .filter(item -> serviceId.equals(item.getServiceId()))
                    .sorted((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()))
                    .collect(Collectors.toList());
        }

        public Optional<DeploymentOperation> getDeploymentByRun(String environmentId, String jobName, String runNumber) {
            return getRunOperations()
                    .stream()
                    .filter(item -> environmentId.equals(item.getServiceId()))
                    .filter(item -> jobName.equals(item.getBuildJob()))
                    .filter(item -> runNumber.equals(item.getBuildNumber()))
                    .max((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        }

        public boolean deleteDeploymentByRun(String environmentId, String jobName, String runNumber) {
            final DeploymentOperation operation = getDeploymentByRun(environmentId, jobName, runNumber)
                    .orElse(null);
            if (operation != null) {
                synchronized (this) {
                    if (runOperations.remove(operation)) {
                        save();
                        return true;
                    }
                }
            }
            return false;
        }

    }

}
