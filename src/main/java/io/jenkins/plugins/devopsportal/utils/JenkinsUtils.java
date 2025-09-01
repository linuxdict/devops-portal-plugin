package io.jenkins.plugins.devopsportal.utils;

import hudson.model.*;
import jenkins.model.Jenkins;

import java.util.Collection;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class to read data from Jenkins.
 *
 * @author Rémi BELLO {@literal <remi@evolya.fr>}
 */
public final class JenkinsUtils {

    private static final Logger LOGGER = Logger.getLogger("io.jenkins.plugins.devopsportal");

    public static Optional<Run<?, ?>> getBuild(String jobName, String branchName, String buildNumber) {
        if (Jenkins.getInstanceOrNull() == null) {
            return Optional.empty();
        }
        if (branchName != null && branchName.isEmpty()) {
            branchName = null;
        }
        Job<?, ?> job = findJobByPath(jobName, branchName);
        if (job == null) {
            return Optional.empty();
        }

        // Try to get build by number - Jenkins getBuild() method accepts both string and int
        try {
            // First try with string (for build IDs)
            Run<?, ?> run = job.getBuild(buildNumber);
            if (run != null) {
                return Optional.of(run);
            }

            // If string failed, try converting to integer
            int buildNum = Integer.parseInt(buildNumber);
            return Optional.ofNullable(job.getBuildByNumber(buildNum));
        } catch (NumberFormatException e) {
            // If buildNumber is not a valid integer, try as string only
            return Optional.ofNullable(job.getBuild(buildNumber));
        }
    }

    /**
     * Find a job by its path, handling folder navigation properly.
     * For example: "folder1/folder2/jobName" will navigate through folders to find the job.
     */
    public static Job<?, ?> findJobByPath(String jobPath, String branchName) {
        if (Jenkins.getInstanceOrNull() == null || jobPath == null || jobPath.trim().isEmpty()) {
            return null;
        }

        // Split the path into folder parts and job name
        String[] pathParts = jobPath.split("/");
        if (pathParts.length == 0) {
            return null;
        }

        String actualJobName = pathParts[pathParts.length - 1];
        if (actualJobName.isEmpty()) {
            return null;
        }

        // Start from Jenkins root
        ItemGroup<?> currentGroup = Jenkins.get();

        // Navigate through folders
        for (int i = 0; i < pathParts.length - 1; i++) {
            String folderName = pathParts[i];
            if (folderName.isEmpty()) {
                continue; // Skip empty path parts
            }

            Item item = currentGroup.getItem(folderName);
            if (item instanceof ItemGroup) {
                currentGroup = (ItemGroup<?>) item;
            } else {
                return null; // Folder not found
            }
        }

        // Now find the job in the final folder
        Item jobItem = currentGroup.getItem(actualJobName);

        // Handle multi-branch pipelines
        if (branchName != null && jobItem instanceof ItemGroup) {
            Item branchItem = ((ItemGroup<?>) jobItem).getItem(branchName);
            if (branchItem instanceof Job) {
                return (Job<?, ?>) branchItem;
            }
        } else if (jobItem instanceof Job) {
            return (Job<?, ?>) jobItem;
        }

        return null;
    }

    public static Job<?, ?> findJobByName(String jobName, String itemName, Collection<? extends TopLevelItem> items) {
        return findJobByName(jobName, itemName, items, "");
    }

    /**
     * This function is used to retrieve a job instance from Jenkins persistent data.
     * It has two behaviour:
     * - It is used to retrieve a simple element (job) by designating it by its name.
     *   In this case, only the jobName is required and itemName must be null.
     * - But it also allows to get the sub-job in the case of a complex object like a
     *   Multi-Branch pipeline. In this case, itemName must be given.
     */
    private static Job<?, ?> findJobByName(String jobName, String itemName, Collection<? extends TopLevelItem> items, String path) {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.finer("Find: " + jobName + " / " + itemName + " path=" + path);
        }
        for (TopLevelItem item : items) {
            // Item groups (WorkflowMultiBranchProject)
            if (itemName != null && item instanceof ItemGroup && item.getName().equals(jobName)) {
                if (LOGGER.isLoggable(Level.FINER)) {
                    LOGGER.finer(" - ItemGroup: " + item.getName() + " path=" + path);
                }
                try {
                    Object job = ((ItemGroup<?>) item).getItem(itemName);
                    if (job != null) {
                        return (Job<?, ?>) job;
                    }
                }
                catch (Exception ex) {
                    LOGGER.warning("Unable to find job '" + jobName + "/" + itemName + "': "
                            + ex.getClass().getSimpleName() + " - " + ex.getMessage());
                }
            }
            // View groups (Folders)
            else if (item instanceof ViewGroup) {
                if (LOGGER.isLoggable(Level.FINER)) {
                    LOGGER.finer(" - ViewGroup: " + path + "/" + item.getName());
                }
                for (View view : ((ViewGroup) item).getAllViews()) {
                    if (LOGGER.isLoggable(Level.FINER)) {
                        LOGGER.finer("   - View: " + path + "/" + item.getName() + "/" + view.getViewName());
                    }
                    Job<?, ?> job = findJobByName(jobName, itemName, view.getItems(), path + "/" + item.getName() + "/" + view.getViewName());
                    if (job != null) {
                        return job;
                    }
                }
            }
            // Jobs (FreeStyleProject, WorkflowJob, ...)
            else if (itemName == null && item instanceof Job) {
                if (LOGGER.isLoggable(Level.FINER)) {
                    LOGGER.finer(" - Job: " + path + "/" + item.getName());
                }
                if (item.getName().equals(jobName)) {
                    return (Job<?, ?>) item;
                }
            }
            else if (LOGGER.isLoggable(Level.FINER)) {
                LOGGER.finer(" - Unknown: " + path + "/" + item.getName() + " (" + item.getClass() + ")");
            }
        }
        return null;
    }

}
