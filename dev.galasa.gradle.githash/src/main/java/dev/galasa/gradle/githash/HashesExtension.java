package dev.galasa.gradle.githash;

import javax.inject.Inject;

import org.gradle.api.model.ObjectFactory;

public class HashesExtension {
    
    @Inject
    public HashesExtension(ObjectFactory objectFactory) {
    }

    public String gitHash;
    
    public void setGitGash(String gitHash) {
        this.gitHash = gitHash;
    }
    
}
