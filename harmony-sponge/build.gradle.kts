import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.shadow)
}

repositories {
	maven {
		name = "sponge"
		url = uri("https://repo.spongepowered.org/repository/maven-public/")
	}
}

dependencies {
	api(project(":harmony-api"))

	compileOnly("org.spongepowered:sponge:1.20.6-11.0.0-SNAPSHOT")
	compileOnly("com.google.guava:guava:33.2.0-jre")
}

kotlin {
	jvmToolchain(21)
}

tasks.withType<ShadowJar> {
	relocate("kotlin", "io.quut.harmony.libs.kotlin")
	relocate("org", "io.quut.harmony.libs.org") {
		exclude("org.spongepowered.**")
		exclude("org.apache.logging.**")
	}
}

tasks.named("assemble").configure {
	dependsOn("shadowJar")
}
