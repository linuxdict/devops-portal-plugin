package io.jenkins.plugins.devopsportal.models;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Result;
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
    private Boolean failure;

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

    public Optional<Boolean> isFailure() {
        return Optional.ofNullable(failure);
    }

    @DataBoundSetter
    public void setFailure(boolean failure) {
        this.failure = failure;
    }

    public boolean setFailure(Result result) {
        if (result == null) {
            return false;
        }
        if (result.isCompleteBuild()) {
            this.failure = result.isBetterOrEqualTo(Result.FAILURE);
            return true;
        }
        return false;
    }

    public String getSuccessState() {
        if (failure == null) {
            return "unknown";
        }
        return failure ? "failure" : "success";
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
        if (buildJob == null || buildJob.isEmpty()) {
            return buildJob;
        }

        // Extract the last part of the path (the actual job name)
        String[] pathParts = buildJob.split("/");
        return pathParts[pathParts.length - 1];
    }

    /**
     * Get the folder path without the job name.
     * For example: "folder1/folder2/jobName" becomes "folder1/folder2"
     */
    @SuppressWarnings("unused")
    public String getFolderPath() {
        if (buildJob == null || buildJob.isEmpty() || !buildJob.contains("/")) {
            return null;
        }

        int lastSlashIndex = buildJob.lastIndexOf("/");
        return buildJob.substring(0, lastSlashIndex);
    }

    /**
     * Debug method to check if the job and build can be resolved.
     * This helps identify folder path issues.
     */
    @SuppressWarnings("unused")
    public String getBuildResolutionDebugInfo() {
        StringBuilder debug = new StringBuilder();
        debug.append("Job: ").append(buildJob).append(", ");
        debug.append("Branch: ").append(buildBranch).append(", ");
        debug.append("Build: ").append(buildNumber).append(", ");

        try {
            Job<?, ?> job = JenkinsUtils.findJobByPath(buildJob, buildBranch);
            if (job != null) {
                debug.append("Job found: ").append(job.getClass().getSimpleName()).append(", ");
                Run<?, ?> run = job.getBuild(buildNumber);
                if (run != null) {
                    debug.append("Run found: ").append(run.getClass().getSimpleName()).append(", ");
                    debug.append("Status: ").append(run.getBuildStatusIconClassName());
                } else {
                    debug.append("Run not found");
                }
            } else {
                debug.append("Job not found");
            }
        } catch (Exception ex) {
            debug.append("Error: ").append(ex.getMessage());
        }

        return debug.toString();
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
        if (buildJob == null || buildJob.trim().isEmpty()) {
            return "icon-disabled";
        }
        if (buildNumber == null || buildNumber.trim().isEmpty()) {
            return "icon-disabled";
        }

        try {
            // First try the direct build lookup using JenkinsUtils
            Run<?, ?> run = JenkinsUtils.getBuild(buildJob, buildBranch, buildNumber).orElse(null);
            if (run != null) {
                String statusClass = run.getBuildStatusIconClassName();
                return statusClass != null ? statusClass : "icon-disabled";
            }

            // If direct lookup fails, try to get the job first, then the build
            Job<?, ?> job = JenkinsUtils.findJobByPath(buildJob, buildBranch);
            if (job != null) {
                // Try multiple ways to get the build
                run = findBuildInJob(job, buildNumber);
                if (run != null) {
                    String statusClass = run.getBuildStatusIconClassName();
                    return statusClass != null ? statusClass : "icon-disabled";
                }
            }

            // Last resort: try the old findJobByName approach
            job = JenkinsUtils.findJobByName(buildJob, buildBranch, Jenkins.get().getItems());
            if (job != null) {
                run = findBuildInJob(job, buildNumber);
                if (run != null) {
                    String statusClass = run.getBuildStatusIconClassName();
                    return statusClass != null ? statusClass : "icon-disabled";
                }
            }

        } catch (Exception ex) {
            // Log the error for debugging but don't break the UI
            System.err.println("Error getting build status for job: " + buildJob +
                             ", branch: " + buildBranch +
                             ", build: " + buildNumber +
                             " - " + ex.getMessage());
        }

        return "icon-disabled";
    }

    /**
     * Helper method to find a build in a job using multiple strategies.
     */
    private Run<?, ?> findBuildInJob(Job<?, ?> job, String buildNumber) {
        if (job == null || buildNumber == null) {
            return null;
        }

        try {
            // Try by build number string first
            Run<?, ?> run = job.getBuild(buildNumber);
            if (run != null) {
                return run;
            }

            // Try by build number integer if it's numeric
            if (buildNumber.matches("\\d+")) {
                try {
                    int buildNum = Integer.parseInt(buildNumber);
                    run = job.getBuildByNumber(buildNum);
                    if (run != null) {
                        return run;
                    }
                } catch (NumberFormatException ex) {
                    // Not a valid number, continue with other methods
                }
            }

            // Try to search through all builds (less efficient but thorough)
            for (Run<?, ?> build : job.getBuilds()) {
                if (buildNumber.equals(String.valueOf(build.getNumber())) ||
                    buildNumber.equals(build.getId()) ||
                    buildNumber.equals(build.getDisplayName())) {
                    return build;
                }
            }

        } catch (Exception ex) {
            // Ignore individual lookup failures and try next method
        }

        return null;
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
            retVal.sort(Comparator.comparing(DeploymentOperation::getApplicationName,
                    Comparator.nullsLast(Comparator.naturalOrder())));
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
                    .filter(item -> Objects.equals(serviceId, item.getServiceId()))
                    .max(Comparator.comparingLong(DeploymentOperation::getTimestamp));
        }

        public Optional<DeploymentOperation> getLastDeploymentByApplication(String applicationName, String applicationVersion) {
            return getRunOperations()
                    .stream()
                    .filter(item -> Objects.equals(item.getApplicationName(), applicationName))
                    .filter(item -> Objects.equals(item.getApplicationVersion(), applicationVersion))
                    .max(Comparator.comparingLong(DeploymentOperation::getTimestamp));
        }

        public List<DeploymentOperation> getDeploymentsByService(String serviceId) {
            return getRunOperations()
                    .stream()
                    .filter(item -> Objects.equals(serviceId, item.getServiceId()))
                    .sorted((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()))
                    .collect(Collectors.toList());
        }

        public Optional<DeploymentOperation> getDeploymentByRun(String environmentId, String jobName, String runNumber) {
            return getRunOperations()
                    .stream()
                    .filter(item -> Objects.equals(environmentId, item.getServiceId()))
                    .filter(item -> Objects.equals(jobName, item.getBuildJob()))
                    .filter(item -> Objects.equals(runNumber, item.getBuildNumber()))
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
