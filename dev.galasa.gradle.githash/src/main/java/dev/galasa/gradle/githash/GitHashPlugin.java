/*
 * Licensed Materials - Property of IBM
 * 
 * (c) Copyright IBM Corp. 2020.
 */
package dev.galasa.gradle.githash;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;

/**
 * Add hashes to the test/manager projects
 */
public class GitHashPlugin implements Plugin<Project> {
    
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);
        
        createHashBuildTask(project);
        
        project.getExtensions().create("hashes", HashesExtension.class, project.getObjects());

    }

    private void createHashBuildTask(Project project) {
        // Create the new Task, called gentestcatalog
        project.getTasks().create("githash", GitHashBuildTask.class, tcTask -> {
            tcTask.apply();
        });
    }

}
