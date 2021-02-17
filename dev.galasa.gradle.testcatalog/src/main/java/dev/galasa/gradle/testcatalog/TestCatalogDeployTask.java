/*
 * Licensed Materials - Property of IBM
 * 
 * (c) Copyright IBM Corp. 2020.
 */
package dev.galasa.gradle.testcatalog;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskAction;

public class TestCatalogDeployTask extends DefaultTask {

    @TaskAction
    public void genobr() throws Exception {
        getLogger().debug("Deploying Testcatalog");
        DeployTestCatalogExtension extension = getProject().getExtensions().findByType(DeployTestCatalogExtension.class);
        
        //*** we need the bootstrap URL and the test stream.   Eventually will need credentials as well
        
        if (extension.bootstrap == null || extension.bootstrap.trim().isEmpty()) {
            throw new TestCatalogException("The bootstrap url is missing in the deployTestCatalog extension");
        }
        
        URL urlBootstrap = null;
        try {
            urlBootstrap = new URL(extension.bootstrap.trim());
        } catch(Exception e) {
            throw new TestCatalogException("Invalid bootstrap url '" + extension.bootstrap + "' in the deployTestCatalog extension",e);
        }
        
        if (extension.stream == null || extension.stream.trim().isEmpty()) {
            throw new TestCatalogException("The test stream is missing in the deployTestCatalog extension");
        }
        
        String testStream = extension.stream.trim().toLowerCase();
        
        // *** Get the bootstrap
        Properties bootstrapProperties = new Properties();
        try {
            URLConnection connection = urlBootstrap.openConnection();
            bootstrapProperties.load(connection.getInputStream());
        } catch (Exception e) {
            throw new TestCatalogException("Unable to load the bootstrap properties", e);
        }

        // *** Calculate the testcatalog url
        String sTestcatalogUrl = bootstrapProperties.getProperty("framework.testcatalog.url");
        if (sTestcatalogUrl == null || sTestcatalogUrl.trim().isEmpty()) {
            String sBootstrapUrl = urlBootstrap.toString();
            if (!sBootstrapUrl.endsWith("/bootstrap")) {
                throw new TestCatalogException(
                        "Unable to calculate the test catalog url, the bootstrap url does not end with /bootstrap, need a framework.testcatalog.url property in the bootstrap");
            }

            sTestcatalogUrl = sBootstrapUrl.substring(0, sBootstrapUrl.length() - 10) + "/testcatalog";
        }

        // *** Check to see if we need authentication
        String sAuthenticationUrl = bootstrapProperties.getProperty("framework.authentication.url");
        if (sAuthenticationUrl != null) {
            throw new TestCatalogException("Unable to support Galasa authentication at the moment");
        }

        // *** Get the test catalog url
        URL testCatalogUrl = new URL(sTestcatalogUrl + "/" + testStream);

        HttpURLConnection conn = (HttpURLConnection) testCatalogUrl.openConnection();
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setRequestMethod("PUT");
        conn.addRequestProperty("Content-Type", "application/json");
        conn.addRequestProperty("Accept", "application/json");
        
        Path pathArtifact = Paths.get(getInputs().getFiles().getSingleFile().toURI());
        Files.copy(pathArtifact, conn.getOutputStream());
        int rc = conn.getResponseCode();
        String message = conn.getResponseMessage();

        InputStream is = null;
        if (rc < HttpURLConnection.HTTP_BAD_REQUEST) {
            is = conn.getInputStream();
        } else {
            is = conn.getErrorStream();
        }

        String response = "";
        if (is != null) {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                String line = null;
                while((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }
            response = sb.toString();
        }

        conn.disconnect();

        if (rc >= HttpURLConnection.HTTP_BAD_REQUEST) {
            getLogger().error("Deploy to Test Catalog Store failed:-");
            getLogger().error(Integer.toString(rc) + " - " + message);
            if (!response.isEmpty()) {
                getLogger().error(response);
            }

            throw new TestCatalogException("Deploy to Test Catalog Store failed");
        }

        getLogger().warn("Test Catalog successfully deployed to " + testCatalogUrl.toString());

    }



    public void apply() {
        Task gentestcat = getProject().getTasks().getByName("mergetestcat");
        
        getDependsOn().add(gentestcat);
        
        getInputs().files(gentestcat.getOutputs().getFiles());
    }

}
