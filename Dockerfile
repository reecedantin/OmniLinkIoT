FROM arm32v7/maven:3.6.3-adoptopenjdk-8

RUN apt update
RUN apt install cmake build-essential libssl-dev -y

WORKDIR /usr/src/
RUN git clone --recursive https://github.com/awslabs/aws-crt-java.git
WORKDIR /usr/src/aws-crt-java
RUN mvn install -Dmaven.test.skip=true

WORKDIR /usr/src/
RUN git clone https://github.com/awslabs/aws-iot-device-sdk-java-v2.git
WORKDIR /usr/src/aws-iot-device-sdk-java-v2
RUN mvn clean install

COPY . /usr/src/omnilinkiot
WORKDIR /usr/src/omnilinkiot

RUN mvn clean install
RUN mvn dependency:copy-dependencies

WORKDIR /usr/src/omnilinkiot
CMD ["java", "-cp", "target/dependency/*:target/omnilinkiot-1.0-SNAPSHOT.jar", "com.dantin.omnilinkiot.Main", "/config/config.properties"]
