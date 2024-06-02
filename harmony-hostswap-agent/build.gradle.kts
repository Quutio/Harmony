plugins {
	id("java")
}

group = "io.quut"
version = "1.0-SNAPSHOT"

repositories {
	val gprUser: String? by project
	val gprPassword: String? by project

	maven {
		name = "github"
		url = uri("https://maven.pkg.github.com/quutio/HotswapAgent")
		credentials {
			username = gprUser ?: System.getenv("GITHUB_ACTOR")
			password = gprPassword ?: System.getenv("GITHUB_TOKEN")
		}
	}
}

dependencies {
	compileOnly("org.hotswapagent:hotswap-agent-sponge-plugin:1.4.2-SNAPSHOT")
}
