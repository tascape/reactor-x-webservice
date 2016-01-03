/*
 * Copyright 2016 tascape.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tascape.qa.th.driver;

import com.tascape.qa.th.driver.jmeter.Summariser;
import com.tascape.qa.th.SystemConfiguration;
import com.tascape.qa.th.comm.WebServiceCommunication;
import com.tascape.qa.th.driver.jmeter.SummariserRunningSample;
import java.io.File;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.config.gui.ArgumentsPanel;
import org.apache.jmeter.control.LoopController;
import org.apache.jmeter.control.gui.LoopControlPanel;
import org.apache.jmeter.control.gui.TestPlanGui;
import org.apache.jmeter.engine.StandardJMeterEngine;
import org.apache.jmeter.protocol.http.control.gui.HttpTestSampleGui;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerProxy;
import org.apache.jmeter.reporters.ResultCollector;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.TestPlan;
import org.apache.jmeter.threads.ThreadGroup;
import org.apache.jmeter.threads.gui.ThreadGroupGui;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.HashTree;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.jmeter.modifiers.BeanShellPreProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebServiceJMeter extends EntityDriver {
    private static final Logger LOG = LoggerFactory.getLogger(WebServiceJMeter.class);

    public static final String SYSPROP_JMETER_HOME = "qa.th.jmeter.home";

    public static final String SUMMARY = "th-summary";

    static {
        try {
            String jh = SystemConfiguration.getInstance().getProperty(SYSPROP_JMETER_HOME);
            Path jmeterHome = Paths.get(jh);
            LOG.info("Use jmeter home {}", jmeterHome);
            JMeterUtils.setJMeterHome(jmeterHome.toFile().getCanonicalPath());
            JMeterUtils.loadJMeterProperties(jmeterHome.resolve("bin").resolve("jmeter.properties").toFile()
                .getCanonicalPath());

            JMeterUtils.setProperty("summariser.log", "true");
            JMeterUtils.initLogging();
            JMeterUtils.initLocale();
        } catch (IOException ex) {
            throw new RuntimeException();
        }
    }

    private WebServiceCommunication wsc;

    private final StandardJMeterEngine jmeter;

    private ThreadGroup threadGroup;

    public WebServiceJMeter() throws IOException {
        this.jmeter = new StandardJMeterEngine();
    }

    public void setWebServiceCommunication(WebServiceCommunication wsc) {
        this.wsc = wsc;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    public void configureLoopThread(int loop, int thread) throws IOException {
        LoopController loopController = new LoopController();
        loopController.setLoops(loop);
        LOG.info("Number of loop(s) {}", loop);
        loopController.setFirst(true);
        loopController.setProperty(TestElement.TEST_CLASS, LoopController.class.getName());
        loopController.setProperty(TestElement.GUI_CLASS, LoopControlPanel.class.getName());
        loopController.initialize();

        this.threadGroup = new ThreadGroup();
        threadGroup.setName("th");
        threadGroup.setNumThreads(thread);
        LOG.info("Number of thread(s) {}", thread);
        threadGroup.setRampUp(thread / 5 + 1);
        threadGroup.setSamplerController(loopController);
        threadGroup.setProperty(TestElement.TEST_CLASS, ThreadGroup.class.getName());
        threadGroup.setProperty(TestElement.GUI_CLASS, ThreadGroupGui.class.getName());
    }

    public Summariser runGet(String path) throws IOException {
        TestPlan testPlan = this.newTestPlan();

        HashTree testPlanTree = new HashTree();
        testPlanTree.add(testPlan);
        HashTree threadGroupHashTree = testPlanTree.add(testPlan, this.threadGroup);
        threadGroupHashTree.add(this.newHttpGetSampler(path));

        BeanShellPreProcessor bs = new BeanShellPreProcessor();
        bs.setScript("log.info('CUSTOM INFO log &quot')");
        threadGroupHashTree.add(bs);

        File jmx = this.saveIntoFile("jmx", "xml", "");
        SaveService.saveTree(testPlanTree, new FileOutputStream(jmx));

        // add Summarizer output to get test progress in stdout like:
        // summary =      2 in   1.3s =    1.5/s Avg:   631 Min:   290 Max:   973 Err:     0 (0.00%)
        Summariser summary = new Summariser(SUMMARY);

        File logFile = this.saveAsTextFile("jmeter-get-log", "");
        ResultCollector logger = new ResultCollector(summary);
        logger.setFilename(logFile.getAbsolutePath());
        testPlanTree.add(testPlanTree.getArray()[0], logger);
        this.jmeter.configure(testPlanTree);
        this.jmeter.run();

        return summary;
    }

    @Override
    public void reset() throws Exception {
        if (jmeter != null) {
            this.jmeter.reset();
        }
    }

    public WebServiceCommunication getWebServiceCommunication() {
        return wsc;
    }

    private TestPlan newTestPlan() {
        TestPlan testPlan = new TestPlan("Testharness JMeter Script");
        testPlan.setProperty(TestElement.TEST_CLASS, TestPlan.class.getName());
        testPlan.setProperty(TestElement.GUI_CLASS, TestPlanGui.class.getName());
        testPlan.setUserDefinedVariables((Arguments) new ArgumentsPanel().createTestElement());
        return testPlan;
    }

    private HTTPSamplerProxy newHttpGetSampler(String path) {
        HTTPSamplerProxy httpSampler = new HTTPSamplerProxy();
        httpSampler.setName("th-get");
        httpSampler.setProtocol(wsc.getHttpHost().getSchemeName());
        httpSampler.setMethod("GET");
        httpSampler.setDomain(wsc.getHttpHost().getHostName());
        httpSampler.setPort(wsc.getHttpHost().getPort());
        httpSampler.setPath(path);
        httpSampler.setFollowRedirects(true);
        httpSampler.setUseKeepAlive(true);
        httpSampler.setProperty(TestElement.TEST_CLASS, HTTPSamplerProxy.class.getName());
        httpSampler.setProperty(TestElement.GUI_CLASS, HttpTestSampleGui.class.getName());
        LOG.info("GET {}{}", wsc.getHttpHost(), path);
        return httpSampler;
    }

    public static void main(String[] argv) throws Exception {
        try {
            WebServiceCommunication wsc = new WebServiceCommunication("54.226.209.194", 9000);
            wsc.connect();

            WebServiceJMeter jm = new WebServiceJMeter();
            jm.setWebServiceCommunication(wsc);
            jm.configureLoopThread(10, 30);

            Summariser summer = jm.runGet("/thr/history.xhtml?interval=1&entries=30");
            SummariserRunningSample result = summer.getResult();

        } catch (Exception ex) {
            LOG.error("", ex);
        } finally {
            System.exit(0);
        }
    }
}
