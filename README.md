# Steps to build .dmg/app file.

1. Make sure to use "liberica-jdk-21-full.jdk" which also contains JavaFX.
2. Make sure the JAVA_HOME is set to the above mentioned JDK
3. make a fat Jar file using "mvn clean package"
4. Finally run the following jpackage command.
```declarative
jpackage \
  --type dmg \
  --name "BlinkDrop" \
  --input target \
  --main-jar blinkdrop-1.0-SNAPSHOT-all.jar \
  --main-class com.blinkdrop.BlinkDropApp \
  --icon icns/icon.icns \
  --dest out \
  --app-version 1.0 \
  --java-options "-Xmx512m" \
  --vendor "Your name/Team name" \
  --mac-package-identifier com.blinkdrop \
  --mac-package-name "BlinkDrop" \
  --verbose
```