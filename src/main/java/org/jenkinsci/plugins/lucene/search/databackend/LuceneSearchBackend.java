package org.jenkinsci.plugins.lucene.search.databackend;

import hudson.model.BallColor;
import hudson.model.Run;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.QueryTermScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.jenkinsci.plugins.lucene.search.Field;
import org.jenkinsci.plugins.lucene.search.FreeTextSearchExtension;
import org.jenkinsci.plugins.lucene.search.FreeTextSearchItemImplementation;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.jenkinsci.plugins.lucene.search.Field.*;

public class LuceneSearchBackend extends SearchBackend<Document> {
    private static final Logger LOGGER = Logger.getLogger(LuceneSearchBackend.class);

    private static final int MAX_NUM_FRAGMENTS = 5;
    private static final String[] EMPTY_ARRAY = new String[0];
    private static final Locale LOCALE = Locale.ENGLISH;

    private static final org.apache.lucene.document.Field.Store DONT_STORE = org.apache.lucene.document.Field.Store.NO;
    private static final org.apache.lucene.document.Field.Store STORE = org.apache.lucene.document.Field.Store.YES;

    private enum LuceneFieldType {
        STRING, LONG, TEXT
    }

    static final Map<Field, LuceneFieldType> FIELD_TYPE_MAP;

    static {
        Map<Field, LuceneFieldType> types = new HashMap<Field, LuceneFieldType>();
        types.put(PROJECT_NAME, LuceneFieldType.TEXT);
        types.put(BUILD_NUMBER, LuceneFieldType.STRING);
        types.put(START_TIME, LuceneFieldType.LONG);
        types.put(CONSOLE, LuceneFieldType.TEXT);
        FIELD_TYPE_MAP = Collections.unmodifiableMap(types);
    }

    private static final int MAX_HITS_PER_PAGE = 100;

    private final Directory index;
    private final Analyzer analyzer;
    private final IndexWriter dbWriter;
    private ScoreDoc lastDoc;

    public LuceneSearchBackend(final File indexPath) throws IOException {
        analyzer = new CaseSensitiveAnalyzer();
        index = FSDirectory.open(indexPath.toPath());
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        dbWriter = new IndexWriter(index, config);
        dbWriter.commit();
    }

    public static LuceneSearchBackend create(final Map<String, Object> config) {
        try {
            LOGGER.debug("create starts");
            return new LuceneSearchBackend(getIndexPath(config));
        } catch (IOException e) {
            LOGGER.error("create lucene search backend failed: " + e);
        }
        return null;
    }

    private static File getIndexPath(final Map<String, Object> config) {
        return (File) config.get("lucenePath");
    }

    @Override
    public SearchBackend<Document> reconfigure(final Map<String, Object> newConfig) {
        close();
        return create(newConfig);
    }

    public void close() {
        IOUtils.closeQuietly(dbWriter);
//        IOUtils.closeQuietly(reader);
        IOUtils.closeQuietly(index);
    }

    private Long getWithDefault(String number, Long defaultNumber) {
        if (number != null) {
            Long l = Long.getLong(number);
            if (l != null) {
                return l;
            }
        }
        return defaultNumber;
    }

    private Query parseNameAndConsole(String q, IndexSearcher searcher) throws ParseException, IOException {

        List<String> words = new ArrayList<>(Arrays.asList(q.trim().split(" ", 2)));
        words.removeAll(Arrays.asList("", null));

        if (words.size() >= 2) {
            Query jobNameQuery = new TermQuery(new Term(PROJECT_NAME.fieldName, words.get(0)));
            if (searcher.search(jobNameQuery, 1).scoreDocs.length > 0) {
//                String[] queries = {words.get(0), words.get(1)};
//                String[] fields = {PROJECT_NAME.fieldName, CONSOLE.fieldName};
//                BooleanClause.Occur[] clauses = {BooleanClause.Occur.MUST, BooleanClause.Occur.MUST};
//                return MultiFieldQueryParser.parse(queries, fields, clauses, analyzer);
                return new BooleanQuery.Builder()
                        .add(new TermQuery(new Term(PROJECT_NAME.fieldName, words.get(0))), BooleanClause.Occur.MUST)
                        .add(getQueryParser().parse(words.get(1)), BooleanClause.Occur.MUST)
                        .build();
            }
        }

        MultiFieldQueryParser queryParser = getQueryParser();
        return queryParser.parse(q).rewrite(searcher.getIndexReader());
    }

