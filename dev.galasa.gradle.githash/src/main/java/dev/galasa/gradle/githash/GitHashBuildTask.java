/*
 * Licensed Materials - Property of IBM
 * 
 * (c) Copyright IBM Corp. 2020, 2021.
 */
package dev.galasa.gradle.githash;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.tasks.Jar;

public class GitHashBuildTask extends DefaultTask {

    private SourceSet sourceSet;
    private File gitHash;

    @TaskAction
    public void buildHash() throws Exception {

        //*** Ensure the meta dir exists
        gitHash.getParentFile().mkdirs();
        
        HashesExtension extension = getProject().getExtensions().findByType(HashesExtension.class);
        
        String gitHash = extension.gitHash;
        if (gitHash == null || gitHash.trim().isEmpty()) {
            gitHash = "unknown";
        }
        
        Path path = Paths.get(this.gitHash.toURI());
        Files.write(path, gitHash.getBytes(StandardCharsets.UTF_8));
        
    }

    public void apply() {
        
        SourceSetContainer sourceSets = getProject().getExtensions().getByType(SourceSetContainer.class);
        this.sourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);

        //*** If any source file has changed, we must rerun this task
        getInputs().files(this.sourceSet.getAllSource().getFiles());
        
        //*** We are dependent on the generic Classes task
        Task classes = getProject().getTasks().getByName("classes");
        getDependsOn().add(classes);

        //*** Make sure the jar task is dependent on us
        Jar jarTask = (Jar) getProject().getTasks().getByName("jar");
        jarTask.getDependsOn().add(this);

        //*** Create the directories so Gradle does not moan
        File dirGenTestCatalog = new File(getProject().getBuildDir(),"hashes");
        File dirGenTestCatalogMeta = new File(dirGenTestCatalog,"META-INF");
        //*** dont need to create the file here, just indicate where it will be on the outputs
        this.gitHash = new File(dirGenTestCatalogMeta,"git.hash");

        //*** Tell the JAR task we want to it to include the meta-inf and json file
        jarTask.from(dirGenTestCatalog);

        //*** Mark the meta-inf file as output for caching purposes
        getOutputs().dir(dirGenTestCatalog);
    }

}
