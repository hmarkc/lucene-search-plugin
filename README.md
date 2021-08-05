# Overview

Lucene search is a powerful search library. Many popular search engines like Elastic Search and Solr are built upon it. Lucene Search Plugin uses Lucene library to index and search the console log content. It is embedded in the top search bar of Jenkins.

## Database Rebuild

If you have job data before the installation of Lucene Search, you need to rebuild the database manually. Note that any new builds after the installation of Lucene Search will be indexed automatically and the index of deleted builds and jobs will be deleted accordingly.

To index the exiting data, you need to go to "Manage Jenkins -> Lucene Search Manager" and click rebuild. You can enter the jobs that you want to index. If nothing is entered, all jobs will be indexed by default. There are two modes of rebuild available. In "overwrite" mode, the indexer deletes old index of the job if there are any and then index the job. In "preserve" mode, the indexer searches for the build name. If the build is already indexed, it will skip to the next build. Otherwise, the build will be indexed.

The clean button will delete all your index. Please use it cautiously.

## Search Query

Lucene Search works in the top search bar of Jenkins. There are two kinds of search queries: single-job search and multi-job search. If you want to perform a search for a specific job, put the job name at the start of your query. If you enter only one word or the first word of your query is not recognized as a job name, the search will be conducted across different jobs.

As for the query syntax, you can consult [Apache Lucene Query Parser Syntax](https://lucene.apache.org/core/2_9_4/queryparsersyntax.html).
