// Copyright 2016 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.lib.analysis.actions;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.actions.ActionInputHelper;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.Artifact.SpecialArtifact;
import com.google.devtools.build.lib.actions.Artifact.SpecialArtifactType;
import com.google.devtools.build.lib.actions.Artifact.TreeFileArtifact;
import com.google.devtools.build.lib.actions.ArtifactOwner;
import com.google.devtools.build.lib.actions.ArtifactRoot;
import com.google.devtools.build.lib.actions.util.ActionsTestUtil;
import com.google.devtools.build.lib.analysis.actions.SpawnActionTemplate.OutputPathMapper;
import com.google.devtools.build.lib.testutil.Scratch;
import com.google.devtools.build.lib.vfs.Path;
import com.google.devtools.build.lib.vfs.PathFragment;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests {@link SpawnActionTemplate}.
 */
@RunWith(JUnit4.class)
public class SpawnActionTemplateTest {
  private static final OutputPathMapper IDENTITY_MAPPER = new OutputPathMapper() {
    @Override
    public PathFragment parentRelativeOutputPath(TreeFileArtifact inputTreeFileArtifact) {
      return inputTreeFileArtifact.getParentRelativePath();
    }
  };

  private ArtifactRoot root;

  @Before
  public void setRootDir() throws Exception  {
    Scratch scratch = new Scratch();
    Path execRoot = scratch.getFileSystem().getPath("/");
    root = ArtifactRoot.asDerivedRoot(execRoot, scratch.dir("/exec/root"));
  }

  @Test
  public void testInputAndOutputTreeArtifacts() {
    SpawnActionTemplate actionTemplate = createSimpleSpawnActionTemplate();
    assertThat(actionTemplate.getInputs()).containsExactly(createInputTreeArtifact());
    assertThat(actionTemplate.getOutputs()).containsExactly(createOutputTreeArtifact());
  }

  @Test
  public void testCommonToolsAndInputs() {
    Artifact inputTreeArtifact = createInputTreeArtifact();
    Artifact outputTreeArtifact = createOutputTreeArtifact();
    Artifact commonInput = createDerivedArtifact("common/input");
    Artifact commonTool = createDerivedArtifact("common/tool");
    Artifact executable = createDerivedArtifact("bin/cp");


    SpawnActionTemplate actionTemplate = builder(inputTreeArtifact, outputTreeArtifact)
        .setExecutionInfo(ImmutableMap.<String, String>of("local", ""))
        .setExecutable(executable)
        .setCommandLineTemplate(
            createSimpleCommandLineTemplate(inputTreeArtifact, outputTreeArtifact))
        .setOutputPathMapper(IDENTITY_MAPPER)
        .setMnemonics("ActionTemplate", "ExpandedAction")
        .addCommonTools(ImmutableList.of(commonTool))
        .addCommonInputs(ImmutableList.of(commonInput))
        .build(ActionsTestUtil.NULL_ACTION_OWNER);

    assertThat(actionTemplate.getTools()).containsAllOf(commonTool, executable);
    assertThat(actionTemplate.getInputs()).containsAllOf(commonInput, commonTool, executable);
  }

  @Test
  public void testBuilder_outputPathMapperRequired() {
    Artifact inputTreeArtifact = createInputTreeArtifact();
    Artifact outputTreeArtifact = createOutputTreeArtifact();
    SpawnActionTemplate.Builder builder = builder(inputTreeArtifact, outputTreeArtifact)
        .setExecutionInfo(ImmutableMap.<String, String>of("local", ""))
        .setExecutable(PathFragment.create("/bin/cp"))
        .setCommandLineTemplate(
            createSimpleCommandLineTemplate(inputTreeArtifact, outputTreeArtifact))
        .setMnemonics("ActionTemplate", "ExpandedAction");

    try {
      builder.build(ActionsTestUtil.NULL_ACTION_OWNER);
      fail("Expected exception");
    } catch (NullPointerException expected) {}
  }

  @Test
  public void testBuilder_executableRequired() {
    Artifact inputTreeArtifact = createInputTreeArtifact();
    Artifact outputTreeArtifact = createOutputTreeArtifact();
    SpawnActionTemplate.Builder builder = builder(inputTreeArtifact, outputTreeArtifact)
        .setExecutionInfo(ImmutableMap.<String, String>of("local", ""))
        .setOutputPathMapper(IDENTITY_MAPPER)
        .setCommandLineTemplate(
            createSimpleCommandLineTemplate(inputTreeArtifact, outputTreeArtifact))
        .setMnemonics("ActionTemplate", "ExpandedAction");

    try {
      builder.build(ActionsTestUtil.NULL_ACTION_OWNER);
      fail("Expected exception");
    } catch (NullPointerException expected) {}
  }