    @Override
    public List<FreeTextSearchItemImplementation> getHits(String q, boolean searchNext) {
        LOGGER.debug("getHits starts");
        List<FreeTextSearchItemImplementation> luceneSearchResultImpl = new ArrayList<>();
        try {
            IndexReader reader = DirectoryReader.open(index);
            IndexSearcher searcher = new IndexSearcher(reader);
            Query query = parseNameAndConsole(q, searcher);
            QueryTermScorer scorer = new QueryTermScorer(query);
            Highlighter highlighter = new Highlighter(new SimpleHTMLFormatter(), scorer);
            ScoreDoc[] hits;
            if (searchNext) {
                hits = searcher.searchAfter(lastDoc, query, MAX_HITS_PER_PAGE).scoreDocs;
            } else {
                hits = searcher.searchAfter(null, query, MAX_HITS_PER_PAGE).scoreDocs;
            }
            if (hits.length != 0) {
                lastDoc = hits[hits.length - 1];
            }

            for (ScoreDoc hit : hits) {
                Document doc = searcher.doc(hit.doc);
                String[] bestFragments = EMPTY_ARRAY;
                try {
                    bestFragments = highlighter.getBestFragments(analyzer, CONSOLE.fieldName,
                            doc.get(CONSOLE.fieldName), MAX_NUM_FRAGMENTS);
                } catch (InvalidTokenOffsetsException e) {
                    LOGGER.warn("Failed to find bestFragments", e);
                }

                BallColor buildIcon = BallColor.BLUE;

                String projectName = doc.get(PROJECT_NAME.fieldName);
                String buildNumber = doc.get(BUILD_NUMBER.fieldName);

                String url = "/job/" + projectName + "/" + buildNumber + "/";

                luceneSearchResultImpl.add(new FreeTextSearchItemImplementation(projectName, buildNumber, bestFragments, buildIcon.getImage(), url));
            }
            reader.close();
        } catch (ParseException e) {
//            LOGGER.warn("Search Parsing Error: ", e);
        } catch (IOException e) {
            LOGGER.warn("Search IO Error: ", e);
        } catch (AlreadyClosedException e) {
            LOGGER.warn("IndexReader is closed: ", e);
//        } finally {
//            if (reader != null) {
//                try {
//                    reader.close();
//                } catch (IOException e) {
//                    LOGGER.error("Reader cannot be closed" + e);
//                }
//            }
        }
        LOGGER.debug("getHits ends");
        return luceneSearchResultImpl;
    }

    private MultiFieldQueryParser getQueryParser() {
        MultiFieldQueryParser queryParser = new MultiFieldQueryParser(getAllDefaultSearchableFields(), analyzer) {
            @Override
            protected Query getRangeQuery(String field, String part1, String part2, boolean startInclusive,
                                          boolean endInclusive) throws ParseException {
                if (field != null && getIndex(field).numeric) {
                    Long min = getWithDefault(part1, null);
                    Long max = getWithDefault(part2, null);
                    return NumericRangeQuery.newLongRange(field, min, max, true, true);
                } else if (field != null) {
                    return new TermQuery(new Term(field));
                }
                return super.getRangeQuery(null, part1, part2, startInclusive, endInclusive);
            }
        };
        queryParser.setDefaultOperator(QueryParser.Operator.AND);
        queryParser.setLocale(LOCALE);
        queryParser.setAnalyzeRangeTerms(true);
        return queryParser;
    }

