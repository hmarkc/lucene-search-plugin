package org.jenkinsci.plugins.lucene.search;

import hudson.Extension;
import hudson.XmlFile;
import hudson.model.Run;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import org.apache.log4j.Logger;
import org.jenkinsci.plugins.lucene.search.databackend.SearchBackendManager;
import org.jenkinsci.plugins.lucene.search.management.LuceneManager;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;


@Extension
public class FreeTextSaveableListener extends SaveableListener {

    @Inject
    SearchBackendManager searchBackendManager;

    @Override
    public void onChange(Saveable o, XmlFile file) {
        if (o instanceof Run) {
            try {
                searchBackendManager.removeBuild((Run) o);
                searchBackendManager.storeBuild((Run) o);
            } catch (IOException e) {
                //
            }
        }
    }

}