  @Test
  public void testBuilder_commandlineTemplateRequired() {
    Artifact inputTreeArtifact = createInputTreeArtifact();
    Artifact outputTreeArtifact = createOutputTreeArtifact();
    SpawnActionTemplate.Builder builder = builder(inputTreeArtifact, outputTreeArtifact)
        .setExecutionInfo(ImmutableMap.<String, String>of("local", ""))
        .setOutputPathMapper(IDENTITY_MAPPER)
        .setExecutable(PathFragment.create("/bin/cp"))
        .setMnemonics("ActionTemplate", "ExpandedAction");

    try {
      builder.build(ActionsTestUtil.NULL_ACTION_OWNER);
      fail("Expected exception");
    } catch (NullPointerException expected) {}
  }

  @Test
  public void testExpandedAction_inputAndOutputTreeFileArtifacts() throws Exception {
    SpawnActionTemplate actionTemplate = createSimpleSpawnActionTemplate();
    Artifact inputTreeArtifact = createInputTreeArtifact();
    Artifact outputTreeArtifact = createOutputTreeArtifact();

    Iterable<TreeFileArtifact> inputTreeFileArtifacts =
        createInputTreeFileArtifacts(inputTreeArtifact);

    List<SpawnAction> expandedActions = ImmutableList.copyOf(
        actionTemplate.generateActionForInputArtifacts(
            inputTreeFileArtifacts, ArtifactOwner.NULL_OWNER));

    assertThat(expandedActions).hasSize(3);

    for (int i = 0; i < expandedActions.size(); ++i) {
      String baseName = String.format("child%d", i);
      assertThat(expandedActions.get(i).getInputs()).containsExactly(
          ActionInputHelper.treeFileArtifact(
              inputTreeArtifact, PathFragment.create("children/" + baseName)));
      assertThat(expandedActions.get(i).getOutputs()).containsExactly(
          ActionInputHelper.treeFileArtifact(
              outputTreeArtifact, PathFragment.create("children/" + baseName)));
    }
  }

  @Test
  public void testExpandedAction_commonToolsAndInputs() throws Exception {
    Artifact inputTreeArtifact = createInputTreeArtifact();
    Artifact outputTreeArtifact = createOutputTreeArtifact();
    Artifact commonInput = createDerivedArtifact("common/input");
    Artifact commonTool = createDerivedArtifact("common/tool");
    Artifact executable = createDerivedArtifact("bin/cp");

    SpawnActionTemplate actionTemplate = builder(inputTreeArtifact, outputTreeArtifact)
        .setExecutionInfo(ImmutableMap.<String, String>of("local", ""))
        .setExecutable(executable)
        .setCommandLineTemplate(
            createSimpleCommandLineTemplate(inputTreeArtifact, outputTreeArtifact))
        .setOutputPathMapper(IDENTITY_MAPPER)
        .setMnemonics("ActionTemplate", "ExpandedAction")
        .addCommonTools(ImmutableList.of(commonTool))
        .addCommonInputs(ImmutableList.of(commonInput))
        .build(ActionsTestUtil.NULL_ACTION_OWNER);

    Iterable<TreeFileArtifact> inputTreeFileArtifacts =
        createInputTreeFileArtifacts(inputTreeArtifact);
    List<SpawnAction> expandedActions = ImmutableList.copyOf(
        actionTemplate.generateActionForInputArtifacts(
            inputTreeFileArtifacts, ArtifactOwner.NULL_OWNER));

    for (int i = 0; i < expandedActions.size(); ++i) {
      assertThat(expandedActions.get(i).getInputs()).containsAllOf(
          commonInput, commonTool, executable);
      assertThat(expandedActions.get(i).getTools()).containsAllOf(commonTool, executable);
    }
  }

  @Test
  public void testExpandedAction_arguments() throws Exception {
    SpawnActionTemplate actionTemplate = createSimpleSpawnActionTemplate();
    Artifact inputTreeArtifact = createInputTreeArtifact();
    Artifact outputTreeArtifact = createOutputTreeArtifact();

    Iterable<TreeFileArtifact> inputTreeFileArtifacts =
        createInputTreeFileArtifacts(inputTreeArtifact);

    List<SpawnAction> expandedActions = ImmutableList.copyOf(
        actionTemplate.generateActionForInputArtifacts(
            inputTreeFileArtifacts, ArtifactOwner.NULL_OWNER));

    assertThat(expandedActions).hasSize(3);

    for (int i = 0; i < expandedActions.size(); ++i) {
      String baseName = String.format("child%d", i);
      assertThat(expandedActions.get(i).getArguments())
          .containsExactly(
              "/bin/cp",
              inputTreeArtifact.getExecPathString() + "/children/" + baseName,
              outputTreeArtifact.getExecPathString() + "/children/" + baseName)
          .inOrder();
    }
  }

