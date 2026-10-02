#!/bin/sh
# Gradle wrapper 启动脚本

# 获取脚本所在目录
PRG="$0"
while [ -h "$PRG" ]; do
    ls=$(ls -ld "$PRG")
    link=${ls#*' -> '}
    case $link in
      /*) PRG="$link" ;;
      *) PRG=$(dirname "$PRG")/"$link" ;;
    esac
done
SAVED="`pwd`"
cd "$(dirname "$PRG")/" >/dev/null
APP_HOME="`pwd -P`"
cd "$SAVED" >/dev/null

# 确定 JAVA_HOME
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# wrapper jar 路径
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$CLASSPATH" ]; then
    echo "ERROR: 找不到 $CLASSPATH"
    exit 1
fi

exec "$JAVACMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
