# 使用 OpenJDK 17 作为基础镜像
FROM openjdk:17-jdk-slim

# 设置工作目录
WORKDIR /app

# 下载 JAR 文件（替换 <VERSION> 为实际版本号，例如 1.0）
RUN apt-get update && apt-get install -y wget && \
    wget https://github.com/InvertGeek/mixfilecli/releases/download/<VERSION>/mixfile-cli-2.0.1.jar -O mixfile-cli.jar

# 设置环境变量（启用 ZGC 垃圾回收器）
ENV JAVA_OPTS="-XX:+UseZGC"

# 暴露端口（根据需要调整）
EXPOSE 8080

# 运行命令
CMD ["java", "-jar", "/app/mixfile-cli.jar"]
