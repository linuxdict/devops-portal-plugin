package io.jenkins.plugins.devopsportal.utils;

import hudson.model.*;
import jenkins.model.Jenkins;

import java.util.Collection;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Utility class to read data from Jenkins.
 *
 * @author Rémi BELLO {@literal <remi@evolya.fr>}
 */
public final class JenkinsUtils {

    private static final Logger LOGGER = Logger.getLogger("io.jenkins.plugins.devopsportal");

    private JenkinsUtils() {
    }

    public static Optional<Run<?, ?>> getBuild(String jobName, String branchName, String buildNumber) {
        if (Jenkins.getInstanceOrNull() == null) {
            return Optional.empty();
        }
        if (jobName == null || jobName.trim().isEmpty()) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Job name is null or empty");
            }
            return Optional.empty();
        }
        if (branchName != null && branchName.isEmpty()) {
            branchName = null;
        }

        // First try the enhanced path-based lookup for folder support
        if (jobName.contains("/")) {
            Job<?, ?> job = findJobByPath(jobName, branchName);
            if (job != null) {
                return Optional.ofNullable(job.getBuild(buildNumber));
            }
        }

        // Fallback to original logic for backward compatibility
        Job<?, ?> job = findJobByName(jobName, branchName, Jenkins.get().getItems());
        if (job == null) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Job not found: " + jobName + (branchName != null ? "/" + branchName : ""));
            }
            return Optional.empty();
        }
        return Optional.ofNullable(job.getBuild(buildNumber));
    }

    /**
     * Find a job by its full path, supporting nested folders.
     * @param fullJobPath The full path to the job (e.g., "folder1/folder2/jobName")
     * @param branchName The branch name for multi-branch projects (can be null)
     * @return The job if found, null otherwise
     */
    public static Job<?, ?> findJobByPath(String fullJobPath, String branchName) {
        if (fullJobPath == null || fullJobPath.isEmpty()) {
            return null;
        }

        String[] pathParts = fullJobPath.split("/");
        if (pathParts.length == 0) {
            return null;
        }

        // Start from Jenkins root
        Collection<? extends TopLevelItem> items = Jenkins.get().getItems();
        ItemGroup<?> currentGroup = Jenkins.get();

        // Navigate through folders
        for (int i = 0; i < pathParts.length - 1; i++) {
            String folderName = pathParts[i];
            TopLevelItem item = null;

            // Find the folder in current items
            for (TopLevelItem currentItem : items) {
                if (currentItem.getName().equals(folderName) && currentItem instanceof ItemGroup) {
                    item = currentItem;
                    break;
                }
            }

            if (item == null || !(item instanceof ItemGroup)) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Folder not found: " + folderName + " in path: " + fullJobPath);
                }
                return null;
            }

            currentGroup = (ItemGroup<?>) item;
            try {
                Collection<? extends Item> folderItems = currentGroup.getItems();
                if (folderItems == null) {
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.fine("Folder items is null for: " + folderName + " in path: " + fullJobPath);
                    }
                    return null;
                }
                // Convert to TopLevelItem collection for next iteration
                items = folderItems.stream()
                        .filter(TopLevelItem.class::isInstance)
                        .map(TopLevelItem.class::cast)
                        .collect(Collectors.toList());
            } catch (Exception ex) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Error accessing folder items for: " + folderName + " - " + ex.getMessage());
                }
                return null;
            }
        }

        // Get the final job name
        String jobName = pathParts[pathParts.length - 1];

        // Look for the job in the final folder
        try {
            Collection<? extends Item> finalItems = currentGroup.getItems();
            if (finalItems == null) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Final folder items is null for path: " + fullJobPath);
                }
                return null;
            }

            for (Item item : finalItems) {
                if (item.getName().equals(jobName)) {
                    if (branchName != null && item instanceof ItemGroup) {
                        // Multi-branch project
                        try {
                            Object branchJob = ((ItemGroup<?>) item).getItem(branchName);
                            if (branchJob instanceof Job) {
                                return (Job<?, ?>) branchJob;
                            }
                        } catch (Exception ex) {
                            LOGGER.warning("Unable to find branch '" + branchName + "' in multi-branch project '" + jobName + "': " + ex.getMessage());
                        }
                    } else if (branchName == null && item instanceof Job) {
                        // Regular job
                        return (Job<?, ?>) item;
                    }
                }
            }
        } catch (Exception ex) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Error accessing final folder items for path: " + fullJobPath + " - " + ex.getMessage());
            }
        }

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Job not found: " + fullJobPath + (branchName != null ? "/" + branchName : ""));
        }
        return null;
    }

    public static Job<?, ?> findJobByName(String jobName, String itemName, Collection<? extends TopLevelItem> items) {
        return findJobByName(jobName, itemName, items, "");
    }

    /**
     * This function is used to retrieve a job instance from Jenkins persistent data.
     * It has multiple behaviors:
     * - It is used to retrieve a simple element (job) by designating it by its name.
     *   In this case, only the jobName is required and itemName must be null.
     * - It allows to get the sub-job in the case of a complex object like a
     *   Multi-Branch pipeline. In this case, itemName must be given.
     * - It properly handles jobs nested in folders at any depth.
     */
    private static Job<?, ?> findJobByName(String jobName, String itemName, Collection<? extends TopLevelItem> items, String path) {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.finer("Find: " + jobName + " / " + itemName + " path=" + path);
        }
        for (TopLevelItem item : items) {
            if (LOGGER.isLoggable(Level.FINER)) {
                LOGGER.finer(" - Processing item: " + item.getName() + " (" + item.getClass().getSimpleName() + ")");
            }

            // Item groups (includes both Folders and WorkflowMultiBranchProject)
            if (item instanceof ItemGroup) {
                ItemGroup<?> itemGroup = (ItemGroup<?>) item;

                // If we're looking for a multi-branch job's branch
                if (itemName != null && item.getName().equals(jobName)) {
                    if (LOGGER.isLoggable(Level.FINER)) {
                        LOGGER.finer(" - Found target ItemGroup: " + item.getName() + " looking for branch: " + itemName);
                    }
                    try {
                        Object job = itemGroup.getItem(itemName);
                        if (job instanceof Job) {
                            return (Job<?, ?>) job;
                        }
                    }
                    catch (Exception ex) {
                        LOGGER.warning("Unable to find job '" + jobName + "/" + itemName + "': "
                                + ex.getClass().getSimpleName() + " - " + ex.getMessage());
                    }
                }

                // For folders and other containers, recursively search their items
                if (LOGGER.isLoggable(Level.FINER)) {
                    LOGGER.finer(" - Recursing into ItemGroup: " + path + "/" + item.getName());
                }
                try {
                    Collection<? extends Item> subItems = itemGroup.getItems();
                    // Convert to TopLevelItem collection and recursively search
                    Collection<TopLevelItem> topLevelItems = subItems.stream()
                            .filter(TopLevelItem.class::isInstance)
                            .map(TopLevelItem.class::cast)
                            .collect(Collectors.toList());
                    Job<?, ?> foundJob = findJobByName(jobName, itemName, topLevelItems, path + "/" + item.getName());
                    if (foundJob != null) {
                        return foundJob;
                    }
                } catch (Exception ex) {
                    if (LOGGER.isLoggable(Level.WARNING)) {
                        LOGGER.warning("Error accessing items in folder '" + item.getName() + "': " + ex.getMessage());
                    }
                }
            }

            // View groups (additional search path)
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

            // Direct jobs (FreeStyleProject, WorkflowJob, ...)
            else if (itemName == null && item instanceof Job) {
                if (LOGGER.isLoggable(Level.FINER)) {
                    LOGGER.finer(" - Job: " + path + "/" + item.getName());
                }
                if (item.getName().equals(jobName)) {
                    return (Job<?, ?>) item;
                }
            }
            else if (LOGGER.isLoggable(Level.FINER)) {
                LOGGER.finer(" - Unknown/Unhandled: " + path + "/" + item.getName() + " (" + item.getClass() + ")");
            }
        }
        return null;
    }

}
