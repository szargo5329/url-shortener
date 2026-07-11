plugins {
    java
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.urlshortener"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        // AWS SDK v2 BOM — keeps dynamodb + dynamodb-enhanced versions aligned
        mavenBom("software.amazon.awssdk:bom:2.29.45")
    }
}

dependencies {
    // Web + validation
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // DynamoDB (AWS SDK v2 + enhanced client)
    implementation("software.amazon.awssdk:dynamodb")
    implementation("software.amazon.awssdk:dynamodb-enhanced")

    // SQS (AWS SDK v2) — click-event analytics pipeline
    implementation("software.amazon.awssdk:sqs")

    // Redis (ElastiCache) cache layer
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("io.lettuce:lettuce-core")

    // AWS Lambda adapter for Spring Boot 3
    implementation("com.amazonaws.serverless:aws-serverless-java-container-springboot3:2.1.5")

    // AWS Lambda runtime interfaces + event models (handler entry points)
    implementation("com.amazonaws:aws-lambda-java-core:1.2.3")
    implementation("com.amazonaws:aws-lambda-java-events:3.11.4")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
