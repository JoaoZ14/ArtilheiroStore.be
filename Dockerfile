# Estágio 1: build
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# Copiar POM e baixar dependências (cache de camadas)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copiar código e gerar JAR
COPY src ./src
RUN mvn package -DskipTests -B

# Estágio 2: execução
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Usuário não-root (segurança)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=build /app/target/store-0.0.1-SNAPSHOT.jar app.jar

# Render define PORT; expor para documentação
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
