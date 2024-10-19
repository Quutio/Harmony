plugins {
	`maven-publish`
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.spotless)
}

allprojects {
	group = "io.quut"
	version = "1.2.2"

	apply(plugin = "com.diffplug.spotless")

	spotless {
		kotlin {
			ktlint()
			indentWithTabs()
			endWithNewline()
			trimTrailingWhitespace()
		}

		java {
			indentWithTabs()
			endWithNewline()
			trimTrailingWhitespace()
			removeUnusedImports()
		}

		kotlinGradle {
			ktlint()
			indentWithTabs()
			endWithNewline()
			trimTrailingWhitespace()
		}
	}

	repositories {
		mavenCentral()
	}
}

subprojects {
	apply(plugin = "java-library")
	apply(plugin = "maven-publish")

	java {
		withSourcesJar()
	}

	publishing {
		publications {
			register("harmony", MavenPublication::class) {
				from(components["java"])

				this.artifactId = project.name.lowercase()

				pom {
					this.name.set(project.name)
					this.description.set(project.description)
				}
			}

			val mavenUser: String? by project
			val mavenPassword: String? by project

			repositories {
				maven {
					this.name = "equelix-snapshots"
					this.url = uri("https://maven.quut.io/repository/maven-snapshots/")
					credentials {
						this.username = mavenUser
						this.password = mavenPassword
					}
				}
				maven {
					this.name = "GitHubPackages"
					this.url = uri("https://maven.pkg.github.com/Quutio/Harmony")
					credentials {
						this.username = System.getenv("GITHUB_ACTOR")
						this.password = System.getenv("GITHUB_TOKEN")
					}
				}
			}
		}
	}
}
