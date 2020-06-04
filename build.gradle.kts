plugins {
    java
}

repositories {
    jcenter()
}

dependencies {

}

tasks.withType(Jar::class) {
    manifest {
        attributes["Main-Class"] = "link.infra.tailor.agent.App"
        attributes["Premain-Class"] = "link.infra.tailor.agent.Agent"
        attributes["Agent-Class"] = "link.infra.tailor.agent.Agent"
        attributes["Can-Redefine-Classes"] = "true"
        attributes["Can-Retransform-Classes"] = "true"
    }
}
