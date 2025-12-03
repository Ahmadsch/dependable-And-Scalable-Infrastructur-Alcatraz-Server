@echo off
echo Installing spread.jar into local Maven repo...

mvn install:install-file ^
  -Dfile=spread-bin-4.0.0\java\spread.jar ^
  -DgroupId=org.spread ^
  -DartifactId=spread ^
  -Dversion=4.0.0 ^
  -Dpackaging=jar

echo DONE.
pause
