set GRAALVM_HOME=C:\Software\graalvm
set JAVA_HOME=%GRAALVM_HOME%
set Path=%GRAALVM_HOME%\bin;%Path%
"C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvars64.bat" && mvn native:compile -DskipTests -Pnative
