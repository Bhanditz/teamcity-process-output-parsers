/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.agent.messages.regex;

import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;

/**
 * Service Messages controlling Regex Translators:
 * 'RegexMessageParser.Add'
 * 'RegexMessageParser.Remove'
 * 'RegexMessageParser.Reset'
 * <p/>
 * Attributes:
 * 'file' - path to parser config file
 * 'name' - name of predefined (bundled) config file
 * 'scope' - ('Add' command) scope of new parser
 * Also simple argument may be used as 'file' or 'name' (system will distinguish attribute name automatically)
 *
 * Scope is either 'Build' or 'Runner' (case ignored)
 */
public abstract class ParserCommand {
  private static final String PREFIX = "RegexMessageParser.";
  public static final String COMMAND_ENABLE = PREFIX + "Enable";
  public static final String COMMAND_DISABLE = PREFIX + "Disable";
  public static final String COMMAND_RESET = PREFIX + "Reset";
  public static final String[] COMMANDS = {COMMAND_ENABLE, COMMAND_DISABLE, COMMAND_RESET};

  protected ParserCommand() {
  }

  public abstract void apply(@NotNull ParsersRegistry manipulator);

  public enum Scope {
    THIS_RUNNER("runner"),
    NEXT_RUNNER("next-runner"), // TODO: Do we really need that?
    BUILD("build"),; // Build

    private final String myId;

    Scope(final String id) {
      myId = id;
    }

    public static Scope get(final String name, final Scope defaultValue) {
      for (Scope v : values()) {
        if (v.myId.equals(name)) {
          return v;
        }
      }
      return defaultValue;
    }
  }

  public static class ParserId {
    private final String myName;
    private final String myResourcePath;
    private final String myFile;

    public ParserId(final String name, final String resourcePath, final String file) {
      myName = name;
      myResourcePath = resourcePath;
      myFile = file;
    }

    public String getName() {
      return myName;
    }

    public String getResourcePath() {
      return myResourcePath;
    }

    public String getFile() {
      return myFile;
    }
  }

  public abstract static class CommandWithScope extends ParserCommand {
    @NotNull
    private final Scope myScope;

    public CommandWithScope(@NotNull final Scope scope) {
      myScope = scope;
    }

    public CommandWithScope(@NotNull final ServiceMessage message) {
      myScope = getScope(message);
    }


    @NotNull
    public Scope getScope() {
      return myScope;
    }
  }

  public abstract static class CommandWithParserIdentifier extends CommandWithScope {
    @NotNull
    private final ParserId myParserId;

    public CommandWithParserIdentifier(@NotNull final Scope scope, @NotNull final ParserId parserId) {
      super(scope);
      myParserId = parserId;
    }

    public CommandWithParserIdentifier(@NotNull final ServiceMessage message) {
      super(message);
      myParserId = getParserId(message);
    }


    @NotNull
    public ParserId getParserId() {
      return myParserId;
    }
  }

  public static class Enable extends CommandWithParserIdentifier {
    public Enable(@NotNull final Scope scope, @NotNull final ParserId parserId) {
      super(scope, parserId);
    }

    public Enable(@NotNull final ServiceMessage message) {
      super(message);
    }

    @Override
    public void apply(@NotNull final ParsersRegistry manipulator) {
      manipulator.register(getParserId());
    }
  }

  public static class Disable extends CommandWithParserIdentifier {
    public Disable(@NotNull final Scope scope, @NotNull final ParserId parserId) {
      super(scope, parserId);
    }

    public Disable(@NotNull final ServiceMessage message) {
      super(message);
    }

    @Override
    public void apply(@NotNull final ParsersRegistry manipulator) {
      manipulator.unregister(getParserId());
    }
  }

  public static class Reset extends CommandWithScope {
    public Reset(@NotNull final Scope scope) {
      super(scope);
    }

    public Reset(@NotNull final ServiceMessage message) {
      super(message);
    }

    @Override
    public void apply(@NotNull final ParsersRegistry manipulator) {
      // TODO: Implement
    }
  }

  public static ParserCommand fromSM(@NotNull final ServiceMessage message) {
    final Map<String, String> attributes = message.getAttributes();
    final String command = message.getMessageName();
    assert command.startsWith(PREFIX);

    if (COMMAND_ENABLE.equals(command)) {
      return new Enable(message);
    } else if (COMMAND_DISABLE.equals(command)) {
      return new Disable(message);
    } else if (COMMAND_RESET.equals(command)) {
      return new Reset(message);
    } else {
      throw new IllegalArgumentException("Unsupported command type " + command);
    }
  }

  @NotNull
  protected static Scope getScope(@NotNull final ServiceMessage message) {
    return Scope.get(message.getAttributes().get("scope"), Scope.THIS_RUNNER);
  }

  @NotNull
  protected static ParserId getParserId(@NotNull final ServiceMessage message) {
    final Map<String, String> attributes = message.getAttributes();
    final String argument = message.getArgument();
    final String file = attributes.get("file");
    final String name = attributes.get("name");
    final String resource = attributes.get("resource");
    final String id = attributes.get("id");
    if (isEmptyOrSpaces(argument) && isEmptyOrSpaces(file) && isEmptyOrSpaces(name) && isEmptyOrSpaces(resource) && isEmptyOrSpaces(id)) {
      throw new IllegalArgumentException("Command requires either attribute 'name', 'file', 'id' or single argument. Actual message: " + message);
    }
    // TODO: improve
    return new ParserId(name, resource, file);
  }
}
