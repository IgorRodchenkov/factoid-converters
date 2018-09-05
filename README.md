# factoid-biopax-services

A java web server to convert [factoid](https://github.com/PathwayCommons/factoid/) documents to BioPAX and SBGN. 
It uses [Paxtools](https://github.com/BioPAX/Paxtools) to generate a BioPAX model.

## Installation

```
git clone https://github.com/PathwayCommons/factoid-biopax-services.git
cd factoid-biopax-services
mvn clean package
mvn tomcat7:run
```

## Deploying to an existing Tomcat7
The project uses Tomcat7 Maven Plugin to automate deployment process. You should have Apache Tomcat running on port 8080. 
You should have a user authenticated for both Tomcat and Maven. You would like to see "Tomcat Authentication" and 
"Maven Authentication" steps of [this tutorial](https://www.mkyong.com/maven/how-to-deploy-maven-based-war-file-to-tomcat/) 
for "Tomcat 7 example" if you have not authenticated a user yet. Server id in your `settings.xml` must be same 
with `tomcat.server.name` property in `pom.xml`, otherwise you would like to overwrite or override (if possible) 
this property.

After creating an authorized user you can run `mvn tomcat7:deploy`, `mvn tomcat7:undeploy` 
and `mvn tomcat7:redeploy` to manage deployment process.

## Deploying to Docker Container
You can deploy the server to a docker container by following the steps below. 
"<PORT>" must be replaced by the port number where the server is expected to be run. 

```
docker build -f Dockerfile -t factoid-biopax .
docker run -it --rm --name factoid-biopax --publish <PORT>:8080 factoid-biopax 
```

To create the war file with another name than "FactoidBiopaxSrv"(war name affects where the service is deployed), 
add `--build-arg TARGET_WAR_NAME=<WAR_NAME>` to the build command.

## Consuming the Service
After completing installation and deployment steps the server will be up and running 
at "http://localhost:8080/FactoidBiopaxSrv" (You should update port number and path in case you use another ones). 
You can send a post request to `http://localhost:8080/FactoidBiopaxSrv/ConvertToOwl` to consume the service. 

The service takes a String in JSON array format (see Input section below) and returns 
the BioPAX Level3 model as RDF/XML string; try, e.g:

```
cd src/test/resources
curl -XPOST -d "@test.json" http://localhost:8080/FactoidBiopaxSrv/ConvertToOwl
```

The following is a sample Node.js client

```
let url = 'http://localhost:8080/FactoidBiopaxSrv/ConvertToOwl';
let content = fs.readFileSync('input/templates.json', 'utf8');

Promise.try( () => fetch( url, {
        method: 'POST',
        body:    content,
        headers: { 'Content-Type': 'application/json' }
    } ) )
    .then( res => res.text() )
    .then( res => {
      // we have biopax result here as a String
      console.log(res);
    } );
```

## Input
TODO: Fill here with a sample input JSON array