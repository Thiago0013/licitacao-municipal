# ESTÁGIO 1: Build
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# ESTÁGIO 2: Execução
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

ENV TZ=America/Fortaleza

EXPOSE 8080

# Subindo a aposta: 450MB para o Java tentar carregar o modelo ONNX na RAM
ENTRYPOINT ["java", "-Xmx450m", "-jar", "app.jar"]