/*
 * Copyright 2013-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.apple.xcode;

import static org.hamcrest.CoreMatchers.either;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.xml.HasXPath.hasXPath;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.dd.plist.NSDictionary;
import com.dd.plist.NSString;
import com.facebook.buck.apple.IosBinaryDescription;
import com.facebook.buck.apple.IosLibraryDescription;
import com.facebook.buck.apple.IosTestDescription;
import com.facebook.buck.apple.XcodeNativeDescription;
import com.facebook.buck.apple.xcode.xcodeproj.PBXBuildFile;
import com.facebook.buck.apple.xcode.xcodeproj.PBXBuildPhase;
import com.facebook.buck.apple.xcode.xcodeproj.PBXContainerItemProxy;
import com.facebook.buck.apple.xcode.xcodeproj.PBXFileReference;
import com.facebook.buck.apple.xcode.xcodeproj.PBXFrameworksBuildPhase;
import com.facebook.buck.apple.xcode.xcodeproj.PBXGroup;
import com.facebook.buck.apple.xcode.xcodeproj.PBXHeadersBuildPhase;
import com.facebook.buck.apple.xcode.xcodeproj.PBXProject;
import com.facebook.buck.apple.xcode.xcodeproj.PBXReference;
import com.facebook.buck.apple.xcode.xcodeproj.PBXResourcesBuildPhase;
import com.facebook.buck.apple.xcode.xcodeproj.PBXShellScriptBuildPhase;
import com.facebook.buck.apple.xcode.xcodeproj.PBXSourcesBuildPhase;
import com.facebook.buck.apple.xcode.xcodeproj.PBXTarget;
import com.facebook.buck.apple.xcode.xcodeproj.PBXTargetDependency;
import com.facebook.buck.apple.xcode.xcodeproj.XCBuildConfiguration;
import com.facebook.buck.codegen.SourceSigner;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.parser.PartialGraph;
import com.facebook.buck.parser.PartialGraphFactory;
import com.facebook.buck.rules.AbstractBuildRuleBuilderParams;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.DependencyGraph;
import com.facebook.buck.rules.DescribedRule;
import com.facebook.buck.rules.FakeAbstractBuildRuleBuilderParams;
import com.facebook.buck.rules.FakeBuildRuleParams;
import com.facebook.buck.rules.FileSourcePath;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.coercer.Either;
import com.facebook.buck.rules.coercer.Pair;
import com.facebook.buck.shell.Genrule;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.TestExecutionContext;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.testutil.RuleMap;
import com.facebook.buck.util.ProjectFilesystem;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public class ProjectGeneratorTest {

  private static final Path OUTPUT_DIRECTORY = Paths.get("_gen");
  private static final String PROJECT_NAME = "GeneratedProject";
  private static final String PROJECT_CONTAINER = PROJECT_NAME + ".xcodeproj";
  private static final Path OUTPUT_PROJECT_BUNDLE_PATH =
      OUTPUT_DIRECTORY.resolve(PROJECT_CONTAINER);
  private static final Path OUTPUT_PROJECT_FILE_PATH =
      OUTPUT_PROJECT_BUNDLE_PATH.resolve("project.pbxproj");

  private ProjectFilesystem projectFilesystem;
  private ExecutionContext executionContext;
  private XcodeNativeDescription xcodeNativeDescription;
  private IosLibraryDescription iosLibraryDescription;
  private IosTestDescription iosTestDescription;
  private IosBinaryDescription iosBinaryDescription;

  @Before
  public void setUp() {
    projectFilesystem = new FakeProjectFilesystem();
    executionContext = TestExecutionContext.newInstance();
    xcodeNativeDescription = new XcodeNativeDescription();
    iosLibraryDescription = new IosLibraryDescription();
    iosTestDescription = new IosTestDescription();
    iosBinaryDescription = new IosBinaryDescription();
  }

  @Test
  public void testProjectStructureForEmptyProject() throws IOException {
    BuildRuleResolver buildRuleResolver = new BuildRuleResolver();
    ProjectGenerator projectGenerator = createProjectGenerator(
        buildRuleResolver, ImmutableList.<BuildTarget>of());

    Path outputWorkspaceBundlePath = OUTPUT_DIRECTORY.resolve(PROJECT_NAME + ".xcworkspace");
    Path outputWorkspaceFilePath = outputWorkspaceBundlePath.resolve("contents.xcworkspacedata");

    Path outputSchemeFolderPath = OUTPUT_PROJECT_BUNDLE_PATH.resolve(
        Paths.get("xcshareddata", "xcschemes"));
    Path outputSchemePath = outputSchemeFolderPath.resolve("Scheme.xcscheme");

    projectGenerator.createXcodeProjects();

    Optional<String> pbxproj = projectFilesystem.readFileIfItExists(OUTPUT_PROJECT_FILE_PATH);
    assertTrue(pbxproj.isPresent());

    Optional<String> xcworkspacedata = projectFilesystem.readFileIfItExists(outputWorkspaceFilePath);
    assertTrue(xcworkspacedata.isPresent());

    Optional<String> xcscheme = projectFilesystem.readFileIfItExists(outputSchemePath);
    assertTrue(xcscheme.isPresent());
  }

  @Test
  public void testSchemeGeneration() throws IOException {
    BuildRule rootRule = createXcodeNativeRule(
        new BuildTarget("//foo", "root"),
        ImmutableSortedSet.<BuildRule>of());
    BuildRule leftRule = createXcodeNativeRule(
        new BuildTarget("//foo", "left"),
        ImmutableSortedSet.of(rootRule));
    BuildRule rightRule = createXcodeNativeRule(
        new BuildTarget("//foo", "right"),
        ImmutableSortedSet.of(rootRule));
    BuildRule childRule = createXcodeNativeRule(
        new BuildTarget("//foo", "child"),
        ImmutableSortedSet.of(leftRule, rightRule));

    BuildRuleResolver buildRuleResolver = new BuildRuleResolver(ImmutableSet.of(
        rootRule, leftRule, rightRule, childRule));

    ProjectGenerator projectGenerator = createProjectGenerator(
        buildRuleResolver, ImmutableList.of(childRule.getBuildTarget()));

    // Generate the project.
    projectGenerator.createXcodeProjects();

    // Verify the scheme.
    PBXProject project = projectGenerator.getGeneratedProject();
    Map<String, String> targetNameToGid = Maps.newHashMap();
    for (PBXTarget target : project.getTargets()) {
      targetNameToGid.put(target.getName(), target.getGlobalID());
    }

    XCScheme scheme = Preconditions.checkNotNull(projectGenerator.getGeneratedScheme());
    List<String> actualOrdering = Lists.newArrayList();
    for (XCScheme.BuildActionEntry entry : scheme.getBuildAction()) {
      actualOrdering.add(entry.getBlueprintIdentifier());
      assertEquals(PROJECT_CONTAINER, entry.getContainerRelativePath());
    }

    List<String> expectedOrdering1 = ImmutableList.of(
        targetNameToGid.get("//foo:root"),
        targetNameToGid.get("//foo:left"),
        targetNameToGid.get("//foo:right"),
        targetNameToGid.get("//foo:child"));
    List<String> expectedOrdering2 = ImmutableList.of(
        targetNameToGid.get("//foo:root"),
        targetNameToGid.get("//foo:right"),
        targetNameToGid.get("//foo:left"),
        targetNameToGid.get("//foo:child"));
    assertThat(actualOrdering, either(equalTo(expectedOrdering1)).or(equalTo(expectedOrdering2)));
  }

  @Test
  public void testWorkspaceGeneration() throws IOException {
    BuildRuleResolver buildRuleResolver = new BuildRuleResolver();
    ProjectGenerator projectGenerator = createProjectGenerator(
        buildRuleResolver, ImmutableList.<BuildTarget>of());
    projectGenerator.createXcodeProjects();

    Document workspace = projectGenerator.getGeneratedWorkspace();
    assertThat(workspace, hasXPath("/Workspace[@version = \"1.0\"]"));
    assertThat(workspace,
        hasXPath("/Workspace/FileRef/@location", equalTo("container:" + PROJECT_CONTAINER)));
  }

  @Test
  public void testProjectFileSigning() throws IOException {
    BuildRuleResolver buildRuleResolver = new BuildRuleResolver();
    ProjectGenerator projectGenerator = createProjectGenerator(
        buildRuleResolver, ImmutableList.<BuildTarget>of());

    projectGenerator.createXcodeProjects();

    Optional<String> pbxproj = projectFilesystem.readFileIfItExists(OUTPUT_PROJECT_FILE_PATH);
    assertTrue(pbxproj.isPresent());
    assertEquals(
        SourceSigner.SignatureStatus.OK,
        SourceSigner.getSignatureStatus(pbxproj.get()));
  }

  @Test
  public void testXcodeNativeRule() throws IOException {
    BuildRule rule = createXcodeNativeRule(
        new BuildTarget("//foo", "rule"),
        ImmutableSortedSet.<BuildRule>of());
    BuildRuleResolver buildRuleResolver = new BuildRuleResolver(ImmutableSet.of(rule));
    ProjectGenerator projectGenerator = createProjectGenerator(
        buildRuleResolver, ImmutableList.of(rule.getBuildTarget()));

    projectGenerator.createXcodeProjects();

    PBXProject project = projectGenerator.getGeneratedProject();
    assertThat(project.getTargets(), hasSize(2));
    PBXTarget target = project.getTargets().get(0);
    assertThat(target.getName(), equalTo("//foo:rule"));
    assertThat(target.isa(), equalTo("PBXAggregateTarget"));
    assertThat(target.getDependencies(), hasSize(1));
    PBXTargetDependency dependency = target.getDependencies().get(0);
    PBXContainerItemProxy proxy = dependency.getTargetProxy();
    assertThat(
        proxy.getContainerPortal().getSourceTree(),
        equalTo(PBXFileReference.SourceTree.ABSOLUTE));
    assertThat(proxy.getContainerPortal().getPath(), endsWith("foo.xcodeproj"));
    assertThat(proxy.getRemoteGlobalIDString(), equalTo("00DEADBEEF"));

    verifyGeneratedSignedSourceTarget(project.getTargets().get(1));

    PBXGroup projectReferenceGroup =
        project.getMainGroup().getOrCreateChildGroupByName("Project References");
    assertThat(projectReferenceGroup.getChildren(), hasSize(1));
    assertThat(
        projectReferenceGroup.getChildren(), hasItem(sameInstance(proxy.getContainerPortal())));
  }

  @Test
  public void testIosLibraryRule() throws IOException {

    BuildRuleParams params = new FakeBuildRuleParams(
        new BuildTarget("//foo", "lib"), ImmutableSortedSet.<BuildRule>of());
    IosLibraryDescription.Arg arg = iosLibraryDescription.createUnpopulatedConstructorArg();
    arg.configs = ImmutableMap.of(
        "Debug", ImmutableList.<Either<Path, ImmutableMap<String, String>>>of());
    arg.headers = ImmutableSortedSet.of((SourcePath) new FileSourcePath("foo.h"));
    arg.srcs = ImmutableList.of(
        Either.<SourcePath, Pair<SourcePath, String>>ofRight(
            new Pair<SourcePath, String>(new FileSourcePath("foo.m"), "-foo")),
        Either.<SourcePath, Pair<SourcePath, String>>ofLeft(new FileSourcePath("bar.m")));
    arg.frameworks = ImmutableSortedSet.of();
    BuildRule rule = new DescribedRule(
        IosLibraryDescription.TYPE,
        iosLibraryDescription.createBuildable(params, arg), params);
    BuildRuleResolver buildRuleResolver = new BuildRuleResolver(ImmutableSet.of(rule));

    ProjectGenerator projectGenerator = createProjectGenerator(
        buildRuleResolver, ImmutableList.of(rule.getBuildTarget()));

    projectGenerator.createXcodeProjects();

    PBXTarget target = assertTargetExistsAndReturnTarget(
        projectGenerator.getGeneratedProject(),
        "//foo:lib");
    assertThat(target.isa(), equalTo("PBXNativeTarget"));
    assertThat(target.getProductType(), equalTo(PBXTarget.ProductType.IOS_LIBRARY));

    assertHasConfigurations(target, "Debug");
    assertEquals("Should have exact number of build phases", 2, target.getBuildPhases().size());
    assertHasSingletonSourcesPhaseWithSourcesAndFlags(
        target, ImmutableMap.of(
        "foo.m", Optional.of("-foo"),
        "bar.m", Optional.<String>absent()));

   // check headers
    {
      PBXBuildPhase headersBuildPhase =
          Iterables.find(target.getBuildPhases(), new Predicate<PBXBuildPhase>() {
            @Override
            public boolean apply(PBXBuildPhase input) {
              return input instanceof PBXHeadersBuildPhase;
            }
          });
      PBXBuildFile headerBuildFile = Iterables.getOnlyElement(headersBuildPhase.getFiles());

      assertEquals(
          PBXFileReference.SourceTree.ABSOLUTE,
          headerBuildFile.getFileRef().getSourceTree());
      assertEquals(
          projectFilesystem.getRootPath().resolve("foo.h").toAbsolutePath().toString(),
          headerBuildFile.getFileRef().getPath());
    }
  }

  @Test
  public void testIosTestRule() throws IOException {
    BuildRuleParams params = new FakeBuildRuleParams(
        new BuildTarget("//foo", "test"), ImmutableSortedSet.<BuildRule>of());

    IosTestDescription.Arg arg = iosTestDescription.createUnpopulatedConstructorArg();
    arg.infoPlist = Paths.get("Info.plist");
    arg.configs = ImmutableMap.of(
        "Debug", ImmutableList.<Either<Path, ImmutableMap<String, String>>>of());
    arg.headers = ImmutableSortedSet.of((SourcePath) new FileSourcePath("foo.h"));
    arg.srcs = ImmutableList.of(Either.<SourcePath, Pair<SourcePath, String>>ofRight(
        new Pair<SourcePath, String>(new FileSourcePath("foo.m"), "-foo")));
    arg.resources = ImmutableSortedSet.<SourcePath>of(new FileSourcePath("resource.png"));
    arg.frameworks = ImmutableSortedSet.of("$SDKROOT/Foo.framework");
    arg.sourceUnderTest = ImmutableSortedSet.of();

    BuildRule rule = new DescribedRule(
        IosTestDescription.TYPE,
        iosTestDescription.createBuildable(params, arg), params);
    BuildRuleResolver buildRuleResolver = new BuildRuleResolver(ImmutableSet.of(rule));

    ProjectGenerator projectGenerator = createProjectGenerator(
        buildRuleResolver, ImmutableList.of(rule.getBuildTarget()));

    projectGenerator.createXcodeProjects();

    PBXTarget target = assertTargetExistsAndReturnTarget(
        projectGenerator.getGeneratedProject(),
        "//foo:test");
    assertEquals("PBXNativeTarget", target.isa());
    assertEquals(PBXTarget.ProductType.IOS_TEST, target.getProductType());

    assertHasConfigurations(target, "Debug");
    assertEquals("Should have exact number of build phases", 3, target.getBuildPhases().size());
    assertHasSingletonSourcesPhaseWithSourcesAndFlags(target, ImmutableMap.of(
        "foo.m", Optional.of("-foo")));

    assertHasSingletonFrameworksPhaseWithFrameworkEntries(target, ImmutableList.of(
        "$SDKROOT/Foo.framework"));
  }

  @Test
  public void testIosBinaryRule() throws IOException {
    BuildRule depRule = createXcodeNativeRule(
        new BuildTarget("//dep", "dep"), ImmutableSortedSet.<BuildRule>of());
    BuildRuleParams params = new FakeBuildRuleParams(
        new BuildTarget("//foo", "binary"), ImmutableSortedSet.of(depRule));

    IosBinaryDescription.Arg arg = iosBinaryDescription.createUnpopulatedConstructorArg();
    arg.infoPlist = Paths.get("Info.plist");
    arg.configs = ImmutableMap.of(
        "Debug", ImmutableList.<Either<Path, ImmutableMap<String, String>>>of());
    arg.headers = ImmutableSortedSet.of((SourcePath) new FileSourcePath("foo.h"));
    arg.srcs = ImmutableList.of(Either.<SourcePath, Pair<SourcePath, String>>ofRight(
        new Pair<SourcePath, String>(new FileSourcePath("foo.m"), "-foo")));
    arg.resources = ImmutableSortedSet.<SourcePath>of(new FileSourcePath("resource.png"));
    arg.frameworks = ImmutableSortedSet.of("$SDKROOT/Foo.framework");

    BuildRule rule = new DescribedRule(
        IosBinaryDescription.TYPE,
        iosBinaryDescription.createBuildable(params, arg), params);
    BuildRuleResolver buildRuleResolver = new BuildRuleResolver(ImmutableSet.of(rule));

    ProjectGenerator projectGenerator = createProjectGenerator(
        buildRuleResolver, ImmutableList.of(rule.getBuildTarget()));
    projectGenerator.createXcodeProjects();

    PBXTarget target = assertTargetExistsAndReturnTarget(
        projectGenerator.getGeneratedProject(),
        "//foo:binary");
    assertHasConfigurations(target, "Debug");
    assertEquals("Should have exact number of build phases", 3, target.getBuildPhases().size());
    assertHasSingletonSourcesPhaseWithSourcesAndFlags(
        target,
        ImmutableMap.of(
            "foo.m", Optional.of("-foo")));
    assertHasSingletonFrameworksPhaseWithFrameworkEntries(
        target,
        ImmutableList.of(
            "$SDKROOT/Foo.framework",
            // Propagated library from deps.
            "$BUILT_PRODUCTS_DIR/libfoo.a"));
    assertHasSingletonResourcesPhaseWithEntries(target, "resource.png");
  }

  @Test
  public void testIosLibraryRuleWithGenruleDependency() throws IOException {

    BuildRuleResolver buildRuleResolver = new BuildRuleResolver();
    AbstractBuildRuleBuilderParams genruleParams = new FakeAbstractBuildRuleBuilderParams();
    Genrule.Builder builder = Genrule.newGenruleBuilder(genruleParams);
    builder.setBuildTarget(new BuildTarget("//foo", "script"));
    builder.setBash(Optional.of("echo \"hello world!\""));
    builder.setOut("helloworld.txt");

    Genrule genrule = buildRuleResolver.buildAndAddToIndex(builder);

    BuildTarget libTarget = new BuildTarget("//foo", "lib");
    BuildRuleParams libParams = new FakeBuildRuleParams(libTarget,
                                                        ImmutableSortedSet.<BuildRule>of(genrule));
    IosLibraryDescription.Arg arg = iosLibraryDescription.createUnpopulatedConstructorArg();
    arg.configs = ImmutableMap.of(
        "Debug", ImmutableList.<Either<Path, ImmutableMap<String, String>>>of());
    arg.headers = ImmutableSortedSet.of((SourcePath) new FileSourcePath("foo.h"));
    arg.srcs = ImmutableList.of(
        Either.<SourcePath, Pair<SourcePath, String>>ofRight(
            new Pair<SourcePath, String>(new FileSourcePath("foo.m"), "-foo")));
    arg.frameworks = ImmutableSortedSet.of();
    BuildRule rule = new DescribedRule(
        IosLibraryDescription.TYPE,
        iosLibraryDescription.createBuildable(libParams, arg), libParams);

    buildRuleResolver.addToIndex(libTarget, rule);

    ProjectGenerator projectGenerator = createProjectGenerator(
        buildRuleResolver, ImmutableList.of(rule.getBuildTarget()));

    projectGenerator.createXcodeProjects();

    PBXProject project = projectGenerator.getGeneratedProject();
    assertThat(project.getTargets(), hasSize(2));
    PBXTarget target = project.getTargets().get(0);
    assertThat(target.getName(), equalTo("//foo:lib"));
    assertThat(target.isa(), equalTo("PBXNativeTarget"));

    PBXShellScriptBuildPhase shellScriptBuildPhase = getSingletonPhaseByType(
        target,
        PBXShellScriptBuildPhase.class);

    assertThat(
        shellScriptBuildPhase.getShellScript(),
        equalTo("/bin/bash -e -c 'echo \"hello world!\"'"));
  }

  @Test
  public void shouldDiscoverDependenciesAndTests() throws IOException {
    // Create the following dep tree:
    // FooBin -has-test-> FooBinTest
    // |
    // V
    // FooLib -has-test-> FooLibTest
    // |                  |
    // V                  V
    // BarLib             BazLib -has-test-> BazLibTest
    //
    // Calling generate on FooBin should pull in everything except BazLibTest

    BuildRule barLib = createXcodeNativeRule(
        new BuildTarget("//bar", "lib"), ImmutableSortedSet.<BuildRule>of());
    BuildRule fooLib = createXcodeNativeRule(
        new BuildTarget("//foo", "lib"), ImmutableSortedSet.of(barLib));
    BuildRule fooBin = createXcodeNativeRule(
        new BuildTarget("//foo", "bin"), ImmutableSortedSet.of(fooLib));
    BuildRule bazLib = createXcodeNativeRule(
        new BuildTarget("//baz", "lib"), ImmutableSortedSet.of(fooLib));

    BuildRule bazLibTest = createIosTestRule(
        new BuildTarget("//baz", "test"),
        ImmutableSortedSet.of(bazLib),
        ImmutableSortedSet.of(bazLib));
    BuildRule fooLibTest = createIosTestRule(
        new BuildTarget("//foo", "lib-test"),
        ImmutableSortedSet.of(fooLib),
        ImmutableSortedSet.of(fooLib, bazLib));
    BuildRule fooBinTest = createIosTestRule(
        new BuildTarget("//foo", "bin-test"),
        ImmutableSortedSet.of(fooBin),
        ImmutableSortedSet.of(fooBin));

    BuildRuleResolver buildRuleResolver = new BuildRuleResolver(ImmutableSet.of(
        barLib, fooLib, fooBin, bazLib, bazLibTest, fooLibTest, fooBinTest));

    ProjectGenerator projectGenerator = createProjectGenerator(
        buildRuleResolver, ImmutableList.of(fooBin.getBuildTarget()));
    projectGenerator.createXcodeProjects();

    assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:bin");
    assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:lib");
    assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//bar:lib");
    assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:bin-test");
    assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:lib-test");
    assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//baz:lib");
  }

  @Test
  public void generatedGidsForTargetsAreStable() throws IOException {
    BuildRule fooLib = createXcodeNativeRule(
        new BuildTarget("//foo", "foo"),
        ImmutableSortedSet.<BuildRule>of());
    BuildRuleResolver buildRuleResolver = new BuildRuleResolver(ImmutableSet.of(fooLib));

    ProjectGenerator projectGenerator = createProjectGenerator(
        buildRuleResolver, ImmutableList.of(fooLib.getBuildTarget()));
    projectGenerator.createXcodeProjects();

    PBXTarget target = assertTargetExistsAndReturnTarget(
        projectGenerator.getGeneratedProject(),
        "//foo:foo");
    String expectedGID = String.format(
        "%08X%08X%08X", target.isa().hashCode(), target.getName().hashCode(), 0);
    assertEquals("expected GID has correct value (value from which it's derived have not changed)",
        "93C1B2AA2245423200000000", expectedGID);
    assertEquals("generated GID is same as expected", expectedGID, target.getGlobalID());
  }

  private ProjectGenerator createProjectGenerator(
      BuildRuleResolver buildRuleResolver, ImmutableList<BuildTarget> initialBuildTargets) {
    DependencyGraph graph = RuleMap.createGraphFromBuildRules(buildRuleResolver);
    ImmutableList.Builder<BuildTarget> targets = ImmutableList.builder();
    for (BuildRule rule : graph.getNodes()) {
      targets.add(rule.getBuildTarget());
    }
    PartialGraph partialGraph = PartialGraphFactory.newInstance(graph, targets.build());
    return new ProjectGenerator(
        partialGraph,
        initialBuildTargets,
        projectFilesystem,
        executionContext,
        OUTPUT_DIRECTORY,
        PROJECT_NAME);
  }

  /**
   * Create a xcode_native rule. Useful for testing the rule-type agnostic parts of the project
   * generator, as this is the simplest rule that the project generator handles.
   */
  private BuildRule createXcodeNativeRule(
      BuildTarget target, ImmutableSortedSet<BuildRule> deps) {
    BuildRuleParams buildRuleParams = new FakeBuildRuleParams(target, deps);
    XcodeNativeDescription.Arg arg = xcodeNativeDescription.createUnpopulatedConstructorArg();
    arg.projectContainerPath = new FileSourcePath("foo.xcodeproj");
    arg.targetGid = "00DEADBEEF";
    arg.product = "libfoo.a";
    return new DescribedRule(
        XcodeNativeDescription.TYPE,
        xcodeNativeDescription.createBuildable(buildRuleParams, arg),
        buildRuleParams);
  }

  private BuildRule createIosTestRule(
      BuildTarget target,
      ImmutableSortedSet<BuildRule> sourceUnderTest,
      ImmutableSortedSet<BuildRule> deps) {
    BuildRuleParams buildRuleParams = new FakeBuildRuleParams(target, deps);
    IosTestDescription.Arg arg = iosTestDescription.createUnpopulatedConstructorArg();
    arg.configs = ImmutableMap.of();
    arg.infoPlist = Paths.get("Info.plist");
    arg.frameworks = ImmutableSortedSet.of();
    arg.headers = ImmutableSortedSet.of();
    arg.resources = ImmutableSortedSet.of();
    arg.srcs = ImmutableList.of();
    arg.sourceUnderTest = sourceUnderTest;
    return new DescribedRule(
        iosTestDescription.getBuildRuleType(),
        iosTestDescription.createBuildable(buildRuleParams, arg),
        buildRuleParams);
  }

  private PBXTarget assertTargetExistsAndReturnTarget(PBXProject generatedProject, String name) {
    for (PBXTarget target : generatedProject.getTargets()) {
      if (target.getName().equals(name)) {
        return target;
      }
    }
    fail("No generated target with name: " + name);
    return null;
  }

  private void assertHasConfigurations(PBXTarget target, String... names) {
    Map<String, XCBuildConfiguration> buildConfigurationMap =
        target.getBuildConfigurationList().getBuildConfigurationsByName().asMap();
    assertEquals(
        "Configuration list has expected number of entries",
        names.length, buildConfigurationMap.size());

    for (String name : names) {
      XCBuildConfiguration configuration = buildConfigurationMap.get(name);

      assertNotNull("Configuration entry exists", configuration);
      assertEquals("Configuration name is same as key", name, configuration.getName());
      assertTrue(
          "Configuration has xcconfig file",
          configuration.getBaseConfigurationReference().getPath().endsWith(".xcconfig"));
    }
  }

  private void assertHasSingletonSourcesPhaseWithSourcesAndFlags(
      PBXTarget target,
      ImmutableMap<String, Optional<String>> sourcesAndFlags) {

    PBXSourcesBuildPhase sourcesBuildPhase =
        getSingletonPhaseByType(target, PBXSourcesBuildPhase.class);

    assertEquals(
        "Sources build phase should have correct number of sources",
        sourcesAndFlags.size(), sourcesBuildPhase.getFiles().size());

    // map keys to absolute paths
    ImmutableMap.Builder<String, Optional<String>> absolutePathFlagMapBuilder = ImmutableMap.builder();
    for (Map.Entry<String, Optional<String>> name : sourcesAndFlags.entrySet()) {
      absolutePathFlagMapBuilder.put(
          projectFilesystem.getRootPath().resolve(name.getKey()).toAbsolutePath().toString(),
          name.getValue());
    }
    ImmutableMap<String, Optional<String>> absolutePathFlagMap = absolutePathFlagMapBuilder.build();

    for (PBXBuildFile file : sourcesBuildPhase.getFiles()) {
      Optional<String> flags = absolutePathFlagMap.get(file.getFileRef().getPath());
      assertNotNull("Source file is expected", flags);
      if (flags.isPresent()) {
        assertEquals(
            "Build file path should be absolute",
            PBXFileReference.SourceTree.ABSOLUTE, file.getFileRef().getSourceTree());
        assertTrue("Build file should have settings dictionary", file.getSettings().isPresent());

        NSDictionary buildFileSettings = file.getSettings().get();
        NSString compilerFlags = (NSString) buildFileSettings.get("COMPILER_FLAGS");

        assertNotNull("Build file settings should have COMPILER_FLAGS entry", compilerFlags);
        assertEquals(
            "Build file settings should be expected value",
            flags.get(), compilerFlags.getContent());
      } else {
        assertFalse(
            "Build file should not have settings dictionary", file.getSettings().isPresent());
      }
    }
  }

  private void assertHasSingletonFrameworksPhaseWithFrameworkEntries(
      PBXTarget target, ImmutableList<String> frameworks) {
    PBXFrameworksBuildPhase buildPhase =
        getSingletonPhaseByType(target, PBXFrameworksBuildPhase.class);
    assertEquals("Framework phase should have right number of elements",
        frameworks.size(), buildPhase.getFiles().size());

    for (PBXBuildFile file : buildPhase.getFiles()) {
      PBXReference.SourceTree sourceTree = file.getFileRef().getSourceTree();
      switch (sourceTree) {
        case GROUP:
          fail("Should not emit frameworks with sourceTree <group>");
          break;
        case ABSOLUTE:
          fail("Should not emit frameworks with sourceTree <absolute>");
          break;
        default:
          String serialized = "$" + sourceTree + "/" + file.getFileRef().getPath();
          assertTrue(
              "Framework should be listed in list of expected frameworks: " + serialized,
              frameworks.contains(serialized));
          break;
      }
    }
  }

  private void assertHasSingletonResourcesPhaseWithEntries(PBXTarget target, String... resources) {
    PBXResourcesBuildPhase buildPhase =
        getSingletonPhaseByType(target, PBXResourcesBuildPhase.class);
    assertEquals("Resources phase should have right number of elements",
        resources.length, buildPhase.getFiles().size());

    ImmutableSet.Builder<String> expectedResourceSetBuilder = ImmutableSet.builder();
    for (String resource : resources) {
      expectedResourceSetBuilder.add(
          projectFilesystem.getRootPath().resolve(resource).toAbsolutePath().toString());
    }
    ImmutableSet<String> expectedResourceSet = expectedResourceSetBuilder.build();

    for (PBXBuildFile file : buildPhase.getFiles()) {
      String source = file.getFileRef().getPath();
      assertTrue(
          "Resource should be in list of expected resources: " + source,
          expectedResourceSet.contains(source));
    }
  }

  private static <T extends PBXBuildPhase> T getSingletonPhaseByType(
      PBXTarget target, final Class<T> cls) {
    Iterable<PBXBuildPhase> buildPhases =
        Iterables.filter(
            target.getBuildPhases(), new Predicate<PBXBuildPhase>() {
          @Override
          public boolean apply(PBXBuildPhase input) {
            return cls.isInstance(input);
          }
        });
    assertEquals("Build phase should be singleton", 1, Iterables.size(buildPhases));
    @SuppressWarnings("unchecked")
    T element = (T) Iterables.getOnlyElement(buildPhases);
    return element;
  }

  private void verifyGeneratedSignedSourceTarget(PBXTarget target) {
    Iterable<PBXShellScriptBuildPhase> shellSteps = Iterables.filter(
        target.getBuildPhases(), PBXShellScriptBuildPhase.class);
    assertEquals(1, Iterables.size(shellSteps));
    PBXShellScriptBuildPhase generatedScriptPhase = Iterables.get(shellSteps, 0);
    assertThat(
        generatedScriptPhase.getShellScript(),
        containsString(SourceSigner.SIGNED_SOURCE_PLACEHOLDER));
  }
}
