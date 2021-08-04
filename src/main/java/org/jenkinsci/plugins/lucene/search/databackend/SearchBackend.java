package org.jenkinsci.plugins.lucene.search.databackend;

import hudson.model.Job;
import hudson.model.Run;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import jenkins.model.Jenkins;

import org.apache.log4j.Logger;
import org.jenkinsci.plugins.lucene.search.Field;
import org.jenkinsci.plugins.lucene.search.FreeTextSearchExtension;
import org.jenkinsci.plugins.lucene.search.FreeTextSearchItemImplementation;

public abstract class SearchBackend<T> {

    private static final Logger LOGGER = Logger.getLogger(SearchBackend.class);
    private final AtomicBoolean abort = new AtomicBoolean(false);

    @SuppressWarnings("rawtypes")
    private class RebuildBuildWorker implements RunWithArgument<Run> {

        private final Progress progress;
        private final boolean overwrite;

        private RebuildBuildWorker(Progress progress, boolean overwrite) {
            this.progress = progress;
            this.overwrite = overwrite;
        }

        @Override
        public void run(Run run) {
            try {
                if (overwrite) {
                    storeBuild(run);
                } else {
                    if (!findRunIndex(run)) {
                        storeBuild(run);
                    }
                }
            } catch (Exception e) {
                progress.completedWithErrors(e);
                LOGGER.warn("Error rebuilding build", e);
            } finally {
                progress.incCurrent();
            }
        }
    }

    public abstract void close();

    public abstract void storeBuild(final Run<?, ?> run) throws IOException;

    public abstract boolean findRunIndex(Run<?, ?> run);

    public abstract List<FreeTextSearchItemImplementation> getHits(final String query, boolean searchNext);

    public abstract SearchBackend<?> reconfigure(Map<String, Object> config);

    public abstract void removeBuild(Run<?, ?> run);

//    public abstract void cleanDeletedBuilds(Progress progress, Job<?, ?> job) throws Exception;

    public abstract void deleteJob(String jobName);

    public void abort() {
        abort.set(true);
    }

    @SuppressWarnings("rawtypes")
    public void rebuildJob(Progress progress, Job<?, ?> job, int maxWorkers, boolean overwrite) {
        BurstExecutor<Run> burstExecutor = BurstExecutor.create(new RebuildBuildWorker(progress, overwrite), maxWorkers)
               .andStart();
        if (overwrite) {
            deleteJob(job.getName());
        }
        for (Run<?, ?> run : job.getBuilds()) {
            if (abort.get()) {
                break;
            }
            progress.setMax(progress.getMax() + 1);
            burstExecutor.add(run);
//            try {
//                if (overwrite) {
//                    storeBuild(run);
//                } else {
//                    if (!findRunIndex(run)) {
//                        storeBuild(run);
//                    }
//                }
//            } catch (Exception e) {
//                progress.completedWithErrors(e);
//                LOGGER.warn("Error rebuilding build", e);
//            } finally {
//                progress.incCurrent();
//            }
//        } catch(Exception e){
//            e.printStackTrace();
//            LOGGER.error("During rebuildJob, this error occurs: ", e);
//        }
        }
        try {
            burstExecutor.waitForCompletion();
        } catch (InterruptedException e) {
            e.printStackTrace();
            LOGGER.warn("Why was I interrupted?", e);
        }
    }

    // Caching this method might be dangerous
    protected String[] getAllDefaultSearchableFields() {
        List<String> fieldNames = new LinkedList<String>();
        for (Field field : Field.values()) {
            if (field.defaultSearchable) {
                fieldNames.add(field.fieldName);
            }
        }
        for (FreeTextSearchExtension extension : FreeTextSearchExtension.all()) {
            if (extension.isDefaultSearchable()) {
                fieldNames.add(extension.getKeyword());
            }
        }
        return fieldNames.toArray(new String[fieldNames.size()]);
    }

    @SuppressWarnings("rawtypes")
    public void rebuildDatabase(ManagerProgress progress, int maxWorkers, Set<String> jobNames, boolean overwrite) {
        LOGGER.debug("rebuild database started in search backend");
        abort.set(false);
        List<Job> allItems = Jenkins.getInstance().getAllItems(Job.class);
        try {
//            Progress cleanProgress = progress.beginCleanJob();
//            cleanDeletedJobs(cleanProgress);
//            progress.jobComplete();
//            progress.assertNoErrors();
//            ensureOpen();
            if (!jobNames.isEmpty()) {
                progress.setMax(jobNames.size());
                for (Job job : allItems) {
                    if (jobNames.contains(job.getName())) {
                        rebuildSingleJob(progress, job, maxWorkers, overwrite);
                        jobNames.remove(job.getName());
                    }
                    if (jobNames.isEmpty()) {
                        progress.setSuccessfullyCompleted();
                        return;
                    }
                }
            } else {
                progress.setMax(allItems.size());
                for (Job job : allItems) {
                    rebuildSingleJob(progress, job, maxWorkers, overwrite);
                }
                progress.setSuccessfullyCompleted();
            }
        } catch (Exception e) {
            progress.completedWithErrors(e);
            LOGGER.error("Rebuild database failed", e);
        } finally {
            progress.setFinished();
        }
    }

    private void rebuildSingleJob(ManagerProgress progress, Job job, int maxWorkers, boolean overwrite) throws Exception {
        Progress currentJobProgress = progress.beginJob(job);
        try {
            if (job.getBuilds().isEmpty()) {
                deleteJob(job.getName());
            } else {
//                cleanDeletedBuilds(currentJobProgress, job);
//                progress.assertNoErrors();
                rebuildJob(currentJobProgress, job, maxWorkers, overwrite);
                progress.assertNoErrors();
            }
        } finally {
            progress.jobComplete();
        }
    }

    protected String[] getAllFields() {
        List<String> fieldNames = new LinkedList<String>();
        for (Field field : Field.values()) {
            fieldNames.add(field.fieldName);
        }
        for (FreeTextSearchExtension extension : FreeTextSearchExtension.all()) {
            fieldNames.add(extension.getKeyword());
        }
        return fieldNames.toArray(new String[fieldNames.size()]);
    }

    public abstract void cleanAllJob(ManagerProgress progress);
}