    @Override
    public void storeBuild(final Run<?, ?> run) throws IOException {
        try {
            Document doc = new Document();
            for (Field field : Field.values()) {
                org.apache.lucene.document.Field.Store store = field.persist ? STORE : DONT_STORE;
                Object fieldValue = field.getValue(run);
                if (fieldValue != null) {
                    switch (FIELD_TYPE_MAP.get(field)) {
                        case LONG:
                            doc.add(new LongField(field.fieldName, ((Number) fieldValue).longValue(), store));
                            break;
                        case STRING:
                            doc.add(new StringField(field.fieldName, fieldValue.toString(), store));
                            break;
                        case TEXT:
                            doc.add(new TextField(field.fieldName, fieldValue.toString(), store));
                            break;
                        default:
                            throw new IllegalArgumentException("Don't know how to handle " + FIELD_TYPE_MAP.get(field));
                    }
                }
            }

            for (FreeTextSearchExtension extension : FreeTextSearchExtension.all()) {
                try {
                    Object fieldValue = extension.getTextResult(run);
                    if (fieldValue != null) {
                        doc.add(new TextField(extension.getKeyword(), extension.getTextResult(run), (extension
                                .isPersist()) ? STORE : DONT_STORE));
                    }
                } catch (Throwable t) {
                    //We don't want to crash the collection of log from other plugin extensions if we happen to add a plugin that crashes while collecting the logs.
                    LOGGER.warn("CRASH: " + extension.getClass().getName() + ", " + extension.getKeyword() + t);
                }
            }
            dbWriter.addDocument(doc);
        } finally {
            try {
                dbWriter.commit();
            } catch (Exception e) {
                LOGGER.error("updateReader: " + e);
            }
        }
    }

    public Query getRunQuery(Run<?, ?> run) throws ParseException {
        assert (run != null);
        String[] queries = {run.getParent().getName(), String.valueOf(run.getNumber())};
        String[] fields = {PROJECT_NAME.fieldName, BUILD_NUMBER.fieldName};
        BooleanClause.Occur[] clauses = {BooleanClause.Occur.MUST, BooleanClause.Occur.MUST};
        return MultiFieldQueryParser.parse(queries, fields, clauses, analyzer);
    }

    @Override
    public boolean findRunIndex(Run<?, ?> run) {
        try {
            IndexReader reader = DirectoryReader.open(index);
            Query query = getRunQuery(run);
            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs docs = searcher.search(query, 1);
            reader.close();
            return docs.scoreDocs.length > 0;
        } catch (ParseException e) {
            LOGGER.warn("findRunIndex: " + e);
        } catch (IOException e) {
            LOGGER.warn("findRunIndex: " + e);
        }
        return false;
    }

    @Override
    public void removeBuild(Run<?, ?> run) {
        try {
            LOGGER.debug("The current run id is " + run.getId());
            dbWriter.deleteDocuments(getRunQuery(run));
            dbWriter.commit();
        } catch (IOException e) {
            LOGGER.warn("removeBuild: " + e);
        } catch (ParseException e) {
            //
        }
    }

    @Override
    public void deleteJob(String jobName) {
        LOGGER.error("Job deletion started for: " + jobName);
        try {
            Term term = new Term(PROJECT_NAME.fieldName, jobName);
            dbWriter.deleteDocuments(term);
            dbWriter.commit();
        } catch (IOException e) {
            LOGGER.error("Could not delete job", e);
        }
    }

    @Override
    public void cleanAllJob(ManagerProgress progress) {
        Progress currentProgress = progress.beginCleanJob();
        try {
            IndexReader reader = DirectoryReader.open(index);
            currentProgress.setCurrent(reader.numDocs());
            dbWriter.deleteAll();
            dbWriter.commit();
            progress.setSuccessfullyCompleted();
        } catch (IOException e) {
            progress.completedWithErrors(e);
        } finally {
            currentProgress.setFinished();
            progress.jobComplete();
        }
    }
}