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

import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import static groovy.io.FileType.FILES

def arguments = new ArrayList<String>(Arrays.asList(args));
def verbose = false
def useFirstFile = false
if (arguments.contains('-v')) {
    arguments.remove('-v')
    verbose = true;
}
if (arguments.contains('--use-first')) {
    arguments.remove('--use-first')
    useFirstFile = true;
}

if (arguments.size() < 2) {
    System.err.println("\033[0;31mIncorrect arguments count: expected: <path to output> <path to TeamCity distribution>\033[0m")
    System.exit(1);
}

final Path output = Paths.get(arguments.get(0))
final List<File> inputs = new ArrayList<>()
final Path teamcity = Paths.get(arguments.get(1))

inputs.add(teamcity.resolve("buildAgent/lib/").toAbsolutePath().toFile())
inputs.add(teamcity.resolve("webapps/ROOT/WEB-INF/lib/").toAbsolutePath().toFile())
inputs.add(teamcity.resolve("devPackage/tests/").toAbsolutePath().toFile())

// Additional inputs
for (int i = 2; i < arguments.size(); i++) {
    String input = arguments.get(i);
    inputs.add(new File(input))
}
String build = null
for (Path file : Files.newDirectoryStream(teamcity)) {
    def name = file.getFileName().toString()
    if (name.startsWith("BUILD_")) {
        build = name.substring("BUILD_".length())
        break;
    }
}

if (build == null) {
    System.err.println("\033[0;31mCannot determine TeamCity version in directory ${teamcity.toAbsolutePath().toString()}, there no file 'BUILD_XXXXX' found\033[0m")
    System.exit(2)
}

if (!Files.exists(output)) Files.createDirectory(output)
if (!Files.exists(output.resolve("poms"))) Files.createDirectory(output.resolve("poms"))

def jars = new HashMap<String, Path>()
def error = false;
for (File input : inputs) {
    input.eachFileRecurse(FILES) {
        if (it.name.endsWith('.jar')) {
            def current = Paths.get(it.getAbsolutePath())
            if (jars.containsKey(it.name)) {
                def prev = jars.get(it.name)
                if (Files.size(prev) != Files.size(current)) {
                    println((useFirstFile ? "WARN:" : "ERROR:") + " Jars map already have key '${it.name}' and files size is differrent. Paths:\n\t${prev}\n\t${current}")
                    error |= !useFirstFile;
                } else if (verbose) {
                    println "WARN: Jars map already have key '${it.name}' with same file, but ifferent path. Paths:\n\t${prev}\n\t${current}"
                }
            } else {
                jars.put(it.name, current);
            }
        }
    }
}

if (error) {
    System.err.println("Error during jars collecting. See above.")
    System.exit(3);
}

def dependencies = new StringBuilder();

def install = new ArrayList<Tuple>()
for (Path jar : jars.values()) {
    def name = jar.getFileName().toString()
    def artifactId = name.substring(0, name.length() - 4)
    String pomContent = """
<project xmlns="http://maven.apache.org/POM/4.0.0" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.jetbrains.teamcity.internal.generated</groupId>
  <artifactId>${artifactId}</artifactId>
  <version>SNAPSHOT</version>
  <properties>
    <teamcity.build>${build}</teamcity.build>
  </properties>
</project>
"""
    def pom = output.resolve("poms/${name}.pom")
    if (Files.exists(pom)) Files.delete(pom)
    pom = Files.createFile(pom)
    pom = Files.write(pom, Arrays.<String> asList(pomContent), Charset.forName("UTF-8"));
    Tuple tuple = [pom, jar]
    install.add(tuple)

    dependencies.append """
<dependency>
  <groupId>org.jetbrains.teamcity.internal.generated</groupId>
  <artifactId>${artifactId}</artifactId>
  <version>SNAPSHOT</version>
</dependency>
"""
}


def script = new StringBuilder();
script.append """<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.jetbrains.teamcity</groupId>
  <artifactId>local-artifact-installer</artifactId>
  <version>1.0-SNAPSHOT</version>
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-install-plugin</artifactId>
        <executions>
"""
int i = 0;
for (Tuple t : install) {
    def (pom, jar) = t;
    script.append """
          <execution>
            <id>install${i++}</id>
            <phase>package</phase>
            <goals>
              <goal>install-file</goal>
            </goals>
            <configuration>
              <file>${jar.toAbsolutePath().toString()}</file>
              <pomFile>${pom.toAbsolutePath().toString()}</pomFile>
            </configuration>
          </execution>"""

}

script.append """
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
  """

println "Generated poms for ${install.size()} files"

def installPom = output.resolve("install-pom.xml")
if (Files.exists(installPom)) Files.delete(installPom)
Files.createFile(installPom);
Files.write(installPom, Arrays.<String> asList(script.toString()), Charset.forName("UTF-8"))

def dependenciesXml = output.resolve("dependencies-pom.xml")
if (Files.exists(dependenciesXml)) Files.delete(dependenciesXml)
Files.createFile(dependenciesXml);
Files.write(dependenciesXml, Arrays.<String> asList(dependencies.toString()), Charset.forName("UTF-8"))


