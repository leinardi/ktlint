plugins {
    id("ktlint-kotlin-common")
    `java-library`
    `maven-publish`
}

group = "com.github.username"

val sourcesJar by tasks.registering(Jar::class) {
    dependsOn(tasks.classes)
    archiveClassifier.set("sources")
    from(sourceSets.main.map { it.allSource })
}

val javadocJar by tasks.registering(Jar::class) {
    dependsOn(tasks.javadoc)
    archiveClassifier.set("javadoc")
    from(tasks.javadoc.map { it.destinationDir!! })
}

artifacts {
    archives(sourcesJar)
    archives(javadocJar)
}

val ktlint: Configuration by configurations.creating

dependencies {
    ktlint(projects.ktlintCli)

    implementation(projects.ktlintCliRulesetCore)
    implementation(projects.ktlintRuleEngineCore)

    testImplementation(projects.ktlintTest)
}

val skipTests: String = System.getProperty("skipTests", "false")
tasks.test {
    if (skipTests == "false") {
        useJUnitPlatform()
    } else {
        logger.warn("Skipping tests for task '$name' as system property 'skipTests=$skipTests'")
    }
}

tasks.register<JavaExec>("ktlint") {
    dependsOn(tasks.classes)
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    mainClass.set("com.pinterest.ktlint.Main")
    // Adding compiled classes of this ruleset to the classpath so that ktlint validates the ruleset using its own ruleset
    classpath(ktlint, sourceSets.main.map { it.output })
    args("--log-level=debug", "src/**/*.kt")
}.let {
    tasks.check.configure {
        dependsOn(it)
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            pom {
                licenses {
                    license {
                        name.set("The Apache Software License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }
            }
        }
    }
}
