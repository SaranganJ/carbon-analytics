/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.stream.processor.core.internal;

import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.deployment.engine.Artifact;
import org.wso2.carbon.deployment.engine.ArtifactType;
import org.wso2.carbon.deployment.engine.Deployer;
import org.wso2.carbon.deployment.engine.exception.CarbonDeploymentException;
import org.wso2.carbon.stream.processor.common.EventStreamService;
import org.wso2.carbon.stream.processor.core.internal.exception.SiddhiAppDeploymentException;
import org.wso2.carbon.stream.processor.core.internal.util.SiddhiAppProcessorConstants;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;

/**
 * {@code StreamProcessorDeployer} is responsible for all siddhiql file deployment tasks
 *
 * @since 1.0.0
 */

@Component(
        name = "stream-processor-deployer",
        immediate = true,
        service = org.wso2.carbon.deployment.engine.Deployer.class
)

public class StreamProcessorDeployer implements Deployer {


    private static final Logger log = LoggerFactory.getLogger(StreamProcessorDeployer.class);
    private static final String FILE_EXTENSION = ".siddhi";
    private ArtifactType artifactType = new ArtifactType<>("siddhi");
    private URL directoryLocation;

    public static void deploySiddhiQLFile(File file) throws Exception {
        InputStream inputStream = null;

        try {
            inputStream = new FileInputStream(file);
            if (file.getName().endsWith(FILE_EXTENSION)) {
                String executionPlan = getStringFromInputStream(inputStream);
                StreamProcessorDataHolder.getStreamProcessorService().deploySiddhiApp(executionPlan,
                                                                                          file.getName());
            } else {
                throw new SiddhiAppDeploymentException(("Error: File extension not supported for file name "
                                                            + file.getName() + ". Support only"
                                                            + FILE_EXTENSION + " ."));
            }
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    StreamProcessorDataHolder.getInstance().setRuntimeMode(SiddhiAppProcessorConstants.RuntimeMode.ERROR);
                    throw new SiddhiAppDeploymentException("Error when closing the Siddhi QL filestream", e);
                }
            }
        }
    }

    private static String getStringFromInputStream(InputStream is) throws SiddhiAppDeploymentException {

        BufferedReader br = null;
        StringBuilder sb = new StringBuilder();

        String line;
        try {
            br = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
            while ((line = br.readLine()) != null) {
                sb.append(System.getProperty("line.separator")).append(line);
            }
        } catch (IOException e) {
            throw new SiddhiAppDeploymentException("Exception when reading the Siddhi QL file", e);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    throw new SiddhiAppDeploymentException("Exception when closing the Siddhi QL file stream", e);
                }
            }
        }

        return sb.toString();
    }

    @Activate
    protected void activate(BundleContext bundleContext) {
        // Nothing to do.
    }

    @Override
    public void init() {
        try {
            directoryLocation = new URL("file:" + SiddhiAppProcessorConstants.SIDDHIQL_FILES_DIRECTORY);
            log.info("Stream Processor Deployer Initiated");
        } catch (MalformedURLException e) {
            log.error("Error while initializing directoryLocation" + SiddhiAppProcessorConstants.
                    SIDDHIQL_FILES_DIRECTORY, e);
        }
    }

    @Override
    public Object deploy(Artifact artifact) throws CarbonDeploymentException {

        if (StreamProcessorDataHolder.getInstance().getRuntimeMode().equals(SiddhiAppProcessorConstants.
                RuntimeMode.SERVER)) {
            try {
                deploySiddhiQLFile(artifact.getFile());
            } catch (Exception e) {
                throw new CarbonDeploymentException(e.getMessage(), e);
            }
        }
        return artifact.getFile().getName();
    }

    @Override
    public void undeploy(Object key) throws CarbonDeploymentException {
        if (StreamProcessorDataHolder.getInstance().getRuntimeMode().equals(SiddhiAppProcessorConstants.
                RuntimeMode.SERVER)) {
            StreamProcessorDataHolder.getStreamProcessorService().undeployExecutionPlan((String) key);
        }
    }

    @Override
    public Object update(Artifact artifact) throws CarbonDeploymentException {

        if (StreamProcessorDataHolder.getInstance().getRuntimeMode().equals(SiddhiAppProcessorConstants.
                RuntimeMode.SERVER)) {
            StreamProcessorDataHolder.getStreamProcessorService().undeployExecutionPlan(artifact.getName());
            try {
                deploySiddhiQLFile(artifact.getFile());
            } catch (Exception e) {
                throw new CarbonDeploymentException(e.getMessage(), e);
            }
        }
        return artifact.getName();
    }

    @Override
    public URL getLocation() {
        return directoryLocation;
    }

    @Override
    public ArtifactType getArtifactType() {
        return artifactType;
    }

    /**
     * This bind method will be called when Greeter OSGi service is registered.
     */
    @Reference(
            name = "carbon.event.stream.service",
            service = EventStreamService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetGreeterService"
    )
    protected void setGreeterService(EventStreamService eventStreamService) {

    }

    /**
     * This is the unbind method which gets called at the un-registration of CarbonRuntime OSGi service.
     */
    protected void unsetGreeterService(EventStreamService eventStreamService) {

    }


}