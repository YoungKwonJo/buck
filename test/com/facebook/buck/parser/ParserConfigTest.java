/*
 * Copyright 2015-present Facebook, Inc.
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

package com.facebook.buck.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.facebook.buck.cli.BuckConfig;
import com.facebook.buck.cli.BuckConfigTestUtils;
import com.facebook.buck.cli.FakeBuckConfig;
import com.facebook.buck.io.ExecutableFinder;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.testutil.integration.TemporaryPaths;
import com.facebook.buck.util.HumanReadableException;
import com.facebook.buck.util.WatchmanWatcher.CursorType;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ParserConfigTest {

  @Rule public TemporaryPaths temporaryFolder = new TemporaryPaths();

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testGetAllowEmptyGlobs() throws IOException {
    assertTrue(FakeBuckConfig.builder().build().getView(ParserConfig.class).getAllowEmptyGlobs());
    Reader reader = new StringReader(Joiner.on('\n').join("[build]", "allow_empty_globs = false"));
    ParserConfig config =
        BuckConfigTestUtils.createWithDefaultFilesystem(temporaryFolder, reader)
            .getView(ParserConfig.class);
    assertFalse(config.getAllowEmptyGlobs());
  }

  @Test
  public void testGetGlobHandler() throws IOException {
    assertThat(
        FakeBuckConfig.builder().build().getView(ParserConfig.class).getGlobHandler(),
        Matchers.equalTo(ParserConfig.GlobHandler.PYTHON));

    for (ParserConfig.GlobHandler handler : ParserConfig.GlobHandler.values()) {
      Reader reader =
          new StringReader(
              Joiner.on('\n').join("[project]", "glob_handler = " + handler.toString()));
      ParserConfig config =
          BuckConfigTestUtils.createWithDefaultFilesystem(temporaryFolder, reader)
              .getView(ParserConfig.class);
      assertThat(config.getGlobHandler(), Matchers.equalTo(handler));
    }
  }

  @Test
  public void testGetWatchCells() throws IOException {
    assertTrue(
        "watch_cells defaults to true",
        FakeBuckConfig.builder().build().getView(ParserConfig.class).getWatchCells());

    Reader reader = new StringReader(Joiner.on('\n').join("[project]", "watch_cells = false"));
    ParserConfig config =
        BuckConfigTestUtils.createWithDefaultFilesystem(temporaryFolder, reader)
            .getView(ParserConfig.class);
    assertFalse(config.getWatchCells());

    reader = new StringReader(Joiner.on('\n').join("[project]", "watch_cells = true"));
    config =
        BuckConfigTestUtils.createWithDefaultFilesystem(temporaryFolder, reader)
            .getView(ParserConfig.class);
    assertTrue(config.getWatchCells());
  }

  @Test
  public void testGetWatchmanCursor() throws IOException {
    assertEquals(
        "watchman_cursor defaults to clock_id",
        CursorType.CLOCK_ID,
        FakeBuckConfig.builder().build().getView(ParserConfig.class).getWatchmanCursor());

    Reader reader = new StringReader(Joiner.on('\n').join("[project]", "watchman_cursor = named"));
    ParserConfig config =
        BuckConfigTestUtils.createWithDefaultFilesystem(temporaryFolder, reader)
            .getView(ParserConfig.class);
    assertEquals(CursorType.NAMED, config.getWatchmanCursor());

    reader = new StringReader(Joiner.on('\n').join("[project]", "watchman_cursor = clock_id"));
    config =
        BuckConfigTestUtils.createWithDefaultFilesystem(temporaryFolder, reader)
            .getView(ParserConfig.class);
    assertEquals(CursorType.CLOCK_ID, config.getWatchmanCursor());

    reader =
        new StringReader(Joiner.on('\n').join("[project]", "watchman_cursor = some_trash_value"));
    config =
        BuckConfigTestUtils.createWithDefaultFilesystem(temporaryFolder, reader)
            .getView(ParserConfig.class);

    thrown.expect(HumanReadableException.class);
    config.getWatchmanCursor();
  }

  @Test
  public void shouldReturnThreadCountIfParallelParsingIsEnabled() {
    BuckConfig config =
        FakeBuckConfig.builder()
            .setSections("[project]", "parsing_threads = 2", "parallel_parsing = true")
            .build();

    ParserConfig parserConfig = config.getView(ParserConfig.class);

    assertTrue(parserConfig.getEnableParallelParsing());
    assertEquals(2, parserConfig.getNumParsingThreads());
  }

  @Test
  public void shouldReturnOneThreadCountIfParallelParsingIsNotEnabled() {
    BuckConfig config =
        FakeBuckConfig.builder()
            .setSections("[project]", "parsing_threads = 3", "parallel_parsing = false")
            .build();

    ParserConfig parserConfig = config.getView(ParserConfig.class);

    assertFalse(parserConfig.getEnableParallelParsing());
    assertEquals(1, parserConfig.getNumParsingThreads());
  }

  @Test
  public void shouldGetReadOnlyDirs() throws IOException {
    String existingPath1 = "tmp/tmp-file";
    String existingPath2 = "tmp2/tmp2-file";
    ImmutableSet<Path> readOnlyPaths =
        ImmutableSet.of(Paths.get(existingPath1), Paths.get(existingPath2));
    ProjectFilesystem filesystem = new FakeProjectFilesystem(readOnlyPaths);

    ParserConfig parserConfig =
        FakeBuckConfig.builder()
            .setSections("[project]", "read_only_paths = " + existingPath1 + "," + existingPath2)
            .setFilesystem(filesystem)
            .build()
            .getView(ParserConfig.class);

    assertTrue(parserConfig.getReadOnlyPaths().isPresent());
    assertEquals(
        parserConfig.getReadOnlyPaths().get(),
        ImmutableList.of(
            filesystem.resolve(Paths.get(existingPath1)),
            filesystem.resolve(Paths.get(existingPath2))));

    String notExistingDir = "not/existing/path";
    parserConfig =
        FakeBuckConfig.builder()
            .setSections("[project]", "read_only_paths = " + notExistingDir)
            .setFilesystem(filesystem)
            .build()
            .getView(ParserConfig.class);

    thrown.expect(HumanReadableException.class);
    parserConfig.getReadOnlyPaths();
  }

  @Test
  public void testGetBuildFileImportWhitelist() throws IOException {
    assertTrue(
        FakeBuckConfig.builder()
            .build()
            .getView(ParserConfig.class)
            .getBuildFileImportWhitelist()
            .isEmpty());

    Reader reader =
        new StringReader(
            Joiner.on('\n').join("[project]", "build_file_import_whitelist = os, foo"));
    ParserConfig config =
        BuckConfigTestUtils.createWithDefaultFilesystem(temporaryFolder, reader)
            .getView(ParserConfig.class);
    assertEquals(ImmutableList.of("os", "foo"), config.getBuildFileImportWhitelist());
  }

  @Test
  public void whenParserPythonIsExecutableFileThenItIsUsed() throws IOException {
    Path configPythonFile = temporaryFolder.newExecutableFile("python");
    ParserConfig parserConfig =
        FakeBuckConfig.builder()
            .setSections(
                ImmutableMap.of(
                    "parser",
                    ImmutableMap.of(
                        "python_interpreter", configPythonFile.toAbsolutePath().toString())))
            .build()
            .getView(ParserConfig.class);
    assertEquals(
        "Should return path to temp file.",
        configPythonFile.toAbsolutePath().toString(),
        parserConfig.getPythonInterpreter(new ExecutableFinder()));
  }

  @Test(expected = HumanReadableException.class)
  public void whenParserPythonDoesNotExistThenItIsNotUsed() throws IOException {
    String invalidPath = temporaryFolder.getRoot().toAbsolutePath() + "DoesNotExist";
    ParserConfig parserConfig =
        FakeBuckConfig.builder()
            .setSections(
                ImmutableMap.of("parser", ImmutableMap.of("python_interpreter", invalidPath)))
            .build()
            .getView(ParserConfig.class);
    parserConfig.getPythonInterpreter(new ExecutableFinder());
    fail("Should throw exception as python config is invalid.");
  }

  @Test
  public void whenParserPythonIsNotSetFallbackIsUsed() throws IOException {
    Path configPythonFile = temporaryFolder.newExecutableFile("python");
    // This sets the python.interpreter section, not parser.python_interpreter
    ParserConfig parserConfig =
        FakeBuckConfig.builder()
            .setSections(
                ImmutableMap.of(
                    "python",
                    ImmutableMap.of("interpreter", configPythonFile.toAbsolutePath().toString())))
            .build()
            .getView(ParserConfig.class);
    assertEquals(
        "Should return path to temp file.",
        configPythonFile.toAbsolutePath().toString(),
        parserConfig.getPythonInterpreter(new ExecutableFinder()));
  }

  @Test
  public void whenParserPythonPathIsNotSetDefaultIsUsed() {
    ParserConfig parserConfig = FakeBuckConfig.builder().build().getView(ParserConfig.class);
    assertEquals(
        "Should return an empty optional",
        "<not set>",
        parserConfig.getPythonModuleSearchPath().orElse("<not set>"));
  }

  @Test
  public void whenParserPythonPathIsSet() {
    ParserConfig parserConfig =
        FakeBuckConfig.builder()
            .setSections(
                ImmutableMap.of("parser", ImmutableMap.of("python_path", "foobar:spamham")))
            .build()
            .getView(ParserConfig.class);
    assertEquals(
        "Should return the configured string",
        "foobar:spamham",
        parserConfig.getPythonModuleSearchPath().orElse("<not set>"));
  }
}
