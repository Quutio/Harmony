plugins {
	id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}
rootProject.name = "Harmony"
include("harmony-sponge")
include("harmony-api")
include("harmony-hostswap-agent")
