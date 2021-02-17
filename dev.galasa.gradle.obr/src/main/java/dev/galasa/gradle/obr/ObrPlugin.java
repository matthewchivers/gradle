/*
 * Licensed Materials - Property of IBM
 * 
 * (c) Copyright IBM Corp. 2020.
 */
package dev.galasa.gradle.obr;

import javax.inject.Inject;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.api.internal.artifacts.ArtifactAttributes;
import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact;
import org.gradle.api.provider.Provider;

/**
 * Generate an OBR
 */
public class ObrPlugin implements Plugin<Project> {
    
    private final SoftwareComponentFactory softwareComponentFactory;

    @Inject
    public ObrPlugin(SoftwareComponentFactory softwareComponentFactory) {
        this.softwareComponentFactory = softwareComponentFactory;
    }
    
    public void apply(Project project) {
        
        createInboundConfigurations(project);
        createOutboundConfigurations(project);
        createSoftwareComponents(project);
        createObrBuildTask(project);
        
        addVariantsToObr(project);
        
    }

    private void addVariantsToObr(Project project) {
        
        // Add all the variants from the outbound configuration to the software component
        
        Configuration configurationObr = project.getConfigurations().getByName("galasagenobr");
        AdhocComponentWithVariants componentObr = (AdhocComponentWithVariants) project.getComponents().getByName("galasaobr");

        // Add everything from the config
        ((AdhocComponentWithVariants)componentObr).addVariantsFromConfiguration(configurationObr, c -> {
        });
    }

    private void createObrBuildTask(Project project) {
        
        // Create the new Task, called genobr
        Provider<ObrBuildTask> provider = project.getTasks().register("genobr", ObrBuildTask.class, obrTask -> {
            obrTask.apply();
        });
        
        // Create the Publish Artifact that the task will be creating and add it the 
        // configuration outbound list
        LazyPublishArtifact artifact = new LazyPublishArtifact(provider);
        project.getConfigurations().getByName("galasagenobr").getOutgoing().artifact(artifact);
    }

    private void createSoftwareComponents(Project project) {
        // Software components are what the tasks are going to be produced, can be 
        // a merging or multiple components and variants.
        // in our case, we need a component for the OBR and one for the test catalog
        // we don't want the test catalog to be in the same component as the obr
        // as we don't need the build task for the test catalog driven for the 
        // framework/extensions/managers projects
        //
        // you can use the software component in publishing with "from components.galasaobr"
        
        AdhocComponentWithVariants componentObr = softwareComponentFactory.adhoc("galasaobr");
        project.getComponents().add(componentObr);
    }

    private void createInboundConfigurations(Project project) {
        
        ConfigurationContainer configurations = project.getConfigurations();
        
        // Create two new configurations that are used as dependencies
        // dependendencies {
        //     bundle('xxxxx')
        //     obr('xxx')
        // }
        Configuration bundleConfiguration = configurations.maybeCreate("bundle");
        bundleConfiguration.setTransitive(false); // Do not resolve the dependency chain, we only want the top level
        Configuration obrConfiguration = configurations.maybeCreate("obr");
        obrConfiguration.setTransitive(false); // Do not resolve the dependency chain, we only want the top level
    }

    private void createOutboundConfigurations(Project project) {
        ConfigurationContainer configurations = project.getConfigurations();

        // create "outbound" configurations, this is the stuff this plugin is going to generate
        // one configuration per "type",  ie one for obr and one for testcatalog.
        // need it kind of unique so we don't clash with anything from other plugins
        Configuration genobrConfiguration = configurations.create("galasagenobr");
        genobrConfiguration.setCanBeResolved(false);
        genobrConfiguration.setCanBeConsumed(true);
        genobrConfiguration.attributes(t -> {
            // This is required for the metadata publishing, needs atleast one attribute
            // so it can be selective if necessary
           t.attribute(ArtifactAttributes.ARTIFACT_FORMAT, "obr");
        });
    }
}
