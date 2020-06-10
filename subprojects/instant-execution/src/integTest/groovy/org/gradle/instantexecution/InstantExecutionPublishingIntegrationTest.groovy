/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.instantexecution


import static org.gradle.util.GFileUtils.deleteDirectory
import static org.gradle.util.GFileUtils.listFiles

class InstantExecutionPublishingIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    def setup() {
        buildFile << """
            // TODO - use public APIs when available
            import org.gradle.api.internal.attributes.*
            import org.gradle.api.internal.component.*
            class TestComponent implements SoftwareComponentInternal, ComponentWithVariants {
                String name
                Set usages = []
                Set variants = []
            }

            class TestUsage implements UsageContext {
                String name
                Usage usage
                Set dependencies = []
                Set dependencyConstraints = []
                Set artifacts = []
                Set capabilities = []
                Set globalExcludes = []
                AttributeContainer attributes = ImmutableAttributes.EMPTY
            }

            class TestVariant implements SoftwareComponentInternal {
                String name
                Set usages = []
            }

            class TestCapability implements Capability {
                String group
                String name
                String version
            }

            allprojects {
                configurations { implementation }
            }

            def testAttributes = project.services.get(ImmutableAttributesFactory)
                 .mutable()
                 .attribute(Attribute.of('foo', String), 'value')
        """
    }

    def "can publish maven publication metadata to local repository"() {
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven-publish'

            group = 'group'
            version = '1.0'

            def mainComponent = new TestComponent()
            mainComponent.usages.add(
                new TestUsage(
                    name: 'api',
                    usage: objects.named(Usage, 'api'),
                    dependencies: configurations.implementation.allDependencies.withType(ModuleDependency),
                    dependencyConstraints: configurations.implementation.allDependencyConstraints,
                    attributes: testAttributes
                )
            )

            dependencies {
                implementation("org:foo:1.0") {
                   because 'version 1.0 is tested'
                }
                constraints {
                    implementation("org:bar:2.0") {
                        because 'because 2.0 is cool'
                    }
                }
            }

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from mainComponent
                        withoutBuildIdentifier()
                    }
                }
            }
        """
        def instant = newInstantExecutionFixture()
        def metadataFile = file('build/publications/maven/module.json')
        def tasks = [
            'generateMetadataFileForMavenPublication',
            'generatePomFileForMavenPublication',
            'publishMavenPublicationToMavenRepository',
            'publishAllPublicationsToMavenRepository'
        ]

        when:
        instantRun(*tasks)

        then:
        instant.assertStateStored()
        metadataFile.exists()

        when:
        def storeTimeRepo = mavenRepoFiles()
        def storeTimeMetadata = metadataFile.text
        metadataFile.delete()
        deleteDirectory(mavenRepo.rootDir)
        instantRun(*tasks)

        then:
        instant.assertStateLoaded()
        def loadTimeRepo = mavenRepoFiles()
        storeTimeRepo == loadTimeRepo
        def loadTimeMetadata = metadataFile.text
        storeTimeMetadata == loadTimeMetadata
    }

    private Map<File, String> mavenRepoFiles() {
        listFiles(mavenRepo.rootDir, null, true)
            .collectEntries { [it, it.text] }
    }
}
