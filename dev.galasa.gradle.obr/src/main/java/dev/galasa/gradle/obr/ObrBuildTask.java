/*
 * Licensed Materials - Property of IBM
 * 
 * (c) Copyright IBM Corp. 2020.
 */
package dev.galasa.gradle.obr;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;

import org.apache.felix.bundlerepository.DataModelHelper;
import org.apache.felix.bundlerepository.Repository;
import org.apache.felix.bundlerepository.Resource;
import org.apache.felix.bundlerepository.impl.DataModelHelperImpl;
import org.apache.felix.bundlerepository.impl.RepositoryImpl;
import org.apache.felix.bundlerepository.impl.ResourceImpl;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.tasks.TaskAction;

public class ObrBuildTask extends DefaultTask {

    private Path pathObr;

    @TaskAction
    public void genobr() throws Exception {
        getLogger().debug("Building OBR");


        DataModelHelper obrDataModelHelper = new DataModelHelperImpl();
        RepositoryImpl newRepository = new RepositoryImpl();

        Configuration config = getProject().getConfigurations().getByName("bundle");
        for(ResolvedDependency dependency : config.getResolvedConfiguration().getFirstLevelModuleDependencies()) {
            processBundle(obrDataModelHelper, newRepository, dependency);
        }
        
        config = getProject().getConfigurations().getByName("obr");
        for(ResolvedDependency dependency : config.getResolvedConfiguration().getFirstLevelModuleDependencies()) {
            processObr(obrDataModelHelper, newRepository, dependency);
        }
        
        if (newRepository.getResources().length == 0) {
            throw new ObrException("No resources were added to the OBR");
        }   
        
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
        newRepository.setLastModified(sdf.format(Calendar.getInstance().getTime()));

        try (BufferedWriter writer = Files.newBufferedWriter(this.pathObr)) {
            obrDataModelHelper.writeRepository(newRepository, writer);
        } catch (Exception e) {
            throw new ObrException("Problem with writing OBR", e);
        }
        
        if (newRepository.getResources().length == 1) {
            getLogger().info("Repository created with " + newRepository.getResources().length
                    + " resource stored in " + this.pathObr.toAbsolutePath().toString());
        } else {
            getLogger().info("Repository created with " + newRepository.getResources().length
                    + " resource stored in " + this.pathObr.toAbsolutePath().toString());
        }
    }

    private void processBundle(DataModelHelper obrDataModelHelper, RepositoryImpl newRepository, ResolvedDependency dependency) throws ObrException {
        String id = dependency.getName();
        getLogger().debug("Processing bundle " + id);
        
        Iterator<ResolvedArtifact> artifactIterator = dependency.getAllModuleArtifacts().iterator();
        if (!artifactIterator.hasNext()) {
            getLogger().warn("No artifacts found for " + id);
            return;
        }
        
        ResolvedArtifact artifact = artifactIterator.next();

        File file = artifact.getFile();
        
        String fileName = file.getName();
        if (!fileName.endsWith(".jar")) {
            getLogger().warn("Bundle " + id + " does not end with .jar, ignoring");
            return;
        }
        
        try {
            URI location = new URI("mvn:" + dependency.getModuleGroup() + "/" + dependency.getModuleName() + "/"
                    + dependency.getModuleVersion() + "/" + artifact.getType());
            
            ResourceImpl newResource = (ResourceImpl)obrDataModelHelper.createResource(file.toURI().toURL());
            if (newResource == null) {
                throw new ObrException("Problem with file '" + id + ". Not an OSGi bundle?");
            }
            newResource.put(Resource.URI, location);
            newRepository.addResource(newResource);

        } catch(ObrException e) {
            throw e;
        } catch(Exception e) {
            throw new ObrException("Unable to process bundle '" + id + "'", e);
        }
        
        if (artifactIterator.hasNext()) {
            getLogger().warn("Dependency " + id + " resolved to more than one artifact");
        }
        
    }
    
    
    private void processObr(DataModelHelper obrDataModelHelper, RepositoryImpl newRepository, ResolvedDependency dependency) throws ObrException {
        String id = dependency.getName();
        getLogger().warn("Processing OBR " + id);
        
        Iterator<ResolvedArtifact> artifactIterator = dependency.getAllModuleArtifacts().iterator();
        if (!artifactIterator.hasNext()) {
            getLogger().warn("No artifacts found for " + id);
            return;
        }
        
        ResolvedArtifact artifact = artifactIterator.next();

        File file = artifact.getFile();
        
        String fileName = file.getName();
        if (!fileName.endsWith(".obr")) {
            getLogger().warn("OBR " + id + " does end with .obr, ignoring");
            return;
        }
        
        try {
            try (FileReader fr = new FileReader(artifact.getFile())) {
                Repository mergeRepository = obrDataModelHelper.readRepository(fr);

                for (Resource resource : mergeRepository.getResources()) {
                    newRepository.addResource(resource);
                    getLogger().warn("Merged bundle " + resource.getPresentationName() + " - "
                            + resource.getId() + " to repository");
                }
            }
        } catch(ObrException e) {
            throw e;
        } catch(Exception e) {
            throw new ObrException("Unable to process OBR '" + id + "'", e);
        }
        
        if (artifactIterator.hasNext()) {
            getLogger().warn("Dependency " + id + " resolved to more than one artifact");
        }
    }



    public void apply() {
        // Run during apply phase, need to decide on the output file name
        Path buildDirectory = Paths.get(getProject().getBuildDir().toURI());
        this.pathObr = buildDirectory.resolve("galasa.obr");
        getOutputs().file(pathObr);
        
        ConfigurationContainer configurations = getProject().getConfigurations();
        Configuration bundleConfiguration = configurations.findByName("bundle");
        Configuration obrConfiguration = configurations.findByName("obr");
        
        getDependsOn().add(bundleConfiguration);
        getDependsOn().add(obrConfiguration);
    }

}
