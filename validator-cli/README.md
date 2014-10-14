NIST to MassBank converter
==========================

It is required to install the included files into your local maven repository upfront:
```
mvn install:install-file -Dfile=./lib/massbank.jar -DgroupId=massbank  -DartifactId=massbank -Dversion=1.0 -Dpackaging=jar
mvn install:install-file -Dfile=./lib/massbank-admin.jar -DgroupId=massbank  -DartifactId=massbank-admin -Dversion=1.0 -Dpackaging=jar
```

Then compile the converter with

```
mvn clean package
```

run the result with:

```
java -jar target/validator-cli-0.0.1-SNAPSHOT.jar <path_to_records>
```



