/**
 * (c) 2015 StreamSets, Inc. All rights reserved. May not be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.dc.execution.runner;

import com.streamsets.dc.execution.Manager;
import com.streamsets.dc.execution.PipelineState;
import com.streamsets.dc.execution.PipelineStatus;
import com.streamsets.dc.execution.Runner;
import com.streamsets.dc.execution.manager.TestSlaveManager;
import com.streamsets.dc.execution.manager.slave.SlavePipelineManager;
import com.streamsets.pipeline.main.RuntimeInfo;
import com.streamsets.pipeline.main.RuntimeModule;
import com.streamsets.pipeline.util.TestUtil;
import com.streamsets.pipeline.util.TestUtil.TestPipelineProviderModule;
import dagger.ObjectGraph;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class TestSlaveStandaloneRunner {

  @BeforeClass
  public static void beforeClass() throws IOException {
    File testDir = new File("target", UUID.randomUUID().toString()).getAbsoluteFile();
    Assert.assertTrue(testDir.mkdirs());
    System.setProperty(RuntimeModule.SDC_PROPERTY_PREFIX + RuntimeInfo.DATA_DIR, testDir.getAbsolutePath());
    TestUtil.captureStagesForProductionRun();
  }

  @AfterClass
  public static void afterClass() throws IOException {
    System.getProperties().remove(RuntimeModule.SDC_PROPERTY_PREFIX + RuntimeInfo.DATA_DIR);
  }

  @After
  public void tearDown() throws Exception {
    TestUtil.EMPTY_OFFSET = false;
    File dataDir = new File(System.getProperty(RuntimeModule.SDC_PROPERTY_PREFIX + RuntimeInfo.DATA_DIR));
    try {
      FileUtils.cleanDirectory(dataDir);
    } catch (IOException e) {
      // ignore
    }
    Assert.assertTrue(dataDir.isDirectory());
  }

  @Test (timeout = 10000)
  public void testSlaveRunnerStartStop() throws Exception {
    ObjectGraph objectGraph = ObjectGraph.create(new TestPipelineProviderModule(TestUtil.MY_PIPELINE, "0"));
    ObjectGraph plus = objectGraph.plus(new TestSlaveManager.TestSlaveManagerModule());

    Manager manager = new SlavePipelineManager(plus);
    manager.init();
    Runner runner = manager.getRunner("admin", TestUtil.MY_PIPELINE, "0");
    runner.start();
    while (runner.getState().getStatus() != PipelineStatus.RUNNING) {
      Thread.sleep(100);
    }
    runner.stop();
    while (runner.getState().getStatus() != PipelineStatus.STOPPED) {
      Thread.sleep(100);
    }
  }

  @Test(timeout = 20000)
  public void testLoadingUnsupportedPipeline() throws Exception {
    ObjectGraph objectGraph = ObjectGraph.create(new TestPipelineProviderModule(TestUtil.MY_PIPELINE, "0"));
    ObjectGraph plus = objectGraph.plus(new TestSlaveManager.TestSlaveManagerModule());
    Manager pipelineManager = new SlavePipelineManager(plus);
    pipelineManager.init();
    Runner runner = pipelineManager.getRunner("user2", TestUtil.HIGHER_VERSION_PIPELINE, "0");
    runner.start();
    while(pipelineManager.getRunner("user2", TestUtil.HIGHER_VERSION_PIPELINE, "0").getState().getStatus() != PipelineStatus.START_ERROR) {
      Thread.sleep(100);
    }
    PipelineState state = pipelineManager.getRunner("user2", TestUtil.HIGHER_VERSION_PIPELINE, "0").getState();
    Assert.assertTrue(state.getStatus() == PipelineStatus.START_ERROR);
    Assert.assertTrue(state.getMessage().contains("CONTAINER_0158"));
  }

  @Test (timeout = 10000)
  public void testDisconnectingSlaveRunner() throws Exception {
    ObjectGraph objectGraph = ObjectGraph.create(new TestPipelineProviderModule(TestUtil.MY_PIPELINE, "0"));
    ObjectGraph plus = objectGraph.plus(new TestSlaveManager.TestSlaveManagerModule());
    Manager manager = new SlavePipelineManager(plus);
    manager.init();
    Runner runner = manager.getRunner("admin", TestUtil.MY_PIPELINE, "0");
    runner.start();
    while (runner.getState().getStatus() != PipelineStatus.RUNNING) {
      Thread.sleep(100);
    }
    runner.onDataCollectorStop();
    while (runner.getState().getStatus() != PipelineStatus.DISCONNECTED) {
      Thread.sleep(100);
    }
  }

  @Test(timeout = 10000)
  public void testFinishedSlaveRunner() throws Exception {
    ObjectGraph objectGraph = ObjectGraph.create(new TestPipelineProviderModule(TestUtil.MY_PIPELINE, "0"));
    ObjectGraph plus = objectGraph.plus(new TestSlaveManager.TestSlaveManagerModule());
    Manager manager = new SlavePipelineManager(plus);
    manager.init();
    Runner runner = manager.getRunner("admin", TestUtil.MY_PIPELINE, "0");
    runner.start();
    while (runner.getState().getStatus() != PipelineStatus.RUNNING) {
      Thread.sleep(100);
    }
    TestUtil.EMPTY_OFFSET = true;
    while (runner.getState().getStatus() != PipelineStatus.FINISHED) {
      Thread.sleep(100);
    }
  }

}