  @Test
  public void testExpandedAction_executionInfoAndEnvironment() throws Exception {
    SpawnActionTemplate actionTemplate = createSimpleSpawnActionTemplate();
    Artifact inputTreeArtifact = createInputTreeArtifact();
    Iterable<TreeFileArtifact> inputTreeFileArtifacts =
        createInputTreeFileArtifacts(inputTreeArtifact);

    List<SpawnAction> expandedActions = ImmutableList.copyOf(
        actionTemplate.generateActionForInputArtifacts(
            inputTreeFileArtifacts, ArtifactOwner.NULL_OWNER));

    assertThat(expandedActions).hasSize(3);

    for (int i = 0; i < expandedActions.size(); ++i) {
      assertThat(expandedActions.get(i).getEnvironment()).containsExactly("env", "value");
      assertThat(expandedActions.get(i).getExecutionInfo()).containsExactly("local", "");
    }
  }

  @Test
  public void testExpandedAction_illegalOutputPath() throws Exception {
    Artifact inputTreeArtifact = createInputTreeArtifact();
    Artifact outputTreeArtifact = createOutputTreeArtifact();
    Iterable<TreeFileArtifact> inputTreeFileArtifacts =
        createInputTreeFileArtifacts(inputTreeArtifact);

    SpawnActionTemplate.Builder builder = builder(inputTreeArtifact, outputTreeArtifact)
        .setExecutable(PathFragment.create("/bin/cp"))
        .setCommandLineTemplate(
            createSimpleCommandLineTemplate(inputTreeArtifact, outputTreeArtifact));

    OutputPathMapper mapper = new OutputPathMapper() {
      @Override
      public PathFragment parentRelativeOutputPath(TreeFileArtifact inputTreeFileArtifact) {
        return PathFragment.create("//absolute/" + inputTreeFileArtifact.getParentRelativePath());
      }
    };

    SpawnActionTemplate actionTemplate =
        builder.setOutputPathMapper(mapper).build(ActionsTestUtil.NULL_ACTION_OWNER);

    try {
      actionTemplate.generateActionForInputArtifacts(
          inputTreeFileArtifacts, ArtifactOwner.NULL_OWNER);
      fail("Absolute output paths not allowed, expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // expected
    }

    mapper = new OutputPathMapper() {
      @Override
      public PathFragment parentRelativeOutputPath(TreeFileArtifact inputTreeFileArtifact) {
        return PathFragment.create("../" + inputTreeFileArtifact.getParentRelativePath());
      }
    };

    actionTemplate = builder.setOutputPathMapper(mapper).build(
        ActionsTestUtil.NULL_ACTION_OWNER);

    try {
      actionTemplate.generateActionForInputArtifacts(
          inputTreeFileArtifacts, ArtifactOwner.NULL_OWNER);
      fail("Output paths containing '..' not allowed, expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  private SpawnActionTemplate.Builder builder(Artifact inputTreeArtifact,
      Artifact outputTreeArtifact) {
    return new SpawnActionTemplate.Builder(inputTreeArtifact, outputTreeArtifact);
  }

  private SpawnActionTemplate createSimpleSpawnActionTemplate() {
    Artifact inputTreeArtifact = createInputTreeArtifact();
    Artifact outputTreeArtifact = createOutputTreeArtifact();

    return builder(inputTreeArtifact, outputTreeArtifact)
        .setExecutionInfo(ImmutableMap.<String, String>of("local", ""))
        .setEnvironment(ImmutableMap.<String, String>of("env", "value"))
        .setExecutable(PathFragment.create("/bin/cp"))
        .setCommandLineTemplate(
            createSimpleCommandLineTemplate(inputTreeArtifact, outputTreeArtifact))
        .setOutputPathMapper(IDENTITY_MAPPER)
        .setMnemonics("ActionTemplate", "ExpandedAction")
        .build(ActionsTestUtil.NULL_ACTION_OWNER);
  }

  private Artifact createInputTreeArtifact() {
    return createTreeArtifact("my/inputTree");
  }

  private Artifact createOutputTreeArtifact() {
    return createTreeArtifact("my/outputTree");
  }

  private Artifact createTreeArtifact(String rootRelativePath) {
    PathFragment relpath = PathFragment.create(rootRelativePath);
    return new SpecialArtifact(
        root.getRoot().getRelative(relpath),
        root,
        root.getExecPath().getRelative(relpath),
        ArtifactOwner.NULL_OWNER,
        SpecialArtifactType.TREE);
  }

  private Artifact createDerivedArtifact(String rootRelativePath) {
    return new Artifact(PathFragment.create(rootRelativePath), root);
  }

  private CustomCommandLine createSimpleCommandLineTemplate(
      Artifact inputTreeArtifact, Artifact outputTreeArtifact) {
    return CustomCommandLine.builder()
        .addPlaceholderTreeArtifactExecPath(inputTreeArtifact)
        .addPlaceholderTreeArtifactExecPath(outputTreeArtifact)
        .build();
  }

  private Iterable<TreeFileArtifact> createInputTreeFileArtifacts(Artifact inputTreeArtifact) {
    return ActionInputHelper.asTreeFileArtifacts(
        inputTreeArtifact,
        ImmutableList.of(
            PathFragment.create("children/child0"),
            PathFragment.create("children/child1"),
            PathFragment.create("children/child2")));
  }
}
