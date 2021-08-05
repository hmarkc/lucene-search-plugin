package org.jenkinsci.plugins.lucene.search;

import hudson.search.SearchIndex;
import hudson.search.SearchItem;

import java.util.List;
import java.util.regex.Pattern;

public class FreeTextSearchItemImplementation extends FreeTextSearchItem {

    private static final Pattern LINE_ENDINGS = Pattern.compile("(\\r\\n|\\n|\\r)");

    private final String buildNumber;
    private final String projectName;
    private final String iconFileName;
    private final String[] bestFragments;
    private final String url;
    private final String searchName;

    public FreeTextSearchItemImplementation(final String projectName, final String buildNumber,
            final String[] bestFragments, final String iconFileName, final String url) {
        this.projectName = projectName;
        this.buildNumber = buildNumber;
        this.searchName = projectName + " #" + buildNumber;
        this.url = url;

        this.bestFragments = new String[bestFragments.length];
        for (int i = 0; i < bestFragments.length; i++) {
            this.bestFragments[i] = LINE_ENDINGS.matcher(bestFragments[i]).replaceAll("<br/>");
        }
        this.iconFileName = iconFileName;
    }

    @Override
    public String getSearchUrl() {
        return url;
    }

    @Override
    public String getSearchName() {
        return searchName;
    }

    public String getProjectName() {
        return projectName;
    }

    public String[] getBestFragments() {
        return bestFragments;
    }

    public String getIconFileName() {
        return iconFileName;
    }

    @Override
    public boolean isShowConsole() {
        return true;
    }

    @Override
    public SearchIndex getSearchIndex() {
        return new SearchIndex() {

            @Override
            public void suggest(final String token, final List<SearchItem> result) {
            }

            @Override
            public void find(final String token, final List<SearchItem> result) {
            }
        };
    }
}
