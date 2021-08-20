package org.jenkinsci.plugins.lucene.search;

import javax.inject.Inject;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.listeners.ItemListener;
import org.jenkinsci.plugins.lucene.search.databackend.SearchBackendManager;

@Extension
public class FreeTextItemListener extends ItemListener {

    @Inject
    SearchBackendManager searchBackendManager;

    @Override
    public void onDeleted(Item item) {
        searchBackendManager.deleteJob(item.getName());
    }
}
